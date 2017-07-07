package com.baloise.confluence.digitalsignature;

import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

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
	public <K,V> Map<K,V> union(Map<K,V> map1, Map<K,V> map2) {
		Map<K,V> union = new HashMap<K,V>(map1.size()+map2.size());
		union.putAll(map1);
		union.putAll(map2);
		return union;
	}
	
	public Map<String, UserProfile> getProfiles(UserManager userManager, Set<String>  userNames) {
		Map<String, UserProfile> ret  = new HashMap<String, UserProfile>();
		for (String userName : userNames) {
			ret.put(userName, userManager.getUserProfile(userName));
		}
		return ret;
	}
}
