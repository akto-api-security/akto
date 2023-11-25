package com.akto.dto.CollectionConditions;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;

public class ParamCondition extends CollectionCondition{

    boolean isHeader;
    boolean isRequest;
    String param;
    String value;

    public ParamCondition(Operator operator, boolean isHeader, boolean isRequest, String param, String value) {
        super(Type.PARAM, operator);
        this.isHeader = isHeader;
        this.isRequest = isRequest;
        this.param = param;
        this.value = value;
    }

    public ParamCondition() {
        super(Type.PARAM, Operator.OR);
    }

    public ParamCondition(Operator operator) {
        super(Type.PARAM, operator);
    }

    @Override
    public Set<ApiInfoKey> returnApis() {

        Bson responseFilter;

        if(isRequest){
            responseFilter = Filters.eq(SingleTypeInfo._RESPONSE_CODE, -1);
        } else {
            responseFilter = Filters.ne(SingleTypeInfo._RESPONSE_CODE, -1);
        }

        Bson headerFilter;

        if(isHeader){
            headerFilter = Filters.eq(SingleTypeInfo._IS_HEADER, true);
        } else {
            headerFilter = Filters.or(
                Filters.eq(SingleTypeInfo._IS_HEADER, false),
                Filters.exists(SingleTypeInfo._IS_HEADER, false)
            );
        }

        Bson filter = Filters.and( 
            responseFilter,
            headerFilter,
            Filters.eq(SingleTypeInfo._PARAM, param),
            Filters.in(SingleTypeInfo._VALUES_ELEMENTS, value));

        // values not present for mirrored collections.
        // either use sampleData to STI and then do this or use regex queries on sample data.

        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(filter).stream().collect(Collectors.toSet());
    }

    @Override
    public Map<CollectionType, Bson> returnFiltersMap() {
        return CollectionCondition.createFiltersMapWithApiList(returnApis());
    }

    public boolean isHeader() {
        return isHeader;
    }

    public void setHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isRequest() {
        return isRequest;
    }

    public void setRequest(boolean isRequest) {
        this.isRequest = isRequest;
    }
    
}
