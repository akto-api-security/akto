package com.akto.action.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.action.ApiCollectionsAction;
import com.akto.action.CustomDataTypeAction;
import com.akto.action.UserAction;
import com.akto.action.CustomDataTypeAction.ConditionFromUser;
import com.akto.dao.CodeAnalysisApiInfoDao;
import com.akto.dao.CodeAnalysisCollectionDao;
import com.akto.dao.CodeAnalysisSingleTypeInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.CodeAnalysisApiInfo;
import com.akto.dto.CodeAnalysisCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.data_types.Predicate.Type;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;

public class AgentHelperAction extends UserAction {

    int skip;
    int apiCollectionId;
    int limit;
    SampleData sample;

    public String fetchAllResponsesForApiCollectionOrdered() {

        limit = Math.min(Math.max(1, limit), 10);
        skip = Math.max(0, skip);

        List<SampleData> sampleData = SampleDataDao.instance.findAll(Filters.eq(
                "_id.apiCollectionId", apiCollectionId), skip, limit, Sorts.descending("_id"));

        if (sampleData.isEmpty()) {
            addActionError("sample data not found");
            return Action.ERROR.toUpperCase();
        }

        /*
         * TODO: optimise this to send only samples which are actually different, 
         * i.e. contain different parameters
         */
        sample = sampleData.get(0);
        return Action.SUCCESS.toUpperCase();
    }

