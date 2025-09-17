package com.baloise.confluence.digitalsignature;

import com.atlassian.bandana.BandanaManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignatureSerialisationTest {
  public static final String SIG_JSON = "{\"key\":\"signature.a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3\",\"hash\":\"a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3\",\"pageId\":123,\"title\":\"title\",\"body\":\"body\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{\"signed1\":\"1970-01-01T01:00:09CET\"},\"missingSignatures\":[\"missing1\",\"missing2\"],\"notify\":[\"notify1\"]}";

  @Test
  void deserialize() throws IOException, ClassNotFoundException {
    String signatureKey = "signature.a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3";

    Signature2 signature;
    try(ObjectInputStream in = new ObjectInputStream(getClass().getResourceAsStream("/signature.ser"))) {

      HashSet<String> keys = new HashSet<>();
      keys.add(signatureKey);
      BandanaManager mgr = mock(BandanaManager.class);
      when(mgr.getValue(any(), any())).thenReturn(in.readObject());
      when(mgr.getKeys(any())).thenReturn(keys);

      signature = Signature2.fromBandana(mgr, signatureKey);
    }

    assertNotNull(signature);
    assertAll(
        () -> assertEquals(signatureKey, signature.getKey()),
        () -> assertEquals("[missing1, missing2]", signature.getMissingSignatures().toString()),
        () -> assertEquals(1, signature.getSignatures().size()),
        () -> assertTrue(signature.getSignatures().containsKey("signed1")),
        () -> assertEquals(9999, signature.getSignatures().get("signed1").getTime()),

        // assert we can still read the old gson serialization
        () -> assertEquals(signature, Signature2.deserialize(SIG_JSON)),

        // assert that deserialization of the serialization results in the original Signature
        () -> assertEquals(signature, Signature2.deserialize(signature.serialize()))
    );
  }

  @Test
  void serialize() throws IOException, ClassNotFoundException {
    Signature2 signature = new Signature2(123L, "body", "title");
    signature.getNotify().add("notify1");
    signature.getMissingSignatures().add("missing1");
    signature.getMissingSignatures().add("missing2");
    signature.getSignatures().put("signed1", new Date(9999));

    Path path = Paths.get("src/test/resources/signature-test.ser");
    try(ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(path))) {
      out.writeObject(signature);
    }

    // assert the serialization we just wrote can be deserialized
    ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path));
    assertEquals(signature, in.readObject());
  }

  @Test
  void deserializeHistoricalRecord() throws IOException, ClassNotFoundException {
    Signature signature = new Signature(123L, "body", "title");
    signature.getNotify().add("notify1");
    signature.getMissingSignatures().add("missing1");
    signature.getMissingSignatures().add("missing2");
    signature.getSignatures().put("signed1", new Date(9999));

    ObjectInputStream in;

    // assert the historically serialized class can still be deserialized
    in = new ObjectInputStream(this.getClass().getResourceAsStream("/signature.ser"));
    assertEquals(signature, in.readObject());
  }
}
