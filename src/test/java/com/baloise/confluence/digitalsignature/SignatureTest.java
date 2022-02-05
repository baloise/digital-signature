package com.baloise.confluence.digitalsignature;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SignatureTest {
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
