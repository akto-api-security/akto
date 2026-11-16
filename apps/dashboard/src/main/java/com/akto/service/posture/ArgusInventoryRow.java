package com.akto.service.posture;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** One discovered agent, MCP server or LLM host with everything the inventory page shows or filters on. */
@Getter
@Builder
public class ArgusInventoryRow {

    private static final int DAY_SECONDS = 86400;
    private static final int WEEK_DAYS = 7;
    private static final int PRIVILEGED_REASON_AT = 3;

    private final int id;
    private final String name;
    private final String type;
    private final String platform;
    private final int risk;
    private final int createdTs;
    // Last traffic; 0 when none was ever recorded.
    private final int seenTs;
    private final int policies;
    private final int tools;
    private final int privileged;
    private final List<String> teams;
    private final List<String> toolNames;
    private final Set<String> privilegedToolNames;
    // At risk, needs attention or healthy.
    private final ArgusInventoryFlag posture;
    private final Set<ArgusInventoryFlag> flags;

    public boolean has(ArgusInventoryFlag flag) {
        return flags.contains(flag);
    }

    public ArgusInventoryQuadrant quadrant() {
        return ArgusInventoryQuadrant.of(has(ArgusInventoryFlag.HIGH), has(ArgusInventoryFlag.DORMANT));
    }

    public ArgusInventoryUse use(int now) {
        return ArgusInventoryUse.of(seenTs, now);
    }

    /** Whole weeks since the asset was created. */
    public int weeksAgo(int now) {
        return ((now - createdTs) / DAY_SECONDS) / WEEK_DAYS;
    }

    /** The most telling reason to look at this asset, or an empty string. */
    public String reason(int now) {
        if (has(ArgusInventoryFlag.MALICIOUS)) return "Flagged as malicious";
        if (has(ArgusInventoryFlag.EXPOSED)) return "Reachable from the internet with no authentication";
        if (has(ArgusInventoryFlag.SHADOW)) return "Unreviewed, with risk signals";
        if (has(ArgusInventoryFlag.DANGEROUS)) return "Tools can delete data or read credentials";
        if (has(ArgusInventoryFlag.NO_POLICY)) return "No guardrail policy";
        if (has(ArgusInventoryFlag.DORMANT)) {
            return seenTs == 0 ? "No traffic recorded" : "No traffic for " + (now - seenTs) / DAY_SECONDS + " days";
        }
        if (privileged >= PRIVILEGED_REASON_AT) return privileged + " privileged tools";
        return has(ArgusInventoryFlag.UNREVIEWED) ? "Not reviewed yet" : "";
    }

    /** Whether the lower-cased text is in the name, platform, type, a team or a tool name. */
    public boolean matchesText(String lowerText) {
        String searchable = name + " " + platform + " " + type + " " + String.join(" ", teams) + " " + String.join(" ", toolNames);
        return searchable.toLowerCase(Locale.ROOT).contains(lowerText);
    }
}
