package com.baloise.confluence.digitalsignature;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import java.io.Serializable;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
public class Signature implements Serializable, Cloneable {
  private static final long serialVersionUID = 1L;

  private String key = "";
  private String hash = "";
  private long pageId;
  private String title = "";
  private String body = "";
  private long maxSignatures = -1;
  private long visibilityLimit = -1;
  private Map<String, Date> signatures = new HashMap<>();
  private Set<String> missingSignatures = new TreeSet<>();
  private Set<String> notify = new TreeSet<>();

  public Signature(long pageId, String body, String title) {
    this.pageId = pageId;
    this.body = body;
    this.title = title == null ? "" : title;
    hash = sha256Hex(pageId + ":" + title + ":" + body);
    key = "signature." + hash;
  }

  public static boolean isPetitionMode(Set<String> userGroups) {
    return userGroups != null
               && userGroups.size() == 1
               && userGroups.iterator().next().trim().equals("*");
  }

  public String getHash() {
    if (hash == null) {
      hash = getKey().replace("signature.", "");
    }
    return hash;
  }

  public String getProtectedKey() {
    return "protected." + getHash();
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

    return Objects.equals(key, ((Signature) obj).key);
  }

  public Signature withNotified(Set<String> notified) {
    this.notify = notified;
    return this;
  }

  public Signature withMaxSignatures(long maxSignatures) {
    this.maxSignatures = maxSignatures;
    return this;
  }

  public Signature withVisibilityLimit(long visibilityLimit) {
    this.visibilityLimit = visibilityLimit;
    return this;
  }

  public boolean hasSigned(String userName) {
    return signatures.containsKey(userName);
  }

  public boolean isPetitionMode() {
    return isPetitionMode(getMissingSignatures());
  }

  public boolean sign(String userName) {
    if (!isMaxSignaturesReached() && !isPetitionMode() && !getMissingSignatures().remove(userName)) {
      return false;
    }

    getSignatures().put(userName, new Date());
    return true;
  }

  public boolean isMaxSignaturesReached() {
    return maxSignatures > -1 && maxSignatures <= getSignatures().size();
  }

  public boolean isSignatureMissing(String userName) {
    return !isMaxSignaturesReached() && !hasSigned(userName) && isSignatory(userName);
  }

  public boolean isSignatory(String userName) {
    return isPetitionMode() || getMissingSignatures().contains(userName);
  }

  public boolean hasMissingSignatures() {
    return !isMaxSignaturesReached() && (isPetitionMode() || !getMissingSignatures().isEmpty());
  }

  @Override
  public Signature clone() throws CloneNotSupportedException {
    return (Signature) super.clone();
  }
}
