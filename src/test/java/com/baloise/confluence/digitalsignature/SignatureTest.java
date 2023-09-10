package com.baloise.confluence.digitalsignature;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.bandana.DefaultBandanaManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

class SignatureTest {
  @Nested
  class SerializationTest {
    @Test
    void serialize_empty() {
      Signature2 signature = new Signature2();

      String json = signature.serialize();

      assertEquals("{\"key\":\"\",\"hash\":\"\",\"pageId\":0,\"title\":\"\",\"body\":\"\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[],\"notify\":[]}", json);
    }

    @Test
    void serialize_initializedObject() {
      Signature2 signature = new Signature2(42L, "body text", "title text");
      signature.sign("max.mustermann");
      signature.setMissingSignatures(Collections.singleton("max.muster"));
      signature.setNotify(Collections.singleton("max.meier"));

      String json = signature.serialize();

      assertEquals("{\"key\":\"signature.752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"hash\":\"752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"pageId\":42,\"title\":\"title text\",\"body\":\"body text\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[\"max.muster\"],\"notify\":[\"max.meier\"]}", json);
    }

    @Test
    void deserialize_empty() {
      assertNull(Signature2.deserialize(null));
      assertNull(Signature2.deserialize(""));
    }

    @Test
    void serializeAndDeserialize() {
      Signature2 signature = new Signature2(42L, "body text", "title text");
      signature.sign("max.mustermann");
      signature.setMissingSignatures(Collections.singleton("max.muster"));
      signature.setNotify(Collections.singleton("max.meier"));

      String json = signature.serialize();

      Signature2 restoredSignature = Signature2.deserialize(json);

      assertEquals("{\"key\":\"signature.752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"hash\":\"752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\",\"pageId\":42,\"title\":\"title text\",\"body\":\"body text\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[\"max.muster\"],\"notify\":[\"max.meier\"]}", json);
      assertEquals(signature, restoredSignature);
    }
  }

  @Nested
  class BandanaWrapperTest {
    private final BandanaManager bandana = mock(DefaultBandanaManager.class);
    private final Signature2 signature = new Signature2(1, "test", "title");
    private final Signature signatureOld = new Signature(1, "test", "title");

    @Test
    void toBandanaFromBandana_readAsWritten() {
      ArgumentCaptor<String> stringCapator = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<Object> objectCapator = ArgumentCaptor.forClass(Object.class);

      String key = signature.getKey();
      assertNull(Signature2.fromBandana(bandana, key), "Should not be there yet.");

      doNothing().when(bandana).setValue(any(), stringCapator.capture(), objectCapator.capture());
      when(bandana.getKeys(any())).thenReturn(Collections.singletonList(key));

      Signature2.toBandana(bandana, signature);
      assertEquals(key, stringCapator.getValue());
      assertEquals(signature.serialize(), objectCapator.getValue());

      when(bandana.getValue(any(), any())).thenCallRealMethod();
      when(bandana.getValue(any(), eq(key), eq(true))).thenReturn(signature.serialize());
      assertEquals(signature, Signature2.fromBandana(bandana, signature.getKey()));
    }

    @Test
    void fromBandana_signature_signature() {
      String key = signature.getKey();
      assertNull(Signature2.fromBandana(bandana, key), "Should not be there yet.");

      when(bandana.getKeys(any())).thenReturn(Collections.singletonList(key));
      when(bandana.getValue(any(), any())).thenCallRealMethod();
      when(bandana.getValue(any(), eq(key), eq(true))).thenReturn(signatureOld);

      assertEquals(signature, Signature2.fromBandana(bandana, signature.getKey()));
    }

    @Test
    void fromBandana_string_signature() {
      String key = signature.getKey();
      assertNull(Signature2.fromBandana(bandana, key), "Should not be there yet.");

      when(bandana.getKeys(any())).thenReturn(Collections.singletonList(key));
      when(bandana.getValue(any(), any())).thenCallRealMethod();
      when(bandana.getValue(any(), eq(key), eq(true))).thenReturn(signature.serialize());

      assertEquals(signature, Signature2.fromBandana(bandana, signature.getKey()));
    }
  }
}
