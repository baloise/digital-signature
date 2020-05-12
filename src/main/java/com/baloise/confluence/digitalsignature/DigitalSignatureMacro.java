package com.baloise.confluence.digitalsignature;

import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.core.DefaultSaveContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.ContentPermission;
import com.atlassian.confluence.security.ContentPermissionSet;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.user.EntityException;
import com.atlassian.user.Group;
import com.atlassian.user.GroupManager;
import com.atlassian.user.search.page.Pager;
import org.apache.velocity.tools.generic.DateTool;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidParameterException;
import java.util.*;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.security.ContentPermission.*;
import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

@Scanned
public class DigitalSignatureMacro implements Macro {
    private final int MAX_MAILTO_CHARACTER_COUNT = 500;
    private final String REST_PATH = "/rest/signature/1.0";
    private final String DISPLAY_PATH = "/display";
    private final transient Markdown markdown = new Markdown();
    private final PermissionManager permissionManager;
    private final Set<String> all = new HashSet<>();
    private BandanaManager bandanaManager;
    private UserManager userManager;
    private BootstrapManager bootstrapManager;
    private PageManager pageManager;
    private ContextHelper contextHelper = new ContextHelper();
    private GroupManager groupManager;
    private I18nResolver i18nResolver;

    @Autowired
    public DigitalSignatureMacro(
            @ComponentImport BandanaManager bandanaManager,
            @ComponentImport UserManager userManager,
            @ComponentImport BootstrapManager bootstrapManager,
            @ComponentImport PageManager pageManager,
            @ComponentImport PermissionManager permissionManager,
            @ComponentImport GroupManager groupManager,
            @ComponentImport I18nResolver i18nResolver
    ) {
        this.bandanaManager = bandanaManager;
        this.userManager = userManager;
        this.bootstrapManager = bootstrapManager;
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.groupManager = groupManager;
        this.i18nResolver = i18nResolver;
        all.add("*");
    }

    @Override
    public String execute(Map<String, String> params, String body, ConversionContext conversionContext) {
        if (body != null && body.length() > 10) {
            Set<String> userGroups = getSet(params, "signerGroups");
            boolean petitionMode = Signature.isPetitionMode(userGroups);
            @SuppressWarnings("unchecked")
            Set<String> signers = petitionMode ? all : contextHelper.union(
                    getSet(params, "signers"),
                    loadUserGroups(userGroups),
                    loadInheritedSigners(InheritSigners.ofValue(params.get("inheritSigners")), conversionContext)
            );
            ContentEntityObject entity = conversionContext.getEntity();
            Signature signature = sync(new Signature(
                            entity.getLatestVersionId(),
                            body,
                            params.get("title"))
                                               .withNotified(getSet(params, "notified"))
                                               .withMaxSignatures(getLong(params, "maxSignatures", -1)),
                    signers
            );
            ConfluenceUser currentUser = AuthenticatedUserThreadLocal.get();
            String currentUserName = currentUser.getName();

            boolean protectedContent = getBoolean(params, "protectedContent", false);
            boolean protectedContentAccess = protectedContent && (permissionManager.hasPermission(currentUser, Permission.EDIT, entity) || signature.hasSigned(currentUserName));

            if (protectedContent && isPage(conversionContext)) {
                Page protectedPage = pageManager.getPage(conversionContext.getSpaceKey(), signature.getProtectedKey());
                if (protectedPage == null) {
                    Page page = (Page) entity;
                    ContentPermissionSet editors = page.getContentPermissionSet(EDIT_PERMISSION);
                    if (editors == null || editors.size() == 0) {
                        return warning(i18nResolver.getText("com.baloise.confluence.digital-signature.signature.macro.warning.editPermissionRequiredForProtectedContent", "<a class=\"system-metadata-restrictions\">", "</a>"));
                    }
                    protectedPage = new Page();
                    protectedPage.setSpace(page.getSpace());
                    protectedPage.setParentPage(page);
                    protectedPage.setVersion(1);
                    protectedPage.setCreator(page.getCreator());
                    for (ContentPermission editor : editors) {
                        protectedPage.addPermission(createUserPermission(EDIT_PERMISSION, editor.getUserSubject()));
                        protectedPage.addPermission(createUserPermission(VIEW_PERMISSION, editor.getUserSubject()));
                    }
                    for (String signedUserName : signature.getSignatures().keySet()) {
                        protectedPage.addPermission(createUserPermission(VIEW_PERMISSION, signedUserName));
                    }
                    protectedPage.setTitle(signature.getProtectedKey());
                    pageManager.saveContentEntity(protectedPage, DefaultSaveContext.DEFAULT);
                    page.addChild(protectedPage);
                }
            }

            Map<String, Object> context = defaultVelocityContext();
            context.put("date", new DateTool());
            context.put("markdown", markdown);

            if (signature.isSignatureMissing(currentUserName)) {
                context.put("signAs", contextHelper.getProfileNotNull(userManager, currentUserName).getFullName());
                context.put("signAction", bootstrapManager.getWebAppContextPath() + REST_PATH + "/sign");
            }
            context.put("panel", getBoolean(params, "panel", true));
            context.put("protectedContent", protectedContentAccess);
            if (protectedContentAccess && isPage(conversionContext)) {
                Page page = (Page) entity;
                context.put("protectedContentURL", bootstrapManager.getWebAppContextPath() + DISPLAY_PATH + "/" + page.getSpaceKey() + "/" + signature.getProtectedKey());
            }

            boolean canExport = hideSignatures(params, signature, currentUserName);
            Map<String, UserProfile> signed = contextHelper.getProfiles(userManager, signature.getSignatures().keySet());
            Map<String, UserProfile> missing = contextHelper.getProfiles(userManager, signature.getMissingSignatures());

            context.put("orderedSignatures", contextHelper.getOrderedSignatures(signature));
            context.put("orderedMissingSignatureProfiles", contextHelper.getOrderedProfiles(userManager, signature.getMissingSignatures()));
            context.put("profiles", contextHelper.union(signed, missing));
            context.put("signature", signature);
            context.put("mailtoSigned", getMailto(signed.values(), signature.getTitle(), true, signature));
            context.put("mailtoMissing", getMailto(missing.values(), signature.getTitle(), false, signature));
            context.put("UUID", UUID.randomUUID().toString().replace("-", ""));
            context.put("downloadURL", canExport ? bootstrapManager.getWebAppContextPath() + REST_PATH + "/export?key=" + signature.getKey() : null);
            return getRenderedTemplate("templates/macro.vm", context);
        }
        return warning(i18nResolver.getText("com.baloise.confluence.digital-signature.signature.macro.warning.bodyToShort"));
    }

