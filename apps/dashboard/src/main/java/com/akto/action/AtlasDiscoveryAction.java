package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.atlas.AtlasAssetStatus;
import com.akto.service.atlas.AtlasAssetStatusService;
import com.akto.service.atlas.AtlasDeviceScope;
import com.akto.service.atlas.AtlasDiscoveryService;
import com.akto.service.atlas.AtlasPeople;
import com.akto.service.atlas.AtlasToolRow;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightService;
import com.akto.utils.ArgusCollectionScope;
import com.akto.utils.TtlCache;
import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AtlasDiscoveryAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AtlasDiscoveryAction.class, LogDb.DASHBOARD);

    // The insights bundle is keyed by its time range, so the range end is rounded up to share one cached bundle across loads.
    private static final int BUNDLE_BUCKET_SECONDS = 300;
    private static final int MAX_STATUS_UPDATE_KEYS = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final long DEVICE_CACHE_TTL_MS = 60_000;
    private static final int DEVICE_CACHE_MAX_ENTRIES = 1000;
    private static final String TOOL_NOT_FOUND = "Tool not found";
    private static final TtlCache<Integer, Map<String, Map<String, String>>> DEVICE_CACHE = new TtlCache<>(DEVICE_CACHE_TTL_MS, DEVICE_CACHE_MAX_ENTRIES);

    private final InsightService insightService = new InsightService();
    private final AtlasAssetStatusService statusService = new AtlasAssetStatusService();

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
        return serve("Failed to load the agentic discovery overview", AtlasDiscoveryService::buildOverview);
    }

    // One page of the "needs a decision" list; the card asks for as many rows as fit its height.
    public String fetchAtlasDecisions() {
        return serve("Failed to load the tools that need a decision", service -> service.pageDecisions(skip, pageSize()));
    }

    // Filterable, sortable, paged list of discovered tools; backs the CSV export.
    public String fetchAtlasTools() {
        return serve("Failed to load the discovered tools", service -> {
            AtlasDiscoveryService.Query query = new AtlasDiscoveryService.Query();
            query.setCategory(category);
            query.setStatus(status);
            query.setText(queryValue);
            query.setSortKey(sortKey);
            query.setSortOrder(sortOrder);
            query.setSkip(skip);
            query.setLimit(pageSize());
            return service.pageTools(query);
        });
    }

    // One tool for the flyout: its row, top employees and connections.
    public String fetchAtlasToolDetail() {
        return serve("Failed to load the tool", service -> StringUtils.isBlank(assetKey) ? null : service.detail(assetKey));
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
            List<AtlasToolRow> chosen = toolRows().stream()
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

    // Runs one read over the discovery service; users restricted to some collections get an empty page, an unknown tool an error.
    private String serve(String failureMessage, Function<AtlasDiscoveryService, Map<String, Object>> read) {
        try {
            AtlasDiscoveryService service = buildService();
            if (service == null) return SUCCESS.toUpperCase();
            Map<String, Object> result = read.apply(service);
            if (result == null) {
                addActionError(TOOL_NOT_FOUND);
                return ERROR.toUpperCase();
            }
            response = new BasicDBObject(result);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Atlas discovery: " + failureMessage + ": " + e.getMessage());
            addActionError(failureMessage);
            return ERROR.toUpperCase();
        }
    }

    private int pageSize() {
        return limit <= 0 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }

    private static List<AtlasToolRow> toolRows() {
        return new AgenticObserveAction().fetchAtlasToolRows();
    }

    // Null for users restricted to some collections: employee-level data is account-wide, so they get an empty page.
    private AtlasDiscoveryService buildService() {
        if (ArgusCollectionScope.isLimited(getSUser())) return null;
        int now = Context.now();
        InsightDataBundle bundle = insightService.getOrLoadBundle(buildCtx(now));
        List<AtlasToolRow> allRows = toolRows();
        List<AtlasToolRow> rows = AtlasDeviceScope.apply(allRows, deviceScope, AtlasDeviceScope.browserDevices(loadDevices()));
        // Decisions do not depend on the device scope; malicious skills are looked up by name.
        Map<String, AtlasAssetStatus> decisions = new HashMap<>(statusService.loadDecisions());
        decisions.putAll(statusService.loadSkillDecisions(allRows));
        return new AtlasDiscoveryService(rows, decisions, bundle.allowlistNamesLower,
                new AtlasPeople(bundle.deviceIdToUsername, teamByUser(bundle)), now);
    }

    // Every endpoint-shield module is read, so share one read per account across the requests of a page load.
    private static Map<String, Map<String, String>> loadDevices() {
        Integer account = Context.accountId.get();
        Map<String, Map<String, String>> cached = DEVICE_CACHE.get(account);
        if (cached != null) return cached;
        // Read outside any lock; two concurrent loads at worst both read and the later put wins.
        Map<String, Map<String, String>> devices = ModuleInfoDao.instance.fetchDeviceMetadataMapForEndpointShield();
        DEVICE_CACHE.put(account, devices);
        return devices;
    }

    // Atlas reads only the bundle's account-wide data, so a short window keeps its range-scoped reads cheap; the end is shared across loads.
    private InsightContext buildCtx(int now) {
        Integer userId = Context.userId.get();
        int end = (now + BUNDLE_BUCKET_SECONDS - 1) / BUNDLE_BUCKET_SECONDS * BUNDLE_BUCKET_SECONDS;
        return new InsightContext(Context.accountId.get(), userId == null ? 0 : userId, Context.contextSource.get(), end - BUNDLE_BUCKET_SECONDS, end);
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
