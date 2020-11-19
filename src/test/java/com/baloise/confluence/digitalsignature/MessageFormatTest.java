package com.baloise.confluence.digitalsignature;

import java.text.MessageFormat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MessageFormatTest {
  @Test
  public void test() {
    String rawTemplate = "Email addresses of users who {0}signed{1} {2}";
    String actual = MessageFormat.format(rawTemplate, "<b>", "</b>", "#123");
    assertEquals("Email addresses of users who <b>signed</b> #123", actual);
    rawTemplate = "{2} was {0}signed{1}";
    actual = MessageFormat.format(rawTemplate, "<b>", "</b>", "#123");
    assertEquals("#123 was <b>signed</b>", actual);
  }
}
