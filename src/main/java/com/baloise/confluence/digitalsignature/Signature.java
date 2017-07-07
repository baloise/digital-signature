package com.baloise.confluence.digitalsignature;

import static org.apache.commons.codec.digest.DigestUtils.*;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class Signature implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String key = "";
	private long pageId;
	private String title = "";
	private String body = "";
	private Map<String, Date> signatures = new HashMap<String, Date>();
	private Set<String> outstandingSignatures = new TreeSet<String>();
	private Set<String> notified = new TreeSet<String>();
	
	public Signature() {}
	public Signature(long pageId, String body,String title) {
		this.pageId = pageId;
		this.body = body;
		this.title = title == null ? "" : title;
		key = "signature."+sha256Hex(pageId +":"+ title +":" + body);
	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public long getPageId() {
		return pageId;
	}
	public void setPageId(long pageId) {
		this.pageId = pageId;
	}
	public String getBody() {
		return body;
	}
	public void setBody(String body) {
		this.body = body;
	}
	public Map<String, Date> getSignatures() {
		return signatures;
	}
	public void setSignatures(Map<String, Date> signatures) {
		this.signatures = signatures;
	}
	public Set<String> getOutstandingSignatures() {
		return outstandingSignatures;
	}
	public void setOutstandingSignatures(Set<String> outstandingSignatures) {
		this.outstandingSignatures = outstandingSignatures;
	}
	
	
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public Set<String> getNotify() {
		return notified;
	}
	public void setNotify(Set<String> notify) {
		this.notified = notify;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Signature other = (Signature) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}
	
	public Signature withNotified(Set<String> notified) {
		this.notified = notified;
		return this;
	}
	
}
