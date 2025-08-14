package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.akto.dto.McpAuditInfo;
import com.akto.dao.context.Context;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class McpAuditInfoDao extends AccountsContextDaoWithRbac<McpAuditInfo> {
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

    @Override
    public String getFilterKeyString() {
        return "";
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

    public void insertAuditInfoWithMarkedByAndDescription(String markedBy, String description) {
        McpAuditInfo auditInfo = new McpAuditInfo();
        auditInfo.setMarkedBy(markedBy);
        auditInfo.setRemarks(description);
        String now = String.valueOf(System.currentTimeMillis());
        auditInfo.setLastDetected(now);
        auditInfo.setUpdatedTimestamp(now);
        auditInfo.setType("tools");
        auditInfo.setResourceName("resource");
        Set<ApiInfo.ApiAccessType> apiAccessTypes = new HashSet<>();
        apiAccessTypes.add(ApiInfo.ApiAccessType.PUBLIC);
        auditInfo.setApiAccessTypes(apiAccessTypes); // Assuming ApiAccessType is a Set, initialize it as needed
        // Set other required fields if any
        this.getMCollection().insertOne(auditInfo);
    }

    public void updateAuditInfo(String id, String markedBy, String remarks) {
        String updatedTs = String.valueOf(System.currentTimeMillis());
        BasicDBObject query = new BasicDBObject("_id", id);
        BasicDBObject updateFields = new BasicDBObject();
        updateFields.put("markedBy", markedBy);
        updateFields.put("remarks", remarks);
        updateFields.put("updatedTimestamp", updatedTs);
        BasicDBObject update = new BasicDBObject("$set", updateFields);
        this.getMCollection().updateOne(query, update);
    }

}
