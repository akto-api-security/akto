package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.McpAuditInfo;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.dto.User;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuditDataAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AuditDataAction.class, LogDb.DASHBOARD);
    
    @Getter
    private List<McpAuditInfo> auditData;
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

    // When true, fetchAuditData groups records by their raw resourceName. Used for
    // child rows (tools/resources/prompts) where the resourceName is the tool name
    // and multiple device-specific records share that name.
    @Setter
    private boolean mergeMcpComponents = false;

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

                // Surface parent servers whose tools/resources/prompts match the search.
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

            if (mergeMcpServers) {
                runMergedAggregation(filter, sort, skip, limit, true, agentChoices, serverChoices);
            } else if (mergeMcpComponents) {
                runMergedAggregation(filter, sort, skip, limit, false, null, null);
            } else {
                this.auditData = McpAuditInfoDao.instance.findAll(filter, skip, limit, sort);
                this.total = McpAuditInfoDao.instance.count(filter);
            }
            loggerMaker.info("Fetched " + auditData.size() + " audit records out of " + total + " total");

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching audit data: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    /**
     * Aggregation path: dedupe records by canonical (agent, server) name. The canonical
     * name is resourceName with the leading "<device-id>." segment removed when there
     * are 2+ segments; otherwise the resourceName is used as-is. The most recent record
     * (by lastDetected) in each group becomes the representative; every member's
     * hostCollectionId is collected on it so the children-fetch can scope across all
     * device-specific collections.
     */
    @SuppressWarnings("unchecked")
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

    private void runMergedAggregation(Bson matchFilter, Bson sort, int skip, int limit, boolean stripDevicePrefix,
                                      List<String> agentChoices, List<String> serverChoices) {
        List<Bson> commonStages = new ArrayList<>();
        commonStages.add(Aggregates.match(matchFilter));

        if (stripDevicePrefix) {
            // Server-mode key: strip the leading "<device-id>." segment so multiple
            // devices reporting the same MCP server collapse together.
            commonStages.add(new Document("$addFields", new Document("_parts",
                new Document("$split", Arrays.asList("$" + McpAuditInfo.RESOURCE_NAME, ".")))));
            commonStages.add(new Document("$addFields", new Document("_groupKey",
                new Document("$cond", Arrays.asList(
                    new Document("$gte", Arrays.asList(new Document("$size", "$_parts"), 2)),
                    new Document("$reduce", new Document()
                        .append("input", new Document("$slice", Arrays.asList("$_parts", 1, Integer.MAX_VALUE)))
                        .append("initialValue", "")
                        .append("in", new Document("$cond", Arrays.asList(
                            new Document("$eq", Arrays.asList("$$value", "")),
                            "$$this",
                            new Document("$concat", Arrays.asList("$$value", ".", "$$this"))
                        )))
                    ),
                    "$" + McpAuditInfo.RESOURCE_NAME
                ))
            )));
        } else {
            // Component-mode key: raw resourceName. Children records use the tool name
            // directly (e.g. "searchDocumentation") with no device prefix, so we collapse
            // by name alone within the parent's hostCollectionId scope.
            commonStages.add(new Document("$addFields", new Document("_groupKey",
                "$" + McpAuditInfo.RESOURCE_NAME)));
        }

        // Lower _priority = more restrictive, so $first after the priority sort picks the
        // most-restrictive policy across the group. The shield enforces "rejected wins"
        // semantics across all devices, so the UI must reflect that: Rejected (0)
        // > Conditional Approval (1) > Approved (2) > Pending (3). Within a priority
        // tier, latest record wins.
        // Conditional approvals are stored either as remarks="Conditionally Approved"
        // (current modal output) or as remarks="Approved" with an approvalConditions
        // object (legacy). Both shapes resolve to priority 1.
        Document addPriority = new Document("$addFields", new Document("_priority",
            new Document("$cond", Arrays.asList(
                new Document("$eq", Arrays.asList("$" + McpAuditInfo.REMARKS, "Rejected")), 0,
                new Document("$cond", Arrays.asList(
                    new Document("$or", Arrays.asList(
                        new Document("$eq", Arrays.asList("$" + McpAuditInfo.REMARKS, "Conditionally Approved")),
                        new Document("$and", Arrays.asList(
                            new Document("$eq", Arrays.asList("$" + McpAuditInfo.REMARKS, "Approved")),
                            new Document("$ne", Arrays.asList("$" + McpAuditInfo.APPROVAL_CONDITIONS, null))
                        ))
                    )), 1,
                    new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$" + McpAuditInfo.REMARKS, "Approved")), 2,
                        3
                    ))
                ))
            ))
        ));
        commonStages.add(addPriority);

        // Optional agent/server filtering against the derived _groupKey (server-mode only).
        // _groupKey is "<agent>.<server>" when the canonical name has 2+ segments, or
        // just "<server>" when it has 1 (no agent prefix).
        List<Bson> groupKeyMatchOrs = new ArrayList<>();
        if (stripDevicePrefix && agentChoices != null && !agentChoices.isEmpty()) {
            List<Bson> agentOrs = new ArrayList<>();
            for (String a : agentChoices) {
                String esc = java.util.regex.Pattern.quote(a);
                agentOrs.add(Filters.regex("_groupKey", "^" + esc + "\\."));
            }
            groupKeyMatchOrs.add(Filters.or(agentOrs));
        }
        if (stripDevicePrefix && serverChoices != null && !serverChoices.isEmpty()) {
            List<Bson> serverOrs = new ArrayList<>();
            for (String s : serverChoices) {
                String esc = java.util.regex.Pattern.quote(s);
                serverOrs.add(Filters.regex("_groupKey", "(^|\\.)" + esc + "$"));
            }
            groupKeyMatchOrs.add(Filters.or(serverOrs));
        }
        if (!groupKeyMatchOrs.isEmpty()) {
            commonStages.add(Aggregates.match(Filters.and(groupKeyMatchOrs)));
        }

        commonStages.add(Aggregates.sort(Sorts.orderBy(
            Sorts.ascending("_priority"),
            Sorts.descending(McpAuditInfo.LAST_DETECTED)
        )));
        commonStages.add(Aggregates.group("$_groupKey",
            Accumulators.first("doc", "$$ROOT"),
            Accumulators.addToSet("groupedHostCollectionIds", "$" + McpAuditInfo.HOST_COLLECTION_ID),
            Accumulators.addToSet("groupedHexIds",
                new Document("$toString", "$_id")),
            Accumulators.sum("groupedCount", 1)
        ));

        // Total count: same pipeline up to group, then count groups
        List<Bson> countPipeline = new ArrayList<>(commonStages);
        countPipeline.add(Aggregates.count("count"));
        long groupTotal = 0;
        try (MongoCursor<Document> cursor = McpAuditInfoDao.instance.getMCollection()
                .aggregate(countPipeline, Document.class).cursor()) {
            if (cursor.hasNext()) {
                Number n = (Number) cursor.next().get("count");
                if (n != null) groupTotal = n.longValue();
            }
        }
        this.total = groupTotal;

        // Data pipeline: hoist the canonical doc + extras, drop helper fields, paginate
        List<Bson> dataPipeline = new ArrayList<>(commonStages);
        dataPipeline.add(new Document("$replaceRoot", new Document("newRoot",
            new Document("$mergeObjects", Arrays.asList(
                "$doc",
                new Document()
                    .append("groupedHostCollectionIds", "$groupedHostCollectionIds")
                    .append("groupedHexIds", "$groupedHexIds")
                    .append("groupedCount", "$groupedCount")
            ))
        )));
        // _parts only exists when stripDevicePrefix=true, but $project with a value of 0
        // is a no-op for missing fields.
        dataPipeline.add(new Document("$project", new Document()
            .append("_parts", 0)
            .append("_groupKey", 0)
            .append("_priority", 0)));
        dataPipeline.add(Aggregates.sort(sort));
        dataPipeline.add(Aggregates.skip(skip));
        dataPipeline.add(Aggregates.limit(limit));

        List<McpAuditInfo> rows = new ArrayList<>();
        try (MongoCursor<McpAuditInfo> cursor = McpAuditInfoDao.instance.getMCollection()
                .aggregate(dataPipeline, McpAuditInfo.class).cursor()) {
            while (cursor.hasNext()) {
                rows.add(cursor.next());
            }
        }
        this.auditData = rows;
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
                    Filters.eq(McpAuditInfo.TYPE, "mcp-server"),
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
            }

            Bson combined = Updates.combine(updates);
            if (targetIds.size() == 1) {
                McpAuditInfoDao.instance.updateOne(Filters.eq(Constants.ID, targetIds.get(0)), combined);
            } else {
                McpAuditInfoDao.instance.updateMany(Filters.in(Constants.ID, targetIds), combined);
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
