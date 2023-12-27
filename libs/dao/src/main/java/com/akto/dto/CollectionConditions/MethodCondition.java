package com.akto.dto.CollectionConditions;

import java.util.List;
import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.client.model.Filters;

public class MethodCondition extends CollectionCondition{

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

    private Bson createFilter() {
        Bson hostFilterQ = SingleTypeInfoDao.filterForHostHeader(0, false);
        Bson filter = Filters.and(Filters.eq(ApiInfoKey.METHOD, method), hostFilterQ);
        return filter;
    }

    @Override
    public List<ApiInfoKey> returnApis() {
        Bson filter = createFilter();
        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(filter);

    }

    @Override
    public boolean containsApi(ApiInfoKey key) {
        return checkApiUsingSTI(key, createFilter());
    }
    
}
