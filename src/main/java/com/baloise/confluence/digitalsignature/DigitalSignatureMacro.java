package com.baloise.confluence.digitalsignature;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static com.atlassian.html.encode.HtmlEncoder.encode;
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.velocity.tools.generic.DateTool;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.security.ContentPermission;
import com.atlassian.confluence.security.ContentPermissionSet;
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
	private final String REST_PATH = "/rest/signature/1.0";
	private ContextHelper contextHelper = new ContextHelper();
	
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
	public String execute(Map<String, String> params, String body, ConversionContext conversionContext) throws MacroExecutionException {
		if(body != null && body.length() > 10) {
			Set<String> signers = contextHelper.union(
					getSet(params, "signers"), 
					loadInheritedSigners(InheritSigners.ofValue(params.get("inheritSigners")), conversionContext)
					);
			Signature signature = sync(new Signature(
					conversionContext.getEntity().getLatestVersionId(), 
					body, 
					params.get("title"))
					.withNotified(getSet(params, "notified")),
					signers
					);
			ConfluenceUser currentUser = AuthenticatedUserThreadLocal.get();
			String currentUserName = currentUser.getName();
			
			Map<String,Object> context = defaultVelocityContext();
			context.put("signature",  signature);
			context.put("date", new DateTool());
			Map<String, UserProfile> signed = contextHelper.getProfiles(userManager, signature.getSignatures().keySet());
			Map<String, UserProfile> outstanding = contextHelper.getProfiles(userManager, signature.getOutstandingSignatures());
			context.put("orderedSignatures",  contextHelper.getOrderedSignatures(signature));
			context.put("profiles",  contextHelper.union(signed, outstanding));
			
			if(signature.getOutstandingSignatures().contains(currentUserName)) {
				context.put("signAs",  outstanding.get(currentUserName).getFullName());
				context.put("signAction",  	bootstrapManager.getWebAppContextPath()+ REST_PATH+"/sign");
			}
			context.put("panel",  getBoolean(params, "panel", true));
			context.put("mailtoSigned", getMailto(signed.values(), signature.getTitle()));
			context.put("mailtoOutstanding", getMailto(outstanding.values(), signature.getTitle()));
		    context.put("UUID", UUID.randomUUID().toString().replace("-", ""));
		    context.put("downloadURL",  	bootstrapManager.getWebAppContextPath()+ REST_PATH+"/export?key="+signature.getKey());
		    return getRenderedTemplate("templates/macro.vm", context);
		} 
		return "<div class=\"aui-message aui-message-warning\">\n" + 
				"    <p class=\"title\">\n" + 
				"        <strong>Signature Macro</strong>\n" + 
				"    </p>\n" + 
				"    <p>You need to enter at least 10 characters of text to be signed.</p>\n" + 
				"</div>";
		
		
	}

	private Set<String> loadInheritedSigners(InheritSigners inheritSigners, ConversionContext conversionContext) {
		Set<String> users = new HashSet<String>();
		switch (inheritSigners) {
		case READERS_AND_WRITERS:
			users.addAll(loadUsers(conversionContext, ContentPermission.VIEW_PERMISSION));
			users.addAll(loadUsers(conversionContext, ContentPermission.EDIT_PERMISSION));
			break;
		case READERS_ONLY:
			users.addAll(loadUsers(conversionContext, ContentPermission.VIEW_PERMISSION));
			users.removeAll(loadUsers(conversionContext, ContentPermission.EDIT_PERMISSION));
			break;
		case WRITERS_ONLY:
			users.addAll(loadUsers(conversionContext, ContentPermission.EDIT_PERMISSION));
			break;
		case NONE:
			break;
		}
		return users;
	}

	private Set<String> loadUsers(ConversionContext conversionContext, String permission) {
		Set<String> users = new HashSet<String>();
		ContentPermissionSet contentPermissionSet = conversionContext.getEntity().getContentPermissionSet(permission);
		if(contentPermissionSet != null) {
			  for (ContentPermission cp : contentPermissionSet) {
				  users.add(cp.getUserSubject().getName());
	            }
		}
		return users;
	}



	 String getMailto(Collection<UserProfile> profiles, String subject) {
		 if(profiles ==  null || profiles.isEmpty()) return null;
		 StringBuilder ret = new StringBuilder("mailto:");
		 for (UserProfile profile : profiles) {
			if(ret.length()>7) ret.append(',');
			ret.append(format("%s<%s>", profile.getFullName().trim(), profile.getEmail().trim()));
		}
		ret.append("?Subject="+encode(subject));
		return ret.toString();
	}
	
	private Boolean getBoolean(Map<String, String> params, String key, Boolean fallback) {
		String value = params.get(key);
		return value == null ? fallback : Boolean.valueOf(value) ;
	}

	private Set<String> getSet(Map<String, String> params, String key) {
		String value = params.get(key);
		return value == null || value.trim().isEmpty() ? new TreeSet<String>() : new TreeSet<String>(asList(value.split("[;,]")));
	}

	private Signature sync(Signature signature, Set<String> signers) {
		Signature loaded =  (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, signature.getKey());
		if(loaded != null) {
			signature.setSignatures(loaded.getSignatures());
			boolean save = false;

			if(!Objects.equals(loaded.getNotify(),signature.getNotify())) {
				loaded.setNotify(signature.getNotify());
				save = true;
			}
			
			signers.removeAll(loaded.getSignatures().keySet());
			signature.setOutstandingSignatures(signers);
			if(!Objects.equals(loaded.getOutstandingSignatures(),signature.getOutstandingSignatures())) {
				loaded.setOutstandingSignatures(signature.getOutstandingSignatures());
				save = true;
			}
			
			if(save) bandanaManager.setValue(GLOBAL_CONTEXT, signature.getKey(), loaded);
		} else {
			signature.setOutstandingSignatures(signers);
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
