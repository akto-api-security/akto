package com.akto.service.insights;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.GuardrailPolicies.SelectedServer;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static helpers shared across insight providers — number formatting, agent-description
 * lookup, tool-capability classification, and guardrail-policy scope matching. Grouped
 * in one class the way AgenticObserveUtil groups agentic-classification helpers, rather
 * than one file per tiny concern.
 */
public final class InsightUtil {
    private InsightUtil() {}

    // ── Metric formatting — the narrative model copies these strings verbatim ──────────

    public static String count(long n, String noun) {
        return grouped(n) + " " + (n == 1 ? singular(noun) : noun);
    }

    private static String singular(String noun) {
        if (noun.endsWith("ies")) return noun.substring(0, noun.length() - 3) + "y";
        return noun.endsWith("s") ? noun.substring(0, noun.length() - 1) : noun;
    }

    public static String ofTotal(long n, long total, String noun) {
        return grouped(n) + " of " + grouped(total) + " " + noun;
    }

    /** Thousands-grouped, e.g. 18234 -> "18,234" — every formatted count/token number a reader sees goes through this. */
    public static String grouped(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100);
    }

    public static String daysAgo(long epochSeconds, long nowEpochSeconds) {
        long days = Math.max(0, (nowEpochSeconds - epochSeconds) / 86400);
        return days == 0 ? "today" : days + (days == 1 ? " day ago" : " days ago");
    }

    /** A plain-language description of the report window, for headlines that otherwise state only
     *  raw counts with no sense of the time period they cover (e.g. "N violations" vs. "N violations
     *  in the last 2 weeks"). ctx.getStartTs()/getEndTs() &lt;= 0 both means "all time" by the same
     *  convention PolicyHygieneProvider's isWithinWindow uses. */
    public static String dateRangeDescription(int startTs, int endTs) {
        if (startTs <= 0 && endTs <= 0) {
            return "over all recorded activity";
        }
        if (startTs <= 0 || endTs <= 0) {
            return "in the selected window";
        }
        long days = Math.max(1, Math.round((endTs - startTs) / 86400.0));
        boolean endsNearNow = endTs >= com.akto.dao.context.Context.now() - 86400;
        if (endsNearNow) {
            if (days == 1) return "in the last 24 hours";
            if (days == 7) return "in the last week";
            if (days == 14) return "in the last 2 weeks";
            if (days >= 28 && days <= 31) return "in the last month";
            return "in the last " + days + " days";
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US);
        java.time.ZoneId utc = java.time.ZoneId.of("UTC");
        String startStr = java.time.Instant.ofEpochSecond(startTs).atZone(utc).format(fmt);
        String endStr = java.time.Instant.ofEpochSecond(endTs).atZone(utc).format(fmt);
        return "between " + startStr + " and " + endStr;
    }

    // ── Agent purpose (on-domain reference for insights 6/7) ───────────────────────────

    /**
     * ApiCollection.description for one agent/service, or null when unset. Callers must
     * treat null as PARTIAL, never as "off-domain" — see the plan on CollectionDescriptionCron.
     */
    public static String agentDescription(InsightDataBundle bundle, String serviceOrAgentName) {
        for (ApiCollection c : bundle.collectionsForServiceName(serviceOrAgentName)) {
            if (StringUtils.isNotBlank(c.getDescription())) return c.getDescription();
        }
        return null;
    }

    // ── Tool capability classification (insight 8) ──────────────────────────────────────
    // Classification itself is delegated to InsightClassificationHelper.classifyToolDanger
    // (sample-data-driven, no static name/slug table) — see DangerousCapabilityExposureProvider.

    /** Mirrors AgenticObserveAction's private isAgentBuiltinToolUrl. */
    public static boolean isAgentBuiltinToolUrl(String url) {
        return url != null && url.startsWith("/tool/") && url.length() > "/tool/".length();
    }

    /** Mirrors AgenticObserveAction's private mcpDisplayName — last non-empty path segment. */
    public static String toolDisplayName(String url) {
        if (url == null) return null;
        String trimmed = url.replaceAll("/+$", "");
        String[] parts = trimmed.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (StringUtils.isNotBlank(parts[i])) return parts[i];
        }
        return StringUtils.isNotBlank(trimmed) ? trimmed : url;
    }

    // ── Guardrail policy scope matching ─────────────────────────────────────────────────
    //
    // Replicates the enforcement layer's own matching (guardrails-service
    // filterPoliciesByMcpServer / filterPoliciesByDeviceId) rather than inventing a fresh
    // one — see the plan's "Correctness traps". Two rules that are easy to get wrong:
    //   - an empty selected-server set matches NOTHING, not everything.
    //   - policy.getApplyToDeviceIds() == null means "no targeting -> all devices"; a
    //     non-null EMPTY list means "targeting resolved to zero devices -> applies to none".
    //     Resolve it first (AgentUsersDao.findDeviceIdsByTags), as InsightDataLoader does.

    public static boolean isBlockingPolicy(GuardrailPolicies p) {
        return "block".equalsIgnoreCase(p.getBehaviour());
    }

    /** Raw `GuardrailPolicies.behaviour` ("block"/"warn"/"alert"/"approval") is backend jargon —
     *  this is the one place every "Mode" column/caveat should get its display string from,
     *  rather than each provider inventing its own fallback. A blank/unrecognized value means the
     *  lookup missed (most commonly: the policy that fired has since been renamed or deleted), not
     *  that the mode itself is unknown, so it says so plainly instead of "unknown". */
    public static String humanizePolicyMode(String behaviour) {
        if (behaviour == null || behaviour.trim().isEmpty()) {
            return "Policy no longer exists";
        }
        switch (behaviour.trim().toLowerCase(Locale.ROOT)) {
            case "block": return "Block";
            case "warn": return "Warn";
            case "alert": return "Alert only";
            case "approval": return "Needs approval";
            default: return behaviour;
        }
    }

    // ── Taxonomy-mismatch fallback ─────────────────────────────────────────────────────
    // On real accounts, a violation's `subCategory` frequently equals the firing policy's own
    // name rather than the literal ("Secrets" / "PII-<type>" / "PromptInjection") several
    // insights match against — confirmed independently across multiple real accounts. When
    // subCategory doesn't match, fall back to asking the firing POLICY whether it has the
    // relevant scanner enabled at all; a hit from a policy with that scanner on is real signal
    // even when the literal it landed under doesn't say so.

    public static boolean policyHasSecretsDetection(GuardrailPolicies p) {
        return p != null && p.getSecretsDetection() != null && p.getSecretsDetection().isEnabled();
    }

    public static boolean policyHasPiiDetection(GuardrailPolicies p) {
        return p != null && p.getPiiTypes() != null && !p.getPiiTypes().isEmpty();
    }

    public static boolean policyHasPromptInjectionDetection(GuardrailPolicies p) {
        return p != null && p.getContentFiltering() != null && p.getContentFiltering().get("promptAttacks") != null;
    }

    /** Raw `subCategory` literals ("PII-email", "PromptInjection") leaking into evidence tables
     *  and caveats — this turns them into a phrase a reader doesn't need backend context for. */
    public static String humanizeSubCategory(String subCategory) {
        if (subCategory == null || subCategory.trim().isEmpty()) {
            return "(no category)";
        }
        String s = subCategory.trim();
        if (s.regionMatches(true, 0, "PII-", 0, 4) && s.length() > 4) {
            return "PII (" + s.substring(4) + ")";
        }
        if ("PromptInjection".equalsIgnoreCase(s)) {
            return "Prompt injection";
        }
        if ("Secrets".equalsIgnoreCase(s)) {
            return "Secrets";
        }
        return s;
    }

    public static boolean policyCoversHost(GuardrailPolicies p, String hostName) {
        if (hostName == null) return false;
        if (p.isApplyToAllServers()) return true;
        String host = hostName.toLowerCase(Locale.ROOT);
        return matchesAny(p.getSelectedMcpServersV2(), host) || matchesAny(p.getSelectedAgentServersV2(), host);
    }

    private static boolean matchesAny(List<SelectedServer> servers, String hostLower) {
        if (servers == null || servers.isEmpty()) return false;
        for (SelectedServer s : servers) {
            if (s == null) continue;
            if (matchesOne(s.getId(), hostLower) || matchesOne(s.getName(), hostLower)) return true;
        }
        return false;
    }

    private static boolean matchesOne(String stored, String hostLower) {
        if (stored == null || stored.isEmpty()) return false;
        String storedLower = stored.toLowerCase(Locale.ROOT);
        return hostLower.equals(storedLower) || hostLower.endsWith("." + storedLower) || hostLower.contains("." + storedLower + ".");
    }

    public static boolean policyCoversDevice(List<String> resolvedApplyToDeviceIds, String deviceId) {
        if (resolvedApplyToDeviceIds == null) return true; // no targeting configured -> all devices
        return deviceId != null && resolvedApplyToDeviceIds.contains(deviceId);
    }

    public static boolean policyCoversCollection(GuardrailPolicies p, List<String> resolvedApplyToDeviceIds, ApiCollection c) {
        return policyCoversHost(p, c.getHostName()) && policyCoversDevice(resolvedApplyToDeviceIds, deviceIdOf(c));
    }

    // ── Shared content hashing (cache keys for narrative + classification caches) ──────

    public static String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ── CTA deep-link params — only these mechanisms actually work on the frontend today ──
    // (verified against the live pages, not assumed): Users and Devices reads a single `filters`
    // query param shaped `key__v1,v2,...` (GithubServerTable's convention; `username` is the only
    // filterKey its Users-tab `fetchData` actually consumes — `groupName` parses into a UI chip but
    // is silently dropped before it reaches the backend query, so it must not be used here).
    // Guardrail Policies reads `?policy=<exact name>` (opens that policy's edit drawer) and
    // `?policyIds=id1,id2,...` (pre-selects those rows for a bulk action) — `policyId`/`policyName`
    // keys are not read by that page and do nothing.

    /** Empty map (falls back to an unfiltered NAVIGATE) if usernames has no non-blank entries. */
    public static Map<String, Object> usersAndDevicesFilterParams(List<String> usernames) {
        List<String> clean = new ArrayList<>();
        if (usernames != null) {
            for (String u : usernames) if (u != null && !u.isEmpty()) clean.add(u);
        }
        Map<String, Object> params = new HashMap<>();
        if (!clean.isEmpty()) params.put("filters", "username__" + String.join(",", clean));
        return params;
    }

    /** Empty map (falls back to an unfiltered NAVIGATE) if policyName is blank. */
    public static Map<String, Object> guardrailPolicyParams(String policyName) {
        Map<String, Object> params = new HashMap<>();
        if (policyName != null && !policyName.isEmpty()) params.put("policy", policyName);
        return params;
    }

    // ── Collection tag helpers (moved from the former AgenticAssetIndex) ───────────────
    // Tag literals below are the exact strings AgenticObserveAction.computeAgenticTagFlags
    // uses — they are NOT in Constants.java, so they are duplicated here on purpose
    // rather than invented fresh.

    public static String deviceIdOf(ApiCollection c) { return com.akto.util.AgenticObserveUtil.extractEndpointId(c.getHostName()); }
    public static String serviceNameOf(ApiCollection c) { return com.akto.util.AgenticObserveUtil.extractServiceName(c.getHostName()); }

    public static final String TAG_LOCAL_MCP_SERVER = "local-mcp-server";
    public static final String TAG_BROWSER_LLM_ACCOUNT_TYPE = "browser-llm-account-type";
    public static final String TAG_LOGIN_USER_EMAIL_TYPE = "login-user-email-type";
    public static final String TAG_PERSONAL_VALUE = "personal";
    public static final String TAG_MISCONFIGURED_CONFIG = "misconfigured-config";

    public static boolean hasTag(ApiCollection c, String key) {
        if (c.getTagsList() == null) return false;
        for (CollectionTags t : c.getTagsList()) {
            if (t != null && key.equals(t.getKeyName())) return true;
        }
        return false;
    }

    public static boolean hasTagValue(ApiCollection c, String key, String value) {
        if (c.getTagsList() == null) return false;
        for (CollectionTags t : c.getTagsList()) {
            if (t != null && key.equals(t.getKeyName()) && value.equals(t.getValue())) return true;
        }
        return false;
    }

    /** All values seen for a tag key — mode is multi-valued (a collection can be both inline and observe). */
    public static List<String> tagValues(ApiCollection c, String key) {
        List<String> out = new ArrayList<>();
        if (c.getTagsList() == null) return out;
        for (CollectionTags t : c.getTagsList()) {
            if (t != null && key.equals(t.getKeyName()) && t.getValue() != null) out.add(t.getValue());
        }
        return out;
    }

    public static boolean isLocalMcp(ApiCollection c) { return hasTag(c, TAG_LOCAL_MCP_SERVER); }

    public static boolean isPersonalAccount(ApiCollection c) {
        return hasTagValue(c, TAG_BROWSER_LLM_ACCOUNT_TYPE, TAG_PERSONAL_VALUE)
                || hasTagValue(c, TAG_LOGIN_USER_EMAIL_TYPE, TAG_PERSONAL_VALUE);
    }

    public static boolean isMisconfigured(ApiCollection c) {
        return hasTagValue(c, TAG_MISCONFIGURED_CONFIG, "true");
    }

    public static boolean isMaliciousMcpServer(ApiCollection c) {
        return hasTagValue(c, Constants.AKTO_MALICIOUS_MCP_SERVER_TAG, "true");
    }

    public static boolean everSeenInline(ApiCollection c) {
        return tagValues(c, Constants.AKTO_GUARDRAIL_MODE).contains(Constants.AKTO_GUARDRAIL_MODE_INLINE);
    }

    public static boolean everSeenObserve(ApiCollection c) {
        return tagValues(c, Constants.AKTO_GUARDRAIL_MODE).contains(Constants.AKTO_GUARDRAIL_MODE_OBSERVE);
    }
}
