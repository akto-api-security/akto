package com.akto.dao;

import com.akto.dto.SensitiveParamInfo;

public class SensitiveParamInfoDao extends AccountsContextDao<SensitiveParamInfo> {

    public static final SensitiveParamInfoDao instance = new SensitiveParamInfoDao();

    @Override
    public String getCollName() {
        return "sensitive_param_info";
    }

    @Override
    public Class<SensitiveParamInfo> getClassT() {
        return SensitiveParamInfo.class;
    }
}