    List<String> dataTypeKeys;

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String createSensitiveResponseDataTypes(){

        int accountId = Context.accountId.get();
        Map<String, Object> session = getSession();

        executorService.schedule( new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(accountId);
                    for(String datatype: dataTypeKeys){
                        String formattedDataType = datatype.substring(0, Math.min(24, datatype.length()));
                        Map<String, Object> valueMap = new HashMap<>();
                        valueMap.put("value", datatype);
                        ConditionFromUser conditionFromUser = new ConditionFromUser(Type.EQUALS_TO, valueMap);
                        CustomDataTypeAction customDataTypeAction = new CustomDataTypeAction();
                        customDataTypeAction.setKeyConditionFromUsers(Arrays.asList(conditionFromUser));
                        customDataTypeAction.setKeyOperator("OR");
                        customDataTypeAction.setValueOperator("OR");
                        customDataTypeAction.setOperator("OR");
                        customDataTypeAction.setName(formattedDataType);
                        customDataTypeAction.setRedacted(false);
                        customDataTypeAction.setSensitiveAlways(false);
                        customDataTypeAction.setSensitivePosition(Arrays.asList("RESPONSE_PAYLOAD", "RESPONSE_HEADER"));
                        customDataTypeAction.setActive(true);
                        customDataTypeAction.setCreateNew(true);
                        customDataTypeAction.setDataTypePriority(Severity.MEDIUM);
                        customDataTypeAction.setSession(session);   
                        customDataTypeAction.execute();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0 , TimeUnit.SECONDS);
        return Action.SUCCESS.toUpperCase();
    }

    List<BasicDBObject> apiGroupList;
    
    public List<BasicDBObject> getApiGroupList() {
        return apiGroupList;
    }

    public void setApiGroupList(List<BasicDBObject> apiGroupList) {
        this.apiGroupList = apiGroupList;
    }

    public String createAPIGroups() {

        int accountId = Context.accountId.get();

        // TODO: subprocessId , attemptID, processId -> apiGroupList

        executorService.schedule(new Runnable() {
            public void run() {
                try {
                    Context.accountId.set(accountId);

                    for(BasicDBObject apiGroupObject: apiGroupList){
                        String apiGroupName = apiGroupObject.getString("apiGroupName");
                        List<ApiInfoKey> apiList = (List<ApiInfoKey>) apiGroupObject.get("apiList");

                        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
                        apiCollectionsAction.setCollectionName(apiGroupName);
                        apiCollectionsAction.setApiList(apiList);
                        apiCollectionsAction.addApisToCustomCollection();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.SECONDS);
        return Action.SUCCESS.toUpperCase();
    }

    String chosenBackendDirectory;
    List<BasicDBObject> codeAnalysisCollectionIdList;

    public String getSourceCodeCollectionsForDirectories(){

        List<Bson> pipeLine = new ArrayList<>();
        String regexPattern = "^" + this.chosenBackendDirectory + ".*";
        pipeLine.add(Aggregates.match(Filters.regex("location.filePath", regexPattern)));
        pipeLine.add(
            Aggregates.group("$_id.codeAnalysisCollectionId", Accumulators.sum("count", 1))
        );

        this.codeAnalysisCollectionIdList = new ArrayList<>();
        Map<ObjectId, Integer> countMap = new HashMap<>();
        MongoCursor<BasicDBObject> cursor = CodeAnalysisApiInfoDao.instance.getMCollection().aggregate(pipeLine, BasicDBObject.class).cursor();
        while(cursor.hasNext()) {
            try {
                BasicDBObject basicDBObject = cursor.next();
                ObjectId id = basicDBObject.getObjectId("_id");
                int count = basicDBObject.getInt("count");
                countMap.put(id, count);
            } catch (Exception e) {
                e.printStackTrace();
                return Action.ERROR.toUpperCase();
            }
        }

        if(!countMap.isEmpty()){
            List<CodeAnalysisCollection> collections = CodeAnalysisCollectionDao.instance.findAll(Filters.in(Constants.ID, countMap.keySet()), Projections.include("name"));
            for(CodeAnalysisCollection codeAnalysisCollection: collections){
               BasicDBObject basicDBObject = new BasicDBObject();
                basicDBObject.put("id", codeAnalysisCollection.getId());
                basicDBObject.put("name", codeAnalysisCollection.getName());
                basicDBObject.put("count", countMap.get(codeAnalysisCollection.getId()));
                this.codeAnalysisCollectionIdList.add(basicDBObject);
            }
        }

        return SUCCESS.toUpperCase();
    }

    List<String> chosenCodeAnalysisCollectionIds;
    List<Map<ApiInfoKey, BasicDBObject>> apiInfoKeysWithSchema;

    public String getApisForChosenCollectionForSourceCode(){
        this.apiInfoKeysWithSchema = new ArrayList<>();
        for(String hexId: chosenCodeAnalysisCollectionIds){
            ObjectId objectId = new ObjectId(hexId);
            CodeAnalysisCollection codeAnalysisCollection = CodeAnalysisCollectionDao.instance.findOne(Filters.eq(Constants.ID, objectId));
            List<CodeAnalysisApiInfo> apiInfos = CodeAnalysisApiInfoDao.instance.findAll(Filters.eq("codeAnalysisCollectionId", objectId), Projections.include("id"));
            List<ApiInfoKey> apiInfoKeys = new ArrayList<>();
            for(CodeAnalysisApiInfo apiInfo: apiInfos){
                ApiInfoKey apiInfoKey = new ApiInfoKey(
                    codeAnalysisCollection.getApiCollectionId(),
                    apiInfo.getId().getEndpoint(),
                    URLMethods.Method.fromString(apiInfo.getId().getMethod())
                );  
                apiInfoKeys.add(apiInfoKey); 
            }
            Map<ApiInfoKey, BasicDBObject> schemaMap = CodeAnalysisSingleTypeInfoDao.instance.getReqResSchemaForApis(apiInfoKeys);
            apiInfoKeysWithSchema.add(schemaMap);
        }
        return SUCCESS.toUpperCase();
    }

    public List<String> getDataTypeKeys() {
        return dataTypeKeys;
    }

    public void setDataTypeKeys(List<String> dataTypeKeys) {
        this.dataTypeKeys = dataTypeKeys;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public SampleData getSample() {
        return sample;
    }

    public void setSample(SampleData sample) {
        this.sample = sample;
    }

    public List<BasicDBObject> getCodeAnalysisCollectionIdList() {
        return codeAnalysisCollectionIdList;
    }

    public void setChosenBackendDirectory(String chosenBackendDirectory) {
        this.chosenBackendDirectory = chosenBackendDirectory;
    }

    public void setChosenCodeAnalysisCollectionIds(List<String> chosenCodeAnalysisCollectionIds) {
        this.chosenCodeAnalysisCollectionIds = chosenCodeAnalysisCollectionIds;
    }

    public List<Map<ApiInfoKey, BasicDBObject>> getApiInfoKeysWithSchema() {
        return apiInfoKeysWithSchema;
    }
}
