package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.EndpointShieldSettings;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class EndpointShieldSettingsDao extends AccountsContextDao<EndpointShieldSettings> {

    public static final EndpointShieldSettingsDao instance = new EndpointShieldSettingsDao();
    private EndpointShieldSettingsDao() {}

    @Override
    public String getCollName() {
        return "endpoint_shield_settings";
    }

    @Override
    public Class<EndpointShieldSettings> getClassT() {
        return EndpointShieldSettings.class;
    }

    public static Bson generateFilter() {
        return Filters.eq("_id", Context.accountId.get());
    }
}
