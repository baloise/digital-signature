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
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.user.EntityException;
import com.atlassian.user.Group;
import com.atlassian.user.GroupManager;
import com.atlassian.user.search.page.Pager;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.tools.generic.DateTool;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.atlassian.confluence.renderer.radeox.macros.MacroUtils.defaultVelocityContext;
import static com.atlassian.confluence.security.ContentPermission.*;
import static com.atlassian.confluence.util.velocity.VelocityUtils.getRenderedTemplate;
import static com.baloise.confluence.digitalsignature.i18n.TextProperties.*;
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
  private final UserAccessor userAccessor;
  private final Set<String> all = new HashSet<>();
  private final transient Markdown markdown = new Markdown();
  private final ContextHelper contextHelper = new ContextHelper();

  public DigitalSignatureMacro(@ComponentImport BandanaManager bandanaManager,
                               @ComponentImport UserManager userManager,
                               @ComponentImport UserAccessor userAccessor,
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
    this.userAccessor = userAccessor;

    all.add("*");
  }

  static long getLong(Map<String, String> params, String key, long fallback) {
    String value = params.get(key);
    return value == null ? fallback : Long.parseLong(value);
  }

  @Override
  public String execute(Map<String, String> params, String body, ConversionContext conversionContext) {
    if (body == null || body.length() <= 10) {
      return warning(i18nResolver.getText(WARN_BODY_TOO_SHORT));
    }

    ContentEntityObject entity = conversionContext.getEntity();
    if (entity == null) {
      return warning(i18nResolver.getText(WARN_UNKNOWN_CONTEXT));
    }

    Set<String> userGroups = getSet(params, "signerGroups");
    boolean petitionMode = Signature.isPetitionMode(userGroups);
    Set<String> signers = petitionMode ? all : contextHelper.union(getSet(params, "signers"), loadUserGroups(userGroups), loadInheritedSigners(InheritSigners.ofValue(params.get("inheritSigners")), conversionContext));
    Signature signature = sync(new Signature(entity.getLatestVersionId(), body, params.get("title")).withNotified(getSet(params, "notified")).withMaxSignatures(getLong(params, "maxSignatures", -1)).withVisibilityLimit(getLong(params, "visibilityLimit", -1)), signers);

    boolean protectedContent = getBoolean(params, "protectedContent", false);
    if (protectedContent && entity instanceof Page) {
      try {
        ensureProtectedPage((Page) entity, signature);
      } catch (Exception e) {
        return warning(i18nResolver.getText(WARN_EDIT_PERMISSION_REQ, "<a class=\"system-metadata-restrictions\">", "</a>"));
      }
    }

    return getRenderedTemplate("templates/macro.vm", buildContext(params, conversionContext, entity, signature, protectedContent));
  }

  @NotNull
  private Map<String, Object> buildContext(Map<String, String> params, ConversionContext conversionContext, ContentEntityObject page, Signature signature, boolean protectedContent) {
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
    if (protectedContentAccess && conversionContext.getEntity() instanceof Page) {
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

  private void ensureProtectedPage(Page page, Signature signature) {
    Page protectedPage = page.getChildren()
                             .stream()
                             .filter(p -> signature.getProtectedKey().equals(p.getTitle()))
                             .findFirst()
                             .orElse(null);

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
        ConfluenceUser user = userAccessor.getUserByName(signedUserName);
        protectedPage.addPermission(createUserPermission(VIEW_PERMISSION, user));
      }
      protectedPage.setTitle(signature.getProtectedKey());
      pageManager.saveContentEntity(protectedPage, DefaultSaveContext.DEFAULT);
      page.addChild(protectedPage);
    }
  }

  private boolean hideSignatures(Map<String, String> params, Signature signature, String currentUserName) {
    boolean pendingVisible = isVisible(signature, currentUserName, params.get("pendingVisible"));
    if (!pendingVisible) {
      signature.setMissingSignatures(Collections.emptySet());
    }

    boolean signaturesVisible = isVisible(signature, currentUserName, params.get("signaturesVisible"));
    if (!signaturesVisible) {
      signature.setSignatures(Collections.emptyMap());
    }
    return pendingVisible && signaturesVisible;
  }

  private boolean isVisible(Signature signature, String currentUserName, String signaturesVisibleParam) {
    return switch (SignaturesVisible.ofValue(signaturesVisibleParam)) {
      case IF_SIGNATORY -> signature.hasSigned(currentUserName) || signature.isSignatory(currentUserName);
      case IF_SIGNED -> signature.hasSigned(currentUserName);
      case ALWAYS -> true;
    };
  }

  String warning(String message) {
    return String.format("<div class=\"aui-message aui-message-warning\">\n" +
                         "  <p class=\"title\">\n" +
                         "    <strong>%s</strong>\n" +
                         "  </p>\n" +
                         "  <p>%s</p>\n" +
                         "</div>", i18nResolver.getText(LABEL), message);
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
    if (conversionContext == null || conversionContext.getEntity() == null) {
      throw new IllegalArgumentException("permission may not be null!");
    }

    ContentPermissionSet contentPermissionSet = conversionContext.getEntity().getContentPermissionSet(permission);
    if (contentPermissionSet == null) {
      return Collections.emptySet();
    }

    Set<String> users = new HashSet<>();
    for (ContentPermission cp : contentPermissionSet) {
      if (cp.getGroupName() != null) {
        users.addAll(loadUserGroup(cp.getGroupName()));
      }
      if (cp.getUserSubject() != null) {
        users.add(cp.getUserSubject().getName());
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
    if (StringUtils.isBlank(groupName)) {
      return Collections.emptySet();
    }

    Set<String> set = new HashSet<>();
    try {
      Group group = groupManager.getGroup(groupName.trim());
      if (group == null) {
        return set;
      }
      Pager<String> pager = groupManager.getMemberNames(group);
      while (!pager.onLastPage()) {
        set.addAll(pager.getCurrentPage());
        pager.nextPage();
      }
      set.addAll(pager.getCurrentPage());
    } catch (EntityException e) {
      e.printStackTrace();
    }
    return set;
  }

  private Boolean getBoolean(Map<String, String> params, String key, Boolean fallback) {
    String value = params.get(key);
    return value == null ? fallback : Boolean.valueOf(value);
  }

  private Set<String> getSet(Map<String, String> params, String key) {
    String value = params.get(key);
    return value == null || value.trim().isEmpty() ? new TreeSet<>() : new TreeSet<>(asList(value.split("[;, ]+")));
  }

  private Signature sync(Signature signature, Set<String> signers) {
    Signature loaded = Signature.fromBandana(this.bandanaManager, signature.getKey());
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

  private void save(Signature signature) {
    if (signature.hasMissingSignatures()) {
      Signature.toBandana(bandanaManager, signature);
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

  protected String getMailto(Collection<UserProfile> profiles, String subject, boolean signed, Signature signature) {
    if (profiles == null || profiles.isEmpty()) {
      return null;
    }
    Collection<UserProfile> profilesWithMail = profiles.stream()
                                                       .filter(contextHelper::hasEmail)
                                                       .collect(toList());
    StringBuilder ret = new StringBuilder("mailto:");
    for (UserProfile profile : profilesWithMail) {
      if (ret.length() > 7) {
        ret.append(',');
      }
      ret.append(contextHelper.mailTo(profile));
    }
    ret.append("?Subject=").append(URLEncoder.encode(subject, StandardCharsets.UTF_8));
    if (ret.length() > MAX_MAILTO_CHARACTER_COUNT) {
      ret.setLength(0);
      ret.append("mailto:");
      for (UserProfile profile : profilesWithMail) {
        if (ret.length() > 7) {
          ret.append(',');
        }
        ret.append(profile.getEmail().trim());
      }
      ret.append("?Subject=").append(URLEncoder.encode(subject, StandardCharsets.UTF_8));
    }
    if (ret.length() > MAX_MAILTO_CHARACTER_COUNT) {
      return bootstrapManager.getWebAppContextPath() + REST_PATH + "/emails?key=" + signature.getKey() + "&signed=" + signed;
    }
    return ret.toString();
  }
}
