package com.baloise.confluence.digitalsignature;

import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.baloise.confluence.digitalsignature.sal.DummyProfile;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

import static java.lang.String.format;

public class ContextHelper {
    public Object getOrderedSignatures(Signature signature) {
        SortedSet<Entry<String, Date>> ret = new TreeSet<>(Comparator.comparing((Function<Entry<String, Date>, Date>) Entry::getValue)
                                                                     .thenComparing(Entry::getKey));
        ret.addAll(signature.getSignatures().entrySet());
        return ret;
    }

    @SafeVarargs
    public final <K, V> Map<K, V> union(Map<K, V>... maps) {
        Map<K, V> union = new HashMap<>();
        for (Map<K, V> map : maps) {
            union.putAll(map);
        }
        return union;
    }

    @SafeVarargs
    public final <K> Set<K> union(Set<K>... sets) {
        Set<K> union = new HashSet<>();
        for (Set<K> set : sets) {
            union.addAll(set);
        }
        return union;
    }

    public Map<String, UserProfile> getProfiles(UserManager userManager, Set<String> userNames) {
        Map<String, UserProfile> ret = new HashMap<>();
        if (Signature.isPetitionMode(userNames)) return ret;
        for (String userName : userNames) {
            ret.put(userName, getProfileNotNull(userManager, userName));
        }
        return ret;
    }

    public UserProfile getProfileNotNull(UserManager userManager, String userName) {
        UserProfile profile = userManager.getUserProfile(userName);
        return profile == null ? new DummyProfile(userName) : profile;
    }

    public SortedSet<UserProfile> getOrderedProfiles(UserManager userManager, Set<String> userNames) {
        SortedSet<UserProfile> ret = new TreeSet<>(new UserProfileByName());
        if (Signature.isPetitionMode(userNames)) return ret;
        for (String userName : userNames) {
            ret.add(getProfileNotNull(userManager, userName));
        }
        return ret;
    }

    public String mailTo(UserProfile profile) {
        return format("%s<%s>", profile.getFullName().trim(), profile.getEmail().trim());
    }

    public boolean hasEmail(UserProfile profile) {
        return profile != null && profile.getEmail() != null && !profile.getEmail().trim().isEmpty();
    }
}
