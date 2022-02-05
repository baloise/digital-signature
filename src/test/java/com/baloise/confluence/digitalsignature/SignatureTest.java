package com.baloise.confluence.digitalsignature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignatureTest {
  @Test
  void testClone() throws Exception {
    Signature signature = new Signature(999, "title", "body");
    signature.getMissingSignatures().add("Hans");
    Signature cloned = signature.clone();

    assertAll(
        () -> assertNotSame(signature, cloned),
        () -> assertEquals(signature, cloned),
        () -> assertEquals("Hans", cloned.getMissingSignatures().iterator().next())
    );
  }
}
