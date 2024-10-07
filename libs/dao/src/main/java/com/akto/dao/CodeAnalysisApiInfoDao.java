package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.akto.dto.CodeAnalysisApiInfo;
import com.akto.dto.CodeAnalysisCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeAnalysisApiInfoDao extends AccountsContextDao<CodeAnalysisApiInfo> {
    
    public static final CodeAnalysisApiInfoDao instance = new CodeAnalysisApiInfoDao();

    public void createIndicesIfAbsent() {
        String[] fieldNames = {"_id." + CodeAnalysisApiInfo.CodeAnalysisApiInfoKey.CODE_ANALYSIS_COLLECTION_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

    public Map<String, Integer> getUrlsCount() {
        Map<String, Integer> countMap = new HashMap<>();
        List<Bson> pipeline = new ArrayList<>();

        BasicDBObject groupedId = new BasicDBObject("codeAnalysisCollectionId", "$_id.codeAnalysisCollectionId");
        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count",1)));

        MongoCursor<BasicDBObject> endpointsCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while(endpointsCursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = endpointsCursor.next();
                BasicDBObject id = (BasicDBObject) basicDBObject.get("_id");
                ObjectId codeAnalysisCollectionId = (ObjectId) id.get("codeAnalysisCollectionId");
                int count = basicDBObject.getInt("count");
                countMap.put(codeAnalysisCollectionId.toHexString(), count);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return countMap;
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
