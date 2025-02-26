package com.akto.dao.audit_logs;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.audit_logs.ApiAuditLogs;

public class ApiAuditLogsDao extends AccountsContextDao<ApiAuditLogs> {

    public static final ApiAuditLogsDao instance = new ApiAuditLogsDao();

    private ApiAuditLogsDao() {}

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = { ApiAuditLogs.TIMESTAMP };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{ApiAuditLogs.USER_EMAIL, ApiAuditLogs.USER_IP_ADDRESS, ApiAuditLogs.TIMESTAMP};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    @Override
    public String getCollName() {
        return "api_audit_logs";
    }

    @Override
    public Class<ApiAuditLogs> getClassT() {
        return ApiAuditLogs.class;
    }
}
