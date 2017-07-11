package com.baloise.confluence.digitalsignature;

import static org.apache.velocity.app.Velocity.mergeTemplate;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.junit.Test;

public class TemplatesTest {

	@Test
	public void test() throws ResourceNotFoundException, ParseErrorException, MethodInvocationException, Exception {
		StringWriter sw = new StringWriter();
		//let's buffer Writer for better performace:
		Writer writer = new BufferedWriter(sw);
		VelocityContext context = new VelocityContext();
		//add your parameters to context
		mergeTemplate("src/main/resources/templates/macro.vm", "UTF-8", context, writer);
		writer.flush();
		String result = sw.toString();
		System.out.println(result);
	}
	
	@Test
	public void test2() throws ResourceNotFoundException, ParseErrorException, MethodInvocationException, Exception {
		StringWriter sw = new StringWriter();
		//let's buffer Writer for better performace:
		Writer writer = new BufferedWriter(sw);
		VelocityContext context = new VelocityContext();
		//add your parameters to context
		mergeTemplate("src/main/resources/templates/export.vm", "UTF-8", context, writer);
		writer.flush();
		String result = sw.toString();
		System.out.println(result);
	}
	
	@Test
	public void foo() {
		System.out.println("https://test-confluence.baloisenet.com/atlassian/rest/signature/1.0/".split("rest/")[0]);
	}

}
