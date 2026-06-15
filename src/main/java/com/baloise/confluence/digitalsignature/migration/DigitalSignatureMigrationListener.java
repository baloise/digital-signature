package com.baloise.confluence.digitalsignature.migration;

import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.AccessScope;
import com.atlassian.migration.app.ForgeEnvironmentName;
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
    // Server macro key (macro name in atlassian-plugin.xml) → Forge macro key
    static final String SERVER_MACRO_KEY = "digital-signature";
    static final String FORGE_MACRO_KEY = "digital-signature";

    private final BandanaManager bandanaManager;
    private final UserAccessor userAccessor;

    public DigitalSignatureMigrationListener(@ComponentImport BandanaManager bandanaManager,
                                              @ComponentImport UserAccessor userAccessor) {
        this.bandanaManager = bandanaManager;
        this.userAccessor = userAccessor;
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
     * Exports all signature contracts from Bandana as a JSONL.gz stream.
     *
     * <p>Each line in the output is a JSON object with fields:
     * {@code hash}, {@code pageId}, {@code title}, {@code body}, {@code signatures}.
     * The {@code signatures} map uses server usernames as keys; the Forge importer
     * resolves them to Cloud account IDs via the CMA Mappings API.
     */
    @Override
    public void onStartAppMigration(AppCloudForgeMigrationGateway gateway, MigrationDetailsV1 migrationDetails) {
        int scanned = 0;
        int exported = 0;
        int totalSignatures = 0;

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
                    Signature2 sig = Signature2.fromBandana(bandanaManager, key);
                    if (sig == null) {
                        log.warn("Skipping null/corrupt Bandana entry: {}", key);
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
            throw new RuntimeException("Migration export failed", e);
        }

        log.info("Migration export complete: {} keys scanned, {} contracts exported, {} signatures exported",
                scanned, exported, totalSignatures);
        gateway.completeExport();
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
