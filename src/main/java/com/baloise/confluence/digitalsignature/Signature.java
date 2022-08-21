package com.baloise.confluence.digitalsignature;

import com.atlassian.bandana.BandanaManager;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class Signature implements Serializable {
  public static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssz").create();
  @Serial
  private static final long serialVersionUID = 1L;
  public static final Gson GSON = new Gson();
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
    this.hash = sha256Hex(pageId + ":" + title + ":" + body);
    this.key = "signature." + hash;
  }

  public static boolean isPetitionMode(Set<String> userGroups) {
    return userGroups != null
           && userGroups.size() == 1
           && userGroups.iterator().next().trim().equals("*");
  }

  static Signature deserialize(String serialization) {
    return GSON.fromJson(serialization, Signature.class);
  }

  public static Signature fromBandana(BandanaManager mgr, String key) {
    if (mgr.getKeys(GLOBAL_CONTEXT) == null
        || !Sets.newHashSet(mgr.getKeys(GLOBAL_CONTEXT)).contains(key)) {
      return null;
    }

    Object value = mgr.getValue(GLOBAL_CONTEXT, key);

    if (value == null) {
      throw new IllegalArgumentException("Value is null in Bandana???");
    }

    if (value instanceof Signature) {
      // required for downward compatibility - update for next time.
      Signature signature = (Signature) value;
      toBandana(mgr, key, signature);
      return signature;
    }

    if (value instanceof String) {
      try {
        return deserialize((String) value);
      } catch (Exception e) {
        log.error("Could not deserialize String value from Bandana", e);
        return null;
      }
    }

    throw new IllegalArgumentException(String.format("Could not deserialize %s value from Bandana. Please clear the plugin-cache and reboot confluence. (https://github.com/baloise/digital-signature/issues/82)", value));
  }

  public static void toBandana(BandanaManager mgr, String key, Signature sig) {
    mgr.setValue(GLOBAL_CONTEXT, key, sig.serialize());
  }

  public static void toBandana(BandanaManager mgr, Signature sig) {
    toBandana(mgr, sig.getKey(), sig);
  }

  String serialize() {
    return GSON.toJson(this, Signature.class);
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
}
