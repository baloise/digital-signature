package com.baloise.confluence.digitalsignature.i18n;

public class TextProperties {
  private static final String TEXT_PREFIX = "com.baloise.confluence.digital-signature.signature.";

  public static final String LABEL = TEXT_PREFIX + "label";
  public static final String WARN_MAX_SIGNATURES_REACHED = TextProperties.TEXT_PREFIX + "service.warning.maxSignaturesReached";
  public static final String WARN_BODY_TOO_SHORT = TEXT_PREFIX + "macro.warning.bodyToShort";
  public static final String WARN_UNKNOWN_CONTEXT = TEXT_PREFIX + "macro.warning.unknownContext";
  public static final String WARN_EDIT_PERMISSION_REQ = TEXT_PREFIX + "macro.warning.editPermissionRequiredForProtectedContent";
  public static final String ERROR_BAD_USER = TextProperties.TEXT_PREFIX + "service.error.badUser";
  public static final String MESSAGE_HAS_SIGNED_SHORT = TextProperties.TEXT_PREFIX + "service.message.hasSignedShort";
  public static final String MESSAGE_SIGNED_USERS_EMAILS = TextProperties.TEXT_PREFIX + "service.message.signedUsersEmails";
  public static final String MESSAGE_UNSIGNED_USERS_EMAILS = TextProperties.TEXT_PREFIX + "service.message.unsignedUsersEmails";
}
