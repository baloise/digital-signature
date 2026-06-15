package com.baloise.confluence.digitalsignature;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic validation that Velocity templates exist and contain expected content.
 * Full Velocity rendering is tested via integration tests with Confluence.
 */
class TemplatesTest {

  private String loadTemplate(String path) throws Exception {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
      assertNotNull(is, "Template not found: " + path);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void testMacroVmExists() throws Exception {
    String content = loadTemplate("templates/macro.vm");
    assertTrue(content.contains("#requireResource"), "macro.vm should contain #requireResource directive");
    assertTrue(content.contains("$title"), "macro.vm should contain $title variable");
    assertTrue(content.contains("$bodyWithHtml"), "macro.vm should contain $bodyWithHtml variable");
    assertTrue(content.contains("$macroId"), "macro.vm should contain $macroId variable");
  }

  @Test
  void testExportVmExists() throws Exception {
    String content = loadTemplate("templates/export.vm");
    assertTrue(content.contains("$signature"), "export.vm should contain $signature variable");
    assertTrue(content.contains("$bodyWithHtml"), "export.vm should contain $bodyWithHtml variable");
    assertTrue(content.contains("$dateFormatter"), "export.vm should contain $dateFormatter variable");
  }

  @Test
  void testEmailVmExists() throws Exception {
    String content = loadTemplate("templates/email.vm");
    assertNotNull(content, "email.vm should exist");
    assertTrue(content.length() > 0, "email.vm should not be empty");
  }
}
