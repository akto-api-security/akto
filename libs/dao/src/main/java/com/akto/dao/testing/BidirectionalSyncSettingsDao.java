package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.testing.BidirectionalSyncSettings;
import com.mongodb.client.model.CreateCollectionOptions;

public class BidirectionalSyncSettingsDao extends AccountsContextDao<BidirectionalSyncSettings> {

    public static final BidirectionalSyncSettingsDao instance = new BidirectionalSyncSettingsDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());
    }

    @Override
    public String getCollName() {
        return "bidirectional_sync_settings";
    }

    @Override
    public Class<BidirectionalSyncSettings> getClassT() {
        return BidirectionalSyncSettings.class;
    }
}
