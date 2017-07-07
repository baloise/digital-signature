package com.baloise.confluence.digitalsignature.rest;
import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static java.net.URI.create;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.temporaryRedirect;

import java.net.URI;
import java.util.Date;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.velocity.tools.generic.DateTool;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.velocity.htmlsafe.HtmlSafe;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.baloise.confluence.digitalsignature.ContextHelper;
import com.baloise.confluence.digitalsignature.Signature;

@Path("/")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Scanned
public class DigitalSigatureService {
	private BandanaManager bandanaManager;
	private SettingsManager settingsManager;
	private UserManager userManager;
	private ContextHelper contextHelper = new ContextHelper();
	
	public DigitalSigatureService(@ComponentImport BandanaManager bandanaManager, @ComponentImport SettingsManager settingsManager,@ComponentImport UserManager userManager) {
		this.settingsManager = settingsManager;
		this.bandanaManager = bandanaManager;
		this.userManager = userManager;
	}

	@GET
    @Path("currentUser")
    public Response currentUser() {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
		return Response.ok(new User(user.getName(), bandanaManager.toString())).build();
    }
	
	@GET
	@Path("sign")
	public Response sign(@QueryParam("key") final String key) {
		String userName = AuthenticatedUserThreadLocal.get().getName();
		Signature signature =  (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
		if(!signature.getOutstandingSignatures().remove(userName)) {
			 status(Response.Status.BAD_REQUEST)
            .entity(userName +" is not expected to sign document "+ key)
            .type( MediaType.TEXT_PLAIN)
            .build();
		}
		signature.getSignatures().put(userName, new Date());
		bandanaManager.setValue(GLOBAL_CONTEXT, key, signature);
		URI pageUri = create(settingsManager.getGlobalSettings().getBaseUrl()+ "/pages/viewpage.action?pageId="+signature.getPageId());
		return temporaryRedirect(pageUri).build();
	}
	
	@GET
	@Path("export")
	@Produces({"text/html"})
	@HtmlSafe
	public String export(@QueryParam("key") final String key) {
		Signature signature =  (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
		
		Map<String,Object> context = defaultVelocityContext();
		context.put("signature",  signature);
		context.put("date", new DateTool());
		Map<String, UserProfile> signed = contextHelper.getProfiles(userManager, signature.getSignatures().keySet());
		Map<String, UserProfile> outstanding = contextHelper.getProfiles(userManager, signature.getOutstandingSignatures());
		context.put("orderedSignatures",  contextHelper.getOrderedSignatures(signature));
		context.put("profiles",  contextHelper.union(signed, outstanding));
		
		context.put("currentDate", new Date());
		
		return getRenderedTemplate("templates/export.vm", context);
	}

}