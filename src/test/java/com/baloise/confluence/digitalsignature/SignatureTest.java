package com.baloise.confluence.digitalsignature;

import com.atlassian.bandana.BandanaManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignatureTest {
  @Nested
  class SerializationTest {
    @Test
    void serialize_empty() {
      Signature signature = new Signature();

      String json = signature.serialize();

      assertEquals("{\"key\":\"\",\"hash\":\"\",\"pageId\":0,\"title\":\"\",\"body\":\"\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[],\"notify\":[]}", json);
    }

    @Test
    void serialize_initializedObject() {
      Signature signature = new Signature(42L, "body text", "title text");
      signature.sign("max.mustermann");
      signature.setMissingSignatures(Set.of("max.muster"));
      signature.setNotify(Set.of("max.meier"));

      String json = signature.serialize();

      assertEquals("{\"key\":\"signature.752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"hash\":\"752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"pageId\":42,\"title\":\"title text\",\"body\":\"body text\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[\"max.muster\"],\"notify\":[\"max.meier\"]}", json);
    }

    @Test
    void deserialize_empty() {
      assertNull(Signature.deserialize(null));
      assertNull(Signature.deserialize(""));
    }

    @Test
    void serializeAndDeserialize() {
      Signature signature = new Signature(42L, "body text", "title text");
      signature.sign("max.mustermann");
      signature.setMissingSignatures(Set.of("max.muster"));
      signature.setNotify(Set.of("max.meier"));

      String json = signature.serialize();

      Signature restoredSignature = Signature.deserialize(json);

      assertEquals("{\"key\":\"signature.752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"hash\":\"752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"pageId\":42,\"title\":\"title text\",\"body\":\"body text\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[\"max.muster\"],\"notify\":[\"max.meier\"]}", json);
      assertEquals(signature, restoredSignature);
    }
  }

  @Nested
  class BandanaWrapperTest {
    private final Signature signature = new Signature(1, "test", "title");
    private final BandanaManager bandana = mock(BandanaManager.class);

    @Test
    void fromBandana_signature_signature() {
      when(bandana.getValue(any(), any())).thenReturn(signature.serialize());

      Signature readSignature = Signature.fromBandana(bandana, null);

      assertEquals(signature, readSignature);
    }

    @Test
    void fromBandana_string_signatur() {
      when(bandana.getValue(any(), any())).thenReturn(signature);

      Signature readSignature = Signature.fromBandana(bandana, null);

      assertEquals(signature, readSignature);
    }
  }
}
