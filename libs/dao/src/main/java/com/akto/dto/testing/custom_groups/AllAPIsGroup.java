package com.akto.dto.testing.custom_groups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

public class AllAPIsGroup extends TestingEndpoints {
    private static int limit = 50;

    @BsonIgnore
    private List<ApiInfoKey> apiInfos;

    public AllAPIsGroup() {
        super(Type.ALL, Operator.OR);
    }

    public List<ApiInfoKey> getApiInfos() {
        return apiInfos;
    }

    public void setApiInfos(List<ApiInfoKey> apiInfos) {
        this.apiInfos = apiInfos;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
       return this.apiInfos;
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return true;
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = getFilterPrefix(type);

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }

    public final static int ALL_APIS_GROUP_ID = 111_111_121;

    public static void updateCollections(){
        ApiCollectionUsers.reset(ALL_APIS_GROUP_ID);

        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
            Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.toString()), Projections.include("_id")
        );

        for(ApiCollection apiCollection: apiCollections){
            
            int lastTimeStampRecorded = Context.now() + (5*60) ;
            int apiCollectionId = apiCollection.getId(); 
            int skip = 0 ;

            // create instance of the conditions class
            AllAPIsGroup allAPIsGroup = new AllAPIsGroup();
            while (true) {
                Bson filterQ = Filters.and(
                    Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId),
                    Filters.lt(ApiInfo.LAST_SEEN, lastTimeStampRecorded)
                );
                List<ApiInfo> apiInfosBatched = ApiInfoDao.instance.findAll(
                    filterQ, skip, limit, Sorts.descending(ApiInfo.LAST_SEEN), Projections.include(
                        "_id", ApiInfo.LAST_SEEN
                    )
                );

                List<ApiInfoKey> apiInfoKeysTemp = new ArrayList<>();
                for(ApiInfo apiInfo: apiInfosBatched){
                    apiInfoKeysTemp.add(apiInfo.getId());
                    lastTimeStampRecorded = Math.min(lastTimeStampRecorded, apiInfo.getLastSeen());
                }
                lastTimeStampRecorded += 2;
                skip += limit;

                allAPIsGroup.setApiInfos(apiInfoKeysTemp);
                ApiCollectionUsers.addToCollectionsForCollectionId(Collections.singletonList(allAPIsGroup), ALL_APIS_GROUP_ID);

                if(apiInfosBatched.size() < limit){
                    break;
                }
            }
        }

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
