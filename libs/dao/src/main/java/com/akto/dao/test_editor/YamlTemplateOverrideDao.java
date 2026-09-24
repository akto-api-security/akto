package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.YamlTemplate;

public class YamlTemplateOverrideDao extends AccountsContextDao<YamlTemplate> {
    public static final YamlTemplateOverrideDao instance = new YamlTemplateOverrideDao();

    @Override
    public String getCollName() {
        return "yaml_template_overrides";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}
