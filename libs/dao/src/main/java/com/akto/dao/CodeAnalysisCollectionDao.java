package com.akto.dao;

import com.akto.dto.ApiCollection;
import com.akto.dto.CodeAnalysisCollection;
import com.akto.dto.usage.UsageMetric;

public class CodeAnalysisCollectionDao extends AccountsContextDao<CodeAnalysisCollection> {
    
    public static final CodeAnalysisCollectionDao instance = new CodeAnalysisCollectionDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { CodeAnalysisCollection.NAME }, true);
    }

    @Override
    public String getCollName() {
        return "code_analysis_collections";
    }

    @Override
    public Class<CodeAnalysisCollection> getClassT() {
        return CodeAnalysisCollection.class;
    }

}
