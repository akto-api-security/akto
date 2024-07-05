package com.akto.dto.testing;

import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;

import org.apache.commons.lang3.NotImplementedException;
import org.bson.conversions.Bson;

import java.util.List;

public class AllTestingEndpoints extends TestingEndpoints {

    public AllTestingEndpoints() {
        super(Type.ALL);
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
        throw new NotImplementedException();
    }
}
