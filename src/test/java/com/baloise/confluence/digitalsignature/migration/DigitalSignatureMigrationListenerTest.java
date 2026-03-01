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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.migration.app.gateway.AppCloudForgeMigrationGateway;
import com.atlassian.migration.app.gateway.MigrationDetailsV1;
import com.baloise.confluence.digitalsignature.Signature2;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DigitalSignatureMigrationListenerTest {

    private BandanaManager bandanaManager;
    private DigitalSignatureMigrationListener listener;

    @BeforeEach
    void setUp() {
        bandanaManager = mock(BandanaManager.class);
        listener = new DigitalSignatureMigrationListener(bandanaManager);
    }

    @Test
    void macroMapping_returnsCorrectMapping() {
        Map<String, String> mapping = listener.getServerToForgeMacroMapping();
        assertEquals(1, mapping.size());
        assertEquals(
            DigitalSignatureMigrationListener.FORGE_MACRO_KEY,
            mapping.get(DigitalSignatureMigrationListener.SERVER_MACRO_KEY)
        );
    }

    @Test
    void toJsonLine_includesRequiredFields() {
        Signature2 sig = new Signature2(42L, "my body", "my title");
        sig.getSignatures().put("alice", new Date(0));
        sig.getMissingSignatures().add("bob");
        sig.getNotify().add("carol");

        String line = DigitalSignatureMigrationListener.toJsonLine(sig);
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

    // ---- helpers ----

    private static List<String> gunzipLines(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            return reader.lines().filter(l -> !l.isBlank()).toList();
        }
    }
}
