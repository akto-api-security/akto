package com.akto.dto;

import java.util.List;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AgenticUsers {

    public static final String USER_NAME = "userName";
    public static final String USER_EMAIL = "userEmail";
    public static final String USER_ID = "userId";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";

    public static final String DEVICE_TAGS = "deviceTags";

    private String userName;
    private String userEmail;
    // Raw id from whatever external identity source populated this row (e.g. the Microsoft
    // Graph AAD object id for Copilot Studio users) — generic and connector-agnostic, not
    // specific to any one ai-agent source.
    private String userId;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;

    // Generic key-value tags (team, role, department, arbitrary Okta groups, ...).
    private List<DeviceTag> deviceTags;

    // The 8-4-4-4-12 shape. Used to decide whether a userId's trailing segment really is an org
    // uuid, so an unrelated composite id like "okta_12345" is not mistaken for one.
    private static final Pattern ORG_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Pulls the org uuid off a composite userId of the form "&lt;email&gt;_&lt;orgUuid&gt;" — the
     * shape a Claude login writes, one doc per org — returning null when the id carries no org.
     *
     * Split on the LAST underscore, never the first: email local-parts legally contain underscores
     * ("first_last@corp.com_&lt;uuid&gt;"), and a first-underscore split would silently yield a
     * wrong org — a policy that never fires, with nothing in the logs to say why. A uuid contains
     * no underscore, so the last one is always the separator.
     *
     * Mirrors orgUUIDFromUserID in the Go validator (validator/claude_org_match.go), which reads
     * this same id back at request time. Neither side can import the other; keep them in step.
     */
    public static String orgUuidFromUserId(String userId) {
        if (userId == null) return null;
        int i = userId.lastIndexOf('_');
        if (i < 0) return null;
        String candidate = userId.substring(i + 1);
        return ORG_UUID.matcher(candidate).matches() ? candidate : null;
    }
}
