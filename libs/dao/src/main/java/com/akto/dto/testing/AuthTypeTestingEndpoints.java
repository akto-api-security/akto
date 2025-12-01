package com.akto.dto.testing;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.NotImplementedException;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        throw new NotImplementedException();
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return true;
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

        // Create filters for auth types
        // allAuthTypesFound is Set<Set<AuthType>>, so we need to match against nested arrays
        // Use Filters.in similar to UnauthenticatedEndpoint pattern
        List<Bson> authTypeFilters = new ArrayList<>();
        for (ApiInfo.AuthType authType : authTypeEnums) {
            // Match if allAuthTypesFound contains a set that contains this auth type
            authTypeFilters.add(
                Filters.in(ApiInfo.ALL_AUTH_TYPES_FOUND, 
                    Collections.singletonList(Collections.singletonList(authType))
                )
            );
        }

        Bson authFilter = Filters.or(authTypeFilters);
        List<ApiInfo> matchedApis = ApiInfoDao.instance.findAll(authFilter, null);
        
        if (matchedApis.isEmpty()) {
            return MCollection.noMatchFilter;
        }

        List<Bson> apiFilters = new ArrayList<>();
        for (ApiInfo apiInfo : matchedApis) {
            ApiInfo.ApiInfoKey key = apiInfo.getId();
            Bson filter = Filters.and(
                Filters.eq(SingleTypeInfo._URL, key.getUrl()),
                Filters.eq(SingleTypeInfo._METHOD, key.getMethod().toString()),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, key.getApiCollectionId())
            );
            apiFilters.add(filter);
        }

        if (apiFilters.isEmpty()) {
            return MCollection.noMatchFilter;
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

