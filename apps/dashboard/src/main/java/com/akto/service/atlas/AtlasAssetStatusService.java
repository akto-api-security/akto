package com.akto.service.atlas;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.McpAuditInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.utils.TtlCache;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Resolves and writes the review status of a discovered AI tool, from McpAuditInfo rows and the allowlist. */
public class AtlasAssetStatusService {

    public static final String ROW_TYPE_AGENT = "agent";
    public static final String ROW_TYPE_SERVICE = "service";
    public static final String ROW_TYPE_LLM = "llm";
    public static final String ROW_TYPE_SKILL = "skill";
    public static final String ROW_TYPE_PLUGIN = "plugin";

    private static final int PAGE_SIZE = 1000;
    private static final long CACHE_TTL_MS = 60_000;
    private static final int CACHE_MAX_ENTRIES = 1000;
    private static final String CONDITIONALLY_APPROVED = "Conditionally Approved";
    private static final String KEY_SEPARATOR = "|";
    private static final String SKILL_URL_PREFIX = "/skills/";
    private static final List<String> AUDIT_TYPES = Arrays.asList(
            Constants.AKTO_MCP_SERVER_TAG, Constants.AKTO_AI_AGENT_TAG, Constants.AKTO_BROWSER_LLM_TAG);
    // Strongest decision wins when several rows describe one tool; a server's tool rows follow it.
    private static final List<String> TOOL_AUDIT_TYPES = Arrays.asList(
            Constants.AKTO_MCP_TOOLS_TAG, Constants.AKTO_MCP_RESOURCES_TAG, Constants.AKTO_MCP_PROMPTS_TAG);
    private static final int CASCADE_BATCH_SIZE = 1000;
    private static final int MAX_SKILL_DECISION_NAMES = 1000;
    private static final LoggerMaker logger = new LoggerMaker(AtlasAssetStatusService.class, LogDb.DASHBOARD);

    private static final TtlCache<String, Map<String, AtlasAssetStatus>> CACHE = new TtlCache<>(CACHE_TTL_MS, CACHE_MAX_ENTRIES);
    private static final AtomicLong GENERATION = new AtomicLong();

    // Plugins have no decision rows; skills do, but only the malicious ones are put up for a decision.
    public static String auditTypeOf(String rowType) {
        if (ROW_TYPE_AGENT.equals(rowType)) return Constants.AKTO_AI_AGENT_TAG;
        if (ROW_TYPE_LLM.equals(rowType)) return Constants.AKTO_BROWSER_LLM_TAG;
        if (ROW_TYPE_SERVICE.equals(rowType)) return Constants.AKTO_MCP_SERVER_TAG;
        if (ROW_TYPE_SKILL.equals(rowType)) return McpAuditInfo.TYPE_AGENT_SKILL;
        return null;
    }

    public static String toolKey(String rowType, String groupKey) {
        return rowType + KEY_SEPARATOR + groupKey;
    }

    // Agents, MCP servers and browser apps are all reviewed; skills and plugins are not part of the inventory status.
    public static boolean isGovernable(String rowType) {
        return ROW_TYPE_AGENT.equals(rowType) || ROW_TYPE_LLM.equals(rowType) || ROW_TYPE_SERVICE.equals(rowType);
    }

    /** Whether the tool can be approved or blocked: every governable tool, and a skill only once it is flagged malicious. */
    public static boolean canDecide(AtlasToolRow tool) {
        return isGovernable(tool.getRowType()) || (ROW_TYPE_SKILL.equals(tool.getRowType()) && tool.isMalicious());
    }

    public AtlasAssetStatus resolve(AtlasToolRow tool, Map<String, AtlasAssetStatus> decisions, Set<String> allowlistNamesLower) {
        String type = auditTypeOf(tool.getRowType());
        AtlasAssetStatus decided = type == null ? null : decisions.get(decisionKey(type, tool.getGroupKey()));
        // Any decision someone made wins over the allowlist; only a tool nobody touched falls back to it.
        if (decided != null && decided != AtlasAssetStatus.UNAPPROVED) return decided;
        // A flagged tool is never approved by its vendor being allowlisted.
        return !tool.isMalicious() && isAllowlisted(tool, allowlistNamesLower) ? AtlasAssetStatus.APPROVED : AtlasAssetStatus.UNAPPROVED;
    }

