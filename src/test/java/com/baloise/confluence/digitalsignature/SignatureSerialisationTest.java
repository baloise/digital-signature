package com.baloise.confluence.digitalsignature;


import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;


class SignatureSerialisationTest {
  @Test
  void deserialize() throws IOException, ClassNotFoundException {
    ObjectInputStream in = new ObjectInputStream(getClass().getResourceAsStream("/signature.ser"));
    Signature signature = (Signature) in.readObject();
    in.close();

    assertAll(
        () -> assertEquals("signature.a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3", signature.getKey()),
        () -> assertEquals("[missing1, missing2]", signature.getMissingSignatures().toString()),
        () -> assertEquals(1, signature.getSignatures().size()),
        () -> assertTrue(signature.getSignatures().containsKey("signed1")),
        () -> assertEquals(9999, signature.getSignatures().get("signed1").getTime())
    );
  }

  @Test
  void serialize() throws IOException, ClassNotFoundException {
    Signature signature = new Signature(123L, "body", "title");
    signature.getNotify().add("notify1");
    signature.getMissingSignatures().add("missing1");
    signature.getMissingSignatures().add("missing2");
    signature.getSignatures().put("signed1", new Date(9999));
    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("src/test/resources/signature-test.ser"));
    out.writeObject(signature);
    out.close();

    ObjectInputStream in = new ObjectInputStream(this.getClass().getResourceAsStream("/signature.ser"));
    assertEquals(signature, in.readObject());
  }
}
