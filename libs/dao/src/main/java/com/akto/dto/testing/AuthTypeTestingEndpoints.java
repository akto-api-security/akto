package com.akto.dto.testing;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AuthTypeTestingEndpoints extends TestingEndpoints {
    private List<String> authTypes;

    public AuthTypeTestingEndpoints(Operator operator, List<String> authTypes) {
        super(Type.AUTH_TYPE, operator);
        this.authTypes = authTypes;
    }

    public AuthTypeTestingEndpoints() {
        super(Type.AUTH_TYPE, Operator.OR);
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        // Not used in filtering workflow, only for testing/policy contexts
        return new ArrayList<>();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        if (authTypes == null || authTypes.isEmpty()) {
            return false;
        }
        
        ApiInfo apiInfo = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(key));
        if (apiInfo == null || apiInfo.getAllAuthTypesFound() == null) {
            return false;
        }
        
        // Check if any of the selected auth types exist in the API's auth types
        Set<ApiInfo.AuthType> selectedAuthTypes = new HashSet<>();
        for (String authTypeStr : authTypes) {
            try {
                selectedAuthTypes.add(ApiInfo.AuthType.valueOf(authTypeStr));
            } catch (IllegalArgumentException e) {
                // Skip invalid auth types
            }
        }
        
        // Check if any set in allAuthTypesFound contains any of our selected auth types
        for (Set<ApiInfo.AuthType> authTypeSet : apiInfo.getAllAuthTypesFound()) {
            for (ApiInfo.AuthType authType : authTypeSet) {
                if (selectedAuthTypes.contains(authType)) {
                    return true;
                }
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
        if (authTypes == null || authTypes.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        // Convert string auth types to AuthType enum
        List<ApiInfo.AuthType> authTypeEnums = new ArrayList<>();
        for (String authTypeStr : authTypes) {
            try {
                authTypeEnums.add(ApiInfo.AuthType.valueOf(authTypeStr));
            } catch (IllegalArgumentException e) {
                // Skip invalid auth types
            }
        }

        if (authTypeEnums.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        List<Bson> authTypeFilters = new ArrayList<>();
        for (ApiInfo.AuthType authType : authTypeEnums) {
            // Nested elemMatch: outer for array-of-arrays, inner for elements in sub-array
            // Java Filters API doesn't support nested elemMatch, so use Document
            authTypeFilters.add(
                new Document(ApiInfo.ALL_AUTH_TYPES_FOUND,
                    new Document("$elemMatch",
                        new Document("$elemMatch",
                            new Document("$eq", authType.name())
                        )
                    )
                )
            );
        }
        Bson authFilter = Filters.or(authTypeFilters);

        // For ApiInfo-based collections, use the auth filter directly
        if (type == ApiCollectionUsers.CollectionType.Id_ApiCollectionId) {
            return authFilter;
        }

        // For SingleTypeInfo-based collections (ApiCollectionId), 
        // fetch matching ApiInfos and create filters based on URL/method/collectionId
        // This is necessary because SingleTypeInfo doesn't have allAuthTypesFound field
        List<ApiInfo> matchedApis = ApiInfoDao.instance.findAll(authFilter);
        
        if (matchedApis.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        List<Bson> apiFilters = new ArrayList<>();
        for (ApiInfo apiInfo : matchedApis) {
            apiFilters.add(createApiFilters(type, apiInfo.getId()));
        }

        return Filters.or(apiFilters);
    }

    public List<String> getAuthTypes() {
        return authTypes;
    }

    public void setAuthTypes(List<String> authTypes) {
        this.authTypes = authTypes;
    }
}