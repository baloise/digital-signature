package com.baloise.confluence.digitalsignature;

import com.atlassian.sal.api.user.UserProfile;

import java.util.Comparator;

public class UserProfileByName implements Comparator<UserProfile> {

    @Override
    public int compare(UserProfile u1, UserProfile u2) {
        int ret = nn(u1.getFullName()).compareTo(nn(u2.getFullName()));
        if (ret != 0) return ret;

        ret = nn(u1.getEmail()).compareTo(nn(u2.getEmail()));
        if (ret != 0) return ret;

        ret = nn(u1.getUsername()).compareTo(nn(u2.getUsername()));
        if (ret != 0) return ret;

        return Integer.compare(u1.hashCode(), u2.hashCode());
    }

    private String nn(String string) {
        return string == null ? "" : string;
    }
}
