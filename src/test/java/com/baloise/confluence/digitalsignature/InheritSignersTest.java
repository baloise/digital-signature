package com.baloise.confluence.digitalsignature;

import static com.baloise.confluence.digitalsignature.InheritSigners.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InheritSignersTest {

	@Test
	public void READERS_ONLY() throws Exception {
		assertEquals(READERS_ONLY, ofValue("readers only"));
	}
	@Test
	public void NONE_NULL() throws Exception {
		assertEquals(NONE, ofValue(null));
	}
	@Test
	public void NONE_IllegalArgument() throws Exception {
		assertEquals(NONE, ofValue("asdasd"));
	}

}
