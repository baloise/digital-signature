package com.baloise.confluence.digitalsignature.migration;

import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.AccessScope;
import com.atlassian.migration.app.ConfluenceSpaceContainerV1;
import com.atlassian.migration.app.ContainerType;
import com.atlassian.migration.app.ContainerV1;
import com.atlassian.migration.app.ForgeEnvironmentName;
import com.atlassian.migration.app.PaginatedContainers;
import com.atlassian.migration.app.confluence.ConfluenceAppCloudMigrationListenerV1;
import com.atlassian.migration.app.gateway.AppCloudForgeMigrationGateway;
import com.atlassian.migration.app.gateway.MigrationDetailsV1;
import com.atlassian.migration.app.listener.DiscoverableForgeListener;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.baloise.confluence.digitalsignature.Signature2;
import com.google.gson.JsonObject;

/**
 * Cloud Migration Assistant (CMA) listener for the Digital Signature plugin.
 *
 * <p>Exports all signature contracts stored in Bandana as a JSONL.gz payload so
 * the Forge cloud app can import them into its SQL storage during migration.
 * Also provides the macro key mapping so migrated macros render correctly in Cloud.
 *
 * <p>Corresponds to backlog item i0051.
 */
@ExportAsService({DiscoverableForgeListener.class, ConfluenceAppCloudMigrationListenerV1.class})
@Component
public class DigitalSignatureMigrationListener implements DiscoverableForgeListener, ConfluenceAppCloudMigrationListenerV1 {

    private static final Logger log = LoggerFactory.getLogger(DigitalSignatureMigrationListener.class);

    static final String FORGE_APP_ID = "bab5617e-dc42-4ca8-ad38-947c826fe58c";
    static final String SERVER_APP_KEY = "com.baloise.confluence.digital-signature";
    // Server macro NAME exactly as it appears in storage / atlassian-plugin.xml
    // (<xhtml-macro name="signature">). CMA keys the macro mapping by this source
    // name, so it MUST be "signature" — not the plugin key — or CMA won't convert
    // the macro and Cloud shows "Error loading the extension!".
    static final String SERVER_MACRO_KEY = "signature";
    // Forge macro key — the macro module key in the Cloud app's manifest.yml.
    static final String FORGE_MACRO_KEY = "digital-signature";

    private final BandanaManager bandanaManager;
    private final UserAccessor userAccessor;
    private final PageManager pageManager;

    public DigitalSignatureMigrationListener(@ComponentImport BandanaManager bandanaManager,
                                              @ComponentImport UserAccessor userAccessor,
                                              @ComponentImport PageManager pageManager) {
        this.bandanaManager = bandanaManager;
        this.userAccessor = userAccessor;
        this.pageManager = pageManager;
    }

    @Override
    public UUID getForgeAppId() {
        return UUID.fromString(FORGE_APP_ID);
    }

    /**
     * Forge environment the migration data is routed to.
     *
     * <p>Defaults to {@code production} (the shipping behaviour). Overridable via the
     * {@code ds.forge.migration.environment} JVM system property (e.g. {@code development})
     * so the end-to-end migration test can target a development installation without
     * touching production data. See {@code docs/cma-migration-e2e.md} in the Cloud app repo.
     */
    @Override
    public String getForgeEnvironmentName() {
        return System.getProperty("ds.forge.migration.environment", ForgeEnvironmentName.PRODUCTION);
    }

    @Override
    public String getCloudAppKey() {
        return SERVER_APP_KEY;
    }

    @Override
    public String getServerAppKey() {
        return SERVER_APP_KEY;
    }

    @Override
    public Map<String, String> getServerToForgeMacroMapping() {
        return Map.of(SERVER_MACRO_KEY, FORGE_MACRO_KEY);
    }

    @Override
    public Set<AccessScope> getDataAccessScopes() {
        return Set.of(
            AccessScope.APP_DATA_OTHER,
            AccessScope.PRODUCT_DATA_OTHER,
            AccessScope.MIGRATION_TRACING_IDENTITY,
            AccessScope.MIGRATION_TRACING_PRODUCT
        );
    }

