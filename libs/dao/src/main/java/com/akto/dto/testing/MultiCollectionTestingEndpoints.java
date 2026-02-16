package com.akto.dto.testing;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class MultiCollectionTestingEndpoints extends TestingEndpoints {

    private List<Integer> apiCollectionIds;

    public MultiCollectionTestingEndpoints() {
        super(Type.MULTI_COLLECTION);
    }

    public MultiCollectionTestingEndpoints(List<Integer> apiCollectionIds) {
        super(Type.MULTI_COLLECTION);
        this.apiCollectionIds = apiCollectionIds;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        List<ApiInfo.ApiInfoKey> allApis = new ArrayList<>();

        if (apiCollectionIds == null || apiCollectionIds.isEmpty()) {
            return allApis;
        }

        for (Integer collectionId : apiCollectionIds) {
            List<ApiInfo.ApiInfoKey> collectionApis =
                SingleTypeInfoDao.instance.fetchEndpointsInCollection(collectionId);
            if (collectionApis != null) {
                allApis.addAll(collectionApis);
            }
        }

        return allApis;
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        if (apiCollectionIds == null || apiCollectionIds.isEmpty()) {
            return false;
        }
        return apiCollectionIds.contains(key.getApiCollectionId());
    }

    @Override
    public Bson createFilters(CollectionType type) {
        if (apiCollectionIds == null || apiCollectionIds.isEmpty()) {
            return Filters.empty();
        }
        return Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds);
    }

    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }
}