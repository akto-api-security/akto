package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.McpAllowlistDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.McpAllowlist;
import com.akto.dto.McpAuditInfo;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.dto.User;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AuditDataAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AuditDataAction.class, LogDb.DASHBOARD);
    
    // Holds either List<McpAuditInfo> (non-merged path) or List<BasicDBObject> (merged path
    // — see runMergedAggregation / runChildrenForAgentServer). Both serialize to JSON objects,
    // so the frontend reads res.auditData uniformly and branches on the presence of merged-only
    // fields like agentName/serverName/groupedHexIds.
    @Getter
    private List<?> auditData;
    // Pagination and filtering parameters
    @Setter
    private String sortKey;
    @Setter
    private int sortOrder;
    @Setter
    private int limit;
    @Setter
    private int skip;
    @Setter
    private Map<String, List> filters;
    @Setter
    private Map<String, String> filterOperators;
    @Setter
    private String searchString;

    @Getter
    private long total;

    @Setter
    private int apiCollectionId = -1;

    // When true, fetchAuditData groups records by their (agent, server) canonical name —
    // i.e. resourceName with the leading "<device-id>." segment removed — so multiple
    // devices reporting the same MCP server collapse into one row. Used for parent rows.
    @Setter
    private boolean mergeMcpServers = false;

    // When both are set, fetchAuditData returns the unique tools/resources/prompts owned
    // by the (aiAgentName, mcpServerName) MCP server across every device that reported it.
    // The action resolves the parent collection ids server-side, so the caller does not
    // need to know about device-prefixed resourceNames or hostCollectionIds.
    @Setter
    private String aiAgentName;
    @Setter
    private String mcpServerName;

    @Getter
    @Setter
    private List<McpAuditInfo> mcpAuditInfoList;

    public String fetchMcpAuditInfoByCollection() {
        mcpAuditInfoList = new ArrayList<>();

        if (Context.contextSource.get() != null && Context.contextSource.get() != CONTEXT_SOURCE.ENDPOINT)  {
            mcpAuditInfoList = Collections.emptyList();
            return SUCCESS.toUpperCase();
        }

        try {
            if (apiCollectionId == -1) {
                addActionError("API Collection ID cannot be -1");
                return ERROR.toUpperCase();
            }
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(
                    Filters.eq(Constants.ID, apiCollectionId),
                    Projections.include(ApiCollection.HOST_NAME)
            );
            if (apiCollection == null) {
                addActionError("No such collection exists");
                return ERROR.toUpperCase();
            }
            String mcpName = McpRequestResponseUtils.extractServiceNameFromHost(apiCollection.getHostName());
            if (StringUtils.isBlank(mcpName)) {
                loggerMaker.errorAndAddToDb("MCP server name is null or empty for collection: " + apiCollection.getHostName() + " id: " + apiCollectionId, LogDb.DASHBOARD);
                return SUCCESS.toUpperCase();
            }
            Bson filter = Filters.eq(McpAuditInfo.MCP_HOST, mcpName);
            mcpAuditInfoList = McpAuditInfoDao.instance.findAll(filter, 0, 1_000, null);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching McpAuditInfo by collection: " + e.getMessage(), LogDb.DASHBOARD);
            mcpAuditInfoList = Collections.emptyList();
            return ERROR.toUpperCase();
        }
    }

    public String fetchAuditData() {
        try {
            if (sortKey == null || sortKey.isEmpty()) {
                sortKey = "lastDetected";
            }
            if (limit <= 0) {
                limit = 20;
            }
            if (skip < 0) {
                skip = 0;
            }

            List<Integer> collectionsIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(),
                Context.accountId.get());

            Bson filter = Filters.and(
                Filters.eq(McpAuditInfo.CONTEXT_SOURCE, Context.contextSource.get().name()),
                Filters.ne(McpAuditInfo.TYPE, "AGENT_SKILL")
            );
            // Legacy records (written before contextSource was tracked) have no contextSource field.
            // Surface them on the strict-path too: scope to the user's allowed collections when RBAC
            // is active, otherwise (admins) include all such records.
            List<Bson> legacyParts = new ArrayList<>();
            legacyParts.add(Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false));
            legacyParts.add(Filters.ne(McpAuditInfo.TYPE, "AGENT_SKILL"));
            if (collectionsIds != null) {
                legacyParts.add(Filters.in(McpAuditInfo.HOST_COLLECTION_ID, collectionsIds));
            }
            filter = Filters.or(filter, Filters.and(legacyParts));

            if (!StringUtils.isBlank(searchString)) {
                List<Bson> searchOrs = new ArrayList<>();
                searchOrs.add(Filters.regex(McpAuditInfo.REMARKS, searchString, "i"));
                searchOrs.add(Filters.regex(McpAuditInfo.RESOURCE_NAME, searchString, "i"));

                // Only the merged-server table needs to surface parents by their children's
                // names — Argus matches children directly via the resourceName regex, and the
                // children-by-name path is already scoped to a single (agent, server).
                if (mergeMcpServers) {
                    // hostCollectionId is the reliable shared key; mcpHost on children may
                    // differ from the parent's reconstructed resourceName for AI-agent traffic.
                    Bson childMatchFilter = Filters.and(
                        Filters.regex(McpAuditInfo.RESOURCE_NAME, searchString, "i"),
                        Filters.in(McpAuditInfo.TYPE, Arrays.asList("mcp-tool", "mcp-resource", "mcp-prompt"))
                    );
                    List<Integer> matchingCollectionIds = McpAuditInfoDao.instance.getMCollection()
                        .distinct(McpAuditInfo.HOST_COLLECTION_ID, childMatchFilter, Integer.class)
                        .into(new ArrayList<>());
                    if (!matchingCollectionIds.isEmpty()) {
                        searchOrs.add(Filters.in(McpAuditInfo.HOST_COLLECTION_ID, matchingCollectionIds));
                    }
                }

                filter = Filters.and(filter, Filters.or(searchOrs));
            }

            List<Bson> additionalFilters = prepareFilters(filters);
            if (!additionalFilters.isEmpty()) {
                filter = Filters.and(filter, Filters.and(additionalFilters));
            }

            Bson sort = sortOrder == 1 ? Sorts.ascending(sortKey) : Sorts.descending(sortKey);

            // Pull agent/server filters out of the generic filters map so they bypass
            // prepareFilters (they need to be applied to the derived _groupKey, not
            // any DB column).
            List<String> agentChoices = popListFilter(filters, "aiAgent");
            List<String> serverChoices = popListFilter(filters, "mcpServer");

            if (StringUtils.isNotBlank(aiAgentName) && StringUtils.isNotBlank(mcpServerName)) {
                runChildrenForAgentServer(filter, sort, skip, limit, aiAgentName.trim(), mcpServerName.trim());
            } else if (mergeMcpServers) {
                runMergedAggregation(filter, sort, skip, limit, agentChoices, serverChoices);
            } else {
                this.auditData = McpAuditInfoDao.instance.findAll(filter, skip, limit, sort);
                this.total = McpAuditInfoDao.instance.count(filter);
            }
            loggerMaker.info("Fetched " + auditData.size() + " audit records out of " + total + " total");

            try {
                List<McpAllowlist> allowlistEntries = McpAllowlistDao.instance.findAll(new BasicDBObject(), Projections.include(McpAllowlist.NAME));
                Set<String> allowlistNames = allowlistEntries.stream()
                        .map(McpAllowlist::getName)
                        .collect(Collectors.toSet());
                for (Object item : this.auditData) {
                    if (item instanceof BasicDBObject) {
                        BasicDBObject row = (BasicDBObject) item;
                        String serverName = row.getString("serverName");
                        if (serverName != null) {
                            row.put("verified", allowlistNames.contains(serverName));
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error enriching audit data with allowlist: " + e.getMessage(), LogDb.DASHBOARD);
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching audit data: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    private List<String> popListFilter(Map<String, List> filters, String key) {
        if (filters == null) return null;
        List raw = filters.remove(key);
        if (raw == null || raw.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (Object o : raw) {
            if (o != null) out.add(o.toString());
        }
        return out.isEmpty() ? null : out;
    }

    private void runMergedAggregation(Bson matchFilter, Bson sort, int skip, int limit,
                                      List<String> agentChoices, List<String> serverChoices) {
        List<Bson> commonStages = new ArrayList<>();

        // At least 2 dots required (3+ segments). No trailing anchor — resourceNames
        // with more than 3 segments are still valid; we split on the first 2 dots only.
        commonStages.add(Aggregates.match(Filters.and(
            matchFilter,
            Filters.regex(McpAuditInfo.RESOURCE_NAME, "^[^.]+\\.[^.]+\\.[^.]+"),
            Filters.eq(McpAuditInfo.TYPE, "mcp-server")
        )));

        // Locate the first and second dot so we can slice the string without $split.
        // This lets serverName absorb any additional dots (e.g. "cloudapp.azure.com").
        commonStages.add(Aggregates.addFields(
            new Field<>("_firstDot", new BasicDBObject("$indexOfBytes",
                Arrays.asList("$" + McpAuditInfo.RESOURCE_NAME, ".")))
        ));
        commonStages.add(Aggregates.addFields(
            new Field<>("_secondDot", new BasicDBObject("$indexOfBytes",
                Arrays.asList("$" + McpAuditInfo.RESOURCE_NAME, ".",
                    new BasicDBObject("$add", Arrays.asList("$_firstDot", 1)))))
        ));

        // device  = before 1st dot  (not extracted — unused after grouping)
        // agent   = between 1st and 2nd dot
        // server  = everything after 2nd dot  (may itself contain dots)
        // _groupKey = agent.server  = everything after the 1st dot
        commonStages.add(Aggregates.addFields(Arrays.asList(
            new Field<>("agentName", new BasicDBObject("$substrBytes", Arrays.asList(
                "$" + McpAuditInfo.RESOURCE_NAME,
                new BasicDBObject("$add", Arrays.asList("$_firstDot", 1)),
                new BasicDBObject("$subtract", Arrays.asList("$_secondDot",
                    new BasicDBObject("$add", Arrays.asList("$_firstDot", 1))))
            ))),
            new Field<>("serverName", new BasicDBObject("$substrBytes", Arrays.asList(
                "$" + McpAuditInfo.RESOURCE_NAME,
                new BasicDBObject("$add", Arrays.asList("$_secondDot", 1)),
                -1
            ))),
            new Field<>("_groupKey", new BasicDBObject("$substrBytes", Arrays.asList(
                "$" + McpAuditInfo.RESOURCE_NAME,
                new BasicDBObject("$add", Arrays.asList("$_firstDot", 1)),
                -1
            )))
        )));

        // Optional agent/server filtering against the derived agent/server fields.
        List<Bson> groupKeyMatchOrs = new ArrayList<>();
        if (agentChoices != null && !agentChoices.isEmpty()) {
            groupKeyMatchOrs.add(Filters.in("agentName", agentChoices));
        }
        if (serverChoices != null && !serverChoices.isEmpty()) {
            groupKeyMatchOrs.add(Filters.in("serverName", serverChoices));
        }
        if (!groupKeyMatchOrs.isEmpty()) {
            commonStages.add(Aggregates.match(Filters.and(groupKeyMatchOrs)));
        }

        // Sort by lastDetected DESC before grouping so $first picks the most recent
        // record's componentRiskAnalysis / apiAccessTypes for the group leader.
        commonStages.add(Aggregates.sort(Sorts.descending(McpAuditInfo.LAST_DETECTED)));

        // Group
        commonStages.add(Aggregates.group("$_groupKey",
            Accumulators.first("agentName", "$agentName"),
            Accumulators.first("serverName", "$serverName"),
            Accumulators.max(McpAuditInfo.LAST_DETECTED, "$" + McpAuditInfo.LAST_DETECTED),
            Accumulators.max(McpAuditInfo.APPROVED_AT, "$" + McpAuditInfo.APPROVED_AT),
            Accumulators.max(McpAuditInfo.UPDATED_TIMESTAMP, "$" + McpAuditInfo.UPDATED_TIMESTAMP),
            Accumulators.first(McpAuditInfo.COMPONENT_RISK_ANALYSIS, "$" + McpAuditInfo.COMPONENT_RISK_ANALYSIS),
            Accumulators.first(McpAuditInfo.API_ACCESS_TYPES, "$" + McpAuditInfo.API_ACCESS_TYPES),
            Accumulators.addToSet("remarksArr", new BasicDBObject()
                .append(McpAuditInfo.REMARKS, "$" + McpAuditInfo.REMARKS)
                .append(McpAuditInfo.MARKED_BY, "$" + McpAuditInfo.MARKED_BY)
                .append(McpAuditInfo.APPROVAL_CONDITIONS, "$" + McpAuditInfo.APPROVAL_CONDITIONS)
            ),
            Accumulators.addToSet("groupedHostCollectionIds", "$" + McpAuditInfo.HOST_COLLECTION_ID),
            Accumulators.addToSet("groupedHexIds", new BasicDBObject("$toString", "$_id")),
            Accumulators.sum("groupedCount", 1)
        ));

        // Total count: same pipeline up to group, then count groups
        List<Bson> countPipeline = new ArrayList<>(commonStages);
        countPipeline.add(Aggregates.count("count"));
        long groupTotal = 0;
        try (MongoCursor<BasicDBObject> cursor = McpAuditInfoDao.instance.getMCollection()
                .aggregate(countPipeline, BasicDBObject.class).cursor()) {
            if (cursor.hasNext()) {
                Number n = (Number) cursor.next().get("count");
                if (n != null) groupTotal = n.longValue();
            }
        }
        this.total = groupTotal;

        // Data pipeline: paginate + sort, return the BasicDBObject rows directly.
        List<Bson> dataPipeline = new ArrayList<>(commonStages);
        dataPipeline.add(Aggregates.sort(sort));
        dataPipeline.add(Aggregates.skip(skip));
        dataPipeline.add(Aggregates.limit(limit));

        List<BasicDBObject> rows = new ArrayList<>();
        try (MongoCursor<BasicDBObject> cursor = McpAuditInfoDao.instance.getMCollection()
                .aggregate(dataPipeline, BasicDBObject.class).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject row = cursor.next();
                collapseRemarksArr(row);
                rows.add(row);
            }
        }
        this.auditData = rows;
    }

    /**
     * Children-by-name path: given an (agent, server) pair, resolve every parent
     * mcp-server record's hostCollectionId, then return the unique tools/resources/prompts
     * owned by those collections — one row per resourceName, with the most-recent
     * componentRiskAnalysis/apiAccessTypes preserved as the "leader" record.
     */
    private void runChildrenForAgentServer(Bson matchFilter, Bson sort, int skip, int limit,
                                           String agentName, String serverName) {
        // Resolve parent collection ids for this (agent, server). Parent resourceNames are
        // "<device>.<agent>.<server>"; the regex below anchors agent + server.
        String agentEsc = java.util.regex.Pattern.quote(agentName);
        String serverEsc = java.util.regex.Pattern.quote(serverName);
        Bson parentMatch = Filters.and(
            Filters.eq(McpAuditInfo.TYPE, "mcp-server"),
            Filters.regex(McpAuditInfo.RESOURCE_NAME, "^[^.]+\\." + agentEsc + "\\." + serverEsc + "$")
        );
        List<Integer> parentCollectionIds = McpAuditInfoDao.instance.getMCollection()
            .distinct(McpAuditInfo.HOST_COLLECTION_ID, parentMatch, Integer.class)
            .into(new ArrayList<>());

        if (parentCollectionIds.isEmpty()) {
            this.auditData = new ArrayList<BasicDBObject>();
            this.total = 0;
            return;
        }

        List<Bson> commonStages = new ArrayList<>();
        commonStages.add(Aggregates.match(Filters.and(
            matchFilter,
            Filters.in(McpAuditInfo.HOST_COLLECTION_ID, parentCollectionIds),
            Filters.in(McpAuditInfo.TYPE, Arrays.asList("mcp-tool", "mcp-resource", "mcp-prompt"))
        )));

        // Sort by lastDetected DESC so $first picks the most-recent record's per-doc fields.
        commonStages.add(Aggregates.sort(Sorts.descending(McpAuditInfo.LAST_DETECTED)));

        commonStages.add(Aggregates.group("$" + McpAuditInfo.RESOURCE_NAME,
            Accumulators.first(McpAuditInfo.RESOURCE_NAME, "$" + McpAuditInfo.RESOURCE_NAME),
            Accumulators.first(McpAuditInfo.TYPE, "$" + McpAuditInfo.TYPE),
            Accumulators.max(McpAuditInfo.LAST_DETECTED, "$" + McpAuditInfo.LAST_DETECTED),
            Accumulators.max(McpAuditInfo.APPROVED_AT, "$" + McpAuditInfo.APPROVED_AT),
            Accumulators.max(McpAuditInfo.UPDATED_TIMESTAMP, "$" + McpAuditInfo.UPDATED_TIMESTAMP),
            Accumulators.first(McpAuditInfo.COMPONENT_RISK_ANALYSIS, "$" + McpAuditInfo.COMPONENT_RISK_ANALYSIS),
            Accumulators.first(McpAuditInfo.API_ACCESS_TYPES, "$" + McpAuditInfo.API_ACCESS_TYPES),
            Accumulators.addToSet("remarksArr", new BasicDBObject()
                .append(McpAuditInfo.REMARKS, "$" + McpAuditInfo.REMARKS)
                .append(McpAuditInfo.MARKED_BY, "$" + McpAuditInfo.MARKED_BY)
                .append(McpAuditInfo.APPROVAL_CONDITIONS, "$" + McpAuditInfo.APPROVAL_CONDITIONS)
            ),
            Accumulators.addToSet("groupedHostCollectionIds", "$" + McpAuditInfo.HOST_COLLECTION_ID),
            Accumulators.addToSet("groupedHexIds", new BasicDBObject("$toString", "$_id")),
            Accumulators.sum("groupedCount", 1)
        ));

        // Total count
        List<Bson> countPipeline = new ArrayList<>(commonStages);
        countPipeline.add(Aggregates.count("count"));
        long groupTotal = 0;
        try (MongoCursor<BasicDBObject> cursor = McpAuditInfoDao.instance.getMCollection()
                .aggregate(countPipeline, BasicDBObject.class).cursor()) {
            if (cursor.hasNext()) {
                Number n = (Number) cursor.next().get("count");
                if (n != null) groupTotal = n.longValue();
            }
        }
        this.total = groupTotal;

        List<Bson> dataPipeline = new ArrayList<>(commonStages);
        dataPipeline.add(Aggregates.sort(sort));
        dataPipeline.add(Aggregates.skip(skip));
        dataPipeline.add(Aggregates.limit(limit));

        List<BasicDBObject> rows = new ArrayList<>();
        try (MongoCursor<BasicDBObject> cursor = McpAuditInfoDao.instance.getMCollection()
                .aggregate(dataPipeline, BasicDBObject.class).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject row = cursor.next();
                collapseRemarksArr(row);
                rows.add(row);
            }
        }
        this.auditData = rows;
    }

    private static boolean isConditionalApprovalExpired(Object cond, long nowEpochSeconds) {
        if (!(cond instanceof Map)) return false;
        Object expiresAt = ((Map<?, ?>) cond).get("expiresAt");
        if (!(expiresAt instanceof Number)) return false;
        long exp = ((Number) expiresAt).longValue();
        return exp > 0 && nowEpochSeconds > exp;
    }

    // Priority: Rejected=0, ActiveConditional=1, Approved=2, ExpiredConditional=3, Pending=4
    // Expired conditional loses to both Rejected and Approved - reverts to null (Pending in UI).
    // Only "Conditionally Approved" entries are checked for expiry; "Approved" with stale
    // approvalConditions in DB is still treated as Approved.
    private static int remarksPriority(String r, Object cond, long nowEpochSeconds) {
        if ("Rejected".equals(r)) return 0;
        if ("Conditionally Approved".equals(r)) {
            return isConditionalApprovalExpired(cond, nowEpochSeconds) ? 3 : 1;
        }
        if ("Approved".equals(r)) return 2;
        return 4;
    }

    @SuppressWarnings("unchecked")
    private static void collapseRemarksArr(BasicDBObject row) {
        if (row == null) return;
        Object raw = row.get("remarksArr");
        if (!(raw instanceof List)) {
            row.removeField("remarksArr");
            return;
        }
        long nowEpochSeconds = System.currentTimeMillis() / 1000L;
        int chosenPriority = Integer.MAX_VALUE;
        String displayRemarks = null;
        Object displayMarkedBy = null;
        Object activeConditionalCond = null; // approvalConditions from the active conditional entry, if any
        Object rejectedCond = null;          // non-expired approvalConditions from any Rejected entry

        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> entry = (Map<String, Object>) o;
            String r = entry.get(McpAuditInfo.REMARKS) instanceof String
                    ? (String) entry.get(McpAuditInfo.REMARKS) : null;
            Object cond = entry.get(McpAuditInfo.APPROVAL_CONDITIONS);
            int p = remarksPriority(r, cond, nowEpochSeconds);
            if (p == 1) activeConditionalCond = cond;
            if (p == 0 && cond != null && !isConditionalApprovalExpired(cond, nowEpochSeconds)) {
                rejectedCond = cond; // pick up non-expired conditions from any Rejected entry
            }
            if (p < chosenPriority) {
                chosenPriority = p;
                displayRemarks = r;
                displayMarkedBy = entry.get(McpAuditInfo.MARKED_BY);
            }
        }

        if (chosenPriority < Integer.MAX_VALUE) {
            if (chosenPriority == 3) displayRemarks = "Rejected";
            row.put(McpAuditInfo.REMARKS, displayRemarks);
            row.put(McpAuditInfo.MARKED_BY, displayMarkedBy);
            // Expose approvalConditions for Rejected and active-conditional statuses only.
            // For Rejected: prefer active conditional's conditions (Scenario 5),
            // fall back to non-expired conditions from any Rejected entry in the group.
            // For active conditional: show its conditions directly.
            // Strip for Approved and all other statuses.
            Object condToExpose = null;
            if (chosenPriority == 0) {
                condToExpose = activeConditionalCond != null ? activeConditionalCond : rejectedCond;
            } else if (chosenPriority == 1) {
                condToExpose = activeConditionalCond;
            }
            if (condToExpose != null) {
                row.put(McpAuditInfo.APPROVAL_CONDITIONS, condToExpose);
            } else {
                row.removeField(McpAuditInfo.APPROVAL_CONDITIONS);
            }
        }
        row.removeField("remarksArr");
    }

    /**
     * Common function for handling filters
     */
    private List<Bson> prepareFilters(Map<String, List> filters) {
        List<Bson> filterList = new ArrayList<>();
        
        if (filters == null || filters.isEmpty()) {
            return filterList;
        }
        
        // Apply filters
        for(Map.Entry<String, List> entry: filters.entrySet()) {
            String key = entry.getKey();
            List value = entry.getValue();
            if (value == null || value.size() == 0) continue;

            switch (key) {
                case "markedBy":
                case "type":
                case "resourceName":
                case "hostCollectionId":
                case "mcpHost":
                    filterList.add(Filters.in(key, value));
                    break;
                case "lastDetected":
                    if (value.size() >= 2) {
                        filterList.add(Filters.gte(key, value.get(0)));
                        filterList.add(Filters.lte(key, value.get(1)));
                    }
                    break;
                case "apiAccessTypes":
                    // Handle API access types filtering
                    List<Bson> accessTypeFilters = new ArrayList<>();
                    for (Object accessType : value) {
                        accessTypeFilters.add(Filters.elemMatch("apiAccessTypes", Filters.eq("$eq", accessType)));
                    }
                    if (!accessTypeFilters.isEmpty()) {
                        filterList.add(Filters.or(accessTypeFilters));
                    }
                    break;
            }
        }

        return filterList;
    }

    @Setter
    String hexId;
    @Setter
    List<String> hexIds;
    @Setter
    String remarks;
    @Setter
    Map<String, Object> approvalData;
    // When non-empty, also apply the same update to all mcp-tool / mcp-resource /
    // mcp-prompt records belonging to these hostCollectionIds. Used by the UI when
    // a server-level decision should cascade to every component it owns. Tools
    // can still be overridden afterwards via a per-tool action on the child row.
    @Setter
    List<Integer> cascadeHostCollectionIds;
    // When set, the update applies to every mcp-server record whose resourceName
    // resolves to this server name across any AI agent (and to those servers'
    // children when cascading). E.g. value "akto-docs" matches "claudecli.akto-docs",
    // "cursor.akto-docs", and "<device>.cursor.akto-docs" alike.
    @Setter
    String mcpServerForAllAgents;

    public String updateAuditData() {
        User user = getSUser();
        String markedBy = user.getLogin();

        try {
            // When the UI is in merged mode, the parent row represents N device-specific
            // records. The shield enforces "rejected wins" across all of them, so the
            // mutation must fan out to every member or the policies will diverge.
            List<ObjectId> targetIds = new ArrayList<>();
            if (hexIds != null && !hexIds.isEmpty()) {
                for (String h : hexIds) {
                    if (h != null && !h.isEmpty()) targetIds.add(new ObjectId(h));
                }
            } else if (hexId != null && !hexId.isEmpty()) {
                targetIds.add(new ObjectId(hexId));
            }

            // "For all agents" mode: discover every mcp-server record whose
            // resourceName matches the requested server (bare, agent-prefixed, or
            // device+agent-prefixed) and pull their hexIds/hostCollectionIds into
            // the update scope.
            List<Integer> allAgentsCollectionIds = new ArrayList<>();
            if (mcpServerForAllAgents != null && !mcpServerForAllAgents.trim().isEmpty()) {
                String escaped = java.util.regex.Pattern.quote(mcpServerForAllAgents.trim());
                Bson serverMatch = Filters.and(
                    Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_MCP_SERVER_TAG),
                    Filters.regex(McpAuditInfo.RESOURCE_NAME, "(^|\\.)" + escaped + "$")
                );
                try (MongoCursor<McpAuditInfo> cursor = McpAuditInfoDao.instance.getMCollection()
                        .find(serverMatch).cursor()) {
                    while (cursor.hasNext()) {
                        McpAuditInfo rec = cursor.next();
                        if (rec.getId() != null && !targetIds.contains(rec.getId())) {
                            targetIds.add(rec.getId());
                        }
                        allAgentsCollectionIds.add(rec.getHostCollectionId());
                    }
                }
            }

            if (targetIds.isEmpty()) {
                addActionError("No record id provided");
                return ERROR.toUpperCase();
            }

            int currentTime = Context.now();
            List<Bson> updates = new ArrayList<>();

            if (approvalData != null) {
                updates.add(Updates.set("remarks", approvalData.get("remarks")));
                updates.add(Updates.set("markedBy", markedBy));
                updates.add(Updates.set("updatedTimestamp", currentTime));
                updates.add(Updates.set("approvedAt", currentTime));

                Map<String, Object> conditions = (Map<String, Object>) approvalData.get("conditions");
                if (conditions == null) {
                    conditions = new HashMap<>();
                }
                conditions.put("justification", approvalData.get("justification"));
                updates.add(Updates.set("approvalConditions", conditions));
            } else {
                updates.add(Updates.set("remarks", remarks));
                updates.add(Updates.set("markedBy", markedBy));
                updates.add(Updates.set("updatedTimestamp", currentTime));
                if ("Approved".equals(remarks)) {
                    updates.add(Updates.set("approvedAt", currentTime));
                }
                updates.add(Updates.unset(McpAuditInfo.APPROVAL_CONDITIONS));
            }

            Bson combined = Updates.combine(updates);
            if (targetIds.size() == 1) {
                McpAuditInfoDao.instance.updateOne(Filters.eq(Constants.ID, targetIds.get(0)), combined);
            } else {
                McpAuditInfoDao.instance.updateMany(Filters.in(Constants.ID, targetIds), combined);
            }

            // When blocking for all agents, stamp blockAll=true on every matched server record
            // so newly arriving records for this server name are auto-blocked at ingest time.
            if (mcpServerForAllAgents != null && !mcpServerForAllAgents.trim().isEmpty()
                    && "Rejected".equals(remarks)) {
                McpAuditInfoDao.instance.updateMany(
                    Filters.in(Constants.ID, targetIds),
                    Updates.set(McpAuditInfo.BLOCK_ALL, true)
                );
            }

            // When approving any server, clear blockAll=false on every mcp-server record
            // sharing that server name — derived server-side from the approved record's
            // resourceName so the UI doesn't need to send anything extra.
            if ("Approved".equals(remarks) && !targetIds.isEmpty()) {
                String approvedServerName = null;
                McpAuditInfo approvedRec = McpAuditInfoDao.instance.getMCollection()
                        .find(Filters.eq(Constants.ID, targetIds.get(0))).first();
                if (approvedRec != null && approvedRec.getResourceName() != null) {
                    String rn = approvedRec.getResourceName();
                    int secondDot = rn.indexOf('.', rn.indexOf('.') + 1);
                    if (secondDot > 0 && secondDot < rn.length() - 1) {
                        approvedServerName = rn.substring(secondDot + 1);
                    } else {
                        approvedServerName = rn;
                    }
                }
                if (approvedServerName != null && !approvedServerName.isEmpty()) {
                    String escaped = java.util.regex.Pattern.quote(approvedServerName);
                    Bson blockAllMatch = Filters.and(
                        Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_MCP_SERVER_TAG),
                        Filters.regex(McpAuditInfo.RESOURCE_NAME, "(^|\\.)" + escaped + "$")
                    );
                    McpAuditInfoDao.instance.updateMany(
                        blockAllMatch,
                        Updates.set(McpAuditInfo.BLOCK_ALL, false)
                    );
                }
            }

            List<Integer> mergedCascade = new ArrayList<>();
            if (cascadeHostCollectionIds != null) mergedCascade.addAll(cascadeHostCollectionIds);
            for (Integer id : allAgentsCollectionIds) {
                if (id != null && !mergedCascade.contains(id)) mergedCascade.add(id);
            }
            if (!mergedCascade.isEmpty()) {
                Bson childFilter = Filters.and(
                    Filters.in(McpAuditInfo.HOST_COLLECTION_ID, mergedCascade),
                    Filters.in(McpAuditInfo.TYPE, Arrays.asList("mcp-tool", "mcp-resource", "mcp-prompt"))
                );
                McpAuditInfoDao.instance.updateMany(childFilter, combined);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating audit data: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    @Override
    public String execute() throws Exception {
        return "";
    }
}
