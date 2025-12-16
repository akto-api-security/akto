package com.akto.dao.jobs;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.jobs.AccountJob;
import com.mongodb.client.model.CreateCollectionOptions;

/**
 * DAO for generic account-level jobs collection.
 * This collection can be used by any feature that needs account-level job tracking.
 */
public class AccountJobDao extends AccountsContextDao<AccountJob> {

    public static final AccountJobDao instance = new AccountJobDao();

    @Override
    public String getCollName() {
        return "account_jobs";
    }

    @Override
    public Class<AccountJob> getClassT() {
        return AccountJob.class;
    }

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get() + "";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        // Index 1: Filter by job type, sort by creation date
        String[] fieldNames = {AccountJob.JOB_TYPE, AccountJob.CREATED_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index 2: Filter by job type and sub-type, sort by creation date
        fieldNames = new String[]{AccountJob.JOB_TYPE, AccountJob.SUB_TYPE, AccountJob.CREATED_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index 3: For job polling - filter by status, sort by scheduledAt
        fieldNames = new String[]{AccountJob.JOB_STATUS, AccountJob.SCHEDULED_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index 4: For heartbeat monitoring - filter by status and heartbeatAt
        fieldNames = new String[]{AccountJob.JOB_STATUS, AccountJob.HEARTBEAT_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index 5: For account-specific queries - filter by accountId and status
        fieldNames = new String[]{AccountJob.ACCOUNT_ID, AccountJob.JOB_STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }
}
