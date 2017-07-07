package com.baloise.confluence.digitalsignature;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static java.util.Arrays.asList;
import static java.util.Objects.equals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.velocity.tools.generic.DateTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

@Scanned
public class DigitalSignatureMacro implements Macro {
	private BandanaManager bandanaManager;
	private UserManager userManager;
	private  BootstrapManager bootstrapManager;
	private static final Logger log = LoggerFactory.getLogger(DigitalSignatureMacro.class);
	
	@Autowired
	public DigitalSignatureMacro(
			@ComponentImport BandanaManager bandanaManager, 
			@ComponentImport UserManager userManager,
			@ComponentImport BootstrapManager bootstrapManager		
			) {
		this.bandanaManager = bandanaManager;
		this.userManager = userManager;
		this.bootstrapManager = bootstrapManager;
	}

	@Override
	public String execute(Map<String, String> map, String body, ConversionContext conversionContext) throws MacroExecutionException {
		log.info("body is '"+body+"'");
		if(body != null && body.length() > 10) {
			Signature signature = sync(new Signature(conversionContext.getEntity().getLatestVersionId(), body).withProtectedContent(map.get("protectedContent")));
			ConfluenceUser currentUser = AuthenticatedUserThreadLocal.get();
			String currentUserName = currentUser.getName();
			List<String> signers = new ArrayList<String>(asList(map.get("signers").split("[;,]")));
			signers.removeAll(signature.getSignatures().keySet());
			
			Map<String,Object> context = defaultVelocityContext();
			if(signers.contains(currentUserName)) {
				context.put("signAs",  currentUserName);
				context.put("signAction",  	bootstrapManager.getWebAppContextPath()+ "/rest/signature/1.0/sign");
		    }
			context.put("signature",  signature);
			context.put("body",  body);
			context.put("signers",  signers);
			context.put("date", new DateTool());
			context.put("profiles",  getProfiles(signers, signature.getSignatures().keySet()));
			context.put("orderedSignatures",  getOrderedSignatures(signature));
		    
		    return getRenderedTemplate("templates/macro.vm", context);
		} 
		return "<div class=\"aui-message aui-message-warning\">\n" + 
				"    <p class=\"title\">\n" + 
				"        <strong>Signature Macro</strong>\n" + 
				"    </p>\n" + 
				"    <p>You need to enter a text to be signed.</p>\n" + 
				"</div>";
		
		
	}

	Comparator<Entry<String, Date>> comparator = new Comparator<Entry<String, Date>>() {
		@Override
		public int compare(Entry<String, Date> s1, Entry<String, Date> s2) {
			int ret = s1.getValue().compareTo(s2.getValue());
			return ret == 0 ? s1.getKey().compareTo(s2.getKey()) : ret;
		}
	};
	
	private Object getOrderedSignatures(Signature signature) {
		SortedSet<Entry<String, Date>> ret = new TreeSet<Map.Entry<String,Date>>(comparator);
		ret.addAll(signature.getSignatures().entrySet());
		return ret;
	}

	private Map<String, UserProfile> getProfiles(Iterable<String> ... usersNamess) {
		Map<String, UserProfile> ret  = new HashMap<String, UserProfile>();
		for ( Iterable<String> usersNames : usersNamess) {
			for (String userName : usersNames) {
				ret.put(userName, userManager.getUserProfile(userName));
			}
		}
		return ret;
	}

	private Signature sync(Signature signature) {
		Signature loaded =  (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, signature.getKey());
		if(loaded != null) {
			boolean save = false;
			if(loaded.getSignatures().isEmpty()) {
				if(!Objects.equals(loaded.getBody(),signature.getBody())) {
					loaded.setBody(signature.getBody());
					save = true;
				}
			} else {
				signature.setBody(loaded.getBody());
				signature.getSignatures().putAll(loaded.getSignatures());
			}
			if(!Objects.equals(loaded.getProtectedContent(), signature.getProtectedContent())) {
				loaded.setProtectedContent(signature.getProtectedContent());
				save = true;
			}
			if(save) bandanaManager.setValue(GLOBAL_CONTEXT, signature.getKey(), loaded);
		} else {
			bandanaManager.setValue(GLOBAL_CONTEXT, signature.getKey(), signature);
		}
		return signature;
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
