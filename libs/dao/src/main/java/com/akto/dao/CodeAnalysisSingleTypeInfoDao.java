package com.akto.dao;

import com.akto.dto.type.SingleTypeInfo;

public class CodeAnalysisSingleTypeInfoDao extends AccountsContextDao<SingleTypeInfo> {

    public static final CodeAnalysisSingleTypeInfoDao instance = new CodeAnalysisSingleTypeInfoDao();

    @Override
    public String getCollName() {
        return "code_analysis_single_type_infos";
    }

    @Override
    public Class<SingleTypeInfo> getClassT() {
        return SingleTypeInfo.class;
    }
}
