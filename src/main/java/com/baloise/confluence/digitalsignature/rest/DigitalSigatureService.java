package com.baloise.confluence.digitalsignature.rest;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.velocity.htmlsafe.HtmlSafe;
import com.atlassian.mail.Email;
import com.atlassian.mail.MailException;
import com.atlassian.mail.server.MailServerManager;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.mywork.model.NotificationBuilder;
import com.atlassian.mywork.service.LocalNotificationService;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.baloise.confluence.digitalsignature.ContextHelper;
import com.baloise.confluence.digitalsignature.Markdown;
import com.baloise.confluence.digitalsignature.Signature;
import org.apache.velocity.tools.generic.DateTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.security.ContentPermission.VIEW_PERMISSION;
import static com.atlassian.confluence.security.ContentPermission.createUserPermission;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static com.baloise.confluence.digitalsignature.api.DigitalSignatureComponent.PLUGIN_KEY;
import static java.lang.String.format;
import static java.net.URI.create;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.temporaryRedirect;

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
    private final PageManager pageManager;
    private I18nResolver i18nResolver;

    public DigitalSigatureService(
            @ComponentImport BandanaManager bandanaManager,
            @ComponentImport SettingsManager settingsManager,
            @ComponentImport UserManager userManager,
            @ComponentImport LocalNotificationService notificationService,
            @ComponentImport MailServerManager mailServerManager,
            @ComponentImport PageManager pageManager,
            @ComponentImport I18nResolver i18nResolver
    ) {
        this.settingsManager = settingsManager;
        this.bandanaManager = bandanaManager;
        this.notificationService = notificationService;
        this.userManager = userManager;
        this.mailServerManager = mailServerManager;
        this.pageManager = pageManager;
        this.i18nResolver = i18nResolver;
    }

    @GET
    @Path("sign")
    public Response sign(@QueryParam("key") final String key, @Context UriInfo uriInfo) {
        ConfluenceUser confluenceUser = AuthenticatedUserThreadLocal.get();
        String userName = confluenceUser.getName();
        Signature signature = (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
        if (!signature.sign(userName)) {
            status(Response.Status.BAD_REQUEST)
                    .entity(i18nResolver.getText("com.baloise.confluence.digital-signature.signature.service.error.badUser", userName, key))
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }
        bandanaManager.setValue(GLOBAL_CONTEXT, key, signature);

        String baseUrl = settingsManager.getGlobalSettings().getBaseUrl();
        for (String notifiedUser : signature.getNotify()) {
            notify(notifiedUser, confluenceUser, signature, baseUrl);
        }
        Page parentPage = pageManager.getPage(signature.getPageId());
        Page protectedPage = pageManager.getPage(parentPage.getSpaceKey(), signature.getProtectedKey());
        if (protectedPage != null) {
            protectedPage.addPermission(createUserPermission(VIEW_PERMISSION, confluenceUser));
            pageManager.saveContentEntity(protectedPage, null);
        }
        URI pageUri = create(settingsManager.getGlobalSettings().getBaseUrl() + "/pages/viewpage.action?pageId=" + signature.getPageId());
        return temporaryRedirect(pageUri).build();
    }

    private void notify(final String notifiedUser, ConfluenceUser signedUser, final Signature signature, final String baseUrl) {
        try {
            UserProfile notifiedUserProfile = contextHelper.getProfileNotNull(userManager, notifiedUser);

            String user = format("<a href='%s'>%s</a>",
                    baseUrl + "/display/~" + signedUser.getName(),
                    signedUser.getFullName()
            );
            String document = format("<a href='%s'>%s</a>",
                    baseUrl + "/pages/viewpage.action?pageId=" + signature.getPageId(),
                    signature.getTitle()
            );
            String html = i18nResolver.getText("com.baloise.confluence.digital-signature.signature.service.message.hasSignedShort", user, document);
            if (signature.isMaxSignaturesReached()) {
                html = html + "<br/>" + i18nResolver.getText("com.baloise.confluence.digital-signature.signature.service.warning.maxSignaturesReached", signature.getMaxSignatures());
            }
            String titleText = i18nResolver.getText("com.baloise.confluence.digital-signature.signature.service.message.hasSignedShort", signedUser.getFullName(), signature.getTitle());

            notificationService.createOrUpdate(notifiedUser, new NotificationBuilder()
                    .application(PLUGIN_KEY) // a unique key that identifies your plugin
                    .title(titleText)
                    .itemTitle(titleText)
                    .description(html)
                    .groupingId(PLUGIN_KEY + "-signature") // a key to aggregate notifications
                    .createNotification()).get();

            SMTPMailServer mailServer = mailServerManager.getDefaultSMTPMailServer();

            if (mailServer == null) {
                log.warn("No default SMTP server found -> no signature notification sent.");
            } else if (!contextHelper.hasEmail(notifiedUserProfile)) {
                log.warn(notifiedUser + " is to be notified but has no email address. Skipping email notification");
            } else {
                mailServer.send(
                        new Email(notifiedUserProfile.getEmail())
                                .setSubject(titleText)
                                .setBody(html)
                                .setMimeType("text/html")
                );
            }
        } catch (IllegalArgumentException e) {
            log.error("Could not send notification to " + notifiedUser, e);
        } catch (InterruptedException e) {
            log.error("Could not send notification to " + notifiedUser, e);
        } catch (MailException e) {
            log.error("Could not send notification to " + notifiedUser, e);
        } catch (ExecutionException e) {
            log.error("Could not send notification to " + notifiedUser, e);
        }
    }


    @GET
    @Path("export")
    @Produces("text/html; charset=UTF-8")
    @HtmlSafe
    public String export(@QueryParam("key") final String key) {
        Signature signature = (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);

        Map<String, Object> context = defaultVelocityContext();
        context.put("signature", signature);
        context.put("markdown", markdown);
        Map<String, UserProfile> signed = contextHelper.getProfiles(userManager, signature.getSignatures().keySet());
        Map<String, UserProfile> missing = contextHelper.getProfiles(userManager, signature.getMissingSignatures());
        context.put("orderedSignatures", contextHelper.getOrderedSignatures(signature));
        context.put("orderedMissingSignatureProfiles", contextHelper.getOrderedProfiles(userManager, signature.getMissingSignatures()));
        context.put("profiles", contextHelper.union(signed, missing));

        context.put("currentDate", new Date());
        context.put("date", new DateTool());

        return getRenderedTemplate("templates/export.vm", context);
    }

    @GET
    @Path("emails")
    @Produces("text/html; charset=UTF-8")
    public Response emails(@QueryParam("key") final String key, @QueryParam("signed") final boolean signed, @QueryParam("emailOnly") final boolean emailOnly, @Context UriInfo uriInfo) {
        Signature signature = (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, key);
        Map<String, UserProfile> profiles = contextHelper.getProfiles(userManager, signed ? signature.getSignatures().keySet() : signature.getMissingSignatures());

        Map<String, Object> context = defaultVelocityContext();
        context.put("signature", signature);
        String signatureText = format("<i>%s</i> ( %s )", signature.getTitle(), signature.getHash());
        String rawTemplate = signed ?
                i18nResolver.getRawText("com.baloise.confluence.digital-signature.signature.service.message.signedUsersEmails") :
                i18nResolver.getRawText("com.baloise.confluence.digital-signature.signature.service.message.unsignedUsersEmails");
        context.put("signedOrNotWithHtml", MessageFormat.format(rawTemplate, "<b>", "</b>", signatureText));
        context.put("withNamesChecked", emailOnly ? "" : "checked");
        context.put("signedChecked", signed ? "checked" : "");
        context.put("toggleWithNamesURL", uriInfo.getRequestUriBuilder().replaceQueryParam("emailOnly", !emailOnly).build());
        context.put("toggleSignedURL", uriInfo.getRequestUriBuilder().replaceQueryParam("signed", !signed).build());
        Function<UserProfile, String> mapping = p -> (emailOnly ? p.getEmail() : contextHelper.mailTo(p)).trim();
        context.put("emails", profiles.values().stream()
                .filter(contextHelper::hasEmail)
                .map(mapping).collect(toList()));

        context.put("currentDate", new Date());
        context.put("date", new DateTool());
        return Response.ok(getRenderedTemplate("templates/email.vm", context)).build();
    }

}
