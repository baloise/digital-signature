package com.baloise.confluence.digitalsignature;

import org.junit.Test;

import static com.baloise.confluence.digitalsignature.InheritSigners.*;
import static org.junit.Assert.assertEquals;

public class InheritSignersTest {
  @Test
  public void testOfValueReadersOnly() {
    assertEquals(READERS_ONLY, ofValue("readers only"));
  }

  @Test
  public void testOfValueNoneNull() {
    assertEquals(NONE, ofValue(null));
  }

  @Test
  public void testOfValueNoneIllegalArgument() {
    assertEquals(NONE, ofValue("asdasd"));
  }
}
