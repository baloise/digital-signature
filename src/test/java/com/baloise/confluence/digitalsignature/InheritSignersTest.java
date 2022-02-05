package com.baloise.confluence.digitalsignature;


import org.junit.jupiter.api.Test;

import static com.baloise.confluence.digitalsignature.InheritSigners.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InheritSignersTest {
  @Test
  void testOfValueReadersOnly() {
    assertEquals(READERS_ONLY, ofValue("readers only"));
  }

  @Test
  void testOfValueNoneNull() {
    assertEquals(NONE, ofValue(null));
  }

  @Test
  void testOfValueNoneIllegalArgument() {
    assertEquals(NONE, ofValue("asdasd"));
  }
}
