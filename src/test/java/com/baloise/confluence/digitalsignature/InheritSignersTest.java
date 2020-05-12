package com.baloise.confluence.digitalsignature;

import org.junit.Test;

import static com.baloise.confluence.digitalsignature.InheritSigners.*;
import static org.junit.Assert.assertEquals;

public class InheritSignersTest {
    @Test
    public void testOfValue_READERS_ONLY() {
        assertEquals(READERS_ONLY, ofValue("readers only"));
    }

    @Test
    public void testOfValue_NONE_NULL() {
        assertEquals(NONE, ofValue(null));
    }

    @Test
    public void testOfValue_NONE_IllegalArgument() {
        assertEquals(NONE, ofValue("asdasd"));
    }
}
