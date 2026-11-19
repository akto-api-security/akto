package com.akto.dao.agents;

import com.akto.dao.CommonContextDao;
import com.akto.dto.agents.LlmServiceConfig;
import com.mongodb.client.model.Filters;

public class LlmServiceConfigDao extends CommonContextDao<LlmServiceConfig> {

    public static final LlmServiceConfigDao instance = new LlmServiceConfigDao();

    private LlmServiceConfigDao() {}

    public LlmServiceConfig findByService(String service) {
        return findOne(Filters.eq("_id", service));
    }

    @Override
    public String getCollName() {
        return "llm_service_config";
    }

    @Override
    public Class<LlmServiceConfig> getClassT() {
        return LlmServiceConfig.class;
    }
}
