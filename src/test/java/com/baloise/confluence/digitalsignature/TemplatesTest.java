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
        String result = sw.toString();
        assertEquals("<b>$title</b><p>$bodyWithHtml</p><ul style=\"list-style-type: none;\"></ul>", normalize(result));
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
        String result = sw.toString();
        assertEquals("<style type=\"text/css\"> body { padding: 2% 4% 2% 4%; } td { padding-right: 12px; }</style><h1>$signature.getTitle()</h1><p>$bodyWithHtml</p><table></table><!-- generated $dateFormatter.formatDateTime($currentDate) -->",
                normalize(result));
    }

}
