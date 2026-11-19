package com.akto.dto.rbac;

import java.util.regex.Pattern;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Includes collections in a custom role by host name (regex) or by collection tag, so collections
 * (agents) added later are covered without editing the role. Exactly one of hostRegex or tagKey is set.
 */
@Getter
@Setter
@NoArgsConstructor
public class CollectionRule {

    private String hostRegex;
    private String tagKey;
    private String tagValue;

    public CollectionRule(String hostRegex, String tagKey, String tagValue) {
        this.hostRegex = hostRegex;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    private static final int MAX_PATTERN_LENGTH = 200;

    private boolean hasHost() {
        return hostRegex != null && !hostRegex.trim().isEmpty();
    }

    /** Null if the rule is valid, otherwise why it is not. */
    public String validate() {
        boolean hasHost = hasHost();
        boolean hasTag = tagKey != null && !tagKey.trim().isEmpty();
        if (hasHost == hasTag) {
            return "A collection rule needs either a host pattern or a tag.";
        }
        if (hasTag && (tagValue == null || tagValue.trim().isEmpty())) {
            return "A tag rule needs a value, e.g. team=team-a";
        }
        if (hasHost && hostRegex.length() > MAX_PATTERN_LENGTH) {
            return "Host pattern is too long (max " + MAX_PATTERN_LENGTH + " characters).";
        }
        if (hasHost) {
            try {
                Pattern.compile(hostRegex);
            } catch (Exception e) {
                return "Invalid host pattern: " + hostRegex;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return hasHost() ? "host~" + hostRegex : "tag:" + tagKey + "=" + tagValue;
    }
}
