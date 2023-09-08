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
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.*;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.security.ContentPermission.*;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

@Scanned
public class DigitalSignatureMacro implements Macro {
  private static final int MAX_MAILTO_CHARACTER_COUNT = 500;
  private static final String REST_PATH = "/rest/signature/1.0";
  private static final String DISPLAY_PATH = "/display";

  private final BandanaManager bandanaManager;
  private final UserManager userManager;
  private final BootstrapManager bootstrapManager;
  private final PageManager pageManager;
  private final PermissionManager permissionManager;
  private final GroupManager groupManager;
  private final I18nResolver i18nResolver;
  private final transient Markdown markdown = new Markdown();
  private final Set<String> all = new HashSet<>();
  private final ContextHelper contextHelper = new ContextHelper();

  @Autowired
  public DigitalSignatureMacro(@ComponentImport BandanaManager bandanaManager,
                               @ComponentImport UserManager userManager,
                               @ComponentImport BootstrapManager bootstrapManager,
                               @ComponentImport PageManager pageManager,
                               @ComponentImport PermissionManager permissionManager,
                               @ComponentImport GroupManager groupManager,
                               @ComponentImport I18nResolver i18nResolver) {
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
    if (body == null || body.length() <= 10) {
      return warning(i18nResolver.getText("com.baloise.confluence.digital-signature.signature.macro.warning.bodyToShort"));
    }

    Set<String> userGroups = getSet(params, "signerGroups");
    boolean petitionMode = Signature2.isPetitionMode(userGroups);
    Set<String> signers = petitionMode ? all : contextHelper.union(getSet(params, "signers"), loadUserGroups(userGroups), loadInheritedSigners(InheritSigners.ofValue(params.get("inheritSigners")), conversionContext));
    ContentEntityObject entity = conversionContext.getEntity();
    Signature2 signature = sync(new Signature2(entity.getLatestVersionId(), body, params.get("title")).withNotified(getSet(params, "notified")).withMaxSignatures(getLong(params, "maxSignatures", -1)).withVisibilityLimit(getLong(params, "visibilityLimit", -1)), signers);

    boolean protectedContent = getBoolean(params, "protectedContent", false);
    if (protectedContent && isPage(conversionContext)) {
      try {
        ensureProtectedPage(conversionContext, (Page) entity, signature);
      } catch (Exception e) {
        return warning(i18nResolver.getText("com.baloise.confluence.digital-signature.signature.macro.warning.editPermissionRequiredForProtectedContent", "<a class=\"system-metadata-restrictions\">", "</a>"));
      }
    }

    return getRenderedTemplate("templates/macro.vm", buildContext(params, conversionContext, entity, signature, protectedContent));
  }

  @NotNull
  private Map<String, Object> buildContext(Map<String, String> params, ConversionContext conversionContext, ContentEntityObject page, Signature2 signature, boolean protectedContent) {
    ConfluenceUser currentUser = AuthenticatedUserThreadLocal.get();
    String currentUserName = currentUser.getName();
    boolean protectedContentAccess = protectedContent && (permissionManager.hasPermission(currentUser, Permission.EDIT, page) || signature.hasSigned(currentUserName));

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
      context.put("protectedContentURL", bootstrapManager.getWebAppContextPath() + DISPLAY_PATH + "/" + ((Page) page).getSpaceKey() + "/" + signature.getProtectedKey());
    }

    boolean canExport = hideSignatures(params, signature, currentUserName);
    Map<String, UserProfile> signed = contextHelper.getProfiles(userManager, signature.getSignatures().keySet());
    Map<String, UserProfile> missing = contextHelper.getProfiles(userManager, signature.getMissingSignatures());

    context.put("orderedSignatures", contextHelper.getOrderedSignatures(signature));
    context.put("orderedMissingSignatureProfiles", contextHelper.getOrderedProfiles(userManager, signature.getMissingSignatures()));
    context.put("profiles", contextHelper.union(signed, missing));
    context.put("signature", signature);
    context.put("visibilityLimit", signature.getVisibilityLimit());
    context.put("mailtoSigned", getMailto(signed.values(), signature.getTitle(), true, signature));
    context.put("mailtoMissing", getMailto(missing.values(), signature.getTitle(), false, signature));
    context.put("UUID", UUID.randomUUID().toString().replace("-", ""));
    context.put("downloadURL", canExport ? bootstrapManager.getWebAppContextPath() + REST_PATH + "/export?key=" + signature.getKey() : null);
    return context;
  }

  private void ensureProtectedPage(ConversionContext conversionContext, Page page, Signature2 signature) {
    Page protectedPage = pageManager.getPage(conversionContext.getSpaceKey(), signature.getProtectedKey());
    if (protectedPage == null) {
      ContentPermissionSet editors = page.getContentPermissionSet(EDIT_PERMISSION);
      if (editors == null || editors.size() == 0) {
        throw new IllegalStateException("No editors found!");
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

  private boolean hideSignatures(Map<String, String> params, Signature2 signature, String currentUserName) {
    boolean pendingVisible = isVisible(signature, currentUserName, params.get("pendingVisible"));
    boolean signaturesVisible = isVisible(signature, currentUserName, params.get("signaturesVisible"));
    if (!pendingVisible) signature.setMissingSignatures(new TreeSet<>());
    if (!signaturesVisible) signature.setSignatures(new HashMap<>());
    return pendingVisible && signaturesVisible;
  }

  private boolean isVisible(Signature2 signature, String currentUserName, String signaturesVisibleParam) {
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
    return "<div class=\"aui-message aui-message-warning\">\n" + "    <p class=\"title\">\n" + "        <strong>" + i18nResolver.getText("com.baloise.confluence.digital-signature.signature.label") + "</strong>\n" + "    </p>\n" + "    <p>" + message + "</p>\n" + "</div>";
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

  private Signature2 sync(Signature2 signature, Set<String> signers) {
    Signature2 loaded = Signature2.fromBandana(this.bandanaManager, signature.getKey());
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

      if (loaded.getVisibilityLimit() != signature.getVisibilityLimit()) {
        loaded.setVisibilityLimit(signature.getVisibilityLimit());
        save = true;
      }

      if (save) {
        save(loaded);
      }
    } else {
      signature.setMissingSignatures(signers);
      save(signature);
    }
    return signature;
  }

  private void save(Signature2 signature) {
    if (signature.hasMissingSignatures()) {
      Signature2.toBandana(bandanaManager, signature);
    }
  }

  @Override
  public BodyType getBodyType() {
    return BodyType.PLAIN_TEXT;
  }

  @Override
  public OutputType getOutputType() {
    return OutputType.BLOCK;
  }

  protected String getMailto(Collection<UserProfile> profiles, String subject, boolean signed, Signature2 signature) {
    if (profiles == null || profiles.isEmpty()) return null;
    Collection<UserProfile> profilesWithMail = profiles.stream().filter(contextHelper::hasEmail).collect(toList());
    StringBuilder ret = new StringBuilder("mailto:");
    for (UserProfile profile : profilesWithMail) {
      if (ret.length() > 7) ret.append(',');
      ret.append(contextHelper.mailTo(profile));
    }
    ret.append("?Subject=").append(urlEncode(subject));
    if (ret.length() > MAX_MAILTO_CHARACTER_COUNT) {
      ret.setLength(0);
      ret.append("mailto:");
      for (UserProfile profile : profilesWithMail) {
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
      return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
