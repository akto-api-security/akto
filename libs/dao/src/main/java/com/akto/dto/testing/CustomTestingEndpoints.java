package com.akto.dto.testing;

import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dao.MCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

public class CustomTestingEndpoints extends TestingEndpoints {

    private List<ApiInfo.ApiInfoKey> apisList;

    public CustomTestingEndpoints() {
        super(Type.CUSTOM);
    }

    public CustomTestingEndpoints(List<ApiInfo.ApiInfoKey> apisList) {
        super(Type.CUSTOM);
        this.apisList = apisList;
    }

    public CustomTestingEndpoints(List<ApiInfo.ApiInfoKey> apisList, Operator operator) {
        super(Type.CUSTOM, operator);
        this.apisList = apisList;
    }

    public List<ApiInfo.ApiInfoKey> getApisList() {
        return apisList;
    }

    public void setApisList(List<ApiInfo.ApiInfoKey> apisList) {
        this.apisList = apisList;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        return this.getApisList();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return this.getApisList().contains(key);
    }

    public static void updateApiListCondition(CustomTestingEndpoints apiListCondition, List<ApiInfoKey> list, boolean isAddOperation) {
        Set<ApiInfoKey> tmp = new HashSet<>(apiListCondition.returnApis());

        if (isAddOperation) {
            tmp.addAll(list);
        } else {
            tmp.removeAll(list);
        }

        apiListCondition.setApisList(new ArrayList<>(tmp));
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
        Set<ApiInfoKey> apiSet = new HashSet<>(apisList);
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
