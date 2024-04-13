package com.akto.dto.testing;


import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import com.akto.dto.ApiInfo;
import org.bson.conversions.Bson;

import java.util.List;

public class CollectionWiseTestingEndpoints extends TestingEndpoints {

    private int apiCollectionId;

    public CollectionWiseTestingEndpoints() {
        super(Type.COLLECTION_WISE);
    }

    public CollectionWiseTestingEndpoints(int apiCollectionId) {
        super(Type.COLLECTION_WISE);
        this.apiCollectionId = apiCollectionId;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(apiCollectionId);
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return key.getApiCollectionId() == this.apiCollectionId;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    @Override
    public Bson createFilters(CollectionType type) {
        return Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId);
    }

}
