package com.akto.dao;

import com.akto.dto.gpt.AktoGptConfig;

public class AktoGptConfigDao extends AccountsContextDao<AktoGptConfig>{

    public static final AktoGptConfigDao instance = new AktoGptConfigDao();

    @Override
    public String getCollName() {
        return "akto_gpt_config";
    }

    @Override
    public Class<AktoGptConfig> getClassT() {
        return AktoGptConfig.class;
    }
}
