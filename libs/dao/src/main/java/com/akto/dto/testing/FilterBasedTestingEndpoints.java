package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.SingleTypeInfoViewDao;
import com.akto.dto.ApiInfo;
import com.akto.util.EndpointDataQueryBuilder;
import com.mongodb.client.model.Filters;

public class FilterBasedTestingEndpoints extends TestingEndpoints {
    
    private EndpointDataQuery endpointDataQuery;

    public FilterBasedTestingEndpoints() {
        super(Type.FILTER_BASED);
    }

    public FilterBasedTestingEndpoints(EndpointDataQuery endpointDataQuery) {
        super(Type.FILTER_BASED);
        this.endpointDataQuery = endpointDataQuery;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        
        EndpointDataQueryBuilder endpointQueryBuilder = new EndpointDataQueryBuilder();
        ArrayList<Bson> filterList = endpointQueryBuilder.buildEndpointDataFilterList(endpointDataQuery);

        if (filterList == null) {
            return new ArrayList<>();
        }

        Bson sort = endpointQueryBuilder.buildEndpointInfoSort(endpointDataQuery);
        List<SingleTypeInfoView> endpoints = SingleTypeInfoViewDao.instance.findAll(Filters.and(filterList), 0, 10000, sort);

        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        
        for (SingleTypeInfoView singleTypeInfoView : endpoints) {
            apiInfoKeys.add(singleTypeInfoView.getId());
        }

        return apiInfoKeys;
    }

    @Override
    public boolean containsApi(ApiInfo.ApiInfoKey key) {
        return true;
    }

    public EndpointDataQuery getEndpointDataQuery() {
        return endpointDataQuery;
    }

    public void setEndpointDataQuery(EndpointDataQuery endpointDataQuery) {
        this.endpointDataQuery = endpointDataQuery;
    }

}
