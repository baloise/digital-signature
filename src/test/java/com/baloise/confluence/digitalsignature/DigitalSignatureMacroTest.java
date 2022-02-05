package com.baloise.confluence.digitalsignature;

import com.atlassian.confluence.setup.BootstrapManager;
import com.atlassian.sal.api.user.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DigitalSignatureMacroTest {
  private final Signature signature = new Signature(1, "test", "title");
  private final BootstrapManager bootstrapManager = mock(BootstrapManager.class);

  @Test
  void getMailtoLong() {
    DigitalSignatureMacro macro = new DigitalSignatureMacro(null, null, null, null, null, null, null);
    List<UserProfile> profiles = new ArrayList<>();
    UserProfile profile = mock(UserProfile.class);
    when(profile.getFullName()).thenReturn("Heinz Meier");
    when(profile.getEmail()).thenReturn("heinz.meier@meier.com");
    for (int i = 0; i < 20; i++) {
      profiles.add(profile);
    }

    String mailto = macro.getMailto(profiles, "Subject", true, null);

    assertEquals("mailto:heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com?Subject=Subject", mailto);
  }

  @Test
  void getMailtoVeryLong() {
    when(bootstrapManager.getWebAppContextPath()).thenReturn("nirvana");

    DigitalSignatureMacro macro = new DigitalSignatureMacro(null, null, bootstrapManager, null, null, null, null);
    List<UserProfile> profiles = new ArrayList<>();
    UserProfile profile = mock(UserProfile.class);
    when(profile.getFullName()).thenReturn("Heinz Meier");
    when(profile.getEmail()).thenReturn("heinz.meier@meier.com");
    for (int i = 0; i < 200; i++) {
      profiles.add(profile);
    }

    String mailto = macro.getMailto(profiles, "Subject", true, signature);

    assertEquals("nirvana/rest/signature/1.0/emails?key=signature.3224a4d6bba68cd0ece9b64252f8bf5677e24cf6b7c5f543e3176d419d34d517&signed=true", mailto);
  }

  @Test
  void getMailtoShort() {
    DigitalSignatureMacro macro = new DigitalSignatureMacro(null, null, null, null, null, null, null);
    List<UserProfile> profiles = new ArrayList<>();
    UserProfile profile = mock(UserProfile.class);
    when(profile.getFullName()).thenReturn("Heinz Meier");
    when(profile.getEmail()).thenReturn("heinz.meier@meier.com");
    profiles.add(profile);

    String mailto = macro.getMailto(profiles, "Subject", true, null);

    assertEquals("mailto:Heinz Meier<heinz.meier@meier.com>?Subject=Subject", mailto);
  }
}