    public Map<String, AtlasAssetStatus> loadDecisions() {
        String cacheKey = Context.accountId.get() + "_" + Context.contextSource.get();
        Map<String, AtlasAssetStatus> cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        // A write that lands while reading bumps the generation, so a snapshot read before it is not cached.
        long generation = GENERATION.get();
        Map<String, AtlasAssetStatus> decisions = readDecisions();
        CACHE.put(cacheKey, decisions);
        // Checked after the put, so a write finishing in between cannot leave this snapshot cached.
        if (GENERATION.get() != generation) CACHE.removeKeysIf(cacheKey::equals);
        return decisions;
    }

    public static void invalidate() {
        GENERATION.incrementAndGet();
        String prefix = Context.accountId.get() + "_";
        CACHE.removeKeysIf(key -> key.startsWith(prefix));
    }

    /** Applies a decision to every selected tool; returns how many tools were updated. */
    public int updateStatus(Collection<AtlasToolRow> tools, AtlasAssetStatus status, String markedBy) {
        int now = Context.now();
        int updated = 0;
        List<Bson> decision = decisionUpdates(status, markedBy, now);
        Set<Integer> serverCollectionIds = new HashSet<>();
        Set<String> blocked = status == AtlasAssetStatus.UNDER_REVIEW ? blockedKeys(tools) : Collections.emptySet();
        try {
            List<AtlasToolRow> servers = new ArrayList<>();
            for (AtlasToolRow tool : tools) {
                // Marking under review never unblocks, same as the Audit page.
                if (!canDecide(tool) || blocked.contains(toolKey(tool.getRowType(), tool.getGroupKey()))) continue;
                String type = auditTypeOf(tool.getRowType());
                if (Constants.AKTO_MCP_SERVER_TAG.equals(type)) servers.add(tool);
                else updateRows(tool, type, status, decision);
                // Under review leaves a skill's block flag alone, as it leaves a block.
                if (ROW_TYPE_SKILL.equals(tool.getRowType()) && status != AtlasAssetStatus.UNDER_REVIEW) setSkillBlocked(tool, status == AtlasAssetStatus.BLOCKED);
                updated++;
            }
            if (!servers.isEmpty()) serverCollectionIds.addAll(updateServerRows(servers, status, decision));
            // One pass over the tool rows for all servers, not one per server. Under review leaves tools their own decisions, as on the Audit page.
            if (status != AtlasAssetStatus.UNDER_REVIEW) cascadeToTools(new ArrayList<>(serverCollectionIds), decision);
        } finally {
            // Also after a partial write, so the next read does not serve the old decisions.
            invalidate();
        }
        return updated;
    }

    // Read fresh, not from the cache, so a block made in the last minute is never marked under review.
    private Set<String> blockedKeys(Collection<AtlasToolRow> tools) {
        Map<String, AtlasAssetStatus> decisions = readDecisions();
        decisions.putAll(loadSkillDecisions(tools));
        Set<String> blocked = new HashSet<>();
        for (AtlasToolRow tool : tools) {
            String type = auditTypeOf(tool.getRowType());
            if (type != null && decisions.get(decisionKey(type, tool.getGroupKey())) == AtlasAssetStatus.BLOCKED) blocked.add(toolKey(tool.getRowType(), tool.getGroupKey()));
        }
        return blocked;
    }

    // A failed read throws, so it is never cached as "every tool is unapproved".
    private Map<String, AtlasAssetStatus> readDecisions() {
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        readInto(decisions, Filters.and(Filters.in(McpAuditInfo.TYPE, AUDIT_TYPES), contextSourceFilter(Context.contextSource.get())));
        return decisions;
    }