    private boolean hideSignatures(Map<String, String> params, Signature signature, String currentUserName) {
        boolean pendingVisible = isVisible(signature, currentUserName, params.get("pendingVisible"));
        boolean signaturesVisible = isVisible(signature, currentUserName, params.get("signaturesVisible"));
        if (!pendingVisible) signature.setMissingSignatures(emptySet());
        if (!signaturesVisible) signature.setSignatures(emptyMap());
        return pendingVisible && signaturesVisible;
    }

    private boolean isVisible(Signature signature, String currentUserName, String signaturesVisibleParam) {
        switch (SignaturesVisible.ofValue(signaturesVisibleParam)) {
            case IF_SIGNATORY:
                return signature.hasSigned(currentUserName) || signature.isSignatory(currentUserName);
            case IF_SIGNED:
                return signature.hasSigned(currentUserName);
            case ALWAYS:
                return true;
            default:
                throw new InvalidParameterException(String.format("'%s' is an unknown value of SignaturesVisible!", signaturesVisibleParam));
        }
    }

    private boolean isPage(ConversionContext conversionContext) {
        return conversionContext.getEntity() instanceof Page;
    }

    private String warning(String message) {
        return "<div class=\"aui-message aui-message-warning\">\n" +
                       "    <p class=\"title\">\n" +
                       "        <strong>" + i18nResolver.getText("com.baloise.confluence.digital-signature.signature.label") + "</strong>\n" +
                       "    </p>\n" +
                       "    <p>" + message + "</p>\n" +
                       "</div>";
    }

    private Set<String> loadInheritedSigners(InheritSigners inheritSigners, ConversionContext conversionContext) {
        Set<String> users = new HashSet<>();
        switch (inheritSigners) {
            case READERS_AND_WRITERS:
                users.addAll(loadUsers(conversionContext, VIEW_PERMISSION));
                users.addAll(loadUsers(conversionContext, EDIT_PERMISSION));
                break;
            case READERS_ONLY:
                users.addAll(loadUsers(conversionContext, VIEW_PERMISSION));
                users.removeAll(loadUsers(conversionContext, EDIT_PERMISSION));
                break;
            case WRITERS_ONLY:
                users.addAll(loadUsers(conversionContext, EDIT_PERMISSION));
                break;
            case NONE:
                break;
            default:
                throw new IllegalArgumentException(inheritSigners + " is unknown or not yet implemented!");
        }
        return users;
    }

