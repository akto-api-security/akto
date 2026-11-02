package com.akto.dao.jobs;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.jobs.AccountJobConfig;
import com.mongodb.client.model.CreateCollectionOptions;

/**
 * DAO for the generic account-level config/state collection.
 * This collection can be used by any feature that needs to persist a per-account
 * config/state blob keyed by a configKey (e.g. Cyborg's compliance cursor-sync state).
 */
public class AccountJobConfigDao extends AccountsContextDao<AccountJobConfig> {

    public static final AccountJobConfigDao instance = new AccountJobConfigDao();

    private AccountJobConfigDao() {}

    @Override
    public String getCollName() {
        return "account_job_configs";
    }

    @Override
    public Class<AccountJobConfig> getClassT() {
        return AccountJobConfig.class;
    }

    public void createIndicesIfAbsent() {
        String dbName = getDBName();
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());
        MCollection.createUniqueIndex(dbName, getCollName(), new String[]{AccountJobConfig.CONFIG_KEY}, true);
    }
}
