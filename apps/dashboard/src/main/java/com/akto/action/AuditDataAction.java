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

    public String updateAuditData() {
        User user = getSUser();
        String markedBy = user.getLogin();

        try {
            ObjectId id = new ObjectId(hexId);
            McpAuditInfoDao.instance.updateOne(Filters.eq(Constants.ID, id), Updates.combine(Updates.set("remarks", remarks), Updates.set("markedBy", markedBy), Updates.set("updatedTimestamp", Context.now())));
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
