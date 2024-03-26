package com.akto.dao;

import com.akto.dto.CodeAnalysisCollection;

public class CodeAnalysisCollectionDao extends AccountsContextDao<CodeAnalysisCollection> {
    
    public static final CodeAnalysisCollectionDao instance = new CodeAnalysisCollectionDao();

    @Override
    public String getCollName() {
        return "code_analysis_collections";
    }

    @Override
    public Class<CodeAnalysisCollection> getClassT() {
        return CodeAnalysisCollection.class;
    }

}
