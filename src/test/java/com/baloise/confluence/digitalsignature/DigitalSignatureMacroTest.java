package com.baloise.confluence.digitalsignature;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DigitalSignatureMacroTest {

	@Test
	public void testMyName() {
		assertEquals("", "&nbsp; &nbsp; ".replace("&nbsp;", "").trim());
	}
	
}
