package com.baloise.confluence.digitalsignature;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

@Scanned
public class DigitalSignatureMacro implements Macro {
	private BandanaManager bandanaManager;
//	private UserManager userManager;
	
	@Autowired
	public DigitalSignatureMacro(@ComponentImport BandanaManager bandanaManager
//			, @ComponentImport UserManager userManager
			) {
		this.bandanaManager = bandanaManager;
//		this.userManager = userManager;
	}

	@Override
	public String execute(Map<String, String> map, String body, ConversionContext conversionContext) throws MacroExecutionException {
		if(body != null && !body.replace("&nbsp;", "").trim().isEmpty()) {
			Signature signature = load(new Signature(conversionContext.getEntity().getLatestVersionId(), body));
			Map<String,Object> context = defaultVelocityContext();
			context.put("signature",  signature);
			context.put("body",  body);
		    String signers = map.get("signers");
			context.put("signers",  signers);
//			context.put("signerProfiles",  getProfiles(signers));
		    context.put("protectedContent",  map.get("protectedContent"));
		    context.put("context", context);
		    return getRenderedTemplate("templates/macro.vm", context);
		} 
		return "<div class=\"aui-message aui-message-warning\">\n" + 
				"    <p class=\"title\">\n" + 
				"        <strong>Signature Macro</strong>\n" + 
				"    </p>\n" + 
				"    <p>You need to enter a text to be signed.</p>\n" + 
				"</div>";
		
		
	}

//	private List<UserProfile> getProfiles(String signers) {
//		String[] split = signers.split("[;,]");
//		List<UserProfile> ret  = new ArrayList<UserProfile>(split.length);
//		for (String s : split) {
//			ret.add(userManager.getUserProfile(s));
//		}
//		return ret;
//	}

	private Signature load(String key) {
		return (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
	}
	
	private Signature load(Signature signature) {
		Signature loaded = load(signature.getKey());
		return loaded != null ? loaded : signature;
	}

	@Override
	public BodyType getBodyType() {
		return BodyType.PLAIN_TEXT;
	}

	@Override
	public OutputType getOutputType() {
		return OutputType.BLOCK;
	}

}
