package com.akto.dao;

import com.akto.dto.DeviceDomainConfig;
import com.mongodb.client.model.CreateCollectionOptions;

public class DeviceDomainConfigDao extends AccountsContextDao<DeviceDomainConfig> {

    public static final DeviceDomainConfigDao instance = new DeviceDomainConfigDao();

    private DeviceDomainConfigDao() {}

    @Override
    public String getCollName() {
        return "device_domain_config";
    }

    @Override
    public Class<DeviceDomainConfig> getClassT() {
        return DeviceDomainConfig.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{DeviceDomainConfig.DEVICE_ID}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{DeviceDomainConfig.UPDATED_AT}, false);
    }
}
