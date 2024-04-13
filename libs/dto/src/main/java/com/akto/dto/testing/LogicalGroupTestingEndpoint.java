package com.akto.dto.testing;

import com.akto.dao.MCollection;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.data_types.Conditions;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

public class LogicalGroupTestingEndpoint extends TestingEndpoints {
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
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        if (key == null) {
            return false;
        }
        if (this.andConditions == null && this.orConditions == null) {
            return false;
        }

        boolean contains = true;
        if (this.andConditions != null) {
            contains = this.andConditions.validate(key);
        }
        if (this.orConditions != null) {
            contains = contains && this.orConditions.validate(key);
        }
        return contains;
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

    @Override
    public Bson createFilters(CollectionType type) {
        return MCollection.noMatchFilter;
    }
}
