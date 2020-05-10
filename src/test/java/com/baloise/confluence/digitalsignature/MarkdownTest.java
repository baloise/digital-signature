package com.baloise.confluence.digitalsignature;

import static java.nio.file.Files.readAllLines;
import static java.nio.file.Paths.get;
import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;

import org.junit.Before;
import org.junit.Test;


public class MarkdownTest {

	
	Markdown markdown;

	@Before
	public void setUp() {
		markdown = new Markdown();
	}
	
	
	@Test
	public void testToHTML() throws Exception {
		  assertEquals("<p>This is <em>Sparta</em></p>\n", markdown.toHTML("This is *Sparta*"));
	        assertEquals("<p>Link</p>\n",  markdown.toHTML("[Link](http://a.com)"));
	        assertEquals("<p></p>\n",  markdown.toHTML("![Image](http://url/a.png)"));
	        assertEquals("<p>&lt;b&gt;&lt;/b&gt;</p>\n",  markdown.toHTML("<b></b>"));
	        assertEquals(readResource("commonmark.html").trim(), markdown.toHTML(readResource("commonmark.md")).trim());
	}


	private String readResource(String name) throws IOException, URISyntaxException {
		return readAllLines(get(getClass().getResource("/"+name).toURI())).stream().collect(joining("\n"));
	}

}
