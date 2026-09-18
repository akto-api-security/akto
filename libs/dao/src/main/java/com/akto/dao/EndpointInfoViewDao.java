package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.akto.dto.EndpointInfoView;
import com.mongodb.client.model.*;

import java.util.*;

public class EndpointInfoViewDao extends AccountsContextDao<EndpointInfoView> {

    public static final EndpointInfoViewDao instance = new EndpointInfoViewDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.DISCOVERED_TIMESTAMP}, false);

        // the api changes page filters a discoveredTimestamp range with no collection predicate, so
        // neither the compound above (wrong prefix) nor the merge key can serve it. equality field
        // first, range second.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.HAS_HOST_HEADER, EndpointInfoView.DISCOVERED_TIMESTAMP}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{EndpointInfoView.DISCOVERED_TIMESTAMP}, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                Indexes.ascending(EndpointInfoView.API_COLLECTION_ID, EndpointInfoView.URL, EndpointInfoView.METHOD),
                new IndexOptions().name("merge_key_unique").unique(true));
    }

    @Override
    public String getCollName() {
        return "endpoint_info_views";
    }

    @Override
    public Class<EndpointInfoView> getClassT() {
        return EndpointInfoView.class;
    }

    /**
     * Only the read surface this branch needs: the api changes count and the endpoints table.
     * The ApiStats helpers from master are intentionally left out — they use the String-based
     * ApiInfo.AuthType that does not exist here yet (it is still an enum on this base), and
     * nothing on this branch calls them.
     */
}
