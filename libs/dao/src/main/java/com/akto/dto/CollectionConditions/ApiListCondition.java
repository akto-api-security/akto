package com.akto.dto.CollectionConditions;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

public class ApiListCondition extends CollectionCondition{

    Set<ApiInfoKey> apiList;

    public Set<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(Set<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    public ApiListCondition() {
        super(Type.API_LIST, Operator.OR);
    }

    public ApiListCondition(Operator operator) {
        super(Type.API_LIST, operator);
    }

    public ApiListCondition(Set<ApiInfoKey> apiList, Operator operator) {
        super(Type.API_LIST, operator);
        this.apiList = apiList;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
        return new ArrayList<>(apiList);
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return apiList.contains(key);
    }

    public static void updateApiListCondition(ApiListCondition apiListCondition, List<ApiInfoKey> list, boolean isAddOperation) {
        Set<ApiInfoKey> tmp = new HashSet<>(apiListCondition.returnApis());

        if (isAddOperation) {
            tmp.addAll(list);
        } else {
            tmp.removeAll(list);
        }

        apiListCondition.setApiList(tmp);
    }

    private static Bson createApiFilters(CollectionType type, ApiInfoKey api) {

        String prefix = getFilterPrefix(type);

        return Filters.and(
                Filters.eq(prefix + SingleTypeInfo._URL, api.getUrl()),
                Filters.eq(prefix + SingleTypeInfo._METHOD, api.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, api.getApiCollectionId()));

    }

    /*
     * since the API list is being sent from the dashboard,
     * so by virtue of it, it won't be very large, 
     * thus we can afford the following query.
     */
    @Override
    public Bson createFilters(CollectionType type) {
        Set<ApiInfoKey> apiSet = new HashSet<>(apiList);
        List<Bson> apiFilters = new ArrayList<>();
        if (apiSet != null && !apiSet.isEmpty()) {
            for (ApiInfoKey api : apiSet) {
                apiFilters.add(createApiFilters(type, api));
            }
            return Filters.or(apiFilters);
        }

        return Filters.nor(new BasicDBObject());
    }

}