    /** Decisions on the malicious skills among the tools, read by name so the many other skill rows are never scanned. */
    public Map<String, AtlasAssetStatus> loadSkillDecisions(Collection<AtlasToolRow> tools) {
        List<String> names = new ArrayList<>();
        int skills = 0;
        for (AtlasToolRow tool : tools) {
            if (!ROW_TYPE_SKILL.equals(tool.getRowType()) || !tool.isMalicious()) continue;
            if (++skills > MAX_SKILL_DECISION_NAMES) continue;
            names.add(tool.getGroupKey());
        }
        if (skills > MAX_SKILL_DECISION_NAMES) logger.warnAndAddToDb("Atlas discovery: " + skills + " malicious skills, decisions read for the first " + MAX_SKILL_DECISION_NAMES);
        return loadSkillDecisionsByName(names);
    }

    // Pages the matching rows by _id and keeps the strongest decision per tool, ranked like the Audit page.
    private void readInto(Map<String, AtlasAssetStatus> decisions, Bson filter) {
        Map<String, Integer> best = new HashMap<>();
        Bson projection = Projections.include(McpAuditInfo.TYPE, McpAuditInfo.RESOURCE_NAME, McpAuditInfo.REMARKS, McpAuditInfo.APPROVAL_CONDITIONS);
        ObjectId lastId = null;
        while (true) {
            Bson page = lastId == null ? filter : Filters.and(filter, Filters.gt(Constants.ID, lastId));
            List<McpAuditInfo> rows = McpAuditInfoDao.instance.findAll(page, 0, PAGE_SIZE, Sorts.ascending(Constants.ID), projection);
            for (McpAuditInfo row : rows) {
                if (row.getResourceName() == null) continue;
                int priority = priorityOf(row);
                best.merge(decisionKey(row.getType(), serverOrResourceName(row)), priority, Math::min);
                // A row Atlas created for a server has the bare name, which can itself have dots (e.g. "mcp.example.com").
                best.merge(decisionKey(row.getType(), row.getResourceName()), priority, Math::min);
            }
            if (rows.size() < PAGE_SIZE) break;
            lastId = rows.get(rows.size() - 1).getId();
        }
        best.forEach((key, priority) -> decisions.put(key, statusOfPriority(priority)));
    }

    // Same api_info flag the Agentic assets page sets when it blocks a skill, so the decision reaches the skill's collections.
    private static void setSkillBlocked(AtlasToolRow skill, boolean blocked) {
        if (skill.getCollectionIds().isEmpty()) return;
        ApiInfoDao.instance.updateMany(
                Filters.and(Filters.in(ApiInfo.ID_API_COLLECTION_ID, skill.getCollectionIds()), Filters.eq(ApiInfo.ID_URL, SKILL_URL_PREFIX + skill.getGroupKey())),
                Updates.set(ApiInfo.IS_SKILL_BLOCKED, blocked));
    }

    // MCP servers are stored per host ("<device>.<agent>.<name>"); agents and browser apps by their own name.
    static String serverOrResourceName(McpAuditInfo row) {
        if (!Constants.AKTO_MCP_SERVER_TAG.equals(row.getType())) return row.getResourceName();
        return McpRequestResponseUtils.extractServiceNameFromHost(row.getResourceName());
    }

    static String decisionKey(String type, String name) {
        return type + KEY_SEPARATOR + StringUtils.defaultString(name).toLowerCase(Locale.ROOT);
    }

    // Same order as AuditDataAction.remarksPriority: rejected, active conditional, approved, expired conditional, under review, pending.
    static int priorityOf(McpAuditInfo row) {
        String remarks = row.getRemarks();
        if (McpAuditInfo.REMARKS_REJECTED.equals(remarks)) return 0;
        if (CONDITIONALLY_APPROVED.equals(remarks)) return isExpired(row.getApprovalConditions()) ? 3 : 1;
        if (McpAuditInfo.REMARKS_APPROVED.equals(remarks)) return 2;
        return McpAuditInfo.REMARKS_UNDER_REVIEW.equals(remarks) ? 4 : 5;
    }

