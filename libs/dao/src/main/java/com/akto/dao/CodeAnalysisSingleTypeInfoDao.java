package com.akto.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CodeAnalysisApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;

public class CodeAnalysisSingleTypeInfoDao extends AccountsContextDaoWithRbac<SingleTypeInfo> {

    public static final CodeAnalysisSingleTypeInfoDao instance = new CodeAnalysisSingleTypeInfoDao();

    @Override
    public String getCollName() {
        return "code_analysis_single_type_infos";
    }

    @Override
    public Class<SingleTypeInfo> getClassT() {
        return SingleTypeInfo.class;
    }

    public Map<ApiInfo.ApiInfoKey, List<String>> fetchRequestParameters(List<ApiInfo.ApiInfoKey> apiInfoKeys) {
        Map<ApiInfo.ApiInfoKey, List<String>> result = new HashMap<>();
        if (apiInfoKeys == null || apiInfoKeys.isEmpty()) return result;

        /*
         * Since singleTypeInfo is the implemented class for this and SingleTypeInfoDao.
         * reusing the filters here.
         */
        List<Bson> pipeline = SingleTypeInfoDao.instance.createPipelineForFetchParams(apiInfoKeys, true, true);

        MongoCursor<BasicDBObject> stiCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (stiCursor.hasNext()) {
            BasicDBObject next = stiCursor.next();
            BasicDBObject id = (BasicDBObject) next.get("_id");
            Object paramsObj = next.get("params");
            List<String> params = new ArrayList<>();
            if (paramsObj instanceof List<?>) {
                for (Object param : (List<?>) paramsObj) {
                    if (param instanceof String) {
                        params.add((String) param);
                    }
                }
            }
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(id.getInt("apiCollectionId"), id.getString("url"), URLMethods.Method.fromString(id.getString("method")));
            result.put(apiInfoKey, params);
        }
        return result;
    } 
    
    public Map<ApiInfoKey, BasicDBObject> getReqResSchemaForApis(List<CodeAnalysisApiInfo> apiInfos, int apiCollectionId) {
        Map<ApiInfoKey, BasicDBObject> result = new HashMap<>();
        List<ApiInfoKey> apiInfoKeys = new ArrayList<>();
        for(CodeAnalysisApiInfo apiInfo: apiInfos){
            ApiInfoKey apiInfoKey = new ApiInfoKey(
                apiCollectionId,
                apiInfo.getId().getEndpoint(),
                URLMethods.Method.fromString(apiInfo.getId().getMethod())
            );  
            BasicDBObject obj = new BasicDBObject();
            obj.put("filePath", apiInfo.getLocation().getFilePath());
            obj.put("lineNumber", apiInfo.getLocation().getLineNo());
            result.put(apiInfoKey, obj);
            apiInfoKeys.add(apiInfoKey); 
        }
       
        if (apiInfoKeys == null || apiInfoKeys.isEmpty()) return result;
        List<Bson> pipeline = SingleTypeInfoDao.instance.createPipelineForFetchParams(apiInfoKeys, false, false);
        MongoCursor<BasicDBObject> stiCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (stiCursor.hasNext()) {
            BasicDBObject next = stiCursor.next();
            BasicDBObject id = (BasicDBObject) next.get("_id");
            int responseCode = id.getInt("responseCode");
            ApiInfoKey apiInfoKey = new ApiInfoKey(id.getInt("apiCollectionId"), id.getString("url"), URLMethods.Method.fromString(id.getString("method")));
            Object paramsObj = next.get("params");
            String schema = paramsObj.toString();
            BasicDBObject schemaObj = result.get(apiInfoKey);

            if(responseCode > -1){
                schemaObj.put("responseSchema", schema);
            }else{
                schemaObj.put("requestSchema", schema);
            }
            result.put(apiInfoKey, schemaObj);
        }
        return result;
    }

    @Override
    public String getFilterKeyString() {
        return SingleTypeInfo._API_COLLECTION_ID;
    }
}
