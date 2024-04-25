package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.akto.dto.CodeAnalysisApiInfo;
import com.akto.dto.CodeAnalysisCollection;

public class CodeAnalysisApiInfoDao extends AccountsContextDao<CodeAnalysisApiInfo> {
    
    public static final CodeAnalysisApiInfoDao instance = new CodeAnalysisApiInfoDao();

    public void createIndicesIfAbsent() {
        String[] fieldNames = {"_id." + CodeAnalysisApiInfo.CodeAnalysisApiInfoKey.CODE_ANALYSIS_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    @Override
    public String getCollName() {
        return "code_analysis_api_infos";
    }

    @Override
    public Class<CodeAnalysisApiInfo> getClassT() {
        return CodeAnalysisApiInfo.class;
    }

}
