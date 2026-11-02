package com.akto.dao;

import com.akto.dto.EndpointDeviceCa;
import com.mongodb.client.model.CreateCollectionOptions;

public class EndpointDeviceCaDao extends AccountsContextDao<EndpointDeviceCa> {

    public static final EndpointDeviceCaDao instance = new EndpointDeviceCaDao();

    private EndpointDeviceCaDao() {}

    @Override
    public String getCollName() {
        return "endpoint_device_ca";
    }

    @Override
    public Class<EndpointDeviceCa> getClassT() {
        return EndpointDeviceCa.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        // deviceId: the renewal path and the per-device issuance quota both look up by it.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointDeviceCa.DEVICE_ID}, false);
        // issuedAt: the rate limiter counts issuances in a trailing window.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointDeviceCa.ISSUED_AT}, false);
    }
}
