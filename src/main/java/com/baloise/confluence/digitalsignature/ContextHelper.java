package com.baloise.confluence.digitalsignature;

import static java.lang.String.format;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

public class ContextHelper {
	public Object getOrderedSignatures(Signature signature) {
		Comparator<Entry<String, Date>> comparator = new Comparator<Entry<String, Date>>() {
			@Override
			public int compare(Entry<String, Date> s1, Entry<String, Date> s2) {
				int ret = s1.getValue().compareTo(s2.getValue());
				return ret == 0 ? s1.getKey().compareTo(s2.getKey()) : ret;
			}
		};
		SortedSet<Entry<String, Date>> ret = new TreeSet<Map.Entry<String,Date>>(comparator);
		ret.addAll(signature.getSignatures().entrySet());
		return ret;
	}
	public <K,V> Map<K,V> union(Map<K,V> ...maps) {
		Map<K,V> union = new HashMap<K,V>();
		for (Map<K, V> map : maps) {
			union.putAll(map);
		}
		return union;
	}
	
	public <K> Set<K> union(Set<K> ...sets) {
		Set<K> union = new HashSet<K>();
		for (Set<K> set : sets) {
			union.addAll(set);
		}
		return union;
	}
	
	public Map<String, UserProfile> getProfiles(UserManager userManager, Set<String>  userNames) {
		Map<String, UserProfile> ret  = new HashMap<String, UserProfile>();
		if(Signature.isPetitionMode(userNames)) return ret;
		for (String userName : userNames) {
			ret.put(userName, userManager.getUserProfile(userName));
		}
		return ret;
	}
	
	public SortedSet<UserProfile> getOrderedProfiles(UserManager userManager, Set<String> userNames) {
		SortedSet<UserProfile> ret = new TreeSet<UserProfile>(new UserProfileByName());
		if(Signature.isPetitionMode(userNames)) return ret;
		for (String userName : userNames) {
			ret.add(userManager.getUserProfile(userName));
		}
		return ret;
	}
	public String mailTo(UserProfile profile) {
		return format("%s<%s>", profile.getFullName().trim(), profile.getEmail().trim());
	}
	
}
