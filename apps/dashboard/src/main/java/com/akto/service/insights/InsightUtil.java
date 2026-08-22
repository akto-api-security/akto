package com.akto.service.insights;

import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.GuardrailPolicies.SelectedServer;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        return n + " " + (n == 1 ? singular(noun) : noun);
    }

    private static String singular(String noun) {
        return noun.endsWith("s") ? noun.substring(0, noun.length() - 1) : noun;
    }

    public static String ofTotal(long n, long total, String noun) {
        return n + " of " + total + " " + noun;
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100);
    }

    public static String daysAgo(long epochSeconds, long nowEpochSeconds) {
        long days = Math.max(0, (nowEpochSeconds - epochSeconds) / 86400);
        return days == 0 ? "today" : days + (days == 1 ? " day ago" : " days ago");
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

    public static final String SHELL_EXEC = "SHELL_EXEC";
    public static final String FILE_WRITE = "FILE_WRITE";
    public static final String FILE_READ = "FILE_READ";
    public static final String NETWORK_EGRESS = "NETWORK_EGRESS";
    public static final String UNCLASSIFIED = "UNCLASSIFIED";

    private static final Map<String, String> BUILTIN_TOOL_CAPABILITY = new HashMap<>();
    static {
        BUILTIN_TOOL_CAPABILITY.put("bash", SHELL_EXEC);
        BUILTIN_TOOL_CAPABILITY.put("write", FILE_WRITE);
        BUILTIN_TOOL_CAPABILITY.put("edit", FILE_WRITE);
        BUILTIN_TOOL_CAPABILITY.put("notebookedit", FILE_WRITE);
        BUILTIN_TOOL_CAPABILITY.put("read", FILE_READ);
        BUILTIN_TOOL_CAPABILITY.put("glob", FILE_READ);
        BUILTIN_TOOL_CAPABILITY.put("grep", FILE_READ);
        BUILTIN_TOOL_CAPABILITY.put("webfetch", NETWORK_EGRESS);
        BUILTIN_TOOL_CAPABILITY.put("websearch", NETWORK_EGRESS);
    }

    private static final Set<String> DANGEROUS_CAPABILITIES = new HashSet<>();
    static {
        DANGEROUS_CAPABILITIES.add(SHELL_EXEC);
        DANGEROUS_CAPABILITIES.add(FILE_WRITE);
        DANGEROUS_CAPABILITIES.add(NETWORK_EGRESS);
    }

    /** Known-builtin lookup only; unknown (mostly MCP) tool names should fall through to a classifier. */
    public static String builtinToolCapability(String toolNameOrSlug) {
        return toolNameOrSlug == null ? null : BUILTIN_TOOL_CAPABILITY.get(toolNameOrSlug.toLowerCase(Locale.ROOT));
    }

    public static boolean isDangerousCapability(String capability) {
        return DANGEROUS_CAPABILITIES.contains(capability);
    }

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
