package com.akto.dto.CollectionConditions;

import java.util.List;
import org.bson.conversions.Bson;

import com.akto.dao.MCollection;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.client.model.Filters;

public class MethodCondition extends TestingEndpoints {

    Method method;
    
    public Method getMethod() {
        return method;
    }
    
    public void setMethod(Method method) {
        this.method = method;
    }
    
    public MethodCondition() {
        super(Type.METHOD, Operator.OR);
    }

    public MethodCondition(Operator operator) {
        super(Type.METHOD, operator);
    }
    
    public MethodCondition(Operator operator, Method method) {
        super(Type.METHOD, operator);
        this.method = method;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
        // This returns only 50 endpoints, to avoid a huge response
        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(method);
    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return key.getMethod().equals(method);
    }

    public static Bson createMethodFilter(Method method) {
        return createMethodFilter(method, "");
    }

    private static Bson createMethodFilter(Method method, String prefix) {
        // using the URL field in filter to utilize mongo index
        return Filters.and(
                Filters.regex(prefix + SingleTypeInfo._URL, ".*"),
                Filters.eq(prefix + SingleTypeInfo._METHOD, method.toString()));
    }

    @Override
    public Bson createFilters(CollectionType type) {

        String prefix = getFilterPrefix(type);

        if (method != null) {
            return createMethodFilter(method, prefix);
        }

        return MCollection.noMatchFilter;
    }
    
}
