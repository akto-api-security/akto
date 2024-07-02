package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class UnauthenticatedEndpoint extends TestingEndpoints {

    private static int limit = 50;

    @BsonIgnore
    int skip;

    public UnauthenticatedEndpoint() {
        super(Type.UNAUTHENTICATED, Operator.OR);
    }

    Bson unauthenticatedFilter = Filters.in(
        ApiInfo.ALL_AUTH_TYPES_FOUND, 
        Collections.singletonList(Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED))
    );

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(unauthenticatedFilter, this.skip, limit, Projections.include("_id"));
        List<ApiInfo.ApiInfoKey> apiInfoKeys= new ArrayList<>();
        for(ApiInfo apiInfo: apiInfos){
            apiInfoKeys.add(apiInfo.getId());
        }
        return apiInfoKeys;
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = getFilterPrefix(type);

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }


    @Override
    public Bson createFilters(CollectionType type) {
        Set<ApiInfoKey> apiSet = new HashSet<>(returnApis());
        List<Bson> apiFilters = new ArrayList<>();
        if (apiSet != null && !apiSet.isEmpty()) {
            for (ApiInfoKey api : apiSet) {
                apiFilters.add(createApiFilters(type, api));
            }
            return Filters.or(apiFilters);
        }

        return MCollection.noMatchFilter;
    }   
}
