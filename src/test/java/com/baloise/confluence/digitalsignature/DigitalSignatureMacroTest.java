package com.baloise.confluence.digitalsignature;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.sal.api.user.UserProfile;

public class DigitalSignatureMacroTest {

	@Test
	public void getMailtoLong() {
		DigitalSignatureMacro macro = new DigitalSignatureMacro(null, null, null, null, null);
		List<UserProfile> profiles = new ArrayList<UserProfile>();
		UserProfile profile = Mockito.mock(UserProfile.class);
		when(profile.getFullName()).thenReturn("Heinz Meier");
		when(profile.getEmail()).thenReturn("heinz.meier@meier.com");
		for (int i = 0; i < 20; i++) {
			profiles.add(profile);
		}
		String mailto = macro.getMailto(profiles , "Subject");
		assertEquals("mailto:heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com?Subject=Subject", mailto);
	}
	
	@Test
	public void getMailtoShort() {
		DigitalSignatureMacro macro = new DigitalSignatureMacro(null, null, null, null, null);
		List<UserProfile> profiles = new ArrayList<UserProfile>();
		UserProfile profile = Mockito.mock(UserProfile.class);
		when(profile.getFullName()).thenReturn("Heinz Meier");
		when(profile.getEmail()).thenReturn("heinz.meier@meier.com");
		profiles.add(profile);
		String mailto = macro.getMailto(profiles , "Subject");
		assertEquals("mailto:Heinz Meier<heinz.meier@meier.com>?Subject=Subject", mailto);
	}
	
}
