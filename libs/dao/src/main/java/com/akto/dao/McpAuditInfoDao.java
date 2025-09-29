package com.akto.dao;

import com.akto.dto.McpAuditInfo;
import com.akto.dao.context.Context;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
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
