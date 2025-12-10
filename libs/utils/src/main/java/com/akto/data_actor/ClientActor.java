package com.akto.data_actor;

import com.akto.dto.filter.MergedUrls;
import com.akto.testing.ApiExecutor;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.akto.dto.*;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.billing.Organization;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.data_types.BelongsToPredicate;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.ContainsPredicate;
import com.akto.dto.data_types.EndsWithPredicate;
import com.akto.dto.data_types.EqualsToPredicate;
import com.akto.dto.data_types.IsNumberPredicate;
import com.akto.dto.data_types.NotBelongsToPredicate;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.data_types.StartsWithPredicate;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.runtime_filters.FieldExistsFilter;
import com.akto.dto.runtime_filters.ResponseCodeRuntimeFilter;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.types.ObjectId;

import com.google.gson.Gson;

public class ClientActor extends DataActor {

    private static final int batchWriteLimit = 8;
    private static final String url = buildDbAbstractorUrl();
    private static final LoggerMaker loggerMaker = new LoggerMaker(ClientActor.class);
    private static final int maxConcurrentBatchWrites = 150;
    private static final Gson gson = new Gson();
    public static final String CYBORG_URL = "https://cyborg.akto.io";
    private static ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentBatchWrites);
    private static ExecutorService logThreadPool = Executors.newFixedThreadPool(50);
    private static AccountSettings accSettings;
    private static final LoggerMaker logger = new LoggerMaker(ClientActor.class);
    
    ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false).configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

    public static String buildDbAbstractorUrl() {
        String dbAbsHost = CYBORG_URL;
        if (checkAccount()) {
            dbAbsHost = System.getenv("DATABASE_ABSTRACTOR_SERVICE_URL");
        }
        System.out.println("dbHost value " + dbAbsHost);
        if (dbAbsHost.endsWith("/")) {
            dbAbsHost = dbAbsHost.substring(0, dbAbsHost.length() - 1);
        }
        return dbAbsHost + "/api";
    }

    public static int getAccountId() throws Exception {
        String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        DecodedJWT jwt = JWT.decode(token);
        String payload = jwt.getPayload();
        byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
        String decodedPayload = new String(decodedBytes);
        BasicDBObject basicDBObject = BasicDBObject.parse(decodedPayload);
        int accId = (int) basicDBObject.getInt("accountId");
        return accId;
    }

    public static boolean checkAccount() {
        try {
            String token = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            DecodedJWT jwt = JWT.decode(token);
            String payload = jwt.getPayload();
            byte[] decodedBytes = Base64.getUrlDecoder().decode(payload);
            String decodedPayload = new String(decodedBytes);
            BasicDBObject basicDBObject = BasicDBObject.parse(decodedPayload);
            int accId = (int) basicDBObject.getInt("accountId");
            System.out.println("checkaccount accountId log " + accId);
            return accId == 1000000 || accId == 1752722331;
        } catch (Exception e) {
            System.out.println("checkaccount error" + e.getStackTrace());
        }
        return false;
    }

    public AccountSettings fetchAccountSettings() {
        AccountSettings acc = null;
        for (int i=0; i < 5; i++) {
            acc = fetchAccountSettingsRetry();
            if (acc != null) {
                break;
            }
        }
        if (acc == null) {
            return accSettings;
        } else {
            accSettings = acc;
            return acc;
        }
    }

    public AccountSettings fetchAccountSettingsRetry() {
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchAccountSettings", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchAccountSettings", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject accountSettingsObj = (BasicDBObject) payloadObj.get("accountSettings");
                accountSettingsObj.put("telemetrySettings", null);
                accountSettingsObj.put("defaultPayloads", null);
                AccountSettings ac = objectMapper.readValue(accountSettingsObj.toJson(), AccountSettings.class);
                accSettings = ac;
                return ac;
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error in fetchAccountSettings" + e, LoggerMaker.LogDb.RUNTIME);
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchAccountSettings" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public long fetchEstimatedDocCount() {
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchEstimatedDocCount", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchEstimatedDocCount", LoggerMaker.LogDb.RUNTIME);
                return 0;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                int cnt = Integer.parseInt((String) payloadObj.get("count").toString());
                return Long.valueOf(cnt);
            } catch(Exception e) {
                return 0;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEstimatedDocCount" + e, LoggerMaker.LogDb.RUNTIME);
            return 0;
        }
    }

    public void updateCidrList(List<String> cidrList) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("cidrList", cidrList);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateCidrList", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateCidrList", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateCidrList" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public void updateApiCollectionNameForVxlan(int vxlanId, String name) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("vxlanId", vxlanId);
        obj.put("name", name);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateApiCollectionNameForVxlan", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateApiCollectionNameForVxlan", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error updating api collection name for vxlan" + e + " vxlanId " + vxlanId
                    + " name" + name, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public APIConfig fetchApiConfig(String configName) {
        Map<String, List<String>> headers = buildHeaders();
        String queryParams = "?configName="+configName;
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchApiConfig", queryParams, "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchApiConfig, configName" + configName, LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject apiConfigObj = (BasicDBObject) payloadObj.get("apiConfig");
                return objectMapper.readValue(apiConfigObj.toJson(), APIConfig.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error fetching api config" + e + " configName " + configName,
                    LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void bulkWrite(List<Object> bulkWrites, String path, String key) {
        ArrayList<BulkUpdates> writes = new ArrayList<>();
        for (int i = 0; i < bulkWrites.size(); i++) {
            writes.add((BulkUpdates) bulkWrites.get(i));
            if (writes.size() % batchWriteLimit == 0) {
                List<BulkUpdates> finalWrites = writes;
                threadPool.submit(
                        () -> writeBatch(finalWrites, path, key)
                );
                writes = new ArrayList<>();
            }
        }
        if (writes.size() > 0) {
            List<BulkUpdates> finalWrites = writes;
            threadPool.submit(
                    () -> writeBatch(finalWrites, path, key)
            );
        }
    }

    public void writeBatch(List<BulkUpdates> writesForSti, String path, String key) {
        BasicDBObject obj = new BasicDBObject();
        obj.put(key, writesForSti);
        Map<String, List<String>> headers = buildHeaders();
        String objString = gson.toJson(obj);

        OriginalHttpRequest request = new OriginalHttpRequest(url + path, "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in writeBatch", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in writeBatch" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public void bulkWriteSingleTypeInfo(List<Object> writesForSti) {
        bulkWrite(writesForSti, "/bulkWriteSti", "writesForSti");
    }

    public void bulkWriteSensitiveParamInfo(List<Object> writesForSensitiveParamInfo) {
        bulkWrite(writesForSensitiveParamInfo, "/bulkWriteSensitiveParamInfo", "writesForSensitiveParamInfo");
    }
    public void bulkWriteSampleData(List<Object> writesForSampleData) {
        bulkWrite(writesForSampleData, "/bulkWriteSampleData", "writesForSampleData");

    }
    public void bulkWriteSensitiveSampleData(List<Object> writesForSensitiveSampleData) {
        bulkWrite(writesForSensitiveSampleData, "/bulkWriteSensitiveSampleData", "writesForSensitiveSampleData");
    }
    public void bulkWriteTrafficInfo(List<Object> writesForTrafficInfo) {
        bulkWrite(writesForTrafficInfo, "/bulkWriteTrafficInfo", "writesForTrafficInfo");
    }
    public void bulkWriteTrafficMetrics(List<Object> writesForTrafficMetrics) {
        bulkWrite(writesForTrafficMetrics, "/bulkWriteTrafficMetrics", "writesForTrafficMetrics");
    }

    public List<SingleTypeInfo> fetchStiOfCollections(int batchCount, int lastStiFetchTs) {
        List<SingleTypeInfo> allStis = fetchStiInBatches(batchCount, lastStiFetchTs);
        Set<String> stiObjIds = new HashSet<>();
        List<SingleTypeInfo> uniqueStis = new ArrayList<>();

        for (SingleTypeInfo sti: allStis) {
            if (stiObjIds.contains(sti.getId().toString())) {
                continue;
            }
            uniqueStis.add(sti);
            stiObjIds.add(sti.getId().toString());
        }

        return uniqueStis;

        // List<SingleTypeInfo> allStis = new ArrayList<>();

        // Map<String, List<String>> headers = buildHeaders();
        // OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchStiOfCollections", "", "GET", null, headers, "");
        // try {
        //     OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
        //     String responsePayload = response.getBody();
        //     if (response.getStatusCode() != 200 || responsePayload == null) {
        //         loggerMaker.errorAndAddToDb("invalid response in fetchStiOfCollections", LoggerMaker.LogDb.RUNTIME);
        //     }
        //     BasicDBObject payloadObj;
        //     try {
        //         payloadObj =  BasicDBObject.parse(responsePayload);
        //         BasicDBList stiList = (BasicDBList) payloadObj.get("stis");

        //         for (Object obj: stiList) {
        //             BasicDBObject obj2 = (BasicDBObject) obj;
        //             obj2.put("id", obj2.get("strId"));
        //             BasicDBObject subType = (BasicDBObject) obj2.get("subType");
        //             obj2.remove("subType");
        //             SingleTypeInfo s = objectMapper.readValue(obj2.toJson(), SingleTypeInfo.class);
        //             s.setSubType(SingleTypeInfo.subTypeMap.get(subType.get("name")));
        //             allStis.add(s);
        //         }

        //     } catch(Exception e) {
        //         loggerMaker.errorAndAddToDb("error extracting response in fetchStiOfCollections" + e, LoggerMaker.LogDb.RUNTIME);
        //     }
        // } catch (Exception e) {
        //     loggerMaker.errorAndAddToDb("error in fetchStiOfCollections" + e, LoggerMaker.LogDb.RUNTIME);
        // }
    }

    public List<SingleTypeInfo> fetchAllStis(int batchCount, int lastStiFetchTs) {
        Map<String, List<String>> headers = buildHeaders();
        List<SingleTypeInfo> allStis = fetchStiInBatches(batchCount, lastStiFetchTs);
        List<SingleTypeInfo> uniqueStis = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchStiBasedOnHostHeaders", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in getUnsavedSensitiveParamInfos", LoggerMaker.LogDb.RUNTIME);
                return allStis;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList stiList = (BasicDBList) payloadObj.get("stis");
                for (Object stiObj: stiList) {
                    BasicDBObject obj2 = (BasicDBObject) stiObj;
                    obj2.put("id", obj2.get("strId"));
                    BasicDBObject subType = (BasicDBObject) obj2.get("subType");
                    obj2.remove("subType");
                    SingleTypeInfo s = objectMapper.readValue(obj2.toJson(), SingleTypeInfo.class);
                    s.setSubType(SingleTypeInfo.subTypeMap.get(subType.get("name")));
                    allStis.add(s);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
        }

        Set<String> stiObjIds = new HashSet<>();

        for (SingleTypeInfo sti: allStis) {
            if (stiObjIds.contains(sti.getId().toString())) {
                continue;
            }
            uniqueStis.add(sti);
            stiObjIds.add(sti.getId().toString());
        }

        return uniqueStis;
    }

    public List<SingleTypeInfo> fetchStiInBatches(int batchCount, int lastStiFetchTs) {
        Map<String, List<String>> headers = buildHeaders();
        List<SingleTypeInfo> allStis = new ArrayList<>();
        List<SingleTypeInfo> stiBatch = new ArrayList<>();
        int ts1, ts2;
        boolean objectIdRequired = false;
        String objId = null;
        BasicDBObject obj = new BasicDBObject();
        ObjectMapper objectMapper = new ObjectMapper();
        for (int i = 0; i < batchCount; i++) {

            obj.put("lastFetchTimestamp", lastStiFetchTs);
            obj.put("lastSeenObjectId", objId);
            obj.put("resolveLoop", objectIdRequired);
            stiBatch = new ArrayList<>();
            OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSingleTypeInfo", "", "POST", obj.toString(), headers, "");
            try {
                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
                String responsePayload = response.getBody();
                if (response.getStatusCode() != 200 || responsePayload == null) {
                    loggerMaker.errorAndAddToDb("invalid response in fetchAllStis", LoggerMaker.LogDb.RUNTIME);
                    continue;
                }
                BasicDBObject payloadObj;
                try {
                    payloadObj =  BasicDBObject.parse(responsePayload);

                    BasicDBList stiList = (BasicDBList) payloadObj.get("stis");

                    for (Object stiObj: stiList) {
                        BasicDBObject obj2 = (BasicDBObject) stiObj;
                        obj2.put("id", obj2.get("strId"));
                        BasicDBObject subType = (BasicDBObject) obj2.get("subType");
                        obj2.remove("subType");
                        SingleTypeInfo s = objectMapper.readValue(obj2.toJson(), SingleTypeInfo.class);
                        s.setSubType(SingleTypeInfo.subTypeMap.get(subType.get("name")));
                        stiBatch.add(s);
                    }

                } catch(Exception e) {
                    loggerMaker.errorAndAddToDb("error extracting fetchAllStis response " + e, LoggerMaker.LogDb.RUNTIME);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in fetchAllStis " + e, LoggerMaker.LogDb.RUNTIME);
            }

            if (stiBatch.size() < batchWriteLimit) {
                allStis.addAll(stiBatch);
                if (!objectIdRequired) {
                    break;
                } else {
                    objId = null;
                    lastStiFetchTs = lastStiFetchTs + 1;
                    objectIdRequired = false;
                    continue;
                }
            }
            ts1 = stiBatch.get(0).getTimestamp();
            ts2 = stiBatch.get(stiBatch.size() - 1).getTimestamp();

            if (ts1 == ts2) {
                if (objectIdRequired) {
                    allStis.addAll(stiBatch);
                    objId = stiBatch.get(stiBatch.size() - 1).getStrId();
                }
                objectIdRequired = true;
            } else {
                objectIdRequired = false;
                allStis.addAll(stiBatch);
            }
            lastStiFetchTs = ts2;

            if (!objectIdRequired) {
                objId = null;
            }

        }
        return allStis;
    }

    public List<SensitiveParamInfo> getUnsavedSensitiveParamInfos() {
        List<SensitiveParamInfo> sensitiveParamInfos = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/getUnsavedSensitiveParamInfos", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in getUnsavedSensitiveParamInfos", LoggerMaker.LogDb.RUNTIME);
                return sensitiveParamInfos;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("sensitiveParamInfos");
                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    sensitiveParamInfos.add(objectMapper.readValue(obj2.toJson(), SensitiveParamInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return sensitiveParamInfos;
    }

    // public static void main(String[] args) throws Exception {
    //     DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
    //     Context.accountId.set(1_000_000);
    //     List<CustomDataType> customDataTypes = new ArrayList<>();

    //     Map<String, List<String>> headers = buildHeaders();
    //     OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchCustomDataTypes", "", "GET", null, headers, "");
    //     try {
    //         OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
    //         String responsePayload = response.getBody();
    //         if (response.getStatusCode() != 200 || responsePayload == null) {
    //             loggerMaker.errorAndAddToDb("invalid response in fetchCustomDataTypes", LoggerMaker.LogDb.RUNTIME);
    //         }
    //         BasicDBObject payloadObj;
    //         ObjectMapper objectMapper = new ObjectMapper();
    //         try {
    //             payloadObj =  BasicDBObject.parse(responsePayload);
    //             BasicDBList objList = (BasicDBList) payloadObj.get("customDataTypes");
    //             for (Object obj: objList) {
    //                 BasicDBObject obj2 = (BasicDBObject) obj;
    //                 BasicDBObject kConditions = (BasicDBObject) obj2.get("keyConditions");
    //                 BasicDBList predicates = (BasicDBList) kConditions.get("predicates");
    //                 RegexPredicate regexPredicate = objectMapper.readValue(((BasicDBObject) predicates.get(0)).toJson(), RegexPredicate.class);
    //                 System.out.println("hi");
    //                 //customDataTypes.add(objectMapper.readValue(obj2.toJson(), CustomDataType.class));
    //             }
    //         } catch(Exception e) {
    //             loggerMaker.errorAndAddToDb("error extracting response in fetchCustomAuthTypes" + e, LoggerMaker.LogDb.RUNTIME);
    //         }
    //     } catch (Exception e) {
    //         loggerMaker.errorAndAddToDb("error in fetchCustomDataTypes" + e, LoggerMaker.LogDb.RUNTIME);
    //     }
    // }

    public List<CustomDataType> fetchCustomDataTypes() {
        List<CustomDataType> customDataTypes = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchCustomDataTypes", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchCustomDataTypes", LoggerMaker.LogDb.RUNTIME);
                return customDataTypes;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("customDataTypes");

                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    String id = (String) obj2.get("id");
                    BasicDBObject kConditions = (BasicDBObject) obj2.get("keyConditions");
                    BasicDBList predicates = null;
                    Conditions keyConditions = null;
                    if (kConditions != null) {
                        predicates = (BasicDBList) kConditions.get("predicates");
                        
                        List<Predicate> predicateList = new ArrayList<>();
                        if (predicates != null) {
                            for (Object predicate: predicates) {
                                String type = (String) ((BasicDBObject) predicate).get("type");
                                switch(type){
                                    case "REGEX":
                                        RegexPredicate regexPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), RegexPredicate.class);
                                        predicateList.add(regexPredicate);
                                        break;
                                    case "STARTS_WITH":
                                        StartsWithPredicate startsWithPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), StartsWithPredicate.class);
                                        predicateList.add(startsWithPredicate);
                                        break;
                                    case "ENDS_WITH":
                                        EndsWithPredicate endsWithPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), EndsWithPredicate.class);
                                        predicateList.add(endsWithPredicate);
                                        break;
                                    case "IS_NUMBER":
                                        IsNumberPredicate isNumberPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), IsNumberPredicate.class);
                                        predicateList.add(isNumberPredicate);
                                        break;
                                    case "EQUALS_TO":
                                        EqualsToPredicate equalsToPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), EqualsToPredicate.class);
                                        predicateList.add(equalsToPredicate);
                                        break;
                                    case "CONTAINS":
                                        ContainsPredicate containsPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(),  ContainsPredicate.class);
                                        predicateList.add(containsPredicate);
                                        break;
                                    case "BELONGS_TO":
                                        BelongsToPredicate belongsToPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), BelongsToPredicate.class);
                                        predicateList.add(belongsToPredicate);
                                        break;
                                    case "NOT_BELONGS_TO":
                                        NotBelongsToPredicate notBelongsToPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), NotBelongsToPredicate.class);
                                        predicateList.add(notBelongsToPredicate);
                                        break;

                                    default:
                                        loggerMaker.errorAndAddToDb("error resolving predicate type", LogDb.RUNTIME);
                                        break;
                                }
                                
                            }
                        }
                        Operator op;
                        if (((String)kConditions.get("operator")).equalsIgnoreCase("or")) {
                            op = Operator.OR;
                        } else {
                            op = Operator.AND;
                        }
                        keyConditions = new Conditions(predicateList, op);
                    }
                    
                    BasicDBObject vConditions = (BasicDBObject) obj2.get("valueConditions");
                    BasicDBList vPredicates = null;
                    Conditions valueConditions = null;
                    if (vConditions != null) {
                        vPredicates = (BasicDBList) vConditions.get("predicates");
                        List<Predicate> vPredicateList = new ArrayList<>();
                        if (vPredicates != null) {
                            for (Object predicate: vPredicates) {
                                RegexPredicate regexPredicate = objectMapper.readValue(((BasicDBObject) predicate).toJson(), RegexPredicate.class);
                                vPredicateList.add(regexPredicate);
                            }
                        }
                        Operator op;
                        if (((String)vConditions.get("operator")).equalsIgnoreCase("or")) {
                            op = Operator.OR;
                        } else {
                            op = Operator.AND;
                        }
                        valueConditions = new Conditions(vPredicateList, op);
                    } 
                    
                    obj2.put("id", null);
                    obj2.put("keyConditions", null);
                    obj2.put("valueConditions", null);

                    CustomDataTypeMapper customDataTypeMapper = objectMapper.readValue(obj2.toJson(), CustomDataTypeMapper.class);

                    CustomDataType customDataType = CustomDataTypeMapper.buildCustomDataType(customDataTypeMapper, id, keyConditions, valueConditions);
                    customDataTypes.add(customDataType);
                }

            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchCustomDataTypes" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchCustomDataTypes" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return customDataTypes;
    }

    public List<AktoDataType> fetchAktoDataTypes() {
        List<AktoDataType> aktoDataTypes = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchAktoDataTypes", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchAktoDataTypes", LoggerMaker.LogDb.RUNTIME);
                return aktoDataTypes;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("aktoDataTypes");
                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    aktoDataTypes.add(objectMapper.readValue(obj2.toJson(), AktoDataType.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchAktoDataTypes" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchAktoDataTypes" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return aktoDataTypes;
    }

    public List<CustomAuthType> fetchCustomAuthTypes() {
        List<CustomAuthType> customAuthTypes = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchCustomAuthTypes", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchCustomAuthTypes", LoggerMaker.LogDb.RUNTIME);
                return customAuthTypes;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("customAuthTypes");
                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    String id = (String) obj2.get("id");
                    obj2.put("id", null);
                    CustomAuthTypeMapper customAuthTypeMapper = objectMapper.readValue(obj2.toJson(), CustomAuthTypeMapper.class);
                    CustomAuthType customAuthType = CustomAuthTypeMapper.buildCustomAuthType(customAuthTypeMapper, id);
                    customAuthTypes.add(customAuthType);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchCustomAuthTypes" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchCustomAuthTypes" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return customAuthTypes;
    }

    public List<ApiInfo> fetchApiInfos() {
        List<ApiInfo> apiInfos = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchApiInfos", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchApiInfos", LoggerMaker.LogDb.RUNTIME);
                return apiInfos;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("apiInfos");
                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    apiInfos.add(objectMapper.readValue(obj2.toJson(), ApiInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchApiInfos" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchApiInfos" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return apiInfos;
    }

    public List<ApiInfo> fetchApiRateLimits(ApiInfo.ApiInfoKey lastApiInfoKey) {
        List<ApiInfo> allApiInfos = new ArrayList<>();
        BasicDBObject payload = new BasicDBObject();
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url+ "/fetchApiRateLimits", "", "POST", gson.toJson(payload), headers, "");

        for(int i = 0; i < 100; i++){
            if(lastApiInfoKey != null){
                payload.put("lastApiInfoKey", lastApiInfoKey);
            }
            try {
                request.setBody(gson.toJson(payload));
                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
                String responsePayload = response.getBody();
                if (response.getStatusCode() != 200 || responsePayload == null) {
                    loggerMaker.errorAndAddToDb("invalid response in fetchApiRateLimits", LoggerMaker.LogDb.RUNTIME);
                    return allApiInfos;
                }
                BasicDBObject payloadObj;
                try {
                    payloadObj =  BasicDBObject.parse(responsePayload);
                    BasicDBList objList = (BasicDBList) payloadObj.get("apiInfoRateLimits");

                    // All apiInfos fetched
                    if(objList.isEmpty()){
                        break;
                    }
                    for (Object obj: objList) {
                        BasicDBObject obj2 = (BasicDBObject) obj;
                        ApiInfo apiInfo = objectMapper.readValue(obj2.toJson(), ApiInfo.class);
                        allApiInfos.add(apiInfo);
                        lastApiInfoKey = apiInfo.getId();
                    }
                } catch(Exception e) {
                    loggerMaker.errorAndAddToDb("error extracting response in fetchApiRateLimits" + e, LoggerMaker.LogDb.RUNTIME);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in fetchApiRateLimits" + e, LoggerMaker.LogDb.RUNTIME);
            }
        }
        return allApiInfos;
    }

    public List<ApiInfo> fetchNonTrafficApiInfos() {
        List<ApiInfo> apiInfos = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchNonTrafficApiInfos", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchNonTrafficApiInfos", LoggerMaker.LogDb.RUNTIME);
                return apiInfos;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("apiInfos");
                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    apiInfos.add(objectMapper.readValue(obj2.toJson(), ApiInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchNonTrafficApiInfos" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchNonTrafficApiInfos" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return apiInfos;
    }

    public void bulkWriteApiInfo(List<ApiInfo> apiInfoList) {
        ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentBatchWrites);

        List<ApiInfo> apiInfoBatch = new ArrayList<>();
        for (int i = 0; i < apiInfoList.size(); i++) {
            apiInfoBatch.add(apiInfoList.get(i));
            if (apiInfoBatch.size() % batchWriteLimit == 0) {
                List<ApiInfo> finalWrites = apiInfoBatch;
                threadPool.submit(
                        () -> writeApiInfoBatch(finalWrites)
                );
                apiInfoBatch = new ArrayList<>();
            }
        }
        if (apiInfoBatch.size() > 0) {
            List<ApiInfo> finalWrites = apiInfoBatch;
            threadPool.submit(
                    () -> writeApiInfoBatch(finalWrites)
            );
        }
    }

    public void writeApiInfoBatch(List<ApiInfo> writesForApiInfo) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiInfoList", writesForApiInfo);

        String objString = gson.toJson(obj);
        System.out.println("api info batch" + objString);

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/bulkWriteApiInfo", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in bulkWriteApiInfo", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in bulkWriteApiInfo" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public List<RuntimeFilter> fetchRuntimeFilters() {
        List<RuntimeFilter> runtimeFilters = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchRuntimeFilters", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchRuntimeFilters", LoggerMaker.LogDb.RUNTIME);
                return runtimeFilters;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("runtimeFilters");
                for (Object obj: objList) {
                    BasicDBObject obj2 = (BasicDBObject) obj;
                    BasicDBList customFilterList = (BasicDBList) obj2.get("customFilterList");
                    List<CustomFilter> customFilters = new ArrayList<>();
                    if (customFilterList != null) {
                        for (Object customFilter: customFilterList) {
                            BasicDBObject customFilterObj = (BasicDBObject) customFilter;
                            CustomFilter cf;
                            if (customFilterObj.containsField("startValue") || customFilterObj.containsField("endValue")) {
                                cf = objectMapper.readValue(customFilterObj.toJson(), ResponseCodeRuntimeFilter.class);
                            } else {
                                cf = objectMapper.readValue(customFilterObj.toJson(), FieldExistsFilter.class);
                            }
                            customFilters.add(cf);
                        }
                    }
                    obj2.put("customFilterList", null);
                    RuntimeFilter filter = objectMapper.readValue(obj2.toJson(), RuntimeFilter.class);
                    filter.setCustomFilterList(customFilters);
                    runtimeFilters.add(filter);
                    
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchRuntimeFilters" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchCustomAuthTypes" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return runtimeFilters;
    }

    public void updateRuntimeVersion(String fieldName, String version) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("fieldName", fieldName);
        obj.put("version", version);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateRuntimeVersion", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateCidrList", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateRuntimeVersion" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public Account fetchActiveAccount() {
        Map<String, List<String>> headers = buildHeaders();
        Account account = null;
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchActiveAccount", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateCidrList", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject accountObj = (BasicDBObject) payloadObj.get("account");
                account = objectMapper.readValue(accountObj.toJson(), Account.class);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchRuntimeFilters" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateRuntimeVersion" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return account;
    }

    public void updateKafkaIp(String currentInstanceIp) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("currentInstanceIp", currentInstanceIp);
        loggerMaker.infoAndAddToDb("updateKafkaIp api called " + currentInstanceIp, LoggerMaker.LogDb.RUNTIME);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateKafkaIp", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateKafkaIp", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateKafkaIp" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection() {
        Map<String, List<String>> headers = buildHeaders();
        List<ApiInfo.ApiInfoKey> endpoints = new ArrayList<>();
        loggerMaker.infoAndAddToDb("fetchEndpointsInCollection api called ", LoggerMaker.LogDb.RUNTIME);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchEndpointsInCollection", "", "POST", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchEndpointsInCollection", LoggerMaker.LogDb.RUNTIME);
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);

                BasicDBList endpointList = (BasicDBList) payloadObj.get("endpoints");

                for (Object obj: endpointList) {
                    BasicDBObject eObj = (BasicDBObject) obj;
                    ApiInfo.ApiInfoKey s = objectMapper.readValue(eObj.toJson(), ApiInfo.ApiInfoKey.class);
                    endpoints.add(s);
                }

            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchEndpointsInCollection" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEndpointsInCollection" + e, LoggerMaker.LogDb.RUNTIME);
        }
        loggerMaker.infoAndAddToDb("fetchEndpointsInCollection api called, endpoints size " + endpoints.size(), LoggerMaker.LogDb.RUNTIME);
        return endpoints;
    }

    public List<ApiCollection> fetchApiCollections() {
        Map<String, List<String>> headers = buildHeaders();
        List<ApiCollection> apiCollections = new ArrayList<>();
        loggerMaker.infoAndAddToDb("fetchEndpointsInCollection api called ", LoggerMaker.LogDb.RUNTIME);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchApiCollections", "", "POST", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchApiCollections", LoggerMaker.LogDb.RUNTIME);
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);

                BasicDBList apiCollectionList = (BasicDBList) payloadObj.get("apiCollections");

                for (Object obj: apiCollectionList) {
                    BasicDBObject aObj = (BasicDBObject) obj;
                    aObj.remove("displayName");
                    aObj.remove("urlsCount");
                    aObj.remove("envType");
                    ApiCollection col = objectMapper.readValue(aObj.toJson(), ApiCollection.class);
                    apiCollections.add(col);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchApiCollections" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchApiCollections" + e, LoggerMaker.LogDb.RUNTIME);
        }
        loggerMaker.infoAndAddToDb("fetchEndpointsInCollection api called size " + apiCollections.size(), LoggerMaker.LogDb.RUNTIME);
        return apiCollections;
    }

    public void createCollectionForHost(String host, int colId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("colId", colId);
        obj.put("host", host);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/createCollectionForHost", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in createCollectionForHost", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in createCollectionForHost" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public void createCollectionSimple(int vxlanId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("vxlanId", vxlanId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/createCollectionSimple", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in createCollectionSimple", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in createCollectionSimple" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void insertRuntimeLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        BasicDBObject logObj = new BasicDBObject();
        logObj.put("key", log.getKey());
        logObj.put("log", log.getLog());
        logObj.put("timestamp", log.getTimestamp());
        obj.put("log", logObj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertRuntimeLog", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                System.out.println("non 2xx response in insertRuntimeLog");
                return;
            }
        } catch (Exception e) {
            System.out.println("error in insertRuntimeLog" + e);
            return;
        }
    }

    public void insertAnalyserLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("log", log);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertAnalyserLog", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertAnalyserLog", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertAnalyserLog" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void insertTestingLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        BasicDBObject logObj = new BasicDBObject();
        logObj.put("key", log.getKey());
        logObj.put("log", log.getLog());
        logObj.put("timestamp", log.getTimestamp());
        obj.put("log", logObj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertTestingLog", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                System.out.println("non 2xx response in insertTestingLog");
                return;
            }
        } catch (Exception e) {
            System.out.println("error in insertTestingLog" + e);
            return;
        }
    }

    public void insertProtectionLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        BasicDBObject logObj = new BasicDBObject();
        logObj.put("key", log.getKey());
        logObj.put("log", log.getLog());
        logObj.put("timestamp", log.getTimestamp());
        obj.put("log", logObj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertProtectionLog", "", "POST", obj.toString(), headers, "");
        try {
            logThreadPool.submit(
                () -> ApiExecutor.sendRequest(request, true, null, false, null)
            );
        } catch (Exception e) {
            System.out.println("error in insertProtectionLog" + e);
            return;
        }
    }

    public void modifyHybridSaasSetting(boolean isHybridSaas) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("isHybridSaas", isHybridSaas);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/modifyHybridSaasSetting", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in modifyHybridSaasSetting", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in modifyHybridSaasSetting" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public Setup fetchSetup() {
        Map<String, List<String>> headers = buildHeaders();
        Setup setup = null;
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSetup", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateCidrList", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject accountObj = (BasicDBObject) payloadObj.get("setup");
                setup = objectMapper.readValue(accountObj.toJson(), Setup.class);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchSetupObject" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchSetupObject" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return setup;
    }

    public Organization fetchOrganization(int accountId) {
        Map<String, List<String>> headers = buildHeaders();
        Organization organization = null;
        BasicDBObject obj = new BasicDBObject();
        obj.put("accountId", accountId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchOrganization", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchOrganization", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject accountObj = (BasicDBObject) payloadObj.get("organization");
                organization = objectMapper.readValue(accountObj.toJson(), Organization.class);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchSetupObject" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchOrganization" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return organization;
    }

    public AccountSettings fetchAccountSettingsForAccount(int accountId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("accountId", accountId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchAccountSettings", "", "GET", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchEstimatedDocCount", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject accountSettingsObj = (BasicDBObject) payloadObj.get("accountSettings");
                return objectMapper.readValue(accountSettingsObj.toJson(), AccountSettings.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEstimatedDocCount" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void syncExtractedAPIs( CodeAnalysisRepo codeAnalysisRepo, List<CodeAnalysisApi> codeAnalysisApisList, boolean isLastBatch) {
        Map<String, List<String>> headers = buildHeaders();

        Map<String, Object> m = new HashMap<>();
        m.put("projectName", codeAnalysisRepo.getProjectName());
        m.put("repoName", codeAnalysisRepo.getRepoName());
        m.put("codeAnalysisRepo",codeAnalysisRepo);
        m.put("isLastBatch", isLastBatch);
        m.put("codeAnalysisApisList", codeAnalysisApisList);

        String json = gson.toJson(m);

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/syncExtractedAPIs", "", "POST", json , headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in syncExtractedAPIs", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("non 2xx response in syncExtractedAPIs", LoggerMaker.LogDb.RUNTIME);
            return;
        }


    }

    public void updateRepoLastRun( CodeAnalysisRepo codeAnalysisRepo) {
        Map<String, List<String>> headers = buildHeaders();

        Map<String, Object> m = new HashMap<>();
        m.put("projectName", codeAnalysisRepo.getProjectName());
        m.put("repoName", codeAnalysisRepo.getRepoName());
        m.put("codeAnalysisRepo",codeAnalysisRepo);

        String json = gson.toJson(m);

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateRepoLastRun", "", "POST", json , headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in syncExtractedAPIs", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("non 2xx response in syncExtractedAPIs", LoggerMaker.LogDb.RUNTIME);
            return;
        }


    }

    public List<CodeAnalysisRepo> findReposToRun()  {
        List<CodeAnalysisRepo> codeAnalysisRepos = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findReposToRun", "", "GET", "{}", headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findReposToRun", LoggerMaker.LogDb.RUNTIME);
                return codeAnalysisRepos;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList reposObj= (BasicDBList) payloadObj.get("reposToRun");
                for (Object repoObj: reposObj) {
                    BasicDBObject repo = (BasicDBObject) repoObj;
                    repo.put("id", null);
                    String hexId = repo.getString("hexId");
                    CodeAnalysisRepo s = objectMapper.readValue(repo.toJson(), CodeAnalysisRepo.class);
                    s.setId(new ObjectId(hexId));
                    codeAnalysisRepos.add(s);
                }
            } catch(Exception e) {
                return codeAnalysisRepos;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findReposToRun" + e, LoggerMaker.LogDb.RUNTIME);
            return codeAnalysisRepos;
        }

        return codeAnalysisRepos;
    }

    public Map<String, List<String>> buildHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        if(System.getProperty("DATABASE_ABSTRACTOR_SERVICE_TOKEN") != null){
            headers.put("Authorization", Collections.singletonList(System.getProperty("DATABASE_ABSTRACTOR_SERVICE_TOKEN")));
        }else{
            headers.put("Authorization", Collections.singletonList(System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN")));
        }
        return headers;
    }

    public void bulkWriteSuspectSampleData(List<Object> writesForSuspectSampleData) {
        bulkWrite(writesForSuspectSampleData, "/bulkWriteSuspectSampleData", "writesForSuspectSampleData");
    }

    public List<YamlTemplate> fetchFilterYamlTemplates() {
        Map<String, List<String>> headers = buildHeaders();
        List<YamlTemplate> templates = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchFilterYamlTemplates", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchFilterYamlTemplates", LoggerMaker.LogDb.THREAT_DETECTION);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList yamlTemplates = (BasicDBList) payloadObj.get("yamlTemplates");
                for (Object template: yamlTemplates) {
                    BasicDBObject obj2 = (BasicDBObject) template;
                    templates.add(objectMapper.readValue(obj2.toJson(), YamlTemplate.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchFilterYamlTemplates" + e, LoggerMaker.LogDb.THREAT_DETECTION);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchFilterYamlTemplates" + e, LoggerMaker.LogDb.THREAT_DETECTION);
            return null;
        }
        return templates;
    }

    public Set<MergedUrls> fetchMergedUrls() {
        Map<String, List<String>> headers = buildHeaders();

        List<MergedUrls> respList = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchMergedUrls", "", "POST", "", headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchMergedUrls", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;

            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBList newUrls = (BasicDBList) payloadObj.get("mergedUrls");
                for (Object url: newUrls) {
                    BasicDBObject urlObj = (BasicDBObject) url;
                    MergedUrls mergedUrl = objectMapper.readValue(urlObj.toJson(), MergedUrls.class);
                    respList.add(mergedUrl);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchMergedUrls" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetching merged urls: " + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }

        return new HashSet<>(respList);
    }

    public void bulkInsertApiHitCount(List<ApiHitCountInfo> payload) throws Exception {
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiHitCountInfoList", payload);
        String objString = gson.toJson(obj);
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/bulkinsertApiHitCount", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                logger.error("non 2xx response in bulkInsertApiHitCount");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in bulkInsertApiHitCount {}", e.getMessage());
            throw e;
        }
    }

    public void updateModuleInfo(ModuleInfo moduleInfo) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("moduleInfo", moduleInfo);

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateModuleInfoForHeartbeat", "", "POST", gson.toJson(obj), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateModuleInfoForHeartbeat", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error updating heartbeat for :" + moduleInfo.getModuleType().name(), LoggerMaker.LogDb.RUNTIME);
        }
    }

    public String fetchOpenApiSchema(int apiCollectionId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        String openApiSchema = null;
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchOpenApiSchema", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchOpenApiSchema", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                openApiSchema = payloadObj.get("openApiSchema").toString();
            } catch(Exception e) {
                return openApiSchema;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchOpenApiSchema" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return openApiSchema;
    }

    public void insertDataIngestionLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("log", log.getLog());
        obj.put("key", log.getKey());
        obj.put("timestamp", log.getTimestamp());
        BasicDBObject logObj = new BasicDBObject();
        logObj.put("log", obj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertDataIngestionLog", "", "POST", logObj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertDataIngestionLog", LogDb.DATA_INGESTION);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertDataIngestionLog" + e, LogDb.DATA_INGESTION);
            return;
        }
    }

    public List<ApiCollection> fetchAllApiCollections() {
        Map<String, List<String>> headers = buildHeaders();
        List<ApiCollection> apiCollections = new ArrayList<>();
        loggerMaker.infoAndAddToDb("fetchAllApiCollections api called ", LoggerMaker.LogDb.RUNTIME);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchAllApiCollections", "", "POST", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequestBackOff(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchAllApiCollections", LoggerMaker.LogDb.RUNTIME);
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);

                BasicDBList apiCollectionList = (BasicDBList) payloadObj.get("apiCollections");

                for (Object obj: apiCollectionList) {
                    BasicDBObject aObj = (BasicDBObject) obj;
                    aObj.remove("displayName");
                    aObj.remove("urlsCount");
                    aObj.remove("envType");
                    aObj.remove("tagsList");
                    ApiCollection col = objectMapper.readValue(aObj.toJson(), ApiCollection.class);
                    apiCollections.add(col);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchApiCollections" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchApiCollections" + e, LoggerMaker.LogDb.RUNTIME);
        }
        loggerMaker.infoAndAddToDb("fetchAllApiCollections api called size " + apiCollections.size(), LoggerMaker.LogDb.RUNTIME);
        return apiCollections;
    }

}
