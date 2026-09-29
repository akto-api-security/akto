package com.akto.dao;

import com.akto.dto.McpAuditInfo;
import com.akto.dao.context.Context;
import com.akto.dao.insights.agentic.AgentFindingGroupAggregation;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class McpAuditInfoDao extends AccountsContextDao<McpAuditInfo> {
    public static final String COLLECTION_NAME = "mcp_audit_info";
    public static final McpAuditInfoDao instance = new McpAuditInfoDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<McpAuditInfo> getClassT() {
        return McpAuditInfo.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"lastDetected"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"markedBy"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"type"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
        
        fieldNames = new String[]{"resourceName"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
        
        fieldNames = new String[]{"updatedTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{McpAuditInfo.MCP_HOST, McpAuditInfo.TYPE, McpAuditInfo.RESOURCE_NAME};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{McpAuditInfo.CONTEXT_SOURCE};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    /**
     * Argus (AGENTIC) unapproved/malicious-components read: audit rows in the window, grouped
     * {hostCollectionId, type, remarks} in Mongo off the existing `lastDetected` index — never the
     * raw audit documents. `contextSource` missing is included (legacy docs predate the field),
     * mirroring InsightDataLoader#loadAuditRows' own contextSource-or-missing filter.
     */
    public List<AgentFindingGroup> auditGroupsForAgents(int startTs, CONTEXT_SOURCE contextSource, int namesPerGroupCap) {
        Bson filter = Filters.and(
                Filters.gte(McpAuditInfo.LAST_DETECTED, startTs),
                Filters.or(
                        Filters.eq(McpAuditInfo.CONTEXT_SOURCE, contextSource != null ? contextSource.name() : null),
                        Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false)));

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        pipeline.addAll(AgentFindingGroupAggregation.groupAndProject(
                McpAuditInfo.HOST_COLLECTION_ID, McpAuditInfo.TYPE, McpAuditInfo.REMARKS,
                McpAuditInfo.LAST_DETECTED, McpAuditInfo.RESOURCE_NAME, namesPerGroupCap));

        return AgentFindingGroupAggregation.parse(
                getMCollection().aggregate(pipeline, BasicDBObject.class).into(new ArrayList<>()));
    }

    public List<McpAuditInfo> findMarkedByEmptySortedByLastDetected(int pageNumber, int pageSize) {
        BasicDBObject sort = new BasicDBObject();
        // First sort: markedBy empty at top, then by lastDetected descending
        sort.put("markedBy", 1); // empty string comes first
        sort.put("lastDetected", -1); // descending order
        int skip = (pageNumber - 1) * pageSize;
        return this.getMCollection().find(new BasicDBObject())
            .sort(sort)
            .skip(skip)
            .limit(pageSize)
            .into(new ArrayList<>());
    }
}
