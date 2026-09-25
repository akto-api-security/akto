package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dao.threat_detection.ComplianceClauseCoverageDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.threat_detection.ComplianceClauseCoverage;
import com.akto.dto.threat_detection.ComplianceClauseCoverage.ClauseHit;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.service.insights.InsightUtil.GovernanceBucket;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.compliance.ComplianceSubClauseCatalog;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Risk-score breakdown's own 3rd drill level — one "profile" page per sub-score's L2 row (a tool,
 * a device/employee, or a framework; vendor risk's own profile lives on {@link RiskScoreCalculator}
 * instead, next to the vendor grouping/weighting it reuses — see that class's own
 * {@code vendorRiskProfileDrill}).
 *
 * Pulled out of {@link PostureService} once this became its own self-contained slice (that file
 * was getting large): every handler here only reaches back into {@code PostureService} for a
 * handful of shared row/gap/policy helpers ({@code safe}/{@code row}/{@code worstSeverity}/
 * {@code policyByNameLower}/{@code policyEnforcing}/its {@code GAP_*}/{@code REASON_*} constants),
 * the exact same cross-class calling convention {@link RiskScoreCalculator} already uses for those
 * same helpers. Dispatched from {@link PostureService#fetchRiskScoreDrill}'s 2-segment branch.
 *
 * See {@code PostureDrillResult#layout}'s own javadoc for the shape every handler here builds, and
 * the package {@code CLAUDE.md}'s "Risk score breakdown's own 3rd level" section for the full
 * request-lifecycle picture (which window-event fetch each sub-score needs, and why).
 */
final class RiskScoreProfileDrillService {

    private RiskScoreProfileDrillService() {}

    /** Risk-score breakdown's own 3rd level (one entity — a tool/device/vendor/framework) renders
     *  as a "profile" page (facts + sections), not the generic paginated AgGridTable — each
     *  section's rows are capped here rather than skip/limit-paginated, worst/most-recent first;
     *  see PostureDrillResult#sections' own javadoc. */
    static final int PROFILE_SECTION_CAP = 50;

    /** Shared scaffolding for every entity profile: layout=profile, the 3-item breadcrumb (root ->
     *  sub-score -> this entity), the title/subtitle/badge, and drillable=false (this is the
     *  deepest level any sub-score goes today). */
    static PostureDrillResult newProfileResult(String subScoreLabel, String subScoreId, String entityId,
                                                String title, String subtitle, PostureDrillResult.Badge badge) {
        PostureDrillResult result = new PostureDrillResult();
        result.setLayout("profile");
        result.setTitle(title);
        result.setSubtitle(subtitle);
        result.setBadge(badge);
        List<PostureDrillResult.BreadcrumbItem> breadcrumb = new ArrayList<>();
        breadcrumb.add(new PostureDrillResult.BreadcrumbItem("", "Risk score breakdown"));
        breadcrumb.add(new PostureDrillResult.BreadcrumbItem(subScoreId, subScoreLabel));
        breadcrumb.add(new PostureDrillResult.BreadcrumbItem(subScoreId + "/" + entityId, title));
        result.setBreadcrumb(breadcrumb);
        result.setDrillable(false);
        return result;
    }

    /** How close two same-category events must be to count as one "burst" instead of two separate
     *  timeline rows — see {@link #clusterEvents}'s own javadoc for what a burst is. */
    private static final int TIMELINE_CLUSTER_WINDOW_SECONDS = 3600;
    /** Top N bursts shown per timeline, most recent first — the same policy firing every few
     *  minutes for hours used to render as dozens of near-identical rows; capping to the handful
     *  that actually matter reads as "what happened", not a raw event log. */
    private static final int TIMELINE_TOP_BURSTS = 5;

    /** A profile section rendering `events` (newest first — caller's own sort order is preserved)
     *  as a vertical activity feed, clustered into bursts (see {@link #clusterEvents}) and capped
     *  at {@link #TIMELINE_TOP_BURSTS} rows. Section#total stays the real, uncapped raw-event
     *  count regardless of how many bursts they collapse into. */
    static PostureDrillResult.Section timelineSection(String id, String title, String subtitle,
                                                       List<DashboardMaliciousEvent> events) {
        PostureDrillResult.Section section = new PostureDrillResult.Section();
        section.setId(id);
        section.setTitle(title);
        section.setSubtitle(subtitle);
        section.setKind("timeline");
        section.setRows(clusterEvents(PostureService.safe(events)));
        section.setTotal(events == null ? 0 : events.size());
        return section;
    }

    /** Groups consecutive same-category events (already sorted newest-first) into "bursts": every
     *  event whose timestamp falls within {@link #TIMELINE_CLUSTER_WINDOW_SECONDS} of the burst's
     *  own most-recent event joins that burst rather than starting a new row — a policy that fires
     *  every few minutes for hours (e.g. "Default-Customer PII" repeated 18 times over 3 hours)
     *  becomes one row ("Default-Customer PII ×18") instead of 18 near-identical ones. Each row's
     *  own severity is the worst across its whole burst, not just its first event. Capped at
     *  {@link #TIMELINE_TOP_BURSTS} bursts. */
    private static List<Map<String, Object>> clusterEvents(List<DashboardMaliciousEvent> events) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int i = 0;
        while (i < events.size() && rows.size() < TIMELINE_TOP_BURSTS) {
            DashboardMaliciousEvent first = events.get(i);
            if (first == null) { i++; continue; }
            int j = i + 1;
            while (j < events.size()) {
                DashboardMaliciousEvent next = events.get(j);
                if (next == null || !Objects.equals(first.getCategory(), next.getCategory())
                        || (first.getTimestamp() - next.getTimestamp()) > TIMELINE_CLUSTER_WINDOW_SECONDS) {
                    break;
                }
                j++;
            }
            List<DashboardMaliciousEvent> burst = events.subList(i, j);
            String title = first.getCategory() != null ? first.getCategory() : "Flagged activity";
            if (burst.size() > 1) title = title + " ×" + burst.size();
            rows.add(PostureService.row("timestamp", first.getTimestamp(), "title", title,
                    "detail", first.getHost(), "severity", worstSeverityOfEvents(burst), "status", first.getStatus()));
            i = j;
        }
        return rows;
    }

    /** A profile section rendering `allRows` as a plain table, capped at PROFILE_SECTION_CAP with
     *  the real count kept in Section#total (a "showing N of M" footer, not skip/limit paging —
     *  see PostureDrillResult#sections' own javadoc for why L3 doesn't use AgGridTable's SSRM). */
    static PostureDrillResult.Section tableSection(String id, String title, String subtitle,
                                                    List<PostureDrillResult.ColumnDef> columns,
                                                    List<Map<String, Object>> allRows) {
        PostureDrillResult.Section section = new PostureDrillResult.Section();
        section.setId(id);
        section.setTitle(title);
        section.setSubtitle(subtitle);
        section.setKind("table");
        section.setColumns(columns);
        List<Map<String, Object>> rows = PostureService.safe(allRows);
        section.setTotal(rows.size());
        section.setRows(rows.size() > PROFILE_SECTION_CAP ? new ArrayList<>(rows.subList(0, PROFILE_SECTION_CAP)) : rows);
        return section;
    }

    /** epochSeconds -> "Sep 2, 2026", UTC — a profile's Facts card is plain key/value text, not an
     *  AgGridTable column (see EPOCH_FIELDS on the frontend for how table columns instead do
     *  this), so a first/last-seen fact is formatted here rather than shown as a raw epoch. */
    static String humanEpoch(int epochSeconds) {
        if (epochSeconds <= 0) return "Unknown";
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ROOT));
    }

    /** Same rank/worst logic as {@link PostureService#worstSeverity(List)}, over a raw event list
     *  instead of already-built rows — every profile handler below needs "this entity's worst
     *  severity" to both badge itself and set the drill's own structural `severity` field. */
    static String worstSeverityOfEvents(List<DashboardMaliciousEvent> events) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DashboardMaliciousEvent e : PostureService.safe(events)) {
            rows.add(PostureService.row("severity", e != null ? e.getSeverity() : null));
        }
        return PostureService.worstSeverity(rows);
    }

    /** The device/employee-profile risk pill (DLP incidents and Threat activity share this one
     *  page — see deviceProfileDrill). Null severity (no events at all) reads as low risk, not
     *  invented critical. */
    static PostureDrillResult.Badge severityBadge(String worstSeverity) {
        if (worstSeverity == null) return new PostureDrillResult.Badge("Low risk", "success");
        switch (worstSeverity) {
            case "CRITICAL": return new PostureDrillResult.Badge("Critical risk", "critical");
            case "HIGH": return new PostureDrillResult.Badge("High risk", "critical");
            case "MEDIUM": return new PostureDrillResult.Badge("Medium risk", "warning");
            default: return new PostureDrillResult.Badge("Low risk", "success");
        }
    }

    /** Every non-deactivated collection for one Shadow AI tool — the exact same membership test
     *  {@link #shadowAiToolProfileDrill} already applies, pulled out so
     *  {@code SecurityPostureAction} can also call it (via {@link #collectionIdsForTool}) to scope
     *  its own entity-level malicious-event fetch server-side instead of an unfiltered one — see
     *  that method's own javadoc for why this is safe specifically for this sub-score. */
    private static List<ApiCollection> collectionsForTool(InsightDataBundle bundle, String tool) {
        List<ApiCollection> result = new ArrayList<>();
        for (ApiCollection c : PostureService.safe(bundle.collections)) {
            if (c == null || c.isDeactivated() || c.getHostName() == null) continue;
            if (tool.equals(InsightUtil.governanceGroupingName(c))) result.add(c);
        }
        return result;
    }

    /** apiCollectionId of every collection {@link #collectionsForTool} would return — used ONLY by
     *  {@code SecurityPostureAction} to build a server-side {@code apiCollectionId} filter before
     *  firing this entity's own malicious-event fetch (the {@code additionalFilters.apiCollectionId}
     *  path {@code MaliciousEventService#buildQueryFromFilter} already supports — no backend
     *  change). Safe here (and NOT reused for vendorRisk/threatActivity, which stay unfiltered):
     *  {@link #shadowAiToolProfileDrill}'s own {@code toolEvents} filter already only counts an
     *  event whose host is in this same non-deactivated collection set — restricting the fetch to
     *  these ids server-side changes nothing about which events end up counted, it just moves the
     *  same filter earlier. Vendor risk's and threat activity's own event-matching is NOT
     *  collection-gated this way (a vendor match is a host-string pattern independent of whether a
     *  live ApiCollection still exists for it; threat activity's hostSeverityCounts has no
     *  ApiCollection linkage at all) — scoping either of those to a known-collection-id list would
     *  silently under-count real, currently-shown activity, so they deliberately keep the
     *  unfiltered fetch (see SecurityPostureAction's own needsWindowEvents branch and the package
     *  CLAUDE.md's "risk score breakdown third level" section for this asymmetry). */
    static List<Integer> collectionIdsForTool(InsightDataBundle bundle, String tool) {
        List<Integer> ids = new ArrayList<>();
        for (ApiCollection c : collectionsForTool(bundle, tool)) ids.add(c.getId());
        return ids;
    }

    /** deviceId -> [firstSeen, lastSeen] for one Shadow AI tool's own collections — shared by
     *  {@code PostureService#shadowAiDrill}'s own L2 (tool -> devices) branch, the DRILL_SHADOW_AI
     *  panel's drilldown, which used to re-derive this identical per-device grouping inline rather
     *  than reusing this one. shadowAiToolProfileDrill (this class's own L3 profile) does NOT call
     *  this — it needs bucket/hostNames computed in the very same pass, so it keeps its own merged
     *  loop instead of a second scan through collectionsForTool. */
    static Map<String, int[]> devicesForTool(InsightDataBundle bundle, String tool) {
        Map<String, int[]> seenByDevice = new LinkedHashMap<>();
        for (ApiCollection c : collectionsForTool(bundle, tool)) {
            String deviceId = InsightUtil.deviceIdOf(c);
            if (deviceId == null) deviceId = "unknown";
            int[] seen = seenByDevice.computeIfAbsent(deviceId, k -> new int[]{0, 0});
            if (c.getStartTs() > 0 && (seen[0] == 0 || c.getStartTs() < seen[0])) seen[0] = c.getStartTs();
            Integer lastSeen = bundle.collectionLastTrafficSeen != null ? bundle.collectionLastTrafficSeen.get(c.getId()) : null;
            if (lastSeen != null && lastSeen > seen[1]) seen[1] = lastSeen;
        }
        return seenByDevice;
    }

    /** One resolved user's own aggregate across every collection they touched for this tool —
     *  keyed by the resolved display username, not the raw deviceId, so the same person's several
     *  accounts/collections club into one row instead of showing up as separate near-duplicate
     *  ones (see shadowAiToolProfileDrill's own note on why). */
    private static final class UserActivity {
        int firstSeen;
        int lastSeen;
        final Set<String> assets = new LinkedHashSet<>();
    }

    /** "<name> (<Type>)" for one collection — Skill/MCP Server/AI Agent/LLM/Plugin/SaaS Agent, via
     *  the exact same tagsList-based classifier ({@link AgenticObserveUtil#getTypeFromCollection})
     *  AgenticObserveAction/the App catalog already use, so this reads consistently with that page
     *  rather than inventing a second classification. A skill collection can carry more than one
     *  skill tag, so this can return several labels for one collection. */
    private static List<String> assetLabelsOf(ApiCollection c) {
        String type = AgenticObserveUtil.getTypeFromCollection(c);
        if (AgenticObserveUtil.CLIENT_TYPE_SKILL.equals(type)) {
            Set<String> skills = AgenticObserveUtil.getSkillNames(c);
            if (!skills.isEmpty()) {
                List<String> labels = new ArrayList<>();
                for (String skill : skills) labels.add(skill + " (Skill)");
                return labels;
            }
        }
        if (AgenticObserveUtil.CLIENT_TYPE_PLUGIN.equals(type)) {
            String plugin = AgenticObserveUtil.getPluginName(c);
            if (plugin != null) return Collections.singletonList(plugin + " (Plugin)");
        }
        String name = InsightUtil.serviceNameOf(c);
        if (name == null) name = c.getHostName();
        return Collections.singletonList(name + " (" + type + ")");
    }

    static PostureDrillResult shadowAiToolProfileDrill(InsightDataBundle bundle,
                                                        List<DashboardMaliciousEvent> windowEvents, String tool) {
        // One pass over this tool's own collections builds both the tool-level facts (bucket/
        // firstSeen/lastSeen/hostNames) AND the per-user breakdown (byUser) together — these used
        // to be two separate loops over the identical collectionsForTool(bundle, tool) list (the
        // second one hidden inside a since-removed devicesForTool helper), redoing the same scan
        // for no reason since both need it in a single request, same thread.
        //
        // Grouped by the RESOLVED username, not the raw deviceId: two different deviceIds/
        // collections that both resolve to the same display username (e.g. two accounts for
        // "oscar.carcamo") used to render as two separate near-duplicate rows for the same real
        // person — clubbing by username merges them into one, and also collects which distinct
        // agentic assets (name + type, via assetLabelsOf) that person actually used.
        Map<String, String> remarksByService = InsightUtil.remarksByServiceName(bundle.auditRows);
        GovernanceBucket bucket = null;
        int firstSeen = 0, lastSeen = 0;
        Set<String> hostNames = new HashSet<>();
        Map<String, UserActivity> byUser = new LinkedHashMap<>();
        for (ApiCollection c : collectionsForTool(bundle, tool)) {
            if (bucket == null) bucket = InsightUtil.governanceBucket(c, bundle.allowlistNamesLower, remarksByService);
            Integer seenTs = bundle.collectionLastTrafficSeen != null ? bundle.collectionLastTrafficSeen.get(c.getId()) : null;
            if (c.getStartTs() > 0 && (firstSeen == 0 || c.getStartTs() < firstSeen)) firstSeen = c.getStartTs();
            if (seenTs != null && seenTs > lastSeen) lastSeen = seenTs;
            hostNames.add(c.getHostName());

            String deviceId = InsightUtil.deviceIdOf(c);
            String username = bundle.deviceIdToUsername != null
                    ? bundle.deviceIdToUsername.getOrDefault(deviceId, deviceId != null ? deviceId : "unknown")
                    : (deviceId != null ? deviceId : "unknown");
            UserActivity activity = byUser.computeIfAbsent(username, k -> new UserActivity());
            if (c.getStartTs() > 0 && (activity.firstSeen == 0 || c.getStartTs() < activity.firstSeen)) activity.firstSeen = c.getStartTs();
            if (seenTs != null && seenTs > activity.lastSeen) activity.lastSeen = seenTs;
            activity.assets.addAll(assetLabelsOf(c));
        }

        List<DashboardMaliciousEvent> toolEvents = new ArrayList<>();
        Map<String, Long> flagsByUser = new HashMap<>();
        for (DashboardMaliciousEvent e : PostureService.safe(windowEvents)) {
            if (e == null || e.getHost() == null || !hostNames.contains(e.getHost())) continue;
            toolEvents.add(e);
            String deviceId = AgenticObserveUtil.extractEndpointId(e.getHost());
            String username = bundle.deviceIdToUsername != null && deviceId != null
                    ? bundle.deviceIdToUsername.getOrDefault(deviceId, deviceId) : deviceId;
            if (username != null) flagsByUser.merge(username, 1L, Long::sum);
        }
        toolEvents.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        long criticalCount = 0;
        for (DashboardMaliciousEvent e : toolEvents) {
            if ("CRITICAL".equalsIgnoreCase(e.getSeverity())) criticalCount++;
        }

        boolean sanctioned = bucket == GovernanceBucket.SANCTIONED;
        String bucketLabel = bucket == null ? "Unclassified"
                : Character.toUpperCase(bucket.name().charAt(0)) + bucket.name().substring(1).toLowerCase(Locale.ROOT);
        PostureDrillResult.Badge badge = new PostureDrillResult.Badge(bucketLabel, sanctioned ? "success" : "critical");

        PostureDrillResult result = newProfileResult("Shadow AI exposure", "shadowAiExposure", tool, tool,
                byUser.size() + " user" + (byUser.size() == 1 ? "" : "s") + " · " + toolEvents.size()
                        + " flagged action" + (toolEvents.size() == 1 ? "" : "s") + " in this window", badge);
        result.getCtas().add(new InsightResult.Cta("openFullView", "Open in App catalog", "NAVIGATE",
                InsightRoutes.AGENTIC_ASSETS, null, false));
        result.setSeverity(worstSeverityOfEvents(toolEvents));

        List<InsightResult.Metric> summary = new ArrayList<>();
        summary.add(new InsightResult.Metric("users", "Users", (long) byUser.size(), "count", String.valueOf(byUser.size())));
        summary.add(new InsightResult.Metric("incidents", "Incidents", (long) toolEvents.size(), "count", String.valueOf(toolEvents.size())));
        summary.add(new InsightResult.Metric("critical", "Critical incidents", criticalCount, "count", String.valueOf(criticalCount)));
        result.setSummary(summary);

        List<PostureDrillResult.Fact> facts = new ArrayList<>();
        facts.add(new PostureDrillResult.Fact("Status", bucketLabel, sanctioned ? null : "critical"));
        facts.add(new PostureDrillResult.Fact("First seen", humanEpoch(firstSeen), null));
        facts.add(new PostureDrillResult.Fact("Last seen", humanEpoch(lastSeen), null));
        facts.add(new PostureDrillResult.Fact("Collections", String.valueOf(hostNames.size()), null));
        result.setFacts(facts);

        List<Map<String, Object>> deviceRows = new ArrayList<>();
        for (Map.Entry<String, UserActivity> e : byUser.entrySet()) {
            String username = e.getKey();
            UserActivity activity = e.getValue();
            deviceRows.add(PostureService.row("device", username, "assets", String.join(", ", activity.assets),
                    "firstSeen", activity.firstSeen, "lastSeen", activity.lastSeen,
                    "flags", flagsByUser.getOrDefault(username, 0L)));
        }
        deviceRows.sort((a, b) -> Long.compare((long) b.get("flags"), (long) a.get("flags")));
        List<PostureDrillResult.ColumnDef> deviceColumns = Arrays.asList(
                new PostureDrillResult.ColumnDef("device", "Device / user"),
                new PostureDrillResult.ColumnDef("assets", "Agentic assets used"),
                new PostureDrillResult.ColumnDef("firstSeen", "First seen"),
                new PostureDrillResult.ColumnDef("lastSeen", "Last seen"),
                new PostureDrillResult.ColumnDef("flags", "Flags"));
        result.getSections().add(tableSection("devices", "Who is using it", "Top users by activity this window", deviceColumns, deviceRows));
        result.getSections().add(timelineSection("incidents", "Recent activity", null, toolEvents));

        if (toolEvents.isEmpty()) {
            result.addDataGap(new InsightResult.Gap(PostureService.GAP_THREAT_BACKEND, PostureService.REASON_NO_ROWS,
                    "No flagged activity recorded for \"" + tool + "\" in this window."));
        }
        return result;
    }

    /** Shared device/employee "profile" body for the DLP incidents and Threat activity
     *  sub-scores' own L3 — same page (a device's recent flagged activity), different event
     *  source per sub-score (PII-only vs. every threat-backend event), so both wrap this one
     *  method rather than duplicating it (dlpDeviceProfileDrill/threatDeviceProfileDrill below). */
    private static PostureDrillResult deviceProfileDrill(InsightDataBundle bundle, List<DashboardMaliciousEvent> deviceEvents,
                                                          String subScoreLabel, String subScoreId, String deviceId) {
        List<DashboardMaliciousEvent> events = new ArrayList<>(PostureService.safe(deviceEvents));
        events.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        String display = bundle.deviceIdToUsername != null ? bundle.deviceIdToUsername.getOrDefault(deviceId, deviceId) : deviceId;

        Set<String> tools = new HashSet<>();
        Map<String, Integer> toolFrequency = new HashMap<>();
        int personalAccounts = 0, enterpriseAccounts = 0;
        for (ApiCollection c : PostureService.safe(bundle.collections)) {
            if (c == null || c.isDeactivated() || c.getHostName() == null) continue;
            if (!deviceId.equals(InsightUtil.deviceIdOf(c))) continue;
            String tool = InsightUtil.governanceGroupingName(c);
            if (tool != null) toolFrequency.merge(tool, 1, Integer::sum);
            if (tool != null) tools.add(tool);
            if (InsightUtil.isPersonalAccount(c)) personalAccounts++; else enterpriseAccounts++;
        }
        String mostUsedTool = "Unknown";
        int mostUsedCount = 0;
        for (Map.Entry<String, Integer> e : toolFrequency.entrySet()) {
            if (e.getValue() > mostUsedCount) { mostUsedCount = e.getValue(); mostUsedTool = e.getKey(); }
        }

        String worstSeverity = worstSeverityOfEvents(events);
        PostureDrillResult.Badge badge = severityBadge(worstSeverity);
        long lastActivity = events.isEmpty() ? 0 : events.get(0).getTimestamp();

        PostureDrillResult result = newProfileResult(subScoreLabel, subScoreId, deviceId, display,
                tools.size() + " AI tool" + (tools.size() == 1 ? "" : "s") + " used · " + events.size()
                        + " flagged action" + (events.size() == 1 ? "" : "s") + " in this window", badge);
        result.setNotice("This profile is for coaching and investigation support. Flags are behavioural signals, not conclusions.");
        result.setSeverity(worstSeverity);

        List<InsightResult.Metric> summary = new ArrayList<>();
        summary.add(new InsightResult.Metric("tools", "AI tools used", (long) tools.size(), "count", String.valueOf(tools.size())));
        summary.add(new InsightResult.Metric("flags", "Flagged actions", (long) events.size(), "count", String.valueOf(events.size())));
        result.setSummary(summary);

        List<PostureDrillResult.Fact> facts = new ArrayList<>();
        facts.add(new PostureDrillResult.Fact("Most used tool", mostUsedTool, null));
        facts.add(new PostureDrillResult.Fact("Accounts", personalAccounts + " personal, " + enterpriseAccounts + " enterprise", null));
        facts.add(new PostureDrillResult.Fact("Flagged actions", String.valueOf(events.size()), null));
        facts.add(new PostureDrillResult.Fact("Last activity", lastActivity > 0 ? humanEpoch((int) lastActivity) : "No activity recorded", null));
        result.setFacts(facts);

        result.getSections().add(timelineSection("activity", "AI activity timeline", null, events));
        if (events.isEmpty()) {
            result.addDataGap(new InsightResult.Gap(PostureService.GAP_THREAT_BACKEND, PostureService.REASON_NO_ROWS,
                    "No activity recorded for this device in this window."));
        }
        return result;
    }

    static PostureDrillResult dlpDeviceProfileDrill(InsightDataBundle bundle, List<DashboardMaliciousEvent> allThreats,
                                                     String deviceId) {
        List<DashboardMaliciousEvent> deviceEvents = new ArrayList<>();
        for (DashboardMaliciousEvent e : PostureService.safe(allThreats)) {
            if (e == null || e.getHost() == null) continue;
            if (deviceId.equals(AgenticObserveUtil.extractEndpointId(e.getHost()))) deviceEvents.add(e);
        }
        PostureDrillResult result = deviceProfileDrill(bundle, deviceEvents, "DLP incidents", "dlpIncidents", deviceId);
        result.getCtas().add(new InsightResult.Cta("openFullView", "Open in Guardrail violations", "NAVIGATE",
                InsightRoutes.GUARDRAIL_VIOLATIONS, null, false));
        return result;
    }

    static PostureDrillResult threatDeviceProfileDrill(InsightDataBundle bundle, List<DashboardMaliciousEvent> windowEvents,
                                                        String deviceId) {
        List<DashboardMaliciousEvent> deviceEvents = new ArrayList<>();
        for (DashboardMaliciousEvent e : PostureService.safe(windowEvents)) {
            if (e == null || e.getHost() == null) continue;
            if (deviceId.equals(AgenticObserveUtil.extractEndpointId(e.getHost()))) deviceEvents.add(e);
        }
        PostureDrillResult result = deviceProfileDrill(bundle, deviceEvents, "Threat activity", "threatActivity", deviceId);
        result.getCtas().add(new InsightResult.Cta("openFullView", "Open in Guardrail violations", "NAVIGATE",
                InsightRoutes.GUARDRAIL_VIOLATIONS, null, false));

        long critical = 0, high = 0, medium = 0, low = 0;
        for (HostSeverityCount c : PostureService.safe(bundle.hostSeverityCounts)) {
            if (c == null || c.getHost() == null) continue;
            if (!deviceId.equals(AgenticObserveUtil.extractEndpointId(c.getHost()))) continue;
            critical += c.getCritical(); high += c.getHigh(); medium += c.getMedium(); low += c.getLow();
        }
        result.getFacts().add(new PostureDrillResult.Fact("Severity breakdown this window",
                critical + " critical · " + high + " high · " + medium + " medium · " + low + " low", null));
        return result;
    }

    /** One catalog sub-clause's Met/Partial/Gap status for a specific framework: Gap when nothing
     *  hit it in the window; Partial when something did but no hitting policy is actually mapped
     *  to THIS framework (policyHasComplianceMapping only checks "mapped to any framework" — see
     *  its own javadoc — so this re-checks the specific key); Met when at least one hit came from
     *  a policy mapped to this exact framework. */
    static PostureDrillResult complianceFrameworkProfileDrill(InsightDataBundle bundle, String framework) {
        int trendStartTs = bundle.ctx.getStartTs();
        int trendEndTs = bundle.ctx.getEndTs();
        List<ComplianceClauseCoverage> coverageDocs = ComplianceClauseCoverageDao.instance.findAllCoverage();
        ComplianceClauseCoverage doc = null;
        for (ComplianceClauseCoverage d : PostureService.safe(coverageDocs)) {
            if (d != null && framework.equals(d.getId())) { doc = d; break; }
        }
        Map<String, GuardrailPolicies> policyByNameLower = PostureService.policyByNameLower(bundle.policies);

        int met = 0, partial = 0, gap = 0;
        long totalHitsInWindow = 0;
        List<Map<String, Object>> controlRows = new ArrayList<>();
        for (ComplianceSubClauseCatalog.SubClause sc : ComplianceSubClauseCatalog.subClausesFor(framework)) {
            List<ClauseHit> hits = doc != null && doc.getClauseHits() != null ? doc.getClauseHits().get(sc.label) : null;
            List<ClauseHit> inWindow = new ArrayList<>();
            for (ClauseHit h : PostureService.safe(hits)) {
                if (h != null && h.getTimestamp() >= trendStartTs && h.getTimestamp() <= trendEndTs) inWindow.add(h);
            }
            boolean metByMappedPolicy = false;
            int lastHitTs = 0;
            Set<String> policyNames = new LinkedHashSet<>();
            for (ClauseHit h : inWindow) {
                if (h.getTimestamp() > lastHitTs) lastHitTs = h.getTimestamp();
                if (h.getPolicyName() != null) policyNames.add(h.getPolicyName());
                GuardrailPolicies p = h.getPolicyName() != null ? policyByNameLower.get(h.getPolicyName().toLowerCase(Locale.ROOT)) : null;
                if (p != null && PostureService.policyEnforcing(p) && p.getLlmRule().getCompliance() != null
                        && p.getLlmRule().getCompliance().containsKey(framework)) {
                    metByMappedPolicy = true;
                }
            }
            String status = inWindow.isEmpty() ? "Gap" : (metByMappedPolicy ? "Met" : "Partial");
            if ("Met".equals(status)) met++; else if ("Partial".equals(status)) partial++; else gap++;
            totalHitsInWindow += inWindow.size();

            String evidence = inWindow.isEmpty() ? "None"
                    : inWindow.size() + " hit" + (inWindow.size() == 1 ? "" : "s") + " · " + String.join(", ", policyNames)
                            + " · last hit " + humanEpoch(lastHitTs);
            controlRows.add(PostureService.row("control", sc.id, "requirement", sc.label, "status", status, "evidence", evidence));
        }

        int totalClauses = controlRows.size();
        int readinessPct = totalClauses == 0 ? 0 : (int) Math.round((met * 100.0) / totalClauses);
        PostureDrillResult.Badge badge = new PostureDrillResult.Badge(readinessPct + "% met",
                readinessPct >= 75 ? "success" : readinessPct >= 40 ? "warning" : "critical");

        PostureDrillResult result = newProfileResult("Compliance gaps", "complianceGaps", framework, framework,
                "Control-by-control status with linked evidence.", badge);
        result.getCtas().add(new InsightResult.Cta("openFullView", "Open in Guardrail policies", "NAVIGATE",
                InsightRoutes.GUARDRAIL_POLICIES, null, false));

        List<InsightResult.Metric> summary = new ArrayList<>();
        summary.add(new InsightResult.Metric("met", "Met", (long) met, "count", String.valueOf(met)));
        summary.add(new InsightResult.Metric("partial", "Partial", (long) partial, "count", String.valueOf(partial)));
        summary.add(new InsightResult.Metric("gap", "Gap", (long) gap, "count", String.valueOf(gap)));
        summary.add(new InsightResult.Metric("evidence", "Evidence items", totalHitsInWindow, "count", String.valueOf(totalHitsInWindow)));
        result.setSummary(summary);

        List<PostureDrillResult.ColumnDef> columns = Arrays.asList(
                new PostureDrillResult.ColumnDef("control", "Control"),
                new PostureDrillResult.ColumnDef("requirement", "Requirement"),
                new PostureDrillResult.ColumnDef("status", "Status"),
                new PostureDrillResult.ColumnDef("evidence", "Evidence"));
        result.getSections().add(tableSection("controls", "Controls", null, columns, controlRows));

        if (doc == null) {
            result.addDataGap(new InsightResult.Gap(PostureService.GAP_COMPLIANCE_SCAN, PostureService.REASON_NOT_CONFIGURED,
                    "No compliance clause scan has been run yet for \"" + framework + "\"."));
        }
        return result;
    }
}
