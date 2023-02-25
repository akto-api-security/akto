package com.akto.dao;

import com.akto.dto.IgnoredEndpoint;

public class IgnoredEndpointDao extends AccountsContextDao<IgnoredEndpoint> {
    public static IgnoredEndpointDao instance = new IgnoredEndpointDao();
    @Override
    public String getCollName() {
        return "ignored_endpoints";
    }

    @Override
    public Class<IgnoredEndpoint> getClassT() {
        return IgnoredEndpoint.class;
    }
}