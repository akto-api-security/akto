package com.akto.action;

import com.akto.dao.RBACDao;
import com.akto.dao.audit_logs.ApiAuditLogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.audit_logs.ApiAuditLogs;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.List;

public class ApiAuditLogsAction extends UserAction {
    private int skip;
    private int limit;
    private int sortOrder;
    private int startTimestamp;
    private int endTimestamp;

    private List<ApiAuditLogs> apiAuditLogs;
    private long totalAuditLogs;
    public String fetchApiAuditLogsFromDb() {
        User user = getSUser();
        if(user == null) return ERROR.toUpperCase();

        RBAC.Role currentRoleForUser = RBACDao.getCurrentRoleForUser(user.getId(), Context.accountId.get());
        if(currentRoleForUser == null || !currentRoleForUser.equals(RBAC.Role.ADMIN)) {
            addActionError("You do not have access to the audit logs.");
            return ERROR.toUpperCase();
        }

        Bson filters = Filters.and(
                Filters.gte(ApiAuditLogs.TIMESTAMP, startTimestamp),
                Filters.lt(ApiAuditLogs.TIMESTAMP, endTimestamp)
        );
        Bson sort = sortOrder == -1 ? Sorts.descending(ApiAuditLogs.TIMESTAMP) : Sorts.ascending(ApiAuditLogs.TIMESTAMP);

        if(limit == -1) {
            apiAuditLogs = ApiAuditLogsDao.instance.findAll(filters);
            totalAuditLogs = apiAuditLogs.size();
        } else {
            apiAuditLogs = ApiAuditLogsDao.instance.findAll(filters, skip, limit, sort);
            totalAuditLogs = ApiAuditLogsDao.instance.getMCollection().countDocuments(Filters.empty());
        }

        return SUCCESS.toUpperCase();
    }

    public List<ApiAuditLogs> getApiAuditLogs() {
        return apiAuditLogs;
    }

    public long getTotalAuditLogs() {
        return totalAuditLogs;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }
}
