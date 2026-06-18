package com.akto.dao;

import com.akto.dto.EndpointInfoView;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

public class EndpointInfoViewTempDao extends AccountsContextDao<EndpointInfoView> {

    public static final EndpointInfoViewTempDao instance = new EndpointInfoViewTempDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.DISCOVERED_TIMESTAMP}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.ascending(EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.URL, EndpointInfoView.METHOD),
                new IndexOptions().name("merge_key_unique").unique(true));
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
