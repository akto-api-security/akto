package com.akto.service.atlas;

import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpAuditInfo;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Resolves and writes the review status of a discovered AI tool, from McpAuditInfo rows and the allowlist. */
public class AtlasAssetStatusService {

    public static final String ROW_TYPE_AGENT = "agent";
    public static final String ROW_TYPE_SERVICE = "service";
    public static final String ROW_TYPE_LLM = "llm";
    public static final String ROW_TYPE_SKILL = "skill";
    public static final String ROW_TYPE_PLUGIN = "plugin";

    private static final int PAGE_SIZE = 1000;
    private static final long CACHE_TTL_MS = 60_000;
    private static final String CONDITIONALLY_APPROVED = "Conditionally Approved";
    private static final String KEY_SEPARATOR = "|";
    private static final List<String> AUDIT_TYPES = Arrays.asList(
            Constants.AKTO_MCP_SERVER_TAG, Constants.AKTO_AI_AGENT_TAG, Constants.AKTO_BROWSER_LLM_TAG);
    // Strongest decision wins when several rows describe one tool; a server's tool rows follow it.
    private static final List<String> TOOL_AUDIT_TYPES = Arrays.asList(
            Constants.AKTO_MCP_TOOLS_TAG, Constants.AKTO_MCP_RESOURCES_TAG, Constants.AKTO_MCP_PROMPTS_TAG);
    private static final int CASCADE_BATCH_SIZE = 1000;
    private static final List<AtlasAssetStatus> PRECEDENCE = Arrays.asList(
            AtlasAssetStatus.BLOCKED, AtlasAssetStatus.APPROVED, AtlasAssetStatus.UNDER_REVIEW, AtlasAssetStatus.UNAPPROVED);

    private static final Map<String, CachedDecisions> CACHE = new ConcurrentHashMap<>();

    private static class CachedDecisions {
        final Map<String, AtlasAssetStatus> decisions;
        final long loadedAtMs;

        CachedDecisions(Map<String, AtlasAssetStatus> decisions, long loadedAtMs) {
            this.decisions = decisions;
            this.loadedAtMs = loadedAtMs;
        }
    }

    // Skills and plugins are approved through their own flow, so they have no audit type here.
    public static String auditTypeOf(String rowType) {
        if (ROW_TYPE_AGENT.equals(rowType)) return Constants.AKTO_AI_AGENT_TAG;
        if (ROW_TYPE_LLM.equals(rowType)) return Constants.AKTO_BROWSER_LLM_TAG;
        if (ROW_TYPE_SERVICE.equals(rowType)) return Constants.AKTO_MCP_SERVER_TAG;
        return null;
    }

    public static String toolKey(String rowType, String groupKey) {
        return rowType + KEY_SEPARATOR + groupKey;
    }

    public static boolean isGovernable(String rowType) {
        return auditTypeOf(rowType) != null;
    }

    public AtlasAssetStatus resolve(AtlasToolRow tool, Map<String, AtlasAssetStatus> decisions, Set<String> allowlistNamesLower) {
        String type = auditTypeOf(tool.getRowType());
        AtlasAssetStatus decided = type == null ? null : decisions.get(decisionKey(type, tool.getGroupKey()));
        if (decided == AtlasAssetStatus.BLOCKED || decided == AtlasAssetStatus.APPROVED) return decided;
        if (isAllowlisted(tool, allowlistNamesLower)) return AtlasAssetStatus.APPROVED;
        return decided != null ? decided : AtlasAssetStatus.UNAPPROVED;
    }

