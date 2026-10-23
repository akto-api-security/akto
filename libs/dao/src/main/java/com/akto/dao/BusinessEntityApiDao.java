package com.akto.dao;

import com.akto.dto.BusinessEntityApi;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BusinessEntityApiDao extends AccountsContextDao<BusinessEntityApi> {

    public static final BusinessEntityApiDao instance = new BusinessEntityApiDao();

    public static final List<String> BUSINESS_OPERATIONS = Arrays.asList("create", "read", "list", "update", "delete", "action");

    private BusinessEntityApiDao() {}

    @Override
    public String getCollName() {
        return "business_entity_api";
    }

    @Override
    public Class<BusinessEntityApi> getClassT() {
        return BusinessEntityApi.class;
    }

    public List<BusinessEntityApi> findOperations(String scopeKey, Collection<String> entityIds) {
        return getMCollection()
                .find(Filters.and(
                        Filters.eq(BusinessEntityApi.SCOPE_KEY, scopeKey),
                        Filters.in(BusinessEntityApi.ENTITY_ID, entityIds),
                        Filters.in(BusinessEntityApi.OPERATION, BUSINESS_OPERATIONS)))
                .into(new ArrayList<>());
    }
}
