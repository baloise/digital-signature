package com.baloise.confluence.digitalsignature;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class SignatureTest {

	@Test
	public void testClone() throws Exception {
		Signature signature = new Signature(999, "title", "body");
		signature.getMissingSignatures().add("Hans");
		Signature cloned = signature.clone();
		assertFalse(signature == cloned);
		assertEquals(signature, cloned);
		assertEquals("Hans", cloned.getMissingSignatures().iterator().next());
	}

}