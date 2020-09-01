package com.baloise.confluence.digitalsignature.sal;

import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserProfile;

import java.net.URI;

public class DummyProfile implements UserProfile {

    private String userKey;

    public DummyProfile(String userKey) {
        this.userKey = userKey;
    }

    @Override
    public UserKey getUserKey() {
        return new UserKey(userKey);
    }

    @Override
    public String getUsername() {
        return userKey;
    }

    @Override
    public String getFullName() {
        return userKey;
    }

    @Override
    public String getEmail() {
        return "";
    }

    @Override
    public URI getProfilePictureUri(int width, int height) {
        return null;
    }

    @Override
    public URI getProfilePictureUri() {
        return null;
    }

    @Override
    public URI getProfilePageUri() {
        return null;
    }

}
