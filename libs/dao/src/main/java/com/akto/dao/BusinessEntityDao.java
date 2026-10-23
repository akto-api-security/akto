package com.akto.dao;

import com.akto.dto.BusinessEntity;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import java.util.ArrayList;
import java.util.List;

public class BusinessEntityDao extends AccountsContextDao<BusinessEntity> {

    public static final BusinessEntityDao instance = new BusinessEntityDao();

    private BusinessEntityDao() {}

    @Override
    public String getCollName() {
        return "business_entity";
    }

    @Override
    public Class<BusinessEntity> getClassT() {
        return BusinessEntity.class;
    }

    public List<BusinessEntity> findConfirmed(String scopeKey) {
        return getMCollection()
                .find(Filters.and(
                        Filters.eq(BusinessEntity.SCOPE_KEY, scopeKey),
                        Filters.eq(BusinessEntity.TIER, BusinessEntity.TIER_CONFIRMED)))
                .sort(Sorts.descending(BusinessEntity.API_COUNT))
                .into(new ArrayList<>());
    }
}
