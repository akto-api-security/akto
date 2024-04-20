package com.akto.dao;

import com.akto.dto.CodeAnalysisApiInfo;

public class CodeAnalysisApiInfoDao extends AccountsContextDao<CodeAnalysisApiInfo> {
    
    public static final CodeAnalysisApiInfoDao instance = new CodeAnalysisApiInfoDao();

    @Override
    public String getCollName() {
        return "code_analysis_api_infos";
    }

    @Override
    public Class<CodeAnalysisApiInfo> getClassT() {
        return CodeAnalysisApiInfo.class;
    }

}
