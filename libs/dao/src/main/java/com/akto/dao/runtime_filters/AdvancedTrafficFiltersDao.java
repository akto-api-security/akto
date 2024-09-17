package com.akto.dao.runtime_filters;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.YamlTemplate;

public class AdvancedTrafficFiltersDao extends AccountsContextDao<YamlTemplate> {

    public static final AdvancedTrafficFiltersDao instance = new AdvancedTrafficFiltersDao();

    @Override
    public String getCollName() {
        return "advanced_traffic_filters";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}