    // Like the Audit page and the AI inventory, an expired conditional approval shows as blocked.
    static AtlasAssetStatus statusOfPriority(int priority) {
        if (priority == 0 || priority == 3) return AtlasAssetStatus.BLOCKED;
        if (priority == 1 || priority == 2) return AtlasAssetStatus.APPROVED;
        return priority == 4 ? AtlasAssetStatus.UNDER_REVIEW : AtlasAssetStatus.UNAPPROVED;
    }

    static AtlasAssetStatus statusOf(McpAuditInfo row) {
        return statusOfPriority(priorityOf(row));
    }

    private static boolean isExpired(Map<String, Object> conditions) {
        Object expiresAt = conditions == null ? null : conditions.get("expiresAt");
        return expiresAt instanceof Number && ((Number) expiresAt).longValue() > 0 && Context.now() > ((Number) expiresAt).longValue();
    }

    private static boolean isAllowlisted(AtlasToolRow tool, Set<String> allowlistNamesLower) {
        for (String name : Arrays.asList(tool.getVendorName(), tool.getGroupKey(), tool.getName())) {
            if (name != null && allowlistNamesLower.contains(name.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static Bson contextSourceFilter(CONTEXT_SOURCE contextSource) {
        // Same as the AI inventory: without a context source every row counts.
        if (contextSource == null) return new Document();
        return Filters.or(
                Filters.eq(McpAuditInfo.CONTEXT_SOURCE, contextSource.name()),
                Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false));
    }

    private static List<Bson> decisionUpdates(AtlasAssetStatus status, String markedBy, int now) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(McpAuditInfo.REMARKS, remarksOf(status)));
        updates.add(Updates.set(McpAuditInfo.MARKED_BY, markedBy));
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
        return status == AtlasAssetStatus.UNDER_REVIEW ? McpAuditInfo.REMARKS_UNDER_REVIEW : "";
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

    private static Bson globalScope() {
        return Filters.and(globalRowsOnly(), contextSourceFilter(Context.contextSource.get()));
    }

    // Updates every global row of an agent or browser app by name, or inserts one.
    private void updateRows(AtlasToolRow tool, String type, AtlasAssetStatus status, List<Bson> decision) {
        List<Bson> updates = new ArrayList<>(decision);
        // Agent rows keep the raw names guardrails matches on, as the Audit page's AI Agents tab does.
        if (Constants.AKTO_AI_AGENT_TAG.equals(type)) updates.add(Updates.addEachToSet(McpAuditInfo.RAW_VALUES, tool.getRawValues()));
        Bson rowFilter = Filters.and(Filters.eq(McpAuditInfo.TYPE, type),
                Filters.regex(McpAuditInfo.RESOURCE_NAME, rowNamePattern(type, tool.getGroupKey()), "i"), globalScope());
        if (McpAuditInfoDao.instance.getMCollection().updateMany(rowFilter, Updates.combine(updates)).getMatchedCount() > 0) return;
        insertRow(tool, type, status, updates);
    }

    // One scan finds the global rows of every selected server; the update then goes by _id, so many servers cost one scan.
    private List<Integer> updateServerRows(List<AtlasToolRow> servers, AtlasAssetStatus status, List<Bson> decision) {
        List<Bson> updates = new ArrayList<>(decision);
        if (status != AtlasAssetStatus.UNDER_REVIEW) updates.add(Updates.set(McpAuditInfo.BLOCK_ALL, status == AtlasAssetStatus.BLOCKED));
        String names = servers.stream().map(tool -> Pattern.quote(tool.getGroupKey())).collect(Collectors.joining("|"));
        Bson rowFilter = Filters.and(Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_MCP_SERVER_TAG),
                Filters.regex(McpAuditInfo.RESOURCE_NAME, "^(?:[^.]+\\.[^.]+\\.)?(?:" + names + ")$", "i"), globalScope());
        Bson projection = Projections.include(McpAuditInfo.RESOURCE_NAME, McpAuditInfo.HOST_COLLECTION_ID);
        Set<Integer> collectionIds = new HashSet<>();
        Set<String> matchedNames = new HashSet<>();
        ObjectId lastId = null;
        while (true) {
            Bson page = lastId == null ? rowFilter : Filters.and(rowFilter, Filters.gt(Constants.ID, lastId));
            List<McpAuditInfo> rows = McpAuditInfoDao.instance.findAll(page, 0, PAGE_SIZE, Sorts.ascending(Constants.ID), projection);
            if (rows.isEmpty()) break;
            List<ObjectId> ids = new ArrayList<>();
            for (McpAuditInfo row : rows) {
                ids.add(row.getId());
                if (row.getHostCollectionId() != 0) collectionIds.add(row.getHostCollectionId());
                matchedNames.addAll(serverNamesOf(row.getResourceName()));
            }
            McpAuditInfoDao.instance.getMCollection().updateMany(Filters.in(Constants.ID, ids), Updates.combine(updates));
            if (rows.size() < PAGE_SIZE) break;
            lastId = rows.get(rows.size() - 1).getId();
        }
        for (AtlasToolRow tool : servers) {
            if (matchedNames.contains(tool.getGroupKey().toLowerCase(Locale.ROOT))) continue;
            insertRow(tool, Constants.AKTO_MCP_SERVER_TAG, status, updates);
            // A server with no row yet still has tool rows, which follow its decision.
            collectionIds.addAll(tool.getCollectionIds());
        }
        return new ArrayList<>(collectionIds);
    }

    // The names a server row answers to: its whole name, and the name after "<device>.<agent>." when it has that prefix.
    static List<String> serverNamesOf(String resourceName) {
        String name = StringUtils.defaultString(resourceName).toLowerCase(Locale.ROOT);
        String[] parts = name.split("\\.", 3);
        return parts.length == 3 ? Arrays.asList(name, parts[2]) : Collections.singletonList(name);
    }

    // A tool with no decision row yet gets one, except a reset to unapproved: no row already reads as unapproved.
    private void insertRow(AtlasToolRow tool, String type, AtlasAssetStatus status, List<Bson> updates) {
        if (status == AtlasAssetStatus.UNAPPROVED) return;
        List<Bson> insert = new ArrayList<>(updates);
        insert.add(Updates.max(McpAuditInfo.LAST_DETECTED, tool.getLastSeen()));
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        insert.add(Updates.setOnInsert(McpAuditInfo.CONTEXT_SOURCE, (contextSource == null ? CONTEXT_SOURCE.ENDPOINT : contextSource).name()));
        McpAuditInfoDao.instance.getMCollection().updateOne(
                Filters.and(Filters.eq(McpAuditInfo.TYPE, type), Filters.eq(McpAuditInfo.RESOURCE_NAME, tool.getGroupKey()), globalScope()),
                Updates.combine(insert), new UpdateOptions().upsert(true));
    }

    public Map<String, AtlasAssetStatus> loadSkillDecisionsByName(Collection<String> skillNames) {
        Set<String> names = new HashSet<>();
        for (String name : skillNames) {
            if (StringUtils.isBlank(name)) continue;
            // The audit row may differ in case from the skill group's name; names are matched exactly, so look up both spellings.
            names.add(name);
            names.add(name.toLowerCase(Locale.ROOT));
        }
        Map<String, AtlasAssetStatus> decisions = new HashMap<>();
        if (names.isEmpty()) return decisions;
        readInto(decisions, Filters.and(Filters.eq(McpAuditInfo.TYPE, McpAuditInfo.TYPE_AGENT_SKILL),
                Filters.in(McpAuditInfo.RESOURCE_NAME, names), contextSourceFilter(Context.contextSource.get())));
        return decisions;
    }
}