    public Map<String, AtlasAssetStatus> loadDecisions() {
        String cacheKey = Context.accountId.get() + "_" + Context.contextSource.get();
        // Drop long-expired entries (never one being refreshed) so the map stays bounded.
        long nowMs = System.currentTimeMillis();
        CACHE.values().removeIf(entry -> nowMs - entry.loadedAtMs >= 2 * CACHE_TTL_MS);
        CachedDecisions cached = CACHE.compute(cacheKey, (k, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.loadedAtMs < CACHE_TTL_MS) return existing;
            return new CachedDecisions(readDecisions(), System.currentTimeMillis());
        });
        return cached.decisions;
    }

    public static void invalidate() {
        String prefix = Context.accountId.get() + "_";
        CACHE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Applies a decision to every selected tool; returns how many tools were updated. */
    public int updateStatus(Collection<AtlasToolRow> tools, AtlasAssetStatus status, String markedBy) {
        int now = Context.now();
        int updated = 0;
        List<Bson> decision = decisionUpdates(status, markedBy, now);
        Set<Integer> serverCollectionIds = new HashSet<>();
        try {
            for (AtlasToolRow tool : tools) {
                String type = auditTypeOf(tool.getRowType());
                if (type == null) continue;
                serverCollectionIds.addAll(updateRows(tool, type, status, decision));
                updated++;
            }
            // One pass over the tool rows for all servers, not one per server.
            cascadeToTools(new ArrayList<>(serverCollectionIds), decision);
        } finally {
            // Also after a partial write, so the next read does not serve the old decisions.
            invalidate();
        }
        return updated;
    }

    // A failed read throws, so it is never cached as "every tool is unapproved".
    private Map<String, AtlasAssetStatus> readDecisions() {
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        Bson filter = Filters.and(Filters.in(McpAuditInfo.TYPE, AUDIT_TYPES), contextSourceFilter(Context.contextSource.get()));
        Bson projection = Projections.include(McpAuditInfo.TYPE, McpAuditInfo.RESOURCE_NAME, McpAuditInfo.REMARKS, McpAuditInfo.MARKED_BY);
        ObjectId lastId = null;
        while (true) {
            Bson page = lastId == null ? filter : Filters.and(filter, Filters.gt(Constants.ID, lastId));
            List<McpAuditInfo> rows = McpAuditInfoDao.instance.findAll(page, 0, PAGE_SIZE, Sorts.ascending(Constants.ID), projection);
            for (McpAuditInfo row : rows) {
                if (row.getResourceName() == null) continue;
                AtlasAssetStatus rowStatus = statusOf(row);
                decisions.merge(decisionKey(row.getType(), serverOrResourceName(row)), rowStatus, AtlasAssetStatusService::stronger);
                // A row Atlas created for a server has the bare name, which can itself have dots (e.g. "mcp.example.com").
                decisions.merge(decisionKey(row.getType(), row.getResourceName()), rowStatus, AtlasAssetStatusService::stronger);
            }
            if (rows.size() < PAGE_SIZE) return decisions;
            lastId = rows.get(rows.size() - 1).getId();
        }
    }

    // MCP servers are stored per host ("<device>.<agent>.<name>"); agents and browser apps by their own name.
    static String serverOrResourceName(McpAuditInfo row) {
        if (!Constants.AKTO_MCP_SERVER_TAG.equals(row.getType())) return row.getResourceName();
        return McpRequestResponseUtils.extractServiceNameFromHost(row.getResourceName());
    }

    static String decisionKey(String type, String name) {
        return type + KEY_SEPARATOR + StringUtils.defaultString(name).toLowerCase(Locale.ROOT);
    }

    static AtlasAssetStatus statusOf(McpAuditInfo row) {
        String remarks = row.getRemarks();
        if (McpAuditInfo.REMARKS_REJECTED.equals(remarks)) return AtlasAssetStatus.BLOCKED;
        // ponytail: a conditional approval counts as approved even once expired.
        if (McpAuditInfo.REMARKS_APPROVED.equals(remarks) || CONDITIONALLY_APPROVED.equals(remarks)) return AtlasAssetStatus.APPROVED;
        // A blank decision someone set by hand is "under review"; one created by discovery is just unreviewed.
        return StringUtils.isNotBlank(row.getMarkedBy()) ? AtlasAssetStatus.UNDER_REVIEW : AtlasAssetStatus.UNAPPROVED;
    }

    static AtlasAssetStatus stronger(AtlasAssetStatus a, AtlasAssetStatus b) {
        return PRECEDENCE.indexOf(a) <= PRECEDENCE.indexOf(b) ? a : b;
    }

    private static boolean isAllowlisted(AtlasToolRow tool, Set<String> allowlistNamesLower) {
        for (String name : Arrays.asList(tool.getVendorName(), tool.getGroupKey(), tool.getName())) {
            if (name != null && allowlistNamesLower.contains(name.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static Bson contextSourceFilter(CONTEXT_SOURCE contextSource) {
        if (contextSource == null) return Filters.eq(McpAuditInfo.CONTEXT_SOURCE, null);
        return Filters.or(
                Filters.eq(McpAuditInfo.CONTEXT_SOURCE, contextSource.name()),
                Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false));
    }

    private static List<Bson> decisionUpdates(AtlasAssetStatus status, String markedBy, int now) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(McpAuditInfo.REMARKS, remarksOf(status)));
        // Resetting to unapproved clears the marker, which is what tells it apart from "under review".
        updates.add(Updates.set(McpAuditInfo.MARKED_BY, status == AtlasAssetStatus.UNAPPROVED ? "" : markedBy));
        updates.add(Updates.set(McpAuditInfo.UPDATED_TIMESTAMP, now));
        if (status == AtlasAssetStatus.APPROVED) updates.add(Updates.set(McpAuditInfo.APPROVED_AT, now));
        return updates;
    }

    // Rows scoped to devices, tags, users or conditions belong to the Audit page; a global decision leaves them alone.
    private static Bson globalRowsOnly() {
        List<Bson> unscoped = new ArrayList<>();
        for (String field : Arrays.asList(McpAuditInfo.TARGET_TAGS, McpAuditInfo.TARGET_DEVICE_IDS, McpAuditInfo.USER_METADATA, McpAuditInfo.APPROVAL_CONDITIONS)) {
            unscoped.add(Filters.or(Filters.exists(field, false), Filters.eq(field, null),
                    Filters.eq(field, Collections.emptyList()), Filters.eq(field, new Document())));
        }
        return Filters.and(unscoped);
    }

    private static String remarksOf(AtlasAssetStatus status) {
        if (status == AtlasAssetStatus.APPROVED) return McpAuditInfo.REMARKS_APPROVED;
        if (status == AtlasAssetStatus.BLOCKED) return McpAuditInfo.REMARKS_REJECTED;
        return "";
    }

    private static List<Integer> serverCollectionIds(Bson rowFilter) {
        return McpAuditInfoDao.instance.getMCollection()
                .distinct(McpAuditInfo.HOST_COLLECTION_ID, Filters.and(rowFilter, Filters.exists(McpAuditInfo.HOST_COLLECTION_ID)), Integer.class)
                .into(new ArrayList<>());
    }

    // Same decision on the server's tool rows, so a tool still approved cannot undo a server block.
    private static void cascadeToTools(List<Integer> serverCollectionIds, List<Bson> decision) {
        for (int from = 0; from < serverCollectionIds.size(); from += CASCADE_BATCH_SIZE) {
            List<Integer> batch = serverCollectionIds.subList(from, Math.min(from + CASCADE_BATCH_SIZE, serverCollectionIds.size()));
            McpAuditInfoDao.instance.getMCollection().updateMany(
                    Filters.and(Filters.in(McpAuditInfo.HOST_COLLECTION_ID, batch), Filters.in(McpAuditInfo.TYPE, TOOL_AUDIT_TYPES),
                            globalRowsOnly(), contextSourceFilter(Context.contextSource.get())),
                    Updates.combine(decision));
        }
    }

    // Servers match by host ("<device>.<agent>.<name>" or the bare name), the rest by exact name; case-insensitive.
    static String rowNamePattern(String type, String groupKey) {
        String name = Pattern.quote(groupKey);
        return Constants.AKTO_MCP_SERVER_TAG.equals(type) ? "^(?:[^.]+\\.[^.]+\\.)?" + name + "$" : "^" + name + "$";
    }

    // Updates every global row of this tool, or inserts one; returns the collections of the server rows it updated.
    private List<Integer> updateRows(AtlasToolRow tool, String type, AtlasAssetStatus status, List<Bson> decision) {
        boolean isServer = Constants.AKTO_MCP_SERVER_TAG.equals(type);
        List<Bson> updates = new ArrayList<>(decision);
        if (isServer) updates.add(Updates.set(McpAuditInfo.BLOCK_ALL, status == AtlasAssetStatus.BLOCKED));
        // Agent rows keep the raw names guardrails matches on, as the Audit page's AI Agents tab does.
        if (Constants.AKTO_AI_AGENT_TAG.equals(type)) updates.add(Updates.addEachToSet(McpAuditInfo.RAW_VALUES, tool.getRawValues()));
        Bson scope = Filters.and(globalRowsOnly(), contextSourceFilter(Context.contextSource.get()));
        Bson rowFilter = Filters.and(Filters.eq(McpAuditInfo.TYPE, type),
                Filters.regex(McpAuditInfo.RESOURCE_NAME, rowNamePattern(type, tool.getGroupKey()), "i"), scope);
        List<Integer> serverCollectionIds = isServer ? serverCollectionIds(rowFilter) : Collections.emptyList();
        long matched = McpAuditInfoDao.instance.getMCollection().updateMany(rowFilter, Updates.combine(updates)).getMatchedCount();
        if (matched > 0) return serverCollectionIds;
        List<Bson> insert = new ArrayList<>(updates);
        insert.add(Updates.max(McpAuditInfo.LAST_DETECTED, tool.getLastSeen()));
        insert.add(Updates.setOnInsert(McpAuditInfo.CONTEXT_SOURCE, CONTEXT_SOURCE.ENDPOINT.name()));
        McpAuditInfoDao.instance.getMCollection().updateOne(
                Filters.and(Filters.eq(McpAuditInfo.TYPE, type), Filters.eq(McpAuditInfo.RESOURCE_NAME, tool.getGroupKey()), scope),
                Updates.combine(insert), new UpdateOptions().upsert(true));
        return Collections.emptyList();
    }
}
