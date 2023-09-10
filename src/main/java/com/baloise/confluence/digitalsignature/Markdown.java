package com.baloise.confluence.digitalsignature;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class Markdown {
  private final Parser parser;
  private final HtmlRenderer renderer;

  public Markdown() {
    MutableDataSet options = new MutableDataSet();
    options.set(HtmlRenderer.DO_NOT_RENDER_LINKS, true);
    options.set(HtmlRenderer.ESCAPE_HTML, true);

    parser = Parser.builder(options).build();
    renderer = HtmlRenderer.builder(options).build();
  }

  public String toHTML(String markdown) {
    return renderer.render(parser.parse(markdown));
  }
}
