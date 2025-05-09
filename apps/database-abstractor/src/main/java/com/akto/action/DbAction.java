package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DbAction extends ActionSupport {

    long count;
    List<CustomDataTypeMapper> customDataTypes;
    List<AktoDataType> aktoDataTypes;
    List<CustomAuthTypeMapper> customAuthTypes;
    AccountSettings accountSettings;
    List<ApiInfo> apiInfos;
    APIConfig apiConfig;
    List<SingleTypeInfo> stis;
    List<Integer> apiCollectionIds;
    List<RuntimeFilter> runtimeFilters;
    List<SensitiveParamInfo> sensitiveParamInfos;
    Account account;
    int vxlanId;
    String name;
    List<String> cidrList;
    List<BasicDBObject> apiInfoList;
    List<BulkUpdates> writesForFilterSampleData;
    List<BulkUpdates> writesForSti;
    List<BulkUpdates> writesForSampleData;
    List<BulkUpdates> writesForSensitiveSampleData;
    List<BulkUpdates> writesForSensitiveParamInfo;
    List<BulkUpdates> writesForTrafficInfo;
    List<BulkUpdates> writesForTrafficMetrics;
    String configName;
    int lastFetchTimestamp;
    String lastSeenObjectId;
    boolean resolveLoop;
    String fieldName;
    String version;
    String currentInstanceIp;
    List<ApiInfo.ApiInfoKey> endpoints;
    List<ApiCollection> apiCollections;
    String host;
    int colId;
    int accountId;
    BasicDBObject log;
    boolean isHybridSaas;
    Setup setup;
    Organization organization;
    List<BasicDBObject> apiHitCountInfoList;

    private static final Gson gson = new Gson();
    ObjectMapper objectMapper = new ObjectMapper();
    private static final LoggerMaker logger = new LoggerMaker(DbAction.class);

    public String fetchCustomDataTypes() {
        try {
            List<CustomDataType> data = DbLayer.fetchCustomDataTypes();
            List<CustomDataTypeMapper> customDataTypeMappers = new ArrayList<>();
            for (CustomDataType customDataType: data) {
                customDataTypeMappers.add(new CustomDataTypeMapper(customDataType));
            }
            customDataTypes = customDataTypeMappers;
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAktoDataTypes() {
        try {
            aktoDataTypes = DbLayer.fetchAktoDataTypes();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchCustomAuthTypes() {
        try {
            List<CustomAuthType> data = DbLayer.fetchCustomAuthTypes();
            List<CustomAuthTypeMapper> customAuthTypeMappers = new ArrayList<>();
            for (CustomAuthType customAuthType: data) {
                customAuthTypeMappers.add(new CustomAuthTypeMapper(customAuthType));
            }
            customAuthTypes = customAuthTypeMappers;
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateApiCollectionNameForVxlan() {
        try {
            DbLayer.updateApiCollectionName(vxlanId, name);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateCidrList() {
        try {
            DbLayer.updateCidrList(cidrList);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAccountSettings() {
        try {
            int accountId = Context.accountId.get();
            accountSettings = DbLayer.fetchAccountSettings(accountId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiInfos() {
        try {
            apiInfos = DbLayer.fetchApiInfos();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchNonTrafficApiInfos() {
        try {
            apiInfos = DbLayer.fetchNonTrafficApiInfos();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteApiInfo() {
        try {
            List<ApiInfo> apiInfos = new ArrayList<>();
            for (BasicDBObject obj: apiInfoList) {
                ApiInfo apiInfo = objectMapper.readValue(obj.toJson(), ApiInfo.class);
                apiInfos.add(apiInfo);
            }
            DbLayer.bulkWriteApiInfo(apiInfos);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSti() {
        System.out.println("bulkWriteSti called");
        try {
            ArrayList<WriteModel<SingleTypeInfo>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForSti) {
                List<Bson> filters = new ArrayList<>();
                for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("isUrlParam")) {
                        continue;
                    }
                    if (entry.getKey().equalsIgnoreCase("apiCollectionId") || entry.getKey().equalsIgnoreCase("responseCode")) {
                        Long val = (Long) entry.getValue();
                        filters.add(Filters.eq(entry.getKey(), val.intValue()));
                    } else {
                        filters.add(Filters.eq(entry.getKey(), entry.getValue()));
                    }
                }
                List<Boolean> urlParamQuery;
                if ((Boolean) bulkUpdate.getFilters().get("isUrlParam") == true) {
                    urlParamQuery = Collections.singletonList(true);
                } else {
                    urlParamQuery = Arrays.asList(false, null);
                }
                filters.add(Filters.in("isUrlParam", urlParamQuery));
                List<String> updatePayloadList = bulkUpdate.getUpdates();

                List<Bson> updates = new ArrayList<>();
                boolean isDeleteWrite = false;
                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);
                    String operation = (String) json.get("op");
                    if (operation.equalsIgnoreCase("delete")) {
                        isDeleteWrite = true;
                    } else {
                        String field = (String) json.get("field");
                        Double val = (Double) json.get("val");
                        if (field.equalsIgnoreCase("count")) {
                            updates.add(Updates.inc(field, val.intValue()));
                        } else if (field.equalsIgnoreCase("timestamp")) {
                            updates.add(Updates.setOnInsert(field, val.intValue()));
                        } else if (field.equalsIgnoreCase(SingleTypeInfo.LAST_SEEN)) {
                            updates.add(Updates.max(field, val.longValue()));
                        } else if (field.equalsIgnoreCase(SingleTypeInfo.MAX_VALUE)) {
                            updates.add(Updates.max(field, val.longValue()));
                        } else if (field.equalsIgnoreCase(SingleTypeInfo.MIN_VALUE)) {
                            updates.add(Updates.min(field, val.longValue()));
                        } else if (field.equalsIgnoreCase("collectionIds")) {
                            updates.add(Updates.setOnInsert(field, Arrays.asList(val.intValue())));
                        }
                    }
                }

                System.out.println(filters);

                if (isDeleteWrite) {
                    writes.add(
                            new DeleteOneModel<>(Filters.and(filters), new DeleteOptions())
                    );
                } else {
                    writes.add(
                            new UpdateOneModel<>(Filters.and(filters), Updates.combine(updates), new UpdateOptions().upsert(true))
                    );
                }
            }

            DbLayer.bulkWriteSingleTypeInfo(writes);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSampleData() {
        try {
            System.out.println("called");
            ArrayList<WriteModel<SampleData>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForSampleData) {
                Map<String, Object> mObj = (Map) bulkUpdate.getFilters().get("_id");
                Long apiCollectionId = (Long) mObj.get("apiCollectionId");
                Long bucketEndEpoch = (Long) mObj.get("bucketEndEpoch");
                Long bucketStartEpoch = (Long) mObj.get("bucketStartEpoch");
                Long responseCode = (Long) mObj.get("responseCode");

                Bson filters = Filters.and(Filters.eq("_id.apiCollectionId", apiCollectionId.intValue()),
                        Filters.eq("_id.bucketEndEpoch", bucketEndEpoch.intValue()),
                        Filters.eq("_id.bucketStartEpoch", bucketStartEpoch.intValue()),
                        Filters.eq("_id.method", mObj.get("method")),
                        Filters.eq("_id.responseCode", responseCode.intValue()),
                        Filters.eq("_id.url", mObj.get("url")));
                List<String> updatePayloadList = bulkUpdate.getUpdates();

                List<Bson> updates = new ArrayList<>();
                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);
                    String field = (String) json.get("field");
                    UpdatePayload updatePayload;
                    if (field.equals("collectionIds")) {
                        List<Double> dVal = (List) json.get("val");
                        List<Integer> val = new ArrayList<>();
                        for (int i = 0; i < dVal.size(); i++) {
                            val.add(dVal.get(i).intValue());
                        }
                        updates.add(Updates.setOnInsert(field, val));
                    } else {
                        List<String> dVal = (List) json.get("val");
                        updatePayload = new UpdatePayload((String) json.get("field"), dVal , (String) json.get("op"));
                        updates.add(Updates.pushEach(updatePayload.getField(), dVal, new PushOptions().slice(-10)));
                    }
                }
                writes.add(
                        new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                );
            }
            DbLayer.bulkWriteSampleData(writes);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSensitiveSampleData() {
        try {
            System.out.println("bulkWriteSensitiveSampleData called");
            ArrayList<WriteModel<SensitiveSampleData>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForSensitiveSampleData) {
                Bson filters = Filters.empty();
                for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("_id.apiCollectionId") || entry.getKey().equalsIgnoreCase("_id.responseCode")) {
                        Long val = (Long) entry.getValue();
                        filters = Filters.and(filters, Filters.eq(entry.getKey(), val.intValue()));
                    } else {
                        filters = Filters.and(filters, Filters.eq(entry.getKey(), entry.getValue()));
                    }
                }
                List<String> updatePayloadList = bulkUpdate.getUpdates();

                boolean isDeleteWrite = false;
                List<Bson> updates = new ArrayList<>();

                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);
                    String operation = (String) json.get("op");
                    if (operation.equalsIgnoreCase("delete")) {
                        isDeleteWrite = true;
                    } else {
                        String field = (String) json.get("field");
                        Bson bson;
                        if (field.equals("collectionIds")) {
                            List<Double> dVal = (List) json.get("val");
                            List<Integer> val = new ArrayList<>();
                            for (int i = 0; i < dVal.size(); i++) {
                                val.add(dVal.get(i).intValue());
                            }
                            
                            bson = Updates.setOnInsert(field, val);
                        } else {
                            bson = Updates.pushEach((String) json.get("field"), (ArrayList) json.get("val"), new PushOptions().slice(-1 * SensitiveSampleData.cap));
                        }    
                        updates.add(bson);
                    }
                }

                if (isDeleteWrite) {
                    writes.add(
                        new DeleteOneModel<>(filters, new DeleteOptions())
                    );
                } else {
                    writes.add(
                        new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                    );
                }
            }
            DbLayer.bulkWriteSensitiveSampleData(writes);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTrafficInfo() {
        try {
            System.out.println("bulkWriteTrafficInfo called");
            ArrayList<WriteModel<TrafficInfo>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForTrafficInfo) {
                Bson filters = Filters.eq("_id", bulkUpdate.getFilters().get("_id"));
                List<String> updatePayloadList = bulkUpdate.getUpdates();

                List<Bson> updates = new ArrayList<>();                
                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);

                    String field = (String) json.get("field");
                    if (field.equals("collectionIds")) {
                        List<Double> dVal = (List) json.get("val");
                        List<Integer> val = new ArrayList<>();
                        for (int i = 0; i < dVal.size(); i++) {
                            val.add(dVal.get(i).intValue());
                        }
                        updates.add(Updates.setOnInsert(field, val));
                    } else {
                        Double dVal = (Double) json.get("val");
                        UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal.intValue() , (String) json.get("op"));
                        updates.add(Updates.inc(updatePayload.getField(), (Integer) updatePayload.getVal()));
                    }
                }

                writes.add(
                        new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                );
            }
            DbLayer.bulkWriteTrafficInfo(writes);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTrafficMetrics() {
        try {
            System.out.println("bulkWriteTrafficInfo called");
            ArrayList<WriteModel<TrafficMetrics>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForTrafficMetrics) {
                
                Bson filters = Filters.empty();
                for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                    if (entry.getKey().equalsIgnoreCase("_id.bucketStartEpoch") || entry.getKey().equalsIgnoreCase("_id.bucketEndEpoch") || entry.getKey().equalsIgnoreCase("_id.vxlanID")) {
                        Long val = (Long) entry.getValue();
                        filters = Filters.and(filters, Filters.eq(entry.getKey(), val.intValue()));
                    } else {
                        filters = Filters.and(filters, Filters.eq(entry.getKey(), entry.getValue()));
                    }
                }

                //Bson filters = Filters.eq("_id", bulkUpdate.getFilters().get("_id"));
                List<String> updatePayloadList = bulkUpdate.getUpdates();

                List<Bson> updates = new ArrayList<>();                
                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);
                    Double dVal = (Double) json.get("val");
                    UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal.intValue() , (String) json.get("op"));
                    updates.add(Updates.inc(updatePayload.getField(), (Integer) updatePayload.getVal()));
                }

                writes.add(
                        new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                );
            }
            DbLayer.bulkWriteTrafficMetrics(writes);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSensitiveParamInfo() {
        try {
            ArrayList<WriteModel<SensitiveParamInfo>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForSensitiveParamInfo) {
                Bson filters = Filters.empty();
                for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                    filters = Filters.and(filters, Filters.eq(entry.getKey(), entry.getValue()));
                }
                List<String> updatePayloadList = bulkUpdate.getUpdates();

                List<Bson> updates = new ArrayList<>();
                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);

                    String field = (String) json.get("field");
                    if (field.equals("collectionIds")) {
                        List<Double> dVal = (List) json.get("val");
                        List<Integer> val = new ArrayList<>();
                        for (int i = 0; i < dVal.size(); i++) {
                            val.add(dVal.get(i).intValue());
                        }
                        updates.add(Updates.setOnInsert(field, val));
                    } else {
                        boolean dVal = (boolean) json.get("val");
                        UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal, (String) json.get("op"));
                        updates.add(Updates.set(updatePayload.getField(), dVal));
                    }
                }

                writes.add(
                        new UpdateOneModel<>(filters, updates, new UpdateOptions().upsert(true))
                );
            }
            DbLayer.bulkWriteSensitiveParamInfo(writes);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiConfig() {
        try {
            apiConfig = DbLayer.fetchApiconfig(configName);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public static List<SingleTypeInfo> fetchSti(Bson filterQ) {
        // add limit, offset
        return SingleTypeInfoDao.instance.findAll(filterQ, Projections.exclude(SingleTypeInfo._VALUES));
    }

//    public static List<SingleTypeInfo> fetchStiBasedOnId(ObjectId id) {
//        // add limit, offset
//        Bson filters = Filters.gt("_id", id);
//        Bson sort = Sorts.descending("_id") ;
//        return SingleTypeInfoDao.instance.findAll(filters, 0, 20000, sort, Projections.exclude(SingleTypeInfo._VALUES));
//    }

    public String fetchStiBasedOnHostHeaders() {
        try {
            stis = DbLayer.fetchStiBasedOnHostHeaders();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCollectionIds() {
        try {
            apiCollectionIds = DbLayer.fetchApiCollectionIds();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEstimatedDocCount() {
        count = DbLayer.fetchEstimatedDocCount();
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchRuntimeFilters() {
        try {
            runtimeFilters = DbLayer.fetchRuntimeFilters();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchNonTrafficApiCollectionsIds() {
        try {
            apiCollectionIds = DbLayer.fetchNonTrafficApiCollectionsIds();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchStiOfCollections() {
        try {
            stis = DbLayer.fetchStiOfCollections();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String getUnsavedSensitiveParamInfos() {
        try {
            sensitiveParamInfos = DbLayer.getUnsavedSensitiveParamInfos();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSingleTypeInfo() {
        try {
            stis = DbLayer.fetchSingleTypeInfo(lastFetchTimestamp, lastSeenObjectId, resolveLoop);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllSingleTypeInfo() {
        try {
            stis = DbLayer.fetchAllSingleTypeInfo();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchActiveAccount() {
        try {
            account = DbLayer.fetchActiveAccount();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateRuntimeVersion() {
        try {
            DbLayer.updateRuntimeVersion(fieldName, version);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateKafkaIp() {
        try {
            DbLayer.updateKafkaIp(currentInstanceIp);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointsInCollection() {
        try {
            endpoints = DbLayer.fetchEndpointsInCollection();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCollections() {
        try {
            apiCollections = DbLayer.fetchApiCollections();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionSimple() {
        try {
            DbLayer.createCollectionSimple(vxlanId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionForHost() {
        try {
            DbLayer.createCollectionForHost(host, colId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertRuntimeLog() {
        try {
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertRuntimeLog(dbLog);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertAnalyserLog() {
        try {
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertAnalyserLog(dbLog);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String modifyHybridSaasSetting() {
        try {
            DbLayer.modifyHybridSaasSetting(isHybridSaas);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSetup() {
        try {
            setup = DbLayer.fetchSetup();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchOrganization() {
        try {
            organization = DbLayer.fetchOrganization(accountId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkinsertApiHitCount() {

        try {
            List<ApiHitCountInfo> apiHitCountInfos = new ArrayList<>();
            for (BasicDBObject obj: apiHitCountInfoList) {
                ApiHitCountInfo apiHitCountInfo = objectMapper.readValue(obj.toJson(), ApiHitCountInfo.class);
                apiHitCountInfos.add(apiHitCountInfo);
            }
            DbLayer.bulkinsertApiHitCount(apiHitCountInfos);
        } catch (Exception e) {
            logger.error("error in bulkinsertApiHitCount " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public List<CustomDataTypeMapper> getCustomDataTypes() {
        return customDataTypes;
    }

    public void setCustomDataTypes(List<CustomDataTypeMapper> customDataTypes) {
        this.customDataTypes = customDataTypes;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public List<AktoDataType> getAktoDataTypes() {
        return aktoDataTypes;
    }

    public void setAktoDataTypes(List<AktoDataType> aktoDataTypes) {
        this.aktoDataTypes = aktoDataTypes;
    }

    public List<CustomAuthTypeMapper> getCustomAuthTypes() {
        return customAuthTypes;
    }

    public void setCustomAuthTypes(List<CustomAuthTypeMapper> customAuthTypes) {
        this.customAuthTypes = customAuthTypes;
    }

    public AccountSettings getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(AccountSettings accountSettings) {
        this.accountSettings = accountSettings;
    }

    public List<ApiInfo> getApiInfos() {
        return apiInfos;
    }

    public void setApiInfos(List<ApiInfo> apiInfos) {
        this.apiInfos = apiInfos;
    }

    public APIConfig getApiConfig() {
        return apiConfig;
    }

    public void setApiConfig(APIConfig apiConfig) {
        this.apiConfig = apiConfig;
    }

    public List<SingleTypeInfo> getStis() {
        return stis;
    }

    public void setStis(List<SingleTypeInfo> stis) {
        this.stis = stis;
    }

    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public List<RuntimeFilter> getRuntimeFilters() {
        return runtimeFilters;
    }

    public void setRuntimeFilters(List<RuntimeFilter> runtimeFilters) {
        this.runtimeFilters = runtimeFilters;
    }

    public List<SensitiveParamInfo> getSensitiveParamInfos() {
        return sensitiveParamInfos;
    }

    public void setSensitiveParamInfos(List<SensitiveParamInfo> sensitiveParamInfos) {
        this.sensitiveParamInfos = sensitiveParamInfos;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public int getVxlanId() {
        return vxlanId;
    }

    public void setVxlanId(int vxlanId) {
        this.vxlanId = vxlanId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getCidrList() {
        return cidrList;
    }

    public void setCidrList(List<String> cidrList) {
        this.cidrList = cidrList;
    }

    public List<BasicDBObject> getApiInfoList() {
        return apiInfoList;
    }

    public void setApiInfoList(List<BasicDBObject> apiInfoList) {
        this.apiInfoList = apiInfoList;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public List<BulkUpdates> getWritesForSti() {
        return writesForSti;
    }

    public void setWritesForSti(List<BulkUpdates> writesForSti) {
        this.writesForSti = writesForSti;
    }

    public List<BulkUpdates> getWritesForSampleData() {
        return writesForSampleData;
        
    }

    public void setWritesForSampleData(List<BulkUpdates> writesForSampleData) {
        this.writesForSampleData = writesForSampleData;
    }

    public List<BulkUpdates> getWritesForSensitiveSampleData() {
        return writesForSensitiveSampleData;
    }

    public void setWritesForSensitiveSampleData(List<BulkUpdates> writesForSensitiveSampleData) {
        this.writesForSensitiveSampleData = writesForSensitiveSampleData;
    }

    public List<BulkUpdates> getWritesForSensitiveParamInfo() {
        return writesForSensitiveParamInfo;
    }

    public void setWritesForSensitiveParamInfo(List<BulkUpdates> writesForSensitiveParamInfo) {
        this.writesForSensitiveParamInfo = writesForSensitiveParamInfo;
    }

    public List<BulkUpdates> getWritesForTrafficInfo() {
        return writesForTrafficInfo;
    }

    public void setWritesForTrafficInfo(List<BulkUpdates> writesForTrafficInfo) {
        this.writesForTrafficInfo = writesForTrafficInfo;
    }

    public List<BulkUpdates> getWritesForTrafficMetrics() {
        return writesForTrafficMetrics;
    }

    public void setWritesForTrafficMetrics(List<BulkUpdates> writesForTrafficMetrics) {
        this.writesForTrafficMetrics = writesForTrafficMetrics;
    }

    public int getLastFetchTimestamp() {
        return lastFetchTimestamp;
    }

    public void setLastFetchTimestamp(int lastFetchTimestamp) {
        this.lastFetchTimestamp = lastFetchTimestamp;
    }

    public String getLastSeenObjectId() {
        return lastSeenObjectId;
    }

    public void setLastSeenObjectId(String lastSeenObjectId) {
        this.lastSeenObjectId = lastSeenObjectId;
    }

    public boolean getResolveLoop() {
        return resolveLoop;
    }

    public void setResolveLoop(boolean resolveLoop) {
        this.resolveLoop = resolveLoop;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<BulkUpdates> getWritesForFilterSampleData() {
        return writesForFilterSampleData;
    }

    public void setWritesForFilterSampleData(List<BulkUpdates> writesForFilterSampleData) {
        this.writesForFilterSampleData = writesForFilterSampleData;
    }

    public String getCurrentInstanceIp() {
        return currentInstanceIp;
    }

    public void setCurrentInstanceIp(String currentInstanceIp) {
        this.currentInstanceIp = currentInstanceIp;
    }

    public List<ApiInfo.ApiInfoKey> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<ApiInfo.ApiInfoKey> endpoints) {
        this.endpoints = endpoints;
    }

    public List<ApiCollection> getApiCollections() {
        return apiCollections;
    }

    public void setApiCollections(List<ApiCollection> apiCollections) {
        this.apiCollections = apiCollections;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getColId() {
        return colId;
    }

    public void setColId(int colId) {
        this.colId = colId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public BasicDBObject getLog() {
        return log;
    }

    public void setLog(BasicDBObject log) {
        this.log = log;
    }

    public boolean getIsHybridSaas() {
        return isHybridSaas;
    }

    public void setIsHybridSaas(boolean isHybridSaas) {
        this.isHybridSaas = isHybridSaas;
    }

    public Setup getSetup() {
        return setup;
    }

    public void setSetup(Setup setup) {
        this.setup = setup;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public List<BasicDBObject> getApiHitCountInfoList() {
        return apiHitCountInfoList;
    }

    public void setApiHitCountInfoList(List<BasicDBObject> apiHitCountInfoList) {
        this.apiHitCountInfoList = apiHitCountInfoList;
    }

}