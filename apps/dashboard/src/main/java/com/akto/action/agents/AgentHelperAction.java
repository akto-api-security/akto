package com.akto.action.agents;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.action.ApiCollectionsAction;
import com.akto.action.CustomDataTypeAction;
import com.akto.action.UserAction;
import com.akto.action.CustomDataTypeAction.ConditionFromUser;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.data_types.Predicate.Type;
import com.akto.dto.traffic.SampleData;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
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
        Map<String, Object> session = getSession();

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
}