    private Set<String> loadUsers(ConversionContext conversionContext, String permission) {
        Set<String> users = new HashSet<>();
        ContentPermissionSet contentPermissionSet = conversionContext.getEntity().getContentPermissionSet(permission);
        if (contentPermissionSet != null) {
            for (ContentPermission cp : contentPermissionSet) {
                if (cp.getGroupName() != null) {
                    users.addAll(loadUserGroup(cp.getGroupName()));
                }
                if (cp.getUserSubject() != null) {
                    users.add(cp.getUserSubject().getName());
                }
            }
        }
        return users;
    }

    private Set<String> loadUserGroups(Iterable<String> groupNames) {
        Set<String> ret = new HashSet<>();
        for (String groupName : groupNames) {
            ret.addAll(loadUserGroup(groupName));
        }
        return ret;
    }

    private Set<String> loadUserGroup(String groupName) {
        Set<String> ret = new HashSet<>();
        try {
            if (groupName == null) return ret;
            Group group = groupManager.getGroup(groupName.trim());
            if (group == null) return ret;
            Pager<String> pager = groupManager.getMemberNames(group);
            while (!pager.onLastPage()) {
                ret.addAll(pager.getCurrentPage());
                pager.nextPage();
            }
            ret.addAll(pager.getCurrentPage());
        } catch (EntityException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private Boolean getBoolean(Map<String, String> params, String key, Boolean fallback) {
        String value = params.get(key);
        return value == null ? fallback : Boolean.valueOf(value);
    }

    private long getLong(Map<String, String> params, String key, long fallback) {
        String value = params.get(key);
        return value == null ? fallback : Long.parseLong(value);
    }

    private Set<String> getSet(Map<String, String> params, String key) {
        String value = params.get(key);
        return value == null || value.trim().isEmpty() ? new TreeSet<>() : new TreeSet<>(asList(value.split("[;, ]+")));
    }

    private Signature sync(Signature signature, Set<String> signers) {
        Signature loaded = (Signature) bandanaManager.getValue(GLOBAL_CONTEXT, signature.getKey());
        if (loaded != null) {
            signature.setSignatures(loaded.getSignatures());
            boolean save = false;

            if (!Objects.equals(loaded.getNotify(), signature.getNotify())) {
                loaded.setNotify(signature.getNotify());
                save = true;
            }

            signers.removeAll(loaded.getSignatures().keySet());
            signature.setMissingSignatures(signers);
            if (!Objects.equals(loaded.getMissingSignatures(), signature.getMissingSignatures())) {
                loaded.setMissingSignatures(signature.getMissingSignatures());
                save = true;
            }

            if (loaded.getMaxSignatures() != signature.getMaxSignatures()) {
                loaded.setMaxSignatures(signature.getMaxSignatures());
                save = true;
            }

            if (save) save(loaded);
        } else {
            signature.setMissingSignatures(signers);
            save(signature);
        }
        return signature;
    }

    private void save(Signature signature) {
        if (signature.hasMissingSignatures())
            bandanaManager.setValue(GLOBAL_CONTEXT, signature.getKey(), signature);
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }

    String getMailto(Collection<UserProfile> profiles, String subject, boolean signed, Signature signature) {
        if (profiles == null || profiles.isEmpty()) return null;
        profiles = profiles.stream()
                           .filter(contextHelper::hasEmail)
                           .collect(toList());
        StringBuilder ret = new StringBuilder("mailto:");
        for (UserProfile profile : profiles) {
            if (ret.length() > 7) ret.append(',');
            ret.append(contextHelper.mailTo(profile));
        }
        ret.append("?Subject=").append(urlEncode(subject));
        if (ret.length() > MAX_MAILTO_CHARACTER_COUNT) {
            ret.setLength(0);
            ret.append("mailto:");
            for (UserProfile profile : profiles) {
                if (ret.length() > 7) ret.append(',');
                ret.append(profile.getEmail().trim());
            }
            ret.append("?Subject=").append(urlEncode(subject));
        }
        if (ret.length() > MAX_MAILTO_CHARACTER_COUNT) {
            return bootstrapManager.getWebAppContextPath() + REST_PATH + "/emails?key=" + signature.getKey() + "&signed=" + signed;
        }
        return ret.toString();
    }

    public String urlEncode(String string) {
        try {
            return URLEncoder.encode(string, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
