package com.akto.dto.testing;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class AccessTypeTestingEndpoints extends TestingEndpoints {
    private List<String> apiAccessTypes;

    public AccessTypeTestingEndpoints(Operator operator, List<String> apiAccessTypes) {
        super(Type.API_ACCESS_TYPES, operator);
        this.apiAccessTypes = apiAccessTypes;
    }

    public AccessTypeTestingEndpoints() {
        super(Type.API_ACCESS_TYPES, Operator.OR);
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        return new ArrayList<>();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        if (apiAccessTypes == null || apiAccessTypes.isEmpty()) {
            return false;
        }

        ApiInfo apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(key));
        if (apiInfo == null || apiInfo.getApiAccessTypes() == null) {
            return false;
        }

        // Special case: if only THIRD_PARTY is selected, exclude APIs that also have PUBLIC
        boolean onlyThirdParty = apiAccessTypes.size() == 1 && apiAccessTypes.contains("THIRD_PARTY");
        if (onlyThirdParty) {
            return apiInfo.getApiAccessTypes().contains(ApiInfo.ApiAccessType.THIRD_PARTY)
                && !apiInfo.getApiAccessTypes().contains(ApiInfo.ApiAccessType.PUBLIC);
        }

        for (String accessTypeStr : apiAccessTypes) {
            try {
                ApiInfo.ApiAccessType accessType = ApiInfo.ApiAccessType.valueOf(accessTypeStr);
                if (apiInfo.getApiAccessTypes().contains(accessType)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Skip invalid access types
            }
        }

        return false;
    }

    private static Bson createApiFilters(ApiCollectionUsers.CollectionType type, ApiInfo.ApiInfoKey apiKey) {
        String prefix = getFilterPrefix(type);

        return Filters.and(
            Filters.eq(prefix + SingleTypeInfo._URL, apiKey.getUrl()),
            Filters.eq(prefix + SingleTypeInfo._METHOD, apiKey.getMethod().toString()),
            Filters.in(SingleTypeInfo._COLLECTION_IDS, apiKey.getApiCollectionId())
        );
    }

    @Override
    public Bson createFilters(ApiCollectionUsers.CollectionType type) {
        if (apiAccessTypes == null || apiAccessTypes.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        List<ApiInfo.ApiAccessType> accessTypeEnums = new ArrayList<>();
        for (String accessTypeStr : apiAccessTypes) {
            try {
                accessTypeEnums.add(ApiInfo.ApiAccessType.valueOf(accessTypeStr));
            } catch (IllegalArgumentException e) {
                // Skip invalid access types
            }
        }

        if (accessTypeEnums.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        // Special case: if only THIRD_PARTY is selected, exclude APIs that also have PUBLIC
        Bson accessTypeFilter;
        if (accessTypeEnums.size() == 1 && accessTypeEnums.contains(ApiInfo.ApiAccessType.THIRD_PARTY)) {
            accessTypeFilter = Filters.and(
                Filters.in(ApiInfo.API_ACCESS_TYPES, accessTypeEnums),
                Filters.nin(ApiInfo.API_ACCESS_TYPES, ApiInfo.ApiAccessType.PUBLIC)
            );
        } else {
            accessTypeFilter = Filters.in(ApiInfo.API_ACCESS_TYPES, accessTypeEnums);
        }

        // For ApiInfo-based collections, use the filter directly
        if (type == ApiCollectionUsers.CollectionType.Id_ApiCollectionId) {
            return accessTypeFilter;
        }

        // For SingleTypeInfo-based collections, fetch matching ApiInfos
        // and create filters based on URL/method/collectionId
        List<ApiInfo> matchedApis = ApiInfoDao.instance.findAll(accessTypeFilter);

        if (matchedApis.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        List<Bson> apiFilters = new ArrayList<>();
        for (ApiInfo apiInfo : matchedApis) {
            apiFilters.add(createApiFilters(type, apiInfo.getId()));
        }

        return Filters.or(apiFilters);
    }

    public List<String> getApiAccessTypes() {
        return apiAccessTypes;
    }

    public void setApiAccessTypes(List<String> apiAccessTypes) {
        this.apiAccessTypes = apiAccessTypes;
    }
}
