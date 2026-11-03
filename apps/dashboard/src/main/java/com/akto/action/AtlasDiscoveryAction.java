package com.akto.action;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.atlas.AtlasAssetStatus;
import com.akto.service.atlas.AtlasAssetStatusService;
import com.akto.service.atlas.AtlasDeviceScope;
import com.akto.service.atlas.AtlasDiscoveryService;
import com.akto.service.atlas.AtlasToolRow;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightService;
import com.akto.service.insights.InsightsThreatBackendAccess;
import com.akto.utils.ArgusCollectionScope;
import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AtlasDiscoveryAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AtlasDiscoveryAction.class, LogDb.DASHBOARD);

    // Range ends are rounded to this, so repeated loads of the same range share one cached insights bundle.
    private static final int CACHE_BUCKET_SECONDS = 300;
    private static final int MAX_STATUS_UPDATE_KEYS = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final long DEVICE_CACHE_TTL_MS = 60_000;
    private static final Map<Integer, CachedDevices> DEVICE_CACHE = new ConcurrentHashMap<>();
    // The flyout's violation strip always covers the last week, whatever range the page shows.
    private static final int VIOLATION_WINDOW_SECONDS = 7 * 24 * 3600;

    private final InsightService insightService = new InsightService();
    private final AtlasAssetStatusService statusService = new AtlasAssetStatusService();
    private final InsightsThreatBackendAccess threatAccess = new InsightsThreatBackendAccess();
    // Loaded by buildService() and reused when one tool's violations are summed.
    private InsightDataBundle bundle;

    private static final class CachedDevices {
        final Map<String, Map<String, String>> devices;
        final long loadedAtMs;

        CachedDevices(Map<String, Map<String, String>> devices, long loadedAtMs) {
            this.devices = devices;
            this.loadedAtMs = loadedAtMs;
        }
    }

    @Getter @Setter private int startTimestamp;
    @Getter @Setter private int endTimestamp;
    @Getter @Setter private String deviceScope;
    @Getter @Setter private List<String> assetKeys;
    @Getter @Setter private String assetKey;
    @Getter @Setter private String status;
    @Getter @Setter private String category;
    @Getter @Setter private String queryValue;
    @Getter @Setter private String sortKey;
    @Getter @Setter private int sortOrder;
    @Getter @Setter private int skip;
    @Getter @Setter private int limit;

    @Getter private BasicDBObject response = new BasicDBObject();

    public String fetchAtlasDiscoveryOverview() {
        try {
            AtlasDiscoveryService service = buildService();
            if (service != null) response = new BasicDBObject(service.buildOverview());
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Atlas discovery overview: " + e.getMessage());
            addActionError("Failed to load the agentic discovery overview");
            return ERROR.toUpperCase();
        }
    }

    // One page of the "needs a decision" list; the card asks for as many rows as fit its height.
    public String fetchAtlasDecisions() {
        try {
            AtlasDiscoveryService service = buildService();
            if (service == null) return SUCCESS.toUpperCase();
            response = new BasicDBObject(service.pageDecisions(Math.max(skip, 0), limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE)));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error listing Atlas decisions: " + e.getMessage());
            addActionError("Failed to load the tools that need a decision");
            return ERROR.toUpperCase();
        }
    }

    // Filterable, sortable, paged list of discovered tools; backs the CSV export.
    public String fetchAtlasTools() {
        try {
            AtlasDiscoveryService service = buildService();
            if (service == null) return SUCCESS.toUpperCase();
            AtlasDiscoveryService.Query query = new AtlasDiscoveryService.Query();
            query.setCategory(category);
            query.setStatus(status);
            query.setText(queryValue);
            query.setSortKey(sortKey);
            query.setSortOrder(sortOrder);
            query.setSkip(Math.max(skip, 0));
            query.setLimit(limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE));
            response = new BasicDBObject(service.pageTools(query));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error listing Atlas discovered tools: " + e.getMessage());
            addActionError("Failed to load the discovered tools");
            return ERROR.toUpperCase();
        }
    }

    // One tool for the flyout: its row, top employees, connections and its violation counts for the last 7 days.
    public String fetchAtlasToolDetail() {
        try {
            AtlasDiscoveryService service = buildService();
            if (service == null) return SUCCESS.toUpperCase();
            Map<String, Object> detail = StringUtils.isBlank(assetKey) ? null : service.detail(assetKey);
            if (detail == null) {
                addActionError("Tool not found");
                return ERROR.toUpperCase();
            }
            response = new BasicDBObject(detail);
            response.put("violations", violationsOf(assetKey));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error loading Atlas tool detail: " + e.getMessage());
            addActionError("Failed to load the tool");
            return ERROR.toUpperCase();
        }
    }

    // Null when the threat backend could not be reached, so "0 violations" is never claimed without checking.
    private Map<String, Integer> violationsOf(String key) {
        if (!bundle.threatBackendAvailable) return null;
        int split = key.indexOf('|');
        Set<String> hosts = new AgenticObserveAction().fetchAtlasToolHosts(key.substring(0, split), key.substring(split + 1))
                .stream().map(host -> host.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int critical = 0, high = 0, medium = 0, low = 0;
        int end = Context.now();
        List<HostSeverityCount> recent = threatAccess.hostSeverityCounts(end - VIOLATION_WINDOW_SECONDS, end);
        for (HostSeverityCount count : recent == null ? Collections.<HostSeverityCount>emptyList() : recent) {
            if (count.getHost() == null || !hosts.contains(count.getHost().toLowerCase(Locale.ROOT))) continue;
            critical += count.getCritical();
            high += count.getHigh();
            medium += count.getMedium();
            low += count.getLow();
        }
        Map<String, Integer> violations = new HashMap<>();
        violations.put("total", critical + high + medium + low);
        violations.put("critical", critical);
        violations.put("high", high);
        violations.put("medium", medium);
        violations.put("low", low);
        return violations;
    }

    public String updateAtlasAssetStatus() {
        try {
            User user = getSUser();
            // A decision applies to every device, so a collection-restricted user may not make it.
            if (ArgusCollectionScope.isLimited(user)) {
                addActionError("Not allowed to change the status of tools on all devices");
                return ERROR.toUpperCase();
            }
            AtlasAssetStatus target = parseStatus(status);
            if (target == null || assetKeys == null || assetKeys.isEmpty() || assetKeys.size() > MAX_STATUS_UPDATE_KEYS) {
                addActionError("Invalid status update request");
                return ERROR.toUpperCase();
            }
            // Only keys that match a discovered tool are written; anything else is ignored.
            Set<String> selected = new HashSet<>(assetKeys);
            List<AtlasToolRow> chosen = new AgenticObserveAction().fetchAtlasToolRows().stream()
                    .filter(row -> selected.contains(AtlasAssetStatusService.toolKey(row.getRowType(), row.getGroupKey())))
                    .collect(Collectors.toList());
            int updated = statusService.updateStatus(chosen, target, user.getLogin());
            response = new BasicDBObject("updatedCount", updated);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating Atlas asset status: " + e.getMessage());
            addActionError("Failed to update the tool status");
            return ERROR.toUpperCase();
        }
    }

    // Null for users restricted to some collections: employee-level data is account-wide, so they get an empty page.
    private AtlasDiscoveryService buildService() {
        if (ArgusCollectionScope.isLimited(getSUser())) return null;
        int now = Context.now();
        // Never in the future, and rounded up so the newest traffic is in range.
        int end = bucketUp(endTimestamp > 0 ? Math.min(endTimestamp, now) : now);
        int start = Math.min(Math.max(startTimestamp, 0) / CACHE_BUCKET_SECONDS * CACHE_BUCKET_SECONDS, end);
        bundle = insightService.getOrLoadBundle(buildCtx(start, end));
        Map<String, Map<String, String>> devices = deviceMetadata();
        List<AtlasToolRow> rows = AtlasDeviceScope.apply(
                new AgenticObserveAction().fetchAtlasToolRows(), deviceScope, AtlasDeviceScope.browserDevices(devices));
        int managedDevices = devices.isEmpty() ? bundle.deviceIdToUsername.size() : AtlasDeviceScope.countDevices(devices, deviceScope);
        return new AtlasDiscoveryService(rows, statusService.loadDecisions(), bundle.allowlistNamesLower,
                bundle.deviceIdToUsername, teamByUser(bundle), managedDevices, start, end, now);
    }

    // Every endpoint-shield module is read, so share one read per account across the requests of a page load.
    private static Map<String, Map<String, String>> deviceMetadata() {
        long nowMs = System.currentTimeMillis();
        DEVICE_CACHE.values().removeIf(entry -> nowMs - entry.loadedAtMs >= 2 * DEVICE_CACHE_TTL_MS);
        return DEVICE_CACHE.compute(Context.accountId.get(), (account, cached) -> {
            if (cached != null && System.currentTimeMillis() - cached.loadedAtMs < DEVICE_CACHE_TTL_MS) return cached;
            return new CachedDevices(ModuleInfoDao.instance.fetchDeviceMetadataMapForEndpointShield(), System.currentTimeMillis());
        }).devices;
    }

    private static int bucketUp(int timestamp) {
        return (timestamp + CACHE_BUCKET_SECONDS - 1) / CACHE_BUCKET_SECONDS * CACHE_BUCKET_SECONDS;
    }

    private InsightContext buildCtx(int start, int end) {
        Integer userId = Context.userId.get();
        return new InsightContext(Context.accountId.get(), userId == null ? 0 : userId, Context.contextSource.get(), start, end);
    }

    private static Map<String, String> teamByUser(InsightDataBundle bundle) {
        Map<String, String> teams = new HashMap<>();
        for (String username : bundle.userTags.keySet()) {
            String team = bundle.teamForUser(username);
            if (StringUtils.isNotBlank(team)) teams.put(username, team.trim());
        }
        return teams;
    }

    private static AtlasAssetStatus parseStatus(String value) {
        for (AtlasAssetStatus candidate : AtlasAssetStatus.values()) {
            if (candidate.name().equals(value)) return candidate;
        }
        return null;
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