    /**
     * Exports signature contracts from Bandana as a JSONL.gz stream.
     *
     * <p>Each line in the output is a JSON object with fields:
     * {@code hash}, {@code pageId}, {@code title}, {@code body}, {@code signatures}.
     * The {@code signatures} map uses server usernames as keys; the Forge importer
     * resolves them to Cloud account IDs via the CMA Mappings API.
     *
     * <p>The export is <strong>scoped to the spaces being migrated</strong> (resolved from the
     * migration's containers): the global Bandana store can hold thousands of unrelated
     * {@code signature.*} entries, so exporting all of them would bloat the payload and ship
     * unrelated production signatures to the Cloud site. A full-site migration (or a failure to
     * determine the scope) falls back to exporting everything.
     */
    @Override
    public void onStartAppMigration(AppCloudForgeMigrationGateway gateway, MigrationDetailsV1 migrationDetails) {
        long startedAt = System.currentTimeMillis();
        int scanned = 0;
        int exported = 0;
        int totalSignatures = 0;
        int skippedOutOfScope = 0;
        int corrupt = 0;

        Set<String> inScopeSpaceKeys = new HashSet<>();
        boolean exportAll = resolveInScopeSpaceKeys(gateway, inScopeSpaceKeys);
        log.info("Migration export scope: {}", exportAll ? "ALL spaces (full-site / undetermined)" : inScopeSpaceKeys);

        try (OutputStream raw = gateway.createAppData("signatures");
             GZIPOutputStream gzip = new GZIPOutputStream(raw);
             OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {

            Iterable<String> keys = bandanaManager.getKeys(GLOBAL_CONTEXT);
            if (keys != null) {
                for (String key : keys) {
                    if (!key.startsWith("signature.")) {
                        continue;
                    }
                    scanned++;
                    // Read + deserialize directly. Avoids Signature2.fromBandana(), which re-fetches
                    // and streams ALL Bandana keys on every call (O(n^2) across the full store).
                    Signature2 sig = deserialize(key);
                    if (sig == null) {
                        corrupt++;
                        continue;
                    }
                    if (!exportAll && !isInScope(sig, inScopeSpaceKeys)) {
                        skippedOutOfScope++;
                        continue;
                    }
                    writer.write(toJsonLine(sig));
                    writer.write('\n');
                    exported++;
                    totalSignatures += sig.getSignatures().size();
                }
            }
        } catch (IOException e) {
            log.error("Failed to export signature data for migration", e);
            writeMigrationDebug(gateway, startedAt, exportAll, inScopeSpaceKeys, scanned, exported,
                    totalSignatures, skippedOutOfScope, corrupt, "FAILED: " + e.getMessage());
            throw new RuntimeException("Migration export failed", e);
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("Migration export complete: scanned={} exported={} signatures={} skippedOutOfScope={} corrupt={} elapsedMs={}",
                scanned, exported, totalSignatures, skippedOutOfScope, corrupt, elapsedMs);
        writeMigrationDebug(gateway, startedAt, exportAll, inScopeSpaceKeys, scanned, exported,
                totalSignatures, skippedOutOfScope, corrupt, "OK");
        gateway.completeExport();
    }

    /**
     * Writes a per-transfer migration-export summary to GLOBAL Bandana so it can be read from ANY
     * cluster node via ScriptRunner (file logs land on whichever node ran the export). Keys:
     * {@code ds.migration.debug.<transferId>} and a rolling {@code ds.migration.debug.last}.
     * These keys do not start with {@code signature.} so they are never themselves exported.
     */
    private void writeMigrationDebug(AppCloudForgeMigrationGateway gateway, long startedAt, boolean exportAll,
                                     Set<String> inScopeSpaceKeys, int scanned, int exported, int totalSignatures,
                                     int skippedOutOfScope, int corrupt, String status) {
        String transferId;
        try {
            transferId = gateway.getTransferId();
        } catch (Exception e) {
            transferId = "unknown";
        }
        try {
            JsonObject d = new JsonObject();
            d.addProperty("transferId", transferId);
            d.addProperty("status", status);
            d.addProperty("forgeEnv", getForgeEnvironmentName());
            d.addProperty("exportAll", exportAll);
            d.addProperty("inScopeSpaceKeys", String.valueOf(inScopeSpaceKeys));
            d.addProperty("scanned", scanned);
            d.addProperty("exported", exported);
            d.addProperty("totalSignatures", totalSignatures);
            d.addProperty("skippedOutOfScope", skippedOutOfScope);
            d.addProperty("corrupt", corrupt);
            d.addProperty("elapsedMs", System.currentTimeMillis() - startedAt);
            d.addProperty("macroMapping", String.valueOf(getServerToForgeMacroMapping()));
            d.addProperty("startedAtEpochMs", startedAt);
            String json = Signature2.GSON.toJson(d);
            bandanaManager.setValue(GLOBAL_CONTEXT, "ds.migration.debug." + transferId, json);
            bandanaManager.setValue(GLOBAL_CONTEXT, "ds.migration.debug.last", json);
            log.info("Migration debug → Bandana ds.migration.debug.{} (and .last): {}", transferId, json);
        } catch (Exception e) {
            log.warn("Could not write migration debug to Bandana: {}", e.getMessage());
        }
    }

    /**
     * Populates {@code inScopeSpaceKeys} with the space keys being migrated.
     *
     * @return {@code true} if the export should include <em>all</em> spaces — i.e. a full-site
     *         migration, or the scope could not be determined (safe fallback: export everything).
     */
    private boolean resolveInScopeSpaceKeys(AppCloudForgeMigrationGateway gateway, Set<String> inScopeSpaceKeys) {
        int siteContainers = 0;
        int spaceContainers = 0;
        try {
            PaginatedContainers site = gateway.getPaginatedContainers(ContainerType.Site, 50);
            while (site != null && site.next()) {
                siteContainers += site.getContainers().size();
            }
            PaginatedContainers spaces = gateway.getPaginatedContainers(ContainerType.ConfluenceSpace, 100);
            while (spaces != null && spaces.next()) {
                for (ContainerV1 container : spaces.getContainers()) {
                    spaceContainers++;
                    if (container instanceof ConfluenceSpaceContainerV1) {
                        inScopeSpaceKeys.add(((ConfluenceSpaceContainerV1) container).getKey());
                    }
                }
            }
            log.info("Migration scope probe: siteContainers={} confluenceSpaceContainers={} spaceKeys={}",
                    siteContainers, spaceContainers, inScopeSpaceKeys);
            if (siteContainers > 0) {
                return true; // full-site migration → export all
            }
        } catch (Exception e) {
            log.warn("Could not determine migration scope; exporting all signatures. {}", e.getMessage());
            return true;
        }
        if (inScopeSpaceKeys.isEmpty()) {
            log.warn("Migration scope resolved to zero spaces; exporting all signatures as a safe fallback.");
            return true;
        }
        return false;
    }

    /** True if the signature's page belongs to one of the in-scope spaces. */
    private boolean isInScope(Signature2 sig, Set<String> inScopeSpaceKeys) {
        try {
            Page page = pageManager.getPage(sig.getPageId());
            return page != null && inScopeSpaceKeys.contains(page.getSpaceKey());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Deserializes a Bandana {@code signature.*} value. New entries are GSON JSON strings; the
     * rare legacy object form is delegated to {@link Signature2#fromBandana}.
     */
    private Signature2 deserialize(String key) {
        try {
            Object value = bandanaManager.getValue(GLOBAL_CONTEXT, key);
            if (value == null) {
                return null;
            }
            if (value instanceof String) {
                return Signature2.GSON.fromJson((String) value, Signature2.class);
            }
            return Signature2.fromBandana(bandanaManager, key); // legacy Signature object format
        } catch (Exception e) {
            log.warn("Skipping null/corrupt Bandana entry {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Serializes one {@link Signature2} into a single JSONL line (no trailing newline).
     *
     * <p>The {@code signatures} map is re-keyed from usernames to Confluence userKeys
     * (internal IDs like {@code 2c9680839d34a92c019d34add3010000}) because the CMA
     * Mappings API on the Cloud side requires {@code confluence.userkey/<userKey>} format.
     *
     * <p>Omits {@code missingSignatures} and {@code notify} — the Forge app
     * reconstructs required signers from the current macro config at render time.
     */
    String toJsonLine(Signature2 sig) {
        JsonObject obj = new JsonObject();
        obj.addProperty("hash", sig.getHash());
        obj.addProperty("pageId", sig.getPageId());
        obj.addProperty("title", sig.getTitle());
        obj.addProperty("body", sig.getBody());

        // Re-key signatures: username → userKey, export dates as epoch millis (timezone-safe)
        JsonObject sigs = new JsonObject();
        for (Map.Entry<String, Date> entry : sig.getSignatures().entrySet()) {
            String username = entry.getKey();
            String userKey = resolveUserKey(username);
            sigs.addProperty(userKey != null ? userKey : username, entry.getValue().getTime());
        }
        obj.add("signatures", sigs);
        return Signature2.GSON.toJson(obj);
    }

    private String resolveUserKey(String username) {
        try {
            var user = userAccessor.getUserByName(username);
            if (user != null) {
                String key = user.getKey() != null ? user.getKey().getStringValue() : null;
                if (key != null) {
                    log.debug("Resolved username '{}' → userKey '{}'", username, key);
                    return key;
                }
            }
            log.warn("Could not resolve userKey for username: {}", username);
        } catch (Exception e) {
            log.warn("Error resolving userKey for username '{}': {}", username, e.getMessage());
        }
        return null;
    }
}
