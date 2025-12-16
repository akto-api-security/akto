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

        // Index 1: Critical for job polling - covers both SCHEDULED and stale RUNNING jobs
        // Query: {jobStatus: {$in: [SCHEDULED, RUNNING]}, scheduledAt/heartbeatAt: {$lt: now}}
        String[] fieldNames = {AccountJob.JOB_STATUS, AccountJob.SCHEDULED_AT, AccountJob.HEARTBEAT_AT};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        // Index 2: For querying jobs by type and subtype (useful for dashboard/admin queries)
        fieldNames = new String[]{AccountJob.JOB_TYPE, AccountJob.SUB_TYPE, AccountJob.JOB_STATUS};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }
}
