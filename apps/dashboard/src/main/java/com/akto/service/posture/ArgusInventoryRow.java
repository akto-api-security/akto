package com.akto.service.posture;

import lombok.Builder;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One agent, MCP server or LLM host on the inventory page: plain facts, each set once in ArgusInventorySource, and the filter rules over them. */
@Getter
@Builder
public class ArgusInventoryRow {

    public static final String AT_RISK = "atRisk";
    public static final String ATTENTION = "attention";
    public static final String HEALTHY = "healthy";
    public static final List<String> LAST_USED = Arrays.asList("today", "w", "m", "q", "old", "never");
    public static final List<String> QUADRANTS = Arrays.asList("hiA", "hiD", "loA", "loD");

    private static final int DAY_SECONDS = 86400;
    private static final int WEEK_DAYS = 7;
    private static final int PRIVILEGED_REASON_AT = 3;

    private final int id;
    private final String name;
    // AI Agent, MCP Server, ...
    private final String type;
    // The collection's source tag, or Other.
    private final String platform;
    // The collection's start time.
    private final int createdTs;
    // Last traffic in api_info; 0 when none was ever recorded.
    private final int seenTs;
    // The collection's team tags.
    private final List<String> teams;
    // The posture cron's score, 0 to 100.
    private final int risk;
    // How many guardrail policies cover it.
    private final int policies;
    // Tool endpoints in api_info, and how many of them have a capability other than SAFE.
    private final int tools;
    private final int privileged;
    private final List<String> toolNames;
    private final Set<String> privilegedToolNames;

    // Public and unauthenticated: the accessAuth sub-score is 100.
    private final boolean exposed;
    // Can delete data, write to critical systems or read credentials: the overprivilegedTools sub-score is 70 or more.
    private final boolean dangerous;
    // Tagged malicious-mcp-server.
    private final boolean malicious;
    // Sensitive data was seen in its traffic.
    private final boolean sensitive;
    // Nobody approved or rejected it, and it is not on the allowlist (the Overview's rule).
    private final boolean unreviewed;
    // An unreviewed agent that has sensitive data, was only ever observed, or is a personal account (the Overview's rule).
    private final boolean shadow;
    // Older than 30 days, with no traffic for 30 days.
    private final boolean dormant;
    // Created in the last 7 days.
    private final boolean newThisWeek;

    public boolean isHigh() {
        return risk >= ArgusPostureUtils.SEVERITY_HIGH_AT;
    }

    /** A high score with recent traffic is at risk; any other warning sign needs attention; the rest is healthy. */
    public String posture() {
        if (isHigh() && !dormant) return AT_RISK;
        boolean warning = dormant || exposed || dangerous || shadow || malicious || policies == 0;
        return warning ? ATTENTION : HEALTHY;
    }

    /** Whether the row has the flag the page asks for; every flag is one rule over the facts above, and an unknown flag matches nothing. */
    public boolean matchesFlag(String flag) {
        switch (flag) {
            case AT_RISK: return AT_RISK.equals(posture());
            case ATTENTION: return ATTENTION.equals(posture());
            case HEALTHY: return HEALTHY.equals(posture());
            case "high": return isHigh();
            case "noPolicy": return policies == 0;
            case "noTeam": return teams.isEmpty();
            case "dormant": return dormant;
            case "new": return newThisWeek;
            case "unreviewed": return unreviewed;
            case "sensitive": return sensitive;
            case "shadow": return shadow;
            case "exposed": return exposed;
            case "dangerous": return dangerous;
            case "malicious": return malicious;
            case "privileged": return privileged > 0;
            case "comboExposed": return exposed && dangerous;
            case "comboShadow": return shadow && sensitive;
            case "comboUnreviewed": return unreviewed && privileged > 0;
            case "comboDormant": return dormant && privileged > 0;
            default: return false;
        }
    }

    /** Risk against activity: high or lower risk, active or dormant. */
    public String quadrant() {
        return (isHigh() ? "hi" : "lo") + (dormant ? "D" : "A");
    }

    /** How long ago it last had traffic: today, w (1-7 days), m (8-30), q (31-90), old or never. */
    public String lastUsed(int now) {
        if (seenTs == 0) return "never";
        int days = (now - seenTs) / DAY_SECONDS;
        if (days < 1) return "today";
        if (days <= WEEK_DAYS) return "w";
        if (days <= 30) return "m";
        if (days <= 90) return "q";
        return "old";
    }

    /** Whole weeks since it was created. */
    public int weeksAgo(int now) {
        return ((now - createdTs) / DAY_SECONDS) / WEEK_DAYS;
    }

    /** The most telling reason to look at it, or an empty string. */
    public String reason(int now) {
        if (malicious) return "Flagged as malicious";
        if (exposed) return "Reachable from the internet with no authentication";
        if (shadow) return "Unreviewed, with risk signals";
        if (dangerous) return "Tools can delete data or read credentials";
        if (policies == 0) return "No guardrail policy";
        if (dormant) return seenTs == 0 ? "No traffic recorded" : "No traffic for " + (now - seenTs) / DAY_SECONDS + " days";
        if (privileged >= PRIVILEGED_REASON_AT) return privileged + " privileged tools";
        return unreviewed ? "Not reviewed yet" : "";
    }

    /** Whether the lower-cased text is in the name, platform, type, a team or a tool name. */
    public boolean matchesText(String lowerText) {
        String searchable = name + " " + platform + " " + type + " " + String.join(" ", teams) + " " + String.join(" ", toolNames);
        return searchable.toLowerCase(Locale.ROOT).contains(lowerText);
    }
}
