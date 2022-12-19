package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.data_types.Conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public class LogicalGroupTestingEndpoint extends TestingEndpoints{
    private Conditions andConditions;
    private Conditions orConditions;
    public LogicalGroupTestingEndpoint() {
        super(Type.LOGICAL_GROUP);
    }

    public LogicalGroupTestingEndpoint(Conditions andConditions, Conditions orConditions) {
        super(Type.LOGICAL_GROUP);
        this.andConditions = andConditions;
        this.orConditions = orConditions;
    }

    @Override
    public boolean containsApi (ApiInfo.ApiInfoKey key) {
        if (key == null) {
            return false;
        }
        try {
            return this.andConditions.validate(key) && this.orConditions.validate(key);
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
    private boolean containsApiInCollection(Map<String, List<ApiInfo.ApiInfoKey>> collectionsApiInfoKey, ApiInfo.ApiInfoKey key) {
        if (collectionsApiInfoKey != null && collectionsApiInfoKey.containsKey(String.valueOf(key.getApiCollectionId()))) {
            List<ApiInfo.ApiInfoKey> apiInfoKeyList = collectionsApiInfoKey.get(String.valueOf(key.getApiCollectionId()));
            for (ApiInfo.ApiInfoKey apiInfoKey : apiInfoKeyList) {
                if (apiInfoKey.equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {

        return new ArrayList<>();
    }

    public Conditions getAndConditions() {
        return andConditions;
    }

    public void setAndConditions(Conditions andConditions) {
        this.andConditions = andConditions;
    }

    public Conditions getOrConditions() {
        return orConditions;
    }

    public void setOrConditions(Conditions orConditions) {
        this.orConditions = orConditions;
    }
}
