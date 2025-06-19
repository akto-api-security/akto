package com.akto.dao.test_editor;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.test_editor.YamlTemplate;

public class CommonTemplateDao extends AccountsContextDao<YamlTemplate> {
    public static final CommonTemplateDao instance = new CommonTemplateDao();

    @Override
    public String getCollName() {
        return "common_templates";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }

}
