package com.akto.dao;

import com.akto.dto.EndpointInfoView;

public class EndpointInfoViewTempDao extends AccountsContextDao<EndpointInfoView> {

    public static final EndpointInfoViewTempDao instance = new EndpointInfoViewTempDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.DISCOVERED_TIMESTAMP}, false);
    }

    @Override
    public String getCollName() {
        return "endpoint_info_views_temp";
    }

    @Override
    public Class<EndpointInfoView> getClassT() {
        return EndpointInfoView.class;
    }
}
