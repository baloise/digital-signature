package com.baloise.confluence.digitalsignature.migration;

import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.migration.app.ConfluenceSpaceContainerV1;
import com.atlassian.migration.app.ContainerType;
import com.atlassian.migration.app.ContainerV1;
import com.atlassian.migration.app.PaginatedContainers;
import com.atlassian.migration.app.gateway.AppCloudForgeMigrationGateway;
import com.atlassian.migration.app.gateway.MigrationDetailsV1;
import com.baloise.confluence.digitalsignature.Signature2;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DigitalSignatureMigrationListenerTest {

    private BandanaManager bandanaManager;
    private UserAccessor userAccessor;
    private PageManager pageManager;
    private DigitalSignatureMigrationListener listener;

    @BeforeEach
    void setUp() {
        bandanaManager = mock(BandanaManager.class);
        userAccessor = mock(UserAccessor.class);
        pageManager = mock(PageManager.class);
        listener = new DigitalSignatureMigrationListener(bandanaManager, userAccessor, pageManager);
    }

    @Test
    void canonicalIdentifiers_matchExpectedValues() {
        assertAll(
            () -> assertEquals(
                "com.baloise.confluence.digital-signature",
                listener.getCloudAppKey()
            ),
            () -> assertEquals(
                "com.baloise.confluence.digital-signature",
                listener.getServerAppKey()
            ),
            () -> assertEquals(
                "bab5617e-dc42-4ca8-ad38-947c826fe58c",
                listener.getForgeAppId().toString()
            ),
            () -> assertEquals(
                "signature",
                DigitalSignatureMigrationListener.SERVER_MACRO_KEY
            ),
            () -> assertEquals(
                "digital-signature",
                DigitalSignatureMigrationListener.FORGE_MACRO_KEY
            )
        );
    }

    @Test
    void macroMapping_mapsServerMacroNameToForgeKey() {
        // CMA keys the mapping by the SOURCE macro name ("signature", per
        // <xhtml-macro name="signature">), mapping it to the Forge macro key.
        Map<String, String> mapping = listener.getServerToForgeMacroMapping();
        assertEquals(1, mapping.size());
        assertEquals(
            "digital-signature",
            mapping.get("signature")
        );
    }

    @Test
    void toJsonLine_includesRequiredFields() {
        Signature2 sig = new Signature2(42L, "my body", "my title");
        sig.getSignatures().put("alice", new Date(0));
        sig.getMissingSignatures().add("bob");
        sig.getNotify().add("carol");

        String line = listener.toJsonLine(sig);
        JsonObject obj = new JsonParser().parse(line).getAsJsonObject();

        assertAll(
            () -> assertEquals(sig.getHash(), obj.get("hash").getAsString()),
            () -> assertEquals(42L, obj.get("pageId").getAsLong()),
            () -> assertEquals("my title", obj.get("title").getAsString()),
            () -> assertEquals("my body", obj.get("body").getAsString()),
            () -> assertTrue(obj.has("signatures")),
            () -> assertTrue(obj.get("signatures").getAsJsonObject().has("alice")),
            // missingSignatures and notify must NOT appear in the export
            () -> assertFalse(obj.has("missingSignatures")),
            () -> assertFalse(obj.has("notify"))
        );
    }

    @Test
    void onStartAppMigration_writesOneLinePerContract() throws Exception {
        Signature2 sig1 = new Signature2(10L, "body1", "title1");
        sig1.getSignatures().put("user1", new Date(1000));

        Signature2 sig2 = new Signature2(20L, "body2", "title2");
        sig2.getSignatures().put("user2", new Date(2000));
        sig2.getSignatures().put("user3", new Date(3000));

        List<String> keys = List.of(
            sig1.getKey(),
            sig2.getKey(),
            "protected.somehash",  // should be skipped
            "other.key"            // should be skipped
        );

        when(bandanaManager.getKeys(GLOBAL_CONTEXT)).thenReturn(keys);
        when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(sig1.getKey()))).thenReturn(Signature2.GSON.toJson(sig1, Signature2.class));
        when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(sig2.getKey()))).thenReturn(Signature2.GSON.toJson(sig2, Signature2.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AppCloudForgeMigrationGateway gateway = mock(AppCloudForgeMigrationGateway.class);
        when(gateway.createAppData("signatures")).thenReturn(buffer);

        listener.onStartAppMigration(gateway, new MigrationDetailsV1());

        verify(gateway).completeExport();

        List<String> lines = gunzipLines(buffer.toByteArray());
        assertEquals(2, lines.size(), "Expected one JSONL line per signature contract");

        JsonObject first = new JsonParser().parse(lines.get(0)).getAsJsonObject();
        JsonObject second = new JsonParser().parse(lines.get(1)).getAsJsonObject();

        // Verify both contracts appear (order not guaranteed by Bandana)
        List<String> hashes = List.of(
            first.get("hash").getAsString(),
            second.get("hash").getAsString()
        );
        assertTrue(hashes.contains(sig1.getHash()));
        assertTrue(hashes.contains(sig2.getHash()));
    }

    @Test
    void onStartAppMigration_skipNullEntries() throws Exception {
        String key = "signature.deadbeef";
        when(bandanaManager.getKeys(GLOBAL_CONTEXT)).thenReturn(List.of(key));
        // fromBandana returns null when key is not found in getKeys
        when(bandanaManager.getKeys(GLOBAL_CONTEXT)).thenReturn(List.of());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AppCloudForgeMigrationGateway gateway = mock(AppCloudForgeMigrationGateway.class);
        when(gateway.createAppData("signatures")).thenReturn(buffer);

        assertDoesNotThrow(() -> listener.onStartAppMigration(gateway, new MigrationDetailsV1()));
        verify(gateway).completeExport();

        List<String> lines = gunzipLines(buffer.toByteArray());
        assertEquals(0, lines.size());
    }

    @Test
    void onStartAppMigration_scopesToMigratedSpaces() throws Exception {
        Signature2 inScope = new Signature2(10L, "body1", "title1");
        inScope.getSignatures().put("user1", new Date(1000));
        Signature2 outOfScope = new Signature2(20L, "body2", "title2");
        outOfScope.getSignatures().put("user2", new Date(2000));

        when(bandanaManager.getKeys(GLOBAL_CONTEXT)).thenReturn(List.of(inScope.getKey(), outOfScope.getKey()));
        when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(inScope.getKey()))).thenReturn(Signature2.GSON.toJson(inScope, Signature2.class));
        when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(outOfScope.getKey()))).thenReturn(Signature2.GSON.toJson(outOfScope, Signature2.class));

        // pageId 10 lives in the migrated space "INSCOPE"; pageId 20 lives elsewhere.
        Page inScopePage = mock(Page.class);
        when(inScopePage.getSpaceKey()).thenReturn("INSCOPE");
        Page otherPage = mock(Page.class);
        when(otherPage.getSpaceKey()).thenReturn("OTHER");
        when(pageManager.getPage(10L)).thenReturn(inScopePage);
        when(pageManager.getPage(20L)).thenReturn(otherPage);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        AppCloudForgeMigrationGateway gateway = mock(AppCloudForgeMigrationGateway.class);
        when(gateway.createAppData("signatures")).thenReturn(buffer);

        // Regression: CMA returns a Site container even for a single-space migration. The export
        // must NOT treat that as full-site — it must scope to the ConfluenceSpace container.
        PaginatedContainers site = mock(PaginatedContainers.class);
        when(site.next()).thenReturn(true, false);
        when(site.getContainers()).thenReturn(List.of(mock(ContainerV1.class)));
        when(gateway.getPaginatedContainers(ContainerType.Site, 50)).thenReturn(site);
        PaginatedContainers spaces = mock(PaginatedContainers.class);
        when(spaces.next()).thenReturn(true, false);
        when(spaces.getContainers()).thenReturn(List.<ContainerV1>of(new ConfluenceSpaceContainerV1("100", "INSCOPE")));
        when(gateway.getPaginatedContainers(ContainerType.ConfluenceSpace, 100)).thenReturn(spaces);

        listener.onStartAppMigration(gateway, new MigrationDetailsV1());
        verify(gateway).completeExport();

        List<String> lines = gunzipLines(buffer.toByteArray());
        assertEquals(1, lines.size(), "Only the in-scope space's contract should be exported");
        JsonObject only = new JsonParser().parse(lines.get(0)).getAsJsonObject();
        assertEquals(inScope.getHash(), only.get("hash").getAsString());
    }

    @Test
    void onStartAppMigration_chunksAppDataBySize() throws Exception {
        // Scalability: the exporter writes one createAppData("signatures") entry per CHUNK_SIZE
        // contracts, so CMA delivers one uploaded:app_data message per chunk and the Forge
        // importer processes each in its own bounded, sub-25s invocation.
        int original = DigitalSignatureMigrationListener.CHUNK_SIZE;
        DigitalSignatureMigrationListener.CHUNK_SIZE = 2;
        try {
            Signature2 s1 = new Signature2(10L, "b1", "t1"); s1.getSignatures().put("u1", new Date(1000));
            Signature2 s2 = new Signature2(20L, "b2", "t2"); s2.getSignatures().put("u2", new Date(2000));
            Signature2 s3 = new Signature2(30L, "b3", "t3"); s3.getSignatures().put("u3", new Date(3000));

            when(bandanaManager.getKeys(GLOBAL_CONTEXT)).thenReturn(List.of(s1.getKey(), s2.getKey(), s3.getKey()));
            when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(s1.getKey()))).thenReturn(Signature2.GSON.toJson(s1, Signature2.class));
            when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(s2.getKey()))).thenReturn(Signature2.GSON.toJson(s2, Signature2.class));
            when(bandanaManager.getValue(eq(GLOBAL_CONTEXT), eq(s3.getKey()))).thenReturn(Signature2.GSON.toJson(s3, Signature2.class));

            // No space containers stubbed → resolveInScopeSpaceKeys falls back to exportAll=true.
            List<ByteArrayOutputStream> buffers = new ArrayList<>();
            AppCloudForgeMigrationGateway gateway = mock(AppCloudForgeMigrationGateway.class);
            when(gateway.createAppData("signatures")).thenAnswer(inv -> {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                buffers.add(b);
                return b;
            });

            listener.onStartAppMigration(gateway, new MigrationDetailsV1());

            // 3 contracts / CHUNK_SIZE 2 → 2 independent app-data entries (2 + 1); completeExport once.
            verify(gateway, times(2)).createAppData("signatures");
            verify(gateway).completeExport();
            assertEquals(2, buffers.size());

            int total = 0;
            for (ByteArrayOutputStream b : buffers) total += gunzipLines(b.toByteArray()).size();
            assertEquals(3, total, "all contracts exported across chunks");
            assertEquals(2, gunzipLines(buffers.get(0).toByteArray()).size(), "first chunk full");
            assertEquals(1, gunzipLines(buffers.get(1).toByteArray()).size(), "final chunk partial");
        } finally {
            DigitalSignatureMigrationListener.CHUNK_SIZE = original;
        }
    }

    // ---- helpers ----

    private static List<String> gunzipLines(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            return reader.lines().filter(l -> !l.isBlank()).toList();
        }
    }
}
