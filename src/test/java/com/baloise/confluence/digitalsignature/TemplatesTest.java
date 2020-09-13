package com.baloise.confluence.digitalsignature;

import org.apache.velocity.VelocityContext;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.io.Writer;

import static org.apache.velocity.app.Velocity.mergeTemplate;
import static org.junit.Assert.assertEquals;

public class TemplatesTest {
    private static String normalize(String input) {
        return input.replaceAll("[\n\r]", "")
                    .replaceAll(" +", " ")
                    .replaceAll("> <", "><")
                    .trim();
    }

    @Test
    public void testMacroVm() throws Exception {
        StringWriter sw = new StringWriter();
        //lets use BufferedWriter for better performance:
        Writer writer = new BufferedWriter(sw);
        VelocityContext context = new VelocityContext();
        //add your parameters to context
        mergeTemplate("src/main/resources/templates/macro.vm", "UTF-8", context, writer);
        writer.flush();
        String expected = "#requireResource(\"com.baloise.confluence.digital-signature:digital-signature-resources\") <b>$title</b><p>$bodyWithHtml</p><ul class=\"body-list\" id=\"$macroId\"></ul><script type=\"text/javascript\"> AJS.toInit(function() { bindCollapse(AJS.$(\"#$macroId\"), ${visibilityLimit}, '${i18n.getText( \"com.baloise.confluence.digital-signature.signature.macro.button.show-all.label\")}'); });</script>";
        assertEquals(expected, normalize(sw.toString()));
    }

    @Test
    public void testExportVm() throws Exception {
        StringWriter sw = new StringWriter();
        //lets use BufferedWriter for better performance:
        Writer writer = new BufferedWriter(sw);
        VelocityContext context = new VelocityContext();
        //add your parameters to context
        mergeTemplate("src/main/resources/templates/export.vm", "UTF-8", context, writer);
        writer.flush();
        String expected = "<style type=\"text/css\"> body { padding: 2% 4% 2% 4%; } td { padding-right: 12px; }</style><h1>$signature.getTitle()</h1><p>$bodyWithHtml</p><table></table><!-- generated $dateFormatter.formatDateTime($currentDate) -->";
        assertEquals(expected, normalize(sw.toString()));
    }
}
