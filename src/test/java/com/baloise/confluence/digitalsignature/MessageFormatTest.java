package com.baloise.confluence.digitalsignature;

import org.junit.jupiter.api.Test;

import java.text.MessageFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatTest {
  @Test
  void testFormat_inOrder() {
    String rawTemplate = "Email addresses of users who {0}signed{1} {2}";
    String actual = MessageFormat.format(rawTemplate, "<b>", "</b>", "#123");
    assertEquals("Email addresses of users who <b>signed</b> #123", actual);
  }

  @Test
  void testFormat_outOfOrder() {
    String rawTemplate = "{2} was {0}signed{1}";
    String actual = MessageFormat.format(rawTemplate, "<b>", "</b>", "#123");
    assertEquals("#123 was <b>signed</b>", actual);
  }
}
