package com.akto.dto.CollectionConditions;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollectionUsers.CollectionType;
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

    @Override
    public Set<ApiInfoKey> returnApis() {

        Bson hostFilterQ = SingleTypeInfoDao.filterForHostHeader(0, false);
        Bson filter = Filters.and(Filters.eq(ApiInfoKey.METHOD, method), hostFilterQ);

        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(filter).stream().collect(Collectors.toSet());

    }

    @Override
    public Map<CollectionType, Bson> returnFiltersMap() {
        return CollectionCondition.createFiltersMapWithApiList(returnApis());
    }
    
}
