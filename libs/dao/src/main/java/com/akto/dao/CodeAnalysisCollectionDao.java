package com.akto.dao;

import com.akto.dto.ApiCollection;
import com.akto.dto.CodeAnalysisCollection;
import com.akto.dto.usage.UsageMetric;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeAnalysisCollectionDao extends AccountsContextDao<CodeAnalysisCollection> {
    
    public static final CodeAnalysisCollectionDao instance = new CodeAnalysisCollectionDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { CodeAnalysisCollection.NAME }, true);
    }

    public Map<String, Integer> findIdToCollectionNameMap() {
        Map<String, Integer> idToCollectionNameMap = new HashMap<>();
        List<CodeAnalysisCollection> codeAnalysisCollections = instance.findAll(Filters.exists(CodeAnalysisCollection.API_COLLECTION_ID));
        for (CodeAnalysisCollection codeAnalysisCollection: codeAnalysisCollections) {
            idToCollectionNameMap.put(codeAnalysisCollection.getId().toHexString(), codeAnalysisCollection.getApiCollectionId());
        }

        return idToCollectionNameMap;
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
