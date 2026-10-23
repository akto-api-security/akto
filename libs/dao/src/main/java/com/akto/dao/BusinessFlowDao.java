package com.akto.dao;

import com.akto.dto.BusinessFlow;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BusinessFlowDao extends AccountsContextDao<BusinessFlow> {

    public static final BusinessFlowDao instance = new BusinessFlowDao();

    private BusinessFlowDao() {}

    @Override
    public String getCollName() {
        return "business_flow";
    }

    @Override
    public Class<BusinessFlow> getClassT() {
        return BusinessFlow.class;
    }

    public List<BusinessFlow> findFlows(String scopeKey) {
        return getMCollection()
                .find(Filters.and(
                        Filters.eq(BusinessFlow.SCOPE_KEY, scopeKey),
                        Filters.in(BusinessFlow.TYPE, Arrays.asList(BusinessFlow.TYPE_LIFECYCLE, BusinessFlow.TYPE_JOURNEY))))
                .into(new ArrayList<>());
    }
}
