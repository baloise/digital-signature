package com.baloise.confluence.digitalsignature;

import com.atlassian.sal.api.user.UserProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.SortedSet;
import java.util.TreeSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileByNameTest {
  @Test
  void testCompare() {
    UserProfile profile1 = mock(UserProfile.class);
    when(profile1.getFullName()).thenReturn("Heinz Meier");
    when(profile1.getEmail()).thenReturn("heinz.meier@meier.com");
    when(profile1.toString()).thenReturn("Heinz Meier");
    UserProfile profile2 = mock(UserProfile.class);
    when(profile2.getFullName()).thenReturn("Abraham Aebischer");
    when(profile2.getEmail()).thenReturn("Abraham Aebischer@meier.com");
    when(profile2.toString()).thenReturn("Abraham Aebischer");
    SortedSet<UserProfile> profiles = new TreeSet<>(new UserProfileByName());
    profiles.add(profile1);
    profiles.add(profile2);
    profiles.add(profile1);

    Assertions.assertEquals("[Abraham Aebischer, Heinz Meier]", profiles.toString());
  }
}
