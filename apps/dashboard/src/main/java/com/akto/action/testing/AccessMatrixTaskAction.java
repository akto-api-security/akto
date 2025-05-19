package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.akto.dto.testing.AccessMatrixUrlToRole;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class AccessMatrixTaskAction extends UserAction{

    private static final LoggerMaker logger = new LoggerMaker(AccessMatrixTaskAction.class, LogDb.DASHBOARD);;

    private List<AccessMatrixTaskInfo> accessMatrixTaskInfos;
    private List<AccessMatrixUrlToRole> accessMatrixUrlToRoles;
    private Map<String,List<ApiInfoKey>> accessMatrixRoleToUrls = new HashMap<>();
    private String roleName;
    private List<Integer> apiCollectionIds;
    private int frequencyInSeconds;
    private String hexId;
    public String fetchAccessMatrixTaskInfos(){
        accessMatrixTaskInfos = AccessMatrixTaskInfosDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String fetchAccessMatrixUrlToRoles(){
        accessMatrixUrlToRoles = AccessMatrixUrlToRolesDao.instance.findAll(new BasicDBObject());
        for(AccessMatrixUrlToRole urlToRole: accessMatrixUrlToRoles){
            for(String role:urlToRole.getRoles()){
                if(accessMatrixRoleToUrls.containsKey(role)){
                    accessMatrixRoleToUrls.get(role).add(urlToRole.getId());
                } else {
                    accessMatrixRoleToUrls.put(role, new ArrayList<>(Collections.singletonList(urlToRole.getId())));
                }
            }
        }
        return SUCCESS.toUpperCase();
    }

    private boolean sanityCheck(){
        if (frequencyInSeconds <= 0) {
            frequencyInSeconds = 86400;
        }
        return true;
    }

    public String deleteAccessMatrix() {
        logger.debugAndAddToDb("started deleting access details for: " + roleName, LoggerMaker.LogDb.DASHBOARD);

        String endpointLogicalGroupName = roleName + EndpointLogicalGroup.GROUP_NAME_SUFFIX;
        Bson taskInfoFilterQ = Filters.eq(AccessMatrixTaskInfo.ENDPOINT_LOGICAL_GROUP_NAME, endpointLogicalGroupName);
        DeleteResult deleteResult = AccessMatrixTaskInfosDao.instance.deleteAll(taskInfoFilterQ);
        logger.debugAndAddToDb("Deleted AccessMatrixTaskInfo for: " + roleName + " : " + deleteResult, LoggerMaker.LogDb.DASHBOARD);

        Bson urlToRolesUpdateQ = Updates.pull(AccessMatrixUrlToRole.ROLES, roleName);
        UpdateResult updateResult = AccessMatrixUrlToRolesDao.instance.updateMany(Filters.empty(), urlToRolesUpdateQ);
        logger.debugAndAddToDb("Deleted AccessMatrixUrlToRoles for: " + roleName + " : " +  updateResult, LoggerMaker.LogDb.DASHBOARD);

        return SUCCESS.toUpperCase();
    }

    public String createMultipleAccessMatrixTasks(){
        List<WriteModel<AccessMatrixTaskInfo>> writes = new ArrayList<>();
        String endpointLogicalGroupName = roleName + EndpointLogicalGroup.GROUP_NAME_SUFFIX;

        Bson filter = Filters.eq(AccessMatrixTaskInfo.ENDPOINT_LOGICAL_GROUP_NAME, endpointLogicalGroupName);

        Bson update = Updates.combine(
                Updates.set(AccessMatrixTaskInfo.ENDPOINT_LOGICAL_GROUP_NAME, endpointLogicalGroupName),
                Updates.set(AccessMatrixTaskInfo.FREQUENCY_IN_SECONDS, 86400),
                Updates.set(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now()));
        UpdateOptions opts = new UpdateOptions().upsert(true);

        writes.add(new UpdateOneModel<>(filter, update,opts));
        AccessMatrixTaskInfosDao.instance.getMCollection().bulkWrite(writes);

        return SUCCESS.toUpperCase();
    }

    private List<String> headerNames;

    private Map<String, Map<String, Integer>> headerValues;
    public String analyzeApiSamples(){
        if(apiCollectionIds==null || apiCollectionIds.isEmpty()){
            addActionError("No endpoints found to analyze API samples");
            return ERROR.toUpperCase();
        }

        if(headerNames == null || headerNames.isEmpty()){
            addActionError("No header name was provided");
            return ERROR.toUpperCase();
        }

        headerValues = new HashMap<>();
        int numSamples = 0;
        for (int collectionId : apiCollectionIds) {
            List<SampleData> sampleDataList = new ArrayList<>();
            String lastFetchedUrl = null, lastFetchedMethod = null;
            int limit = 1000, sliceLimit = 10;
            boolean isListEmpty = false;
            do {
                sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(collectionId, lastFetchedUrl, lastFetchedMethod, limit, sliceLimit);

                for (SampleData sd : sampleDataList) {
                    for (String sampleStr : sd.getSamples()) {
                        try {
                            HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sampleStr);
                            numSamples++;
                            for (String headerName : headerNames) {
                                List<String> headerValue = httpResponseParams.getRequestParams().getHeaders().get(headerName);
                                if (headerValue == null) {
                                    continue;
                                }
                                Map<String, Integer> recordedValues = headerValues.get(headerName);
                                if (recordedValues == null) {
                                    recordedValues = new HashMap<>();
                                    headerValues.put(headerName, recordedValues);
                                }

                                for(String headerValueFound: headerValue) {
                                    int currCounter = recordedValues.getOrDefault(headerValueFound, 0);
                                    recordedValues.put(headerValueFound, currCounter+1);
                                }
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                isListEmpty = sampleDataList != null && !sampleDataList.isEmpty();
                if (!isListEmpty) {
                    Key id = sampleDataList.get(sampleDataList.size() - 1).getId();
                    lastFetchedMethod = id.getMethod().name();
                    lastFetchedUrl = id.getUrl();
                }
            } while (!isListEmpty && numSamples < 50_000);

        }
        logger.debug("numSamples= " + numSamples);
        return SUCCESS.toUpperCase();
    }

    public String updateAccessMatrixTask(){
        if (!sanityCheck()) {
            return ERROR.toUpperCase();
        }
        try{
            ObjectId id = new ObjectId(hexId);
            Bson q = Filters.eq(Constants.ID, id);
            String endpointLogicalGroupName = roleName + EndpointLogicalGroup.GROUP_NAME_SUFFIX;

            Bson update = Updates.combine(
                Updates.set(AccessMatrixTaskInfo.ENDPOINT_LOGICAL_GROUP_NAME,endpointLogicalGroupName),
                Updates.set(AccessMatrixTaskInfo.FREQUENCY_IN_SECONDS,frequencyInSeconds)
            );
            UpdateOptions opts = new UpdateOptions().upsert(true);
            AccessMatrixTaskInfosDao.instance.getMCollection().updateOne(q, update, opts);
        } catch (Exception e) {
            addActionError("invalid request");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteAccessMatrixTask(){
        try {
            ObjectId id = new ObjectId(hexId);
            Bson q = Filters.eq(Constants.ID, id);
            AccessMatrixTaskInfosDao.instance.deleteAll(q);
        } catch (Exception e) {
            addActionError("unable to delete");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public List<AccessMatrixTaskInfo> getAccessMatrixTaskInfos() {
        return accessMatrixTaskInfos;
    }
    public void setAccessMatrixTaskInfos(List<AccessMatrixTaskInfo> accessMatrixTaskInfos) {
        this.accessMatrixTaskInfos = accessMatrixTaskInfos;
    }
    public List<AccessMatrixUrlToRole> getAccessMatrixUrlToRoles() {
        return accessMatrixUrlToRoles;
    }
    public void setAccessMatrixUrlToRoles(List<AccessMatrixUrlToRole> accessMatrixUrlToRoles) {
        this.accessMatrixUrlToRoles = accessMatrixUrlToRoles;
    }
    public Map<String, List<ApiInfoKey>> getAccessMatrixRoleToUrls() {
        return accessMatrixRoleToUrls;
    }
    public void setAccessMatrixRoleToUrls(Map<String, List<ApiInfoKey>> accessMatrixRoleToUrls) {
        this.accessMatrixRoleToUrls = accessMatrixRoleToUrls;
    }
    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }
    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }
    public int getFrequencyInSeconds() {
        return frequencyInSeconds;
    }
    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public String getHexId() {
        return hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }

    public void setHeaderNames(List<String> headerNames) {
        this.headerNames = headerNames;
    }

    public Map<String, Map<String, Integer>> getHeaderValues() {
        return headerValues;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}