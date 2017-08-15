package com.baloise.confluence.digitalsignature;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.sal.api.user.UserProfile;

public class UserProfileByNameTest {

	@Test
	public void testCompare() throws Exception {
		UserProfile profile1 = Mockito.mock(UserProfile.class);
		when(profile1.getFullName()).thenReturn("Heinz Meier");
		when(profile1.getEmail()).thenReturn("heinz.meier@meier.com");
		when(profile1.toString()).thenReturn("Heinz Meier");
		UserProfile profile2 = Mockito.mock(UserProfile.class);
		when(profile2.getFullName()).thenReturn("Abraham Aebischer");
		when(profile2.getEmail()).thenReturn("Abraham Aebischer@meier.com");
		when(profile2.toString()).thenReturn("Abraham Aebischer");
		SortedSet<UserProfile> profiles = new TreeSet<UserProfile>(new UserProfileByName());
		profiles.add(profile1);
		profiles.add(profile2);
		profiles.add(profile1);
		assertEquals("[Abraham Aebischer, Heinz Meier]", profiles.toString());
	}
}
