package com.baloise.confluence.digitalsignature.rest;
import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static com.baloise.confluence.digitalsignature.api.DigitalSignatureComponent.PLUGIN_KEY;
import static java.lang.String.format;
import static java.net.URI.create;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.temporaryRedirect;

import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.velocity.tools.generic.DateTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.velocity.htmlsafe.HtmlSafe;
import com.atlassian.mail.Email;
import com.atlassian.mail.MailException;
import com.atlassian.mail.server.MailServerManager;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.mywork.model.NotificationBuilder;
import com.atlassian.mywork.service.LocalNotificationService;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.baloise.confluence.digitalsignature.ContextHelper;
import com.baloise.confluence.digitalsignature.Markdown;
import com.baloise.confluence.digitalsignature.Signature;

@Path("/")
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Scanned
public class DigitalSigatureService {
	private static final Logger log = LoggerFactory.getLogger(DigitalSigatureService.class);
	private final BandanaManager bandanaManager;
	private final SettingsManager settingsManager;
	private final UserManager userManager;
	private final LocalNotificationService notificationService;
	private final MailServerManager mailServerManager;
	private final ContextHelper contextHelper = new ContextHelper();
	private final transient Markdown markdown = new Markdown();
	 
   public DigitalSigatureService(
		   @ComponentImport BandanaManager bandanaManager, 
		   @ComponentImport SettingsManager settingsManager,
		   @ComponentImport UserManager userManager, 
		   @ComponentImport LocalNotificationService notificationService,
		   @ComponentImport MailServerManager mailServerManager
		   ) {
		this.settingsManager = settingsManager;
		this.bandanaManager = bandanaManager;
		this.notificationService = notificationService;
		this.userManager = userManager;
		this.mailServerManager = mailServerManager;
	}

	@GET
	@Path("sign")
	public Response sign(@QueryParam("key") final String key, @Context UriInfo uriInfo) {
		String userName = AuthenticatedUserThreadLocal.get().getName();
		Signature signature =  (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
		if(!signature.getMissingSignatures().remove(userName)) {
			 status(Response.Status.BAD_REQUEST)
            .entity(userName +" is not expected to sign document "+ key)
            .type( MediaType.TEXT_PLAIN)
            .build();
		}
		signature.getSignatures().put(userName, new Date());
		bandanaManager.setValue(GLOBAL_CONTEXT, key, signature);
		
		String baseUrl = settingsManager.getGlobalSettings().getBaseUrl();
		for(String notifiedUser : signature.getNotify()) {
				notify(notifiedUser, signature, baseUrl);
		}
		
		URI pageUri = create(settingsManager.getGlobalSettings().getBaseUrl()+ "/pages/viewpage.action?pageId="+signature.getPageId());
		return temporaryRedirect(pageUri).build();
	}
	
	

	private void notify(final String userName, final Signature signature, final String baseUrl) {
		try {
			UserProfile userProfile = userManager.getUserProfile(userName);
			String html = format("<a href='%s'>%s</a> signed <a href='%s'>%s</a>",
									baseUrl+ "/display/~"+userName,
									userProfile.getFullName(),
									baseUrl+"/pages/viewpage.action?pageId="+signature.getPageId(),
									signature.getTitle()
									);
			String titleText = userProfile.getFullName()+" signed "+signature.getTitle();
			notificationService.createOrUpdate(userName, new NotificationBuilder()
		            .application(PLUGIN_KEY) // a unique key that identifies your plugin
		            .title(titleText)
		            .itemTitle(titleText)
		            .description(html)
		            .groupingId(PLUGIN_KEY+"-signature") // a key to aggregate notifications
		            .createNotification()).get();
			
			SMTPMailServer  mailServer = mailServerManager.getDefaultSMTPMailServer();  
			
			if(mailServer== null) {
				log.warn("No default SMTP server found -> no signature notification sent.");
			} else {
				mailServer.send(
						new Email(userProfile.getEmail())
						.setSubject(titleText)
						.setBody(html)
						.setMimeType("text/html")
						);
			}
	    
		} catch (InterruptedException e) {
			log.error("Could not send notification to "+userName, e);
		} catch (MailException e) {
			log.error("Could not send notification to "+userName, e);
		} catch (ExecutionException e) {
			log.error("Could not send notification to "+userName, e);
		}
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
		context.put("markdown", markdown);
		Map<String, UserProfile> signed = contextHelper.getProfiles(userManager, signature.getSignatures().keySet());
		Map<String, UserProfile> missing = contextHelper.getProfiles(userManager, signature.getMissingSignatures());
		context.put("orderedSignatures",  contextHelper.getOrderedSignatures(signature));
		context.put("profiles",  contextHelper.union(signed, missing));
		
		context.put("currentDate", new Date());
		
		return getRenderedTemplate("templates/export.vm", context);
	}

}