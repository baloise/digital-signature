package com.baloise.confluence.digitalsignature;

import static com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext.GLOBAL_CONTEXT;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

import com.atlassian.bandana.BandanaManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Signature2 implements Serializable {
    public static final Gson GSON = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssz").create();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Signature2.class);
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

    public Signature2(long pageId, String body, String title) {
        this.pageId = pageId;
        this.body = body;
        this.title = title == null ? "" : title;
        this.hash = sha256Hex(this.pageId + ":" + this.title + ":" + this.body);
        this.key = "signature." + hash;
    }

    public static boolean isPetitionMode(Set<String> userGroups) {
    return userGroups != null
           && userGroups.size() == 1
           && userGroups.iterator().next().trim().equals("*");
    }

    static Signature2 deserialize(String serialization) {
    return GSON.fromJson(serialization, Signature2.class);
            }

    public static Signature2 fromBandana(BandanaManager mgr, String key) {
    if (mgr.getKeys(GLOBAL_CONTEXT) == null
        || !StreamSupport.stream(mgr.getKeys(GLOBAL_CONTEXT).spliterator(), false)
        .anyMatch(key::equals)) {
            return null;
        }
        Object value = mgr.getValue(GLOBAL_CONTEXT, key);
        if (value == null) {
            throw new IllegalArgumentException("Value is null in Bandana???");
        }
        if (value instanceof Signature) {
            // required for downward compatibility - update for next time.
            Signature signature = (Signature) value;
            Signature2 sig = new Signature2(signature.getPageId(), signature.getBody(), signature.getTitle());
            sig.setSignatures(signature.getSignatures());
            sig.setMaxSignatures(signature.getMaxSignatures());
            sig.setHash(signature.getHash());
            sig.setKey(signature.getKey());
            sig.setNotify(signature.getNotify());
            sig.setMissingSignatures(signature.getMissingSignatures());
            sig.setVisibilityLimit(signature.getVisibilityLimit());
            toBandana(mgr, key, sig);
            return sig;
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

    public static void toBandana(BandanaManager mgr, String key, Signature2 sig) {
        mgr.setValue(GLOBAL_CONTEXT, key, sig.serialize());
    }

    public static void toBandana(BandanaManager mgr, Signature2 sig) {
        toBandana(mgr, sig.getKey(), sig);
    }

    String serialize() {
    return GSON.toJson(this, Signature2.class);
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

        return Objects.equals(key, ((Signature2) obj).key);
    }

    public Signature2 withNotified(Set<String> notified) {
        this.notify = notified;
        return this;
    }

    public Signature2 withMaxSignatures(long maxSignatures) {
        this.maxSignatures = maxSignatures;
        return this;
    }

    public Signature2 withVisibilityLimit(long visibilityLimit) {
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
    

    @java.lang.SuppressWarnings("all")
    
    public String getKey() {
        return this.key;
    }

    @java.lang.SuppressWarnings("all")
    
    public long getPageId() {
        return this.pageId;
    }

    @java.lang.SuppressWarnings("all")
    
    public String getTitle() {
        return this.title;
    }

    @java.lang.SuppressWarnings("all")
    
    public String getBody() {
        return this.body;
    }

    @java.lang.SuppressWarnings("all")
    
    public long getMaxSignatures() {
        return this.maxSignatures;
    }

    @java.lang.SuppressWarnings("all")
    
    public long getVisibilityLimit() {
        return this.visibilityLimit;
    }

    @java.lang.SuppressWarnings("all")
    
    public Map<String, Date> getSignatures() {
        return this.signatures;
    }

    @java.lang.SuppressWarnings("all")
    
    public Set<String> getMissingSignatures() {
        return this.missingSignatures;
    }

    @java.lang.SuppressWarnings("all")
    
    public Set<String> getNotify() {
        return this.notify;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setKey(final String key) {
        this.key = key;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setHash(final String hash) {
        this.hash = hash;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setPageId(final long pageId) {
        this.pageId = pageId;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setTitle(final String title) {
        this.title = title;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setBody(final String body) {
        this.body = body;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setMaxSignatures(final long maxSignatures) {
        this.maxSignatures = maxSignatures;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setVisibilityLimit(final long visibilityLimit) {
        this.visibilityLimit = visibilityLimit;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setSignatures(final Map<String, Date> signatures) {
        this.signatures = signatures;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setMissingSignatures(final Set<String> missingSignatures) {
        this.missingSignatures = missingSignatures;
    }

    @java.lang.SuppressWarnings("all")
    
    public void setNotify(final Set<String> notify) {
        this.notify = notify;
    }

    @java.lang.SuppressWarnings("all")
    
    public Signature2() {
    }
}
