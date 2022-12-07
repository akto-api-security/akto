package com.akto.dto.testing;


import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.mongodb.client.model.Filters;
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
        Bson filter = Filters.and(Filters.eq(ApiInfo.ApiInfoKey.API_COLLECTION_ID, key.getApiCollectionId())
                , Filters.eq(ApiInfo.ApiInfoKey.URL, key.getUrl())
                , Filters.eq(ApiInfo.ApiInfoKey.METHOD, key.getMethod()));
        long count = SingleTypeInfoDao.instance.getMCollection().countDocuments(filter);
        return count > 0;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

}
