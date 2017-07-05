package com.baloise.confluence.digitalsignature;

import java.util.Map;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;

public class DigitalSignatureMacro implements Macro {

	@Override
	public String execute(Map<String, String> map, String body, ConversionContext conversionContext) throws MacroExecutionException {
		 if (map.get("signers") != null) {
			 
		        return ("<h1>Hello " + map.get("signers") + "!</h1><br/>"+map.get("protectedContent")
		        +"<br/>" + body);
		    } else {
		        return "<h1>Hello World!<h1>";
		    }
	}
	
	@Override
	public BodyType getBodyType() {
		 return BodyType.NONE; 
	}

	@Override
	public OutputType getOutputType() {
		 return OutputType.BLOCK;
	}

}
