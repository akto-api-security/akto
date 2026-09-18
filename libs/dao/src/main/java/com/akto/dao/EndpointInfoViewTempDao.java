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

        // the api changes page filters a discoveredTimestamp range with no collection predicate;
        // the compound above has the wrong prefix for that. these have to live here rather than on
        // EndpointInfoViewDao: the rebuild indexes the temp collection and then renames it over
        // live, so only what is created here survives the swap.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.HAS_HOST_HEADER, EndpointInfoView.DISCOVERED_TIMESTAMP}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.DISCOVERED_TIMESTAMP}, true);
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
