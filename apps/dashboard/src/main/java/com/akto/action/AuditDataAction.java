package com.akto.action;

import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
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
    
    @Getter
    private long total;

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
            
           
            List<Bson> filterList = prepareFilters(filters);
            Bson finalFilter = filterList.isEmpty() ? new BasicDBObject() : Filters.and(filterList);
            Bson sort = sortOrder == 1 ? Sorts.ascending(sortKey) : Sorts.descending(sortKey);
            
            this.auditData = McpAuditInfoDao.instance.findAll(finalFilter, skip, limit, sort);   
            this.total = McpAuditInfoDao.instance.count(finalFilter);          
            loggerMaker.info("Fetched " + auditData.size() + " audit records out of " + total + " total");
            
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching audit data: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
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
    String remarks;
    @Setter
    Map<String, Object> approvalData;

    public String updateAuditData() {
        User user = getSUser();
        String markedBy = user.getLogin();

        try {
            ObjectId id = new ObjectId(hexId);
            
            int currentTime = Context.now();
            
            // Check if this is a conditional approval (approvalData exists) or simple approval
            if (approvalData != null) {
                // Handle conditional approval
                Map<String, Object> updateFields = new HashMap<>();
                updateFields.put("remarks", approvalData.get("remarks"));
                updateFields.put("markedBy", markedBy);
                updateFields.put("updatedTimestamp", currentTime);
                updateFields.put("approvedAt", currentTime);
                
                // Structure approvalConditions with justification inside
                Map<String, Object> conditions = (Map<String, Object>) approvalData.get("conditions");
                if (conditions == null) {
                    conditions = new HashMap<>();
                }
                conditions.put("justification", approvalData.get("justification"));
                updateFields.put("approvalConditions", conditions);
                
                // Build the Updates object
                List<Bson> updates = new ArrayList<>();
                for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
                    updates.add(Updates.set(entry.getKey(), entry.getValue()));
                }
                
                McpAuditInfoDao.instance.updateOne(
                    Filters.eq(Constants.ID, id), 
                    Updates.combine(updates)
                );
            } else {
                // Handle simple approval/rejection
                List<Bson> updates = new ArrayList<>();
                updates.add(Updates.set("remarks", remarks));
                updates.add(Updates.set("markedBy", markedBy));
                updates.add(Updates.set("updatedTimestamp", currentTime));
                
                // Set approvedAt only for approvals, not rejections
                if ("Approved".equals(remarks)) {
                    updates.add(Updates.set("approvedAt", currentTime));
                }
                
                McpAuditInfoDao.instance.updateOne(
                    Filters.eq(Constants.ID, id), 
                    Updates.combine(updates)
                );
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
