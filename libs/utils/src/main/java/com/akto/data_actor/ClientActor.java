package com.akto.data_actor;

import com.akto.DaoInit;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.graph.SvcToSvcGraphEdge;
import com.akto.dto.graph.SvcToSvcGraphNode;
import com.akto.dto.settings.DataControlSettings;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.akto.bulk_update_util.ApiInfoBulkUpdate;
import com.akto.dao.SetupDao;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
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
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.runtime_filters.FieldExistsFilter;
import com.akto.dto.runtime_filters.ResponseCodeRuntimeFilter;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.akto.dto.testing.AccessMatrixUrlToRole;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.LoginFlowStepsData;
import com.akto.dto.testing.OtpTestData;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.usage.MetricTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import org.bson.BsonReader;
import org.bson.Document;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.google.gson.Gson;

public class ClientActor extends DataActor {

    private static final int batchWriteLimit = 8;
    private static final String url = buildDbAbstractorUrl();
    private static final LoggerMaker loggerMaker = new LoggerMaker(ClientActor.class);
    private static final int maxConcurrentBatchWrites = 150;
    private static final Gson gson = new Gson();
    private static final CodecRegistry codecRegistry = DaoInit.createCodecRegistry();
    private static final Logger logger = LoggerFactory.getLogger(ClientActor.class);
    public static final String CYBORG_URL = "https://cyborg.akto.io";
    private static ExecutorService threadPool = Executors.newFixedThreadPool(maxConcurrentBatchWrites);
    private static AccountSettings accSettings;

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

    public void bulkWriteTestingRunIssues(List<Object> writesForTestingRunIssues) {
        bulkWrite(writesForTestingRunIssues, "/bulkWriteTestingRunIssues", "writesForTestingRunIssues");
    }

    public boolean overageApisExists(int apiCollectionId, String urlType, URLMethods.Method method, String url) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("urlType", urlType);
        obj.put("method", method);
        obj.put("url", url);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/overageApisExists", "", "GET", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in overageExists", LoggerMaker.LogDb.RUNTIME);
                return false;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                return payloadObj.getBoolean("exists", false);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in overageExists" + e, LoggerMaker.LogDb.RUNTIME);
                return false;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in overageExists" + e, LoggerMaker.LogDb.RUNTIME);
            return false;
        }
    }

    public void bulkWriteOverageInfo(List<Object> writesForOverageInfo) {
        bulkWrite(writesForOverageInfo, "/bulkWriteOverageInfo", "writesForOverageInfo");
    }

    public List<Integer> fetchDeactivatedCollections(){
        List<Integer> ids = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchDeactivatedCollections", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchDeactivatedCollections", LoggerMaker.LogDb.RUNTIME);
                return ids;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("apiCollectionIds");
                for (Object obj: objList) {
                    int obj2 = (Integer) obj;
                    ids.add(obj2);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchDeactivatedCollections" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchDeactivatedCollections" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return ids;
    };

    public void updateUsage(MetricTypes metricType, int deltaUsage){
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("metricType", metricType.name());
        obj.put("deltaUsage", deltaUsage);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateUsage", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in updateUsage", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateUsage" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return;
    };

    public TestSourceConfig findTestSourceConfig(String subType) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("subType", subType);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findTestSourceConfig", "", "GET", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findTestSourceConfig", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testSourceConfigObj = (BasicDBObject) payloadObj.get("testSourceConfig");
                return objectMapper.readValue(testSourceConfigObj.toJson(), TestSourceConfig.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findTestSourceConfig" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
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

    public List<SingleTypeInfo> fetchAllStis() {
        Map<String, List<String>> headers = buildHeaders();
        List<SingleTypeInfo> allStis = new ArrayList<>();
        String lastStiId = null;

        for (int i =0; i<80; i++) {
            BasicDBObject obj = new BasicDBObject();
            obj.put("lastStiId", lastStiId);
            OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchStiBasedOnHostHeaders", "", "POST",  obj.toString(), headers, "");
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
                    if (stiList.isEmpty()) break;

                    for (Object stiObj: stiList) {
                        BasicDBObject obj2 = (BasicDBObject) stiObj;
                        obj2.put("id", obj2.get("strId"));
                        BasicDBObject subType = (BasicDBObject) obj2.get("subType");
                        obj2.remove("subType");
                        SingleTypeInfo s = objectMapper.readValue(obj2.toJson(), SingleTypeInfo.class);
                        s.setSubType(SingleTypeInfo.subTypeMap.get(subType.get("name")));
                        allStis.add(s);
                        lastStiId = s.getId().toHexString();
                    }
                } catch(Exception e) {
                    loggerMaker.errorAndAddToDb("error extracting response in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
            }
        }


        return allStis;
    }
    public List<SingleTypeInfo> fetchStiInBatches(int batchCount, int lastStiFetchTs) {
        Map<String, List<String>> headers = buildHeaders();
        List<SingleTypeInfo> allStis = new ArrayList<>();
        List<SingleTypeInfo> stiBatch = new ArrayList<>();
        int ts1, ts2;
        boolean objectIdRequired = false;
        String objId = null;
        BasicDBObject obj = new BasicDBObject();
        ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

            if (stiBatch.size() < 1000) {
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

    public void createCollectionForHostAndVpc(String host, int colId, String vpcId, List<CollectionTags> tags, String accessType) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("colId", colId);
        obj.put("host", host);
        obj.put("vpcId", vpcId);
        obj.put("tagsList", tags);
        obj.put("accessType", accessType);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/createCollectionForHostAndVpc", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in createCollectionForHostAndVpc", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in createCollectionForHostAndVpc" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public void createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags, String accessType) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("vxlanId", vxlanId);
        obj.put("vpcId", vpcId);
        obj.put("tagsList", tags);
        obj.put("accessType", accessType);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/createCollectionSimpleForVpc", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in createCollectionSimpleForVpc", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in createCollectionSimpleForVpc" + e, LoggerMaker.LogDb.RUNTIME);
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

    // testing queries

    public TestingRunResultSummary createTRRSummaryIfAbsent(String testingRunHexId, int start) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunHexId", testingRunHexId);
        obj.put("start", start);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/createTRRSummaryIfAbsent", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in createTRRSummaryIfAbsent", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in createTRRSummaryIfAbsent" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRun findPendingTestingRun(int delta, String miniTestingName) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("delta", delta);
        obj.put("miniTestingName", miniTestingName);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findPendingTestingRun", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findPendingTestingRun", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                Document doc = Document.parse(responsePayload);
                Document testingRun = (Document) doc.get("testingRun");
                Codec<TestingRun> apiInfoKeyCodec = codecRegistry.get(TestingRun.class);
                String type = ((Document) testingRun.get("testingEndpoints")).getString("type");
                switch (type) {
                    case "CUSTOM":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.CustomTestingEndpoints");
                        break;
                    case "COLLECTION_WISE":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.CollectionWiseTestingEndpoints");
                        break;
                    case "MULTI_COLLECTION":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.MultiCollectionTestingEndpoints");
                        break;
                    case "WORKFLOW":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.WorkflowTestingEndpoints");
                        break;
                    case "ALL":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.AllTestingEndpoints");
                        break;
                    case "LOGICAL_GROUP":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.LogicalGroupTestingEndpoint");
                        break;
                    default:
                        break;
                }
                String hexId = testingRun.getString("hexId");
                testingRun.put("id", hexId);
                TestingRun res = decode(apiInfoKeyCodec, testingRun);
                res.setId(new ObjectId(hexId));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findPendingTestingRun" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public static <T> T decode(Codec<T> codec, Document doc){
        BsonReader bsonReader = doc.toBsonDocument().asBsonReader();
        return codec.decode(bsonReader, DecoderContext.builder().build());
    }

    public TestingRunResultSummary findPendingTestingRunResultSummary(int now, int delta) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("now", now);
        obj.put("delta", delta);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findPendingTestingRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findPendingTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findPendingTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRunConfig findTestingRunConfig(int testIdConfig) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testIdConfig", testIdConfig);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findTestingRunConfig", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findTestingRunConfig", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {

                Document doc = Document.parse(responsePayload);
                Document testingRunConfig = (Document) doc.get("testingRunConfig");
                Codec<TestingRunConfig> apiInfoKeyCodec = codecRegistry.get(TestingRunConfig.class);
                testingRunConfig.remove("authMechanismId");
                TestingRunConfig res = decode(apiInfoKeyCodec, testingRunConfig);
                try {
                    res.setAuthMechanismId(new ObjectId(testingRunConfig.getString("strAuthMechanismId")));
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Unable to set auth in testingRunConfig", LoggerMaker.LogDb.TESTING);
                }
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findTestingRunConfig" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRun findTestingRun(String testingRunId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunId", testingRunId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findTestingRun", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findTestingRun", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                Document doc = Document.parse(responsePayload);
                Document testingRun = (Document) doc.get("testingRun");
                Codec<TestingRun> apiInfoKeyCodec = codecRegistry.get(TestingRun.class);
                String type = ((Document) testingRun.get("testingEndpoints")).getString("type");
                switch (type) {
                    case "CUSTOM":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.CustomTestingEndpoints");
                        break;
                    case "COLLECTION_WISE":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.CollectionWiseTestingEndpoints");
                        break;
                    case "MULTI_COLLECTION":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.MultiCollectionTestingEndpoints");
                        break;
                    case "WORKFLOW":
                        ((Document) testingRun.get("testingEndpoints")).put("_t", "com.akto.dto.testing.WorkflowTestingEndpoints");
                        break;
                    default:
                        break;
                }
                String hexId = testingRun.getString("hexId");
                testingRun.put("id", hexId);
                TestingRun res = decode(apiInfoKeyCodec, testingRun);
                res.setId(new ObjectId(hexId));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findTestingRun" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void updateTestRunResultSummaryNoUpsert(String testingRunResultSummaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", testingRunResultSummaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestRunResultSummaryNoUpsert", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestRunResultSummaryNoUpsert", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestRunResultSummaryNoUpsert" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void updateTestRunResultSummary(String summaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestRunResultSummary", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    @Override
    public void deleteTestRunResultSummary(String summaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", summaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/deleteTestRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    @Override
    public void deleteTestingRunResults(String testingRunResultId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultId", testingRunResultId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/deleteTestingRunResults", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    @Override
    public void updateStartTsTestRunResultSummary(String summaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", summaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateStartTsTestRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }


    @Override
    public List<TestingRunResult> fetchRerunTestingRunResult(String testingRunResultSummaryId) {
        Map<String, List<String>> headers = buildHeaders();
        List<TestingRunResult> results = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", testingRunResultSummaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchRerunTestingRunResult", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLatestTestingRunResult", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList testingRunResults = (BasicDBList) payloadObj.get("testingRunResults");
                for (Object testingRunResult: testingRunResults) {
                    BasicDBObject obj2 = (BasicDBObject) testingRunResult;
                    TestingRunResult s = objectMapper.readValue(obj2.toJson(), TestingRunResult.class);
                    results.add(s);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return results;
    }

    public void updateTestingRun(String testingRunId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunId", testingRunId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestingRun", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestingRun", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestingRun" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public Map<ObjectId, TestingRunResultSummary> fetchTestingRunResultSummaryMap(String testingRunId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunId", testingRunId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestingRunResultSummaryMap", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummaryMap", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultsummaryMap = (BasicDBObject) payloadObj.get("testingRunResultsummaryMap");
                return objectMapper.readValue(testingRunResultsummaryMap.toJson(), Map.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummaryMap" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(String testingRunResultSummaryId) {
        Map<String, List<String>> headers = buildHeaders();
        List<TestingRunResult> results = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", testingRunResultSummaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchLatestTestingRunResult", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLatestTestingRunResult", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList testingRunResults = (BasicDBList) payloadObj.get("testingRunResults");
                for (Object testingRunResult: testingRunResults) {
                    BasicDBObject obj2 = (BasicDBObject) testingRunResult;
                    TestingRunResult s = objectMapper.readValue(obj2.toJson(), TestingRunResult.class);
                    results.add(s);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return results;
    }

    public TestingRunResultSummary fetchTestingRunResultSummary(String testingRunResultSummaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", testingRunResultSummaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestingRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRunResultSummary fetchRerunTestingRunResultSummary(String testingRunResultSummaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", testingRunResultSummaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchRerunTestingRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRunResultSummary markTestRunResultSummaryFailed(String testingRunResultSummaryId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResultSummaryId", testingRunResultSummaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/markTestRunResultSummaryFailed", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in markTestRunResultSummaryFailed", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in markTestRunResultSummaryFailed" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void insertTestingRunResultSummary(TestingRunResultSummary trrs) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("trrs", trrs);
        String objString = gson.toJson(obj);
        
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertTestingRunResultSummary", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void updateTestingRunAndMarkCompleted(String testingRunId, int scheduleTs) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunId", testingRunId);
        obj.put("scheduleTs", scheduleTs);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestingRunAndMarkCompleted", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestingRunAndMarkCompleted", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestingRunAndMarkCompleted " + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public List<TestingRunIssues> fetchOpenIssues(String summaryId) {
        Map<String, List<String>> headers = buildHeaders();
        List<TestingRunIssues> issueList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchOpenIssues", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchOpenIssues", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList testingRunIssues = (BasicDBList) payloadObj.get("testingRunIssues");
                for (Object issue: testingRunIssues) {
                    BasicDBObject obj2 = (BasicDBObject) issue;
                    issueList.add(objectMapper.readValue(obj2.toJson(), TestingRunIssues.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in getUnsavedSensitiveParamInfos" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchOpenIssues" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return issueList;
    }

    public TestingRunResult fetchTestingRunResults(Bson filterForRunResult) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("filterForRunResult", filterForRunResult);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/filterForRunResult", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in filterForRunResult", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResult = (BasicDBObject) payloadObj.get("testingRunResult");
                return objectMapper.readValue(testingRunResult.toJson(), TestingRunResult.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in filterForRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public ApiCollection fetchApiCollectionMeta(int apiCollectionId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchApiCollectionMeta", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchApiCollectionMeta", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject apiCollection = (BasicDBObject) payloadObj.get("apiCollection");
                apiCollection.remove("displayName");
                apiCollection.remove("urlsCount");
                apiCollection.remove("envType");
                return objectMapper.readValue(apiCollection.toJson(), ApiCollection.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchApiCollectionMeta" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<ApiCollection> fetchAllApiCollectionsMeta() {
        Map<String, List<String>> headers = buildHeaders();
        List<ApiCollection> apiCollections = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchAllApiCollectionsMeta", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchAllApiCollectionsMeta", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList collections = (BasicDBList) payloadObj.get("apiCollections");
                for (Object collection: collections) {
                    BasicDBObject obj2 = (BasicDBObject) collection;
                    obj2.remove("displayName");
                    obj2.remove("urlsCount");
                    obj2.remove("envType");
                    apiCollections.add(objectMapper.readValue(obj2.toJson(), ApiCollection.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchAllApiCollectionsMeta" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchAllApiCollectionsMeta" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return apiCollections;
    }

    public WorkflowTest fetchWorkflowTest(int workFlowTestId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("workFlowTestId", workFlowTestId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchWorkflowTest", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchWorkflowTest", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject workflowTest = (BasicDBObject) payloadObj.get("workflowTest");
                return objectMapper.readValue(workflowTest.toJson(), WorkflowTest.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchWorkflowTest" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void insertWorkflowTestResult(WorkflowTestResult workflowTestResult) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("workflowTestResult", workflowTestResult);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertWorkflowTestResult", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertWorkflowTestResult", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertWorkflowTestResult" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void updateIssueCountInTestSummary(String summaryId, Map<String, Integer> totalCountIssues) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("totalCountIssues", totalCountIssues);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestingRunResultSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestingRunResultSummary", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void updateTestInitiatedCountInTestSummary(String summaryId, int testInitiatedCount) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("testInitiatedCount", testInitiatedCount);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestInitiatedCountInTestSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestInitiatedCountInTestSummary", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestInitiatedCountInTestSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public List<YamlTemplate> fetchYamlTemplates(boolean fetchOnlyActive, int skip) {
        Map<String, List<String>> headers = buildHeaders();
        List<YamlTemplate> templates = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("fetchOnlyActive", fetchOnlyActive);
        obj.put("skip", skip);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchYamlTemplates", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchYamlTemplates", LoggerMaker.LogDb.RUNTIME);
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
                loggerMaker.errorAndAddToDb("error extracting response in fetchYamlTemplates" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchYamlTemplates" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return templates;
    }

    public List<YamlTemplate> fetchYamlTemplatesWithIds(List<String> ids, boolean fetchOnlyActive){
        Map<String, List<String>> headers = buildHeaders();
        List<YamlTemplate> templates = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();

        obj.put("fetchOnlyActive", fetchOnlyActive);
        obj.put("ids", ids);

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchYamlTemplatesWithIds", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchYamlTemplatesWithIds", LoggerMaker.LogDb.RUNTIME);
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
                loggerMaker.errorAndAddToDb("error extracting response in fetchYamlTemplatesWithIds" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchYamlTemplatesWithIds" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return templates;
    }

    public void updateTestResultsCountInTestSummary(String summaryId, int testResultsCount) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("testResultsCount", testResultsCount);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestResultsCountInTestSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestResultsCountInTestSummary", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestResultsCountInTestSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void updateLastTestedField(int apiCollectionId, String urlVal, String method) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVal);
        obj.put("methodVal", method);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateLastTestedField", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateLastTestedField", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateLastTestedField" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }


    @Override
    public void bulkUpdateLastTestedField(Map<ApiInfo.ApiInfoKey, Integer> testedApisMap) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        Map<String, Integer> testedApisMapObj = new HashMap<>();
        for (Map.Entry<ApiInfo.ApiInfoKey, Integer> entry : testedApisMap.entrySet()) {
            testedApisMapObj.put(entry.getKey().toString(), entry.getValue());
        }
        obj.put("testedApisMap", testedApisMapObj);
        String objString = gson.toJson(obj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/bulkUpdateLastTestedField", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in bulkUpdateLastTestedField", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in bulkUpdateLastTestedField: " + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void insertTestingRunResults(TestingRunResult testingRunResult) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunResult", testingRunResult);
        String objString = gson.toJson(obj);
        System.out.println(objString);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertTestingRunResults", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertTestingRunResults", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertTestingRunResults" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void updateTotalApiCountInTestSummary(String summaryId, int totalApiCount) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("totalApiCount", totalApiCount);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTotalApiCountInTestSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTotalApiCountInTestSummary", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTotalApiCountInTestSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void insertActivity(int count) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("count", count);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertActivity", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertActivity", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertTestingRunResults" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    private  TestingRunResultSummary getUpdatedSummaryAfterCount(String summaryId, Map<String, Integer> totalCountIssues, String operator){
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("totalCountIssues", totalCountIssues);
        if(operator != null && !operator.isEmpty()){
            obj.put("operator", operator);
        }
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateIssueCountInSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateIssueCountInSummary", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateIssueCountInSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRunResultSummary updateIssueCountInSummary(String summaryId, Map<String, Integer> totalCountIssues, String operator) {
        return getUpdatedSummaryAfterCount(summaryId, totalCountIssues, operator);
    }

    public TestingRunResultSummary updateIssueCountInSummary(String summaryId, Map<String, Integer> totalCountIssues) {
        return getUpdatedSummaryAfterCount(summaryId, totalCountIssues, null);
    }

    public TestingRunResultSummary updateIssueCountAndStateInSummary(String summaryId, Map<String, Integer> totalCountIssues, String state) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("state", state);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateIssueCountAndStateInSummary", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateIssueCountAndStateInSummary", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunResultSummary = (BasicDBObject) payloadObj.get("trrs");
                testingRunResultSummary.remove("id");
                testingRunResultSummary.remove("testingRunId");
                TestingRunResultSummary res = objectMapper.readValue(testingRunResultSummary.toJson(), TestingRunResultSummary.class);
                res.setId(new ObjectId(testingRunResultSummary.getString("hexId")));
                res.setTestingRunId(new ObjectId(testingRunResultSummary.getString("testingRunHexId")));
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateIssueCountAndStateInSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<TestingRunResult> fetchLatestTestingRunResultBySummaryId(String summaryId, int limit, int skip) {
        Map<String, List<String>> headers = buildHeaders();
        List<TestingRunResult> testingRunResultList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("summaryId", summaryId);
        obj.put("limit", limit);
        obj.put("skip", skip);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchLatestTestingRunResultBySummaryId", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLatestTestingRunResultBySummaryId", LoggerMaker.LogDb.RUNTIME);
                return testingRunResultList;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList testingRunResults = (BasicDBList) payloadObj.get("testingRunResults");
                for (Object testingRunResult: testingRunResults) {
                    BasicDBObject obj2 = (BasicDBObject) testingRunResult;
                    TestingRunResult temp = new TestingRunResult();
                    obj2.get("testResults");
                    testingRunResultList.add(objectMapper.readValue(obj2.toJson(), TestingRunResult.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            return testingRunResultList;
        }
        return testingRunResultList;
    }

    public List<TestRoles> fetchTestRoles() {
        Map<String, List<String>> headers = buildHeaders();
        List<TestRoles> roleList = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestRoles", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestRoles", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                Document respDoc = Document.parse(responsePayload);
                List<Document> roleDocs = (List<Document>) respDoc.get("testRoles");
                for (Document roleDoc: roleDocs) {
                    roleList.add(parseTestRole(roleDoc));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchTestRoles" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestRoles" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return roleList;
    }

    public List<SampleData> fetchSampleData(Set<Integer> apiCollectionIds, int skip) {
        Map<String, List<String>> headers = buildHeaders();
        List<SampleData> sampleDataList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionIdsSet", apiCollectionIds);
        obj.put("skip", skip);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSampleData", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchSampleData", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList sampleDatas = (BasicDBList) payloadObj.get("sampleDatas");
                for (Object sampleData: sampleDatas) {
                    BasicDBObject obj2 = (BasicDBObject) sampleData;
                    sampleDataList.add(objectMapper.readValue(obj2.toJson(), SampleData.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchLatestTestingRunResult" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchSampleData" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return sampleDataList;
    }

    public TestRoles fetchTestRole(String key) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("key", key);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestRole", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestRole", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                Document doc = Document.parse(responsePayload);
                Document testRole = (Document) doc.get("testRole");
                TestRoles res = parseTestRole(testRole);
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestRole" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestRoles parseTestRole(Document testRole) {
        Codec<TestRoles> testRoleCodec = codecRegistry.get(TestRoles.class);
        List<Document> authWithCondList = (List<Document>) testRole.get("authWithCondList");
        for (int i = 0; i < authWithCondList.size(); i++) {
            Document authWithCond = (Document) authWithCondList.get(i);
            Document authMechanism = (Document) authWithCond.get("authMechanism");
            authMechanism.put("_t", "com.akto.dto.testing.AuthMechanism");
            String type = authMechanism.getString("type");
            List<Document> authParams = (List<Document>) authMechanism.get("authParams");
            for (Document authParam: authParams) {
                switch (type) {
                    case "HardCoded":
                    case "HARDCODED":
                        authParam.put("_t", "com.akto.dto.testing.HardcodedAuthParam");
                        break;
                    case "LOGIN_REQUEST":
                        authParam.put("_t", "com.akto.dto.testing.LoginRequestAuthParam");
                        break;
                    case "TLS_AUTH":
                        authParam.put("_t", "com.akto.dto.testing.TLSAuthParam");
                        break;
                    default:
                        break;
                }
            }
        }
        Document defaultAuthMechanism = (Document) testRole.get("defaultAuthMechanism");
        if (defaultAuthMechanism != null) {
            defaultAuthMechanism.put("_t", "com.akto.dto.testing.HardcodedAuthParam");
            String type = defaultAuthMechanism.getString("type");
            List<Document> defaultAuthParams = (List<Document>) defaultAuthMechanism.get("authParams");
            for (Document defaultAuthParam: defaultAuthParams) {
                switch (type) {
                    case "HardCoded":
                    case "HARDCODED":
                        defaultAuthParam.put("_t", "com.akto.dto.testing.HardcodedAuthParam");
                        break;
                    case "LOGIN_REQUEST":
                        defaultAuthParam.put("_t", "com.akto.dto.testing.LoginRequestAuthParam");
                        break;
                    case "TLS_AUTH":
                        defaultAuthParam.put("_t", "com.akto.dto.testing.TLSAuthParam");
                        break;
                    default:
                        break;
                }
            }
        }
        testRole.put("endpointLogicalGroupId", new ObjectId(testRole.getString("endpointLogicalGroupIdHexId")));
        TestRoles res = decode(testRoleCodec, testRole);
        return res;
    }
    
    public TestRoles fetchTestRolesforId(String roleId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("roleId", roleId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestRolesforId", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestRolesforId", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                Document doc = Document.parse(responsePayload);
                Document testRole = (Document) doc.get("testRole");
                TestRoles res = parseTestRole(testRole);
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestRolesforId" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public Tokens fetchToken(String organizationId, int accountId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("organizationId", organizationId);
        obj.put("accountId", accountId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchToken", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchToken", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject token = (BasicDBObject) payloadObj.get("token");
                return objectMapper.readValue(token.toJson(), Tokens.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchToken" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<ApiCollection> findApiCollections(List<String> apiCollectionNames) {
        Map<String, List<String>> headers = buildHeaders();
        List<ApiCollection> collectionList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionNames", apiCollectionNames);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findApiCollections", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findApiCollections", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList apiCollections = (BasicDBList) payloadObj.get("apiCollections");
                for (Object apiCollection: apiCollections) {
                    BasicDBObject obj2 = (BasicDBObject) apiCollection;
                    obj2.remove("displayName");
                    obj2.remove("urlsCount");
                    obj2.remove("envType");
                    collectionList.add(objectMapper.readValue(obj2.toJson(), ApiCollection.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in findApiCollections" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findApiCollections" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return collectionList;
    }

    public boolean apiInfoExists(List<Integer> apiCollectionIds, List<String> urls) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionIds", apiCollectionIds);
        obj.put("urls", urls);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/apiInfoExists", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in apiInfoExists", LoggerMaker.LogDb.RUNTIME);
                return false;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                Boolean exists = (Boolean) payloadObj.get("exists");
                return exists;
            } catch(Exception e) {
                return false;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in apiInfoExists" + e, LoggerMaker.LogDb.RUNTIME);
            return false;
        }
    }

    public ApiCollection findApiCollectionByName(String apiCollectionName) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionName", apiCollectionName);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findApiCollectionByName", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findApiCollectionByName", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject apiCollection = (BasicDBObject) payloadObj.get("apiCollection");
                apiCollection.remove("displayName");
                apiCollection.remove("urlsCount");
                apiCollection.remove("envType");
                return objectMapper.readValue(apiCollection.toJson(), ApiCollection.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findApiCollectionByName" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void insertApiCollection(int apiCollectionId, String apiCollectionName) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("apiCollectionName", apiCollectionName);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertApiCollection", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertApiCollection", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertApiCollection" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }
    
    public List<TestingRunIssues> fetchIssuesByIds(Set<TestingIssuesId> issuesIds) {
        Map<String, List<String>> headers = buildHeaders();
        List<TestingRunIssues> issueList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        BasicDBList list = new BasicDBList();
        for(TestingIssuesId id: issuesIds){
            BasicDBObject temp = new BasicDBObject();
            BasicDBObject temp2 = new BasicDBObject();
            ApiInfoKey key = id.getApiInfoKey();
            temp2.put(ApiInfoKey.API_COLLECTION_ID, key.getApiCollectionId());
            temp2.put(ApiInfoKey.METHOD, key.getMethod().name());
            temp2.put(ApiInfoKey.URL, key.getUrl());
            temp.put(TestingIssuesId.API_KEY_INFO, temp2);
            temp.put(TestingIssuesId.TEST_SUB_CATEGORY, id.getTestSubCategory());
            temp.put(TestingIssuesId.TEST_ERROR_SOURCE, id.getTestErrorSource().name());
            list.add(temp);
        }
        obj.put("issuesIds", list);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchIssuesByIds", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchIssuesByIds", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList testingRunIssues = (BasicDBList) payloadObj.get("testingRunIssues");
                for (Object testingRunIssue: testingRunIssues) {
                    BasicDBObject obj2 = (BasicDBObject) testingRunIssue;
                    issueList.add(objectMapper.readValue(obj2.toJson(), TestingRunIssues.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchIssuesByIds" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchIssuesByIds" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return issueList;
    }
    
    public List<SingleTypeInfo> findStiByParam(int apiCollectionId, String param) {
        Map<String, List<String>> headers = buildHeaders();
        List<SingleTypeInfo> stiList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("param", param);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findStiByParam", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findStiByParam", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList stis = (BasicDBList) payloadObj.get("stis");
                for (Object sti: stis) {
                    BasicDBObject obj2 = (BasicDBObject) sti;
                    stiList.add(objectMapper.readValue(obj2.toJson(), SingleTypeInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in findStiByParam" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findStiByParam" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return stiList;
    }

    public SingleTypeInfo findSti(int apiCollectionId, String urlVal, URLMethods.Method method) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVal);
        obj.put("method", method);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findSti", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findSti", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject stiObj = (BasicDBObject) payloadObj.get("sti");
                stiObj.put("id", stiObj.get("strId"));
                BasicDBObject subType = (BasicDBObject) stiObj.get("subType");
                stiObj.remove("subType");
                SingleTypeInfo s = objectMapper.readValue(stiObj.toJson(), SingleTypeInfo.class);
                s.setSubType(SingleTypeInfo.subTypeMap.get(subType.get("name")));
                return s;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findSti" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public AccessMatrixUrlToRole fetchAccessMatrixUrlToRole(ApiInfo.ApiInfoKey apiInfoKey) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiInfoKey", apiInfoKey);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchAccessMatrixUrlToRole", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchAccessMatrixUrlToRole", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject accessMatrixUrlToRole = (BasicDBObject) payloadObj.get("accessMatrixUrlToRole");
                return objectMapper.readValue(accessMatrixUrlToRole.toJson(), AccessMatrixUrlToRole.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchAccessMatrixUrlToRole" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public ApiInfo fetchApiInfo(ApiInfo.ApiInfoKey apiInfoKey) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();

        BasicDBObject temp2 = new BasicDBObject();
        ApiInfoKey key = apiInfoKey;
        temp2.put(ApiInfoKey.API_COLLECTION_ID, key.getApiCollectionId());
        temp2.put(ApiInfoKey.METHOD, key.getMethod().name());
        temp2.put(ApiInfoKey.URL, key.getUrl());

        obj.put("apiInfoKey", temp2);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchApiInfo", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchApiInfo", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject apiInfo = (BasicDBObject) payloadObj.get("apiInfo");
                return objectMapper.readValue(apiInfo.toJson(), ApiInfo.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchApiInfo" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public SampleData fetchSampleDataById(int apiCollectionId, String urlVal, URLMethods.Method method) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVal);
        obj.put("method", method);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSampleDataById", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchSampleDataById", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject sampleData = (BasicDBObject) payloadObj.get("sampleData");
                return objectMapper.readValue(sampleData.toJson(), SampleData.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchSampleDataById" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public SingleTypeInfo findStiWithUrlParamFilters(int apiCollectionId, String urlVal, String method, int responseCode, boolean isHeader, String param, boolean isUrlParam) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVal);
        obj.put("methodVal", method);
        obj.put("responseCode", responseCode);
        obj.put("isHeader", isHeader);
        obj.put("param", param);
        obj.put("isUrlParam", isUrlParam);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findStiWithUrlParamFilters", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findStiWithUrlParamFilters", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject stiObj = (BasicDBObject) payloadObj.get("sti");
                stiObj.put("id", stiObj.get("strId"));
                BasicDBObject subType = (BasicDBObject) stiObj.get("subType");
                stiObj.remove("subType");
                SingleTypeInfo s = objectMapper.readValue(stiObj.toJson(), SingleTypeInfo.class);
                s.setSubType(SingleTypeInfo.subTypeMap.get(subType.get("name")));
                return s;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findStiWithUrlParamFilters" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<TestRoles> fetchTestRolesForRoleName(String roleFromTask) {
        Map<String, List<String>> headers = buildHeaders();
        List<TestRoles> roleList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("roleFromTask", roleFromTask);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestRolesForRoleName", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchTestRolesForRoleName", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            try {
                Document respDoc = Document.parse(responsePayload);
                List<Document> roleDocs = (List<Document>) respDoc.get("testRoles");
                for (Document roleDoc: roleDocs) {
                    roleList.add(parseTestRole(roleDoc));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchTestRolesForRoleName" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestRolesForRoleName" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return roleList;
    }

    public List<AccessMatrixTaskInfo> fetchPendingAccessMatrixInfo(int ts) {
        Map<String, List<String>> headers = buildHeaders();
        List<AccessMatrixTaskInfo> infoList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("ts", ts);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchPendingAccessMatrixInfo", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchPendingAccessMatrixInfo", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList accessMatrixTaskInfos = (BasicDBList) payloadObj.get("accessMatrixTaskInfos");
                for (Object accessMatrixTaskInfo: accessMatrixTaskInfos) {
                    BasicDBObject obj2 = (BasicDBObject) accessMatrixTaskInfo;
                    infoList.add(objectMapper.readValue(obj2.toJson(), AccessMatrixTaskInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchPendingAccessMatrixInfo" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchPendingAccessMatrixInfo" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return infoList;
    }

    public void updateAccessMatrixInfo(String taskId, int frequencyInSeconds) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("taskId", taskId);
        obj.put("frequencyInSeconds", frequencyInSeconds);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateAccessMatrixInfo", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateAccessMatrixInfo", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateAccessMatrixInfo" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public EndpointLogicalGroup fetchEndpointLogicalGroup(String logicalGroupName) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("logicalGroupName", logicalGroupName);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchEndpointLogicalGroup", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchEndpointLogicalGroup", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject endpointLogicalGroup = (BasicDBObject) payloadObj.get("endpointLogicalGroup");
                return objectMapper.readValue(endpointLogicalGroup.toJson(), EndpointLogicalGroup.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEndpointLogicalGroup" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public EndpointLogicalGroup fetchEndpointLogicalGroupById(String endpointLogicalGroupId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("endpointLogicalGroupId", endpointLogicalGroupId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchEndpointLogicalGroupById", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchEndpointLogicalGroupById", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                Document doc = Document.parse(responsePayload);
                Document endpointLogicalGroup = (Document) doc.get("endpointLogicalGroup");
                Codec<EndpointLogicalGroup> codec = codecRegistry.get(EndpointLogicalGroup.class);
                String type = ((Document) endpointLogicalGroup.get("testingEndpoints")).getString("type");
                switch (type) {
                    case "CUSTOM":
                        ((Document) endpointLogicalGroup.get("testingEndpoints")).put("_t", "com.akto.dto.testing.CustomTestingEndpoints");
                        break;
                    case "COLLECTION_WISE":
                        ((Document) endpointLogicalGroup.get("testingEndpoints")).put("_t", "com.akto.dto.testing.CollectionWiseTestingEndpoints");
                        break;
                    case "MULTI_COLLECTION":
                        ((Document) endpointLogicalGroup.get("testingEndpoints")).put("_t", "com.akto.dto.testing.MultiCollectionTestingEndpoints");
                        break;
                    case "WORKFLOW":
                        ((Document) endpointLogicalGroup.get("testingEndpoints")).put("_t", "com.akto.dto.testing.WorkflowTestingEndpoints");
                        break;
                    case "ALL":
                        ((Document) endpointLogicalGroup.get("testingEndpoints")).put("_t", "com.akto.dto.testing.AllTestingEndpoints");
                        break;
                    case "LOGICAL_GROUP":
                        ((Document) endpointLogicalGroup.get("testingEndpoints")).put("_t", "com.akto.dto.testing.LogicalGroupTestingEndpoint");
                        break;
                    default:
                        break;
                }
                endpointLogicalGroup.put("id", null);
                Document cond = (Document) ((Document) endpointLogicalGroup.get("testingEndpoints")).get("orConditions");
                if (cond != null) {
                    cond.put("_t", "com.akto.dto.data_types.Conditions");
                    List<Document> predicateList = (List) cond.get("predicates");
                    for (Document predicate: predicateList) {
                        String predType = (String) predicate.get("type");
                        switch (predType) {
                            case "REGEX":
                                predicate.put("_t", "com.akto.dto.data_types.RegexPredicate");
                                break;
                            case "STARTS_WITH":
                                predicate.put("_t", "com.akto.dto.data_types.StartsWithPredicate");
                                break;
                            case "ENDS_WITH":
                                predicate.put("_t", "com.akto.dto.data_types.ENDSWithPredicate");
                                break;
                            case "IS_NUMBER":
                                predicate.put("_t", "com.akto.dto.data_types.IsNumberWithPredicate");
                                break;
                            case "EQUALS_TO":
                                predicate.put("_t", "com.akto.dto.data_types.EqualsToWithPredicate");
                                break;
                            case "CONTAINS":
                                predicate.put("_t", "com.akto.dto.data_types.ContainsPredicate");
                                break;
                            case "BELONGS_TO":
                                predicate.put("_t", "com.akto.dto.data_types.BelongsToPredicate");
                                break;
                            case "NOT_BELONGS_TO":
                                predicate.put("_t", "com.akto.dto.data_types.NotBelongsToPredicate");
                                break;
                            default:
                                break;
                        }
                    }
                }

                cond = (Document) ((Document) endpointLogicalGroup.get("testingEndpoints")).get("andConditions");
                if (cond != null) {
                    cond.put("_t", "com.akto.dto.data_types.Conditions");
                    List<Document> predicateList = (List) cond.get("predicates");
                    for (Document predicate: predicateList) {
                        String predType = (String) predicate.get("type");
                        switch (predType) {
                            case "REGEX":
                                predicate.put("_t", "com.akto.dto.data_types.RegexPredicate");
                                break;
                            case "STARTS_WITH":
                                predicate.put("_t", "com.akto.dto.data_types.StartsWithPredicate");
                                break;
                            case "ENDS_WITH":
                                predicate.put("_t", "com.akto.dto.data_types.ENDSWithPredicate");
                                break;
                            case "IS_NUMBER":
                                predicate.put("_t", "com.akto.dto.data_types.IsNumberWithPredicate");
                                break;
                            case "EQUALS_TO":
                                predicate.put("_t", "com.akto.dto.data_types.EqualsToWithPredicate");
                                break;
                            case "CONTAINS":
                                predicate.put("_t", "com.akto.dto.data_types.ContainsPredicate");
                                break;
                            case "BELONGS_TO":
                                predicate.put("_t", "com.akto.dto.data_types.BelongsToPredicate");
                                break;
                            case "NOT_BELONGS_TO":
                                predicate.put("_t", "com.akto.dto.data_types.NotBelongsToPredicate");
                                break;
                            default:
                                break;
                        }
                    }
                }

                EndpointLogicalGroup res = decode(codec, endpointLogicalGroup);
                return res;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEndpointLogicalGroupById" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void updateAccessMatrixUrlToRoles(ApiInfo.ApiInfoKey apiInfoKey, List<String> ret) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiInfoKey", apiInfoKey);
        obj.put("ret", ret);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateAccessMatrixUrlToRoles", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateAccessMatrixUrlToRoles", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateAccessMatrixUrlToRoles" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public List<SingleTypeInfo> fetchMatchParamSti(int apiCollectionId, String param) {
        Map<String, List<String>> headers = buildHeaders();
        List<SingleTypeInfo> stiList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("param", param);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchMatchParamSti", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchMatchParamSti", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList stis = (BasicDBList) payloadObj.get("stis");
                for (Object sti: stis) {
                    BasicDBObject obj2 = (BasicDBObject) sti;
                    stiList.add(objectMapper.readValue(obj2.toJson(), SingleTypeInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchMatchParamSti" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchMatchParamSti" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return stiList;
    }

    public DataControlSettings fetchDataControlSettings(String prevResult, String prevCommand) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject("dataControlSettings", new BasicDBObject("postgresResult", prevResult).append("oldPostgresCommand", prevCommand));

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchDataControlSettings", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchDataControlSettings", LoggerMaker.LogDb.RUNTIME);
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject dataControlSettings = (BasicDBObject) payloadObj.get("dataControlSettings");
                return objectMapper.readValue(dataControlSettings.toJson(), DataControlSettings.class);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchDataControlSettings" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchDataControlSettings" + e, LoggerMaker.LogDb.RUNTIME);
        }

        return null;
    }

    public SampleData fetchSampleDataByIdMethod(int apiCollectionId, String urlVal, String method) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVal);
        obj.put("methodVal", method);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSampleDataByIdMethod", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchSampleDataByIdMethod", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject sampleData = (BasicDBObject) payloadObj.get("sampleData");
                return objectMapper.readValue(sampleData.toJson(), SampleData.class);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchSampleDataByIdMethod" + e, LoggerMaker.LogDb.RUNTIME);

                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchSampleDataByIdMethod" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public ApiInfo fetchLatestAuthenticatedByApiCollectionId(int apiCollectionId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchLatestAuthenticatedByApiCollectionId", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLatestAuthenticatedByApiCollectionId", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject apiInfo = (BasicDBObject) payloadObj.get("apiInfo");
                return objectMapper.readValue(apiInfo.toJson(), ApiInfo.class);
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchLatestAuthenticatedByApiCollectionId" + e, LoggerMaker.LogDb.RUNTIME);

                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchLatestAuthenticatedByApiCollectionId" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void modifyHybridTestingSetting(boolean hybridTestingEnabled) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("isHybridSaas", hybridTestingEnabled);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/modifyTestingSetting", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in modifyTestingSetting", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in modifyTestingSetting" + e, LoggerMaker.LogDb.RUNTIME);
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

    public void insertAgenticTestingLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        BasicDBObject logObj = new BasicDBObject();
        logObj.put("key", log.getKey());
        logObj.put("log", log.getLog());
        logObj.put("timestamp", log.getTimestamp());
        obj.put("log", logObj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertAgenticTestingLog", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                System.out.println("non 2xx response in insertAgenticTestingLog");
                return;
            }
        } catch (Exception e) {
            System.out.println("error in insertAgenticTestingLog " + e);
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
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                System.out.println("non 2xx response in insertProtectionLog");
                return;
            }
        } catch (Exception e) {
            System.out.println("error in insertProtectionLog" + e);
            return;
        }
    }

    public void insertCyborgLog(Log log) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        BasicDBObject logObj = new BasicDBObject();
        logObj.put("key", log.getKey());
        logObj.put("log", log.getLog());
        logObj.put("timestamp", log.getTimestamp());
        obj.put("log", logObj);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertCyborgLog", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                System.out.println("non 2xx response in insertCyborgLog");
                return;
            }
        } catch (Exception e) {
            System.out.println("error in insertCyborgLog" + e);
            return;
        }
    }

    public void bulkWriteDependencyNodes(List<DependencyNode> dependencyNodeList) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("dependencyNodeList", dependencyNodeList);

        String objString = gson.toJson(obj);

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/bulkWriteDependencyNodes", "", "POST", objString, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            if (response.getStatusCode() != 200) {
                loggerMaker.errorAndAddToDb("non 2xx response in bulkWriteDependencyNodes", LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in bulkWriteDependencyNodes" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public Map<String, List<String>> buildHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AUTHORIZATION, Collections.singletonList(getAuthToken()));
        return headers;
    }

    public static final String AUTHORIZATION = "Authorization";

    public static String getAuthToken() {
        return System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
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
            return accId == 1000000;
        } catch (Exception e) {
            System.out.println("checkaccount error" + e.getStackTrace());
        }
        return false;
    }

    public List<ApiInfo.ApiInfoKey> fetchLatestEndpointsForTesting(int startTimestamp, int endTimestamp, int apiCollectionId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        List<ApiInfo.ApiInfoKey> respList = new ArrayList<>();

        obj.put("startTimestamp", startTimestamp);
        obj.put("endTimestamp", endTimestamp);
        obj.put("apiCollectionId", apiCollectionId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchLatestEndpointsForTesting", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLatestEndpointsForTesting", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList newEps = (BasicDBList) payloadObj.get("newEps");
                for (Object endpoint: newEps) {
                    BasicDBObject epObj = (BasicDBObject) endpoint;
                    ApiInfo.ApiInfoKey s = objectMapper.readValue(epObj.toJson(), ApiInfo.ApiInfoKey.class);
                    respList.add(s);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchLatestEndpointsForTesting" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchLatestEndpointsForTesting" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return respList;
    }

    public void insertRuntimeMetricsData(BasicDBList metricsData) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("metricsData", metricsData);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertRuntimeMetricsData", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                System.out.println("non 2xx response in insertRuntimeMetricsData");
                return;
            }
        } catch (Exception e) {
            System.out.println("error in insertRuntimeMetricsData" + e);
            return;
        }
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

    public List<YamlTemplate> fetchActiveAdvancedFilters(){
        Map<String, List<String>> headers = buildHeaders();

        List<YamlTemplate> respList = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchActiveAdvancedFilters", "", "POST", "", headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchActiveAdvancedFilters", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;

            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBList newTemplates = (BasicDBList) payloadObj.get("activeAdvancedFilters");
                for (Object template: newTemplates) {
                    BasicDBObject templateObject = (BasicDBObject) template;
                    YamlTemplate yamlTemplate = objectMapper.readValue(templateObject.toJson(), YamlTemplate.class);
                    respList.add(yamlTemplate);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchActiveAdvancedFilters" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetching filter yaml templates" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
        return respList;
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

    public List<TestingRunResultSummary> fetchStatusOfTests() {
        Map<String, List<String>> headers = buildHeaders();
        List<TestingRunResultSummary> result = new ArrayList<>();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchStatusOfTests", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb( "non 2xx response in fetchStatusOfTests", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList tests = (BasicDBList) payloadObj.get("currentlyRunningTests");
                for (Object test: tests) {
                    BasicDBObject obj = (BasicDBObject) test;
                    obj.remove("id");
                    obj.remove("testingRunId");
                    TestingRunResultSummary res = objectMapper.readValue(obj.toJson(), TestingRunResultSummary.class);
                    res.setId(new ObjectId(obj.getString("hexId")));
                    res.setTestingRunId(new ObjectId(obj.getString("testingRunHexId")));
                    result.add(res);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchStatusOfTests" + e, LoggerMaker.LogDb.RUNTIME);
            }

            return result;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchStatusOfTests" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip, int deltaPeriodValue) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("skip", skip);
        obj.put("deltaPeriodValue", deltaPeriodValue);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchEndpointsInCollectionUsingHost", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchEndpointsInCollectionUsingHost", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                return (List<BasicDBObject>) payloadObj.get("apiInfoList");
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEndpointsInCollectionUsingHost" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public OtpTestData fetchOtpTestData(String uuid, int curTime) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("uuid", uuid);
        obj.put("curTime", curTime);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchOtpTestData", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchOtpTestData", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject otpTestDataObj = (BasicDBObject) payloadObj.get("otpTestData");
                OtpTestData otpTestData = objectMapper.readValue(otpTestDataObj.toJson(), OtpTestData.class);
                return otpTestData;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchOtpTestData" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public RecordedLoginFlowInput fetchRecordedLoginFlowInput() {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchRecordedLoginFlowInput", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchRecordedLoginFlowInput", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject recordedLoginFlowInputObj = (BasicDBObject) payloadObj.get("recordedLoginFlowInput");
                RecordedLoginFlowInput recordedLoginFlowInput = objectMapper.readValue(recordedLoginFlowInputObj.toJson(), RecordedLoginFlowInput.class);
                return recordedLoginFlowInput;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchRecordedLoginFlowInput" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public LoginFlowStepsData fetchLoginFlowStepsData(int userId) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("userId", userId);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchLoginFlowStepsData", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchLoginFlowStepsData", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject loginFlowStepsDataObj = (BasicDBObject) payloadObj.get("loginFlowStepsData");
                LoginFlowStepsData loginFlowStepsData = objectMapper.readValue(loginFlowStepsDataObj.toJson(), LoginFlowStepsData.class);
                return loginFlowStepsData;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchLoginFlowStepsData" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public void updateLoginFlowStepsData(int userId, Map<String, Object> valuesMap) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("userId", userId);
        obj.put("valuesMap", valuesMap);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateLoginFlowStepsData", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateLoginFlowStepsData", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateLoginFlowStepsData" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public Node fetchDependencyFlowNodesByApiInfoKey(int apiCollectionId, String urlVar, String method) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVar);
        obj.put("methodVal", method);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchDependencyFlowNodesByApiInfoKey", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchDependencyFlowNodesByApiInfoKey", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject data = (BasicDBObject) payloadObj.get("node");
                return objectMapper.readValue(data.toJson(), Node.class);
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchDependencyFlowNodesByApiInfoKey" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<SampleData> fetchSampleDataForEndpoints(List<ApiInfo.ApiInfoKey> endpoints) {
        Map<String, List<String>> headers = buildHeaders();
        List<SampleData> sampleDataList = new ArrayList<>();
        BasicDBObject obj = new BasicDBObject();
        obj.put("endpoints", endpoints);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchSampleDataForEndpoints", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchSampleDataForEndpoints", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList sampleDatas = (BasicDBList) payloadObj.get("sampleDatas");
                for (Object sampleData: sampleDatas) {
                    BasicDBObject obj2 = (BasicDBObject) sampleData;
                    sampleDataList.add(objectMapper.readValue(obj2.toJson(), SampleData.class));
                }
            } catch(Exception e) {
                return sampleDataList;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchSampleDataForEndpoints" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return sampleDataList;
    }

    public List<Node> fetchNodesForCollectionIds(List<Integer> apiCollectionsIds, boolean removeZeroLevel, int skip) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionIds", apiCollectionsIds);
        obj.put("removeZeroLevel", removeZeroLevel);
        obj.put("skip", skip);
        List<Node> nodeList = new ArrayList<>();

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchNodesForCollectionIds", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchNodesForCollectionIds", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList datas = (BasicDBList) payloadObj.get("nodes");
                for (Object data: datas) {
                    BasicDBObject obj2 = (BasicDBObject) data;
                    nodeList.add(objectMapper.readValue(obj2.toJson(), Node.class));
                }
            } catch(Exception e) {
                return nodeList;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchNodesForCollectionIds" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return nodeList;
    }

    public long countTestingRunResultSummaries(Bson filter) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("filter", filter);
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/countTestingRunResultSummaries", "", "POST",  obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in countTestingRunResultSummaries", LoggerMaker.LogDb.TESTING);
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
            loggerMaker.errorAndAddToDb("error in countTestingRunResultSummaries" + e, LoggerMaker.LogDb.RUNTIME);
            return 0;
        }
    }

    public TestScript fetchTestScript(){
        TestScript testScript = null;

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchTestScript", "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchTestScript", LoggerMaker.LogDb.RUNTIME);
                return testScript;
            }
            BasicDBObject payloadObj;

            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBObject testScriptObj = (BasicDBObject) payloadObj.get("testScript");
                testScript = objectMapper.readValue(testScriptObj.toJson(), TestScript.class);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchTestScript" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchTestScript" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return testScript;
    }

    public List<DependencyNode> findDependencyNodes(int apiCollectionId, String urlVar, String method, String reqMethod) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiCollectionId", apiCollectionId);
        obj.put("url", urlVar);
        obj.put("methodVal", method);
        obj.put("reqMethod", reqMethod);
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findDependencyNodes", "", "POST",  obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findDependencyNodes", LoggerMaker.LogDb.TESTING);
                return new ArrayList<>();
            }
            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBList dependencyNodesObj = (BasicDBList) payloadObj.get("dependencyNodes");
                List<DependencyNode> dependencyNodes = new ArrayList<>();
                for (Object nodeObj : dependencyNodesObj) {
                    BasicDBObject obj2 = (BasicDBObject) nodeObj;
                    dependencyNodes.add(objectMapper.readValue(obj2.toJson(), DependencyNode.class));
                }
                return dependencyNodes;
            } catch (Exception e) {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findDependencyNodes" + e, LoggerMaker.LogDb.RUNTIME);
            return new ArrayList<>();
        }
    }

    public TestingRunResultSummary findLatestTestingRunResultSummary(String testingRunId) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunId", testingRunId);
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findLatestTestingRunResultSummary", "", "POST",  obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findLatestTestingRunResultSummary", LoggerMaker.LogDb.TESTING);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBObject summaryObject = (BasicDBObject) payloadObj.get("trrs");
                TestingRunResultSummary summary = objectMapper.readValue(summaryObject.toJson(), TestingRunResultSummary.class);
                return summary;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findLatestTestingRunResultSummary" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public TestingRunPlayground getCurrentTestingRunDetailsFromEditor(int timestamp){
        BasicDBObject obj = new BasicDBObject();
        obj.put("ts", timestamp);

        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchEditorTest", "", "POST",  obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in fetchEditorTest", LoggerMaker.LogDb.TESTING);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBObject testingRunPlaygroundObj = (BasicDBObject) payloadObj.getOrDefault("testingRunPlayground", null);
                if (testingRunPlaygroundObj == null) {
                    return null;
                }
                return objectMapper.readValue(testingRunPlaygroundObj.toJson(), TestingRunPlayground.class);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchEditorTest " + e, LoggerMaker.LogDb.TESTING);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchEditorTest " + e, LoggerMaker.LogDb.TESTING);
        }
        return null;
    }

    public List<String> findTestSubCategoriesByTestSuiteId(List<String> testSuiteId) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("testSuiteId", testSuiteId);
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findTestSubCategoriesByTestSuiteId", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findTestSubCategoriesByTestSuiteId", LogDb.RUNTIME);
                return new ArrayList<>();
            }
            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBList testSubCategoriesObj = (BasicDBList) payloadObj.get("testSubCategories");
                List<String> testSubCategories = new ArrayList<>();
                for (Object nodeObj : testSubCategoriesObj) {
                    BasicDBObject obj2 = (BasicDBObject) nodeObj;
                    testSubCategories.add(objectMapper.readValue(obj2.toJson(), String.class));
                }
                return testSubCategories;
            } catch (Exception e) {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findTestSubCategoriesByTestSuiteId" + e, LoggerMaker.LogDb.RUNTIME);
            return new ArrayList<>();
        }
    }

    public void updateTestingRunPlayground(TestingRunPlayground testingRunPlayground) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("testingRunPlayground", testingRunPlayground);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateTestingRunPlaygroundStateAndResult", "", "POST",  obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateTestingRunPlaygroundStateAndResult", LoggerMaker.LogDb.TESTING);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateTestingRunPlaygroundStateAndResult" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }


    public List<SvcToSvcGraphEdge> findSvcToSvcGraphEdges(int startTs, int endTs, int skip, int limit) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("startTs", startTs);
        obj.put("endTs", endTs);
        obj.put("skip", skip);
        obj.put("limit", limit);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findAllSvcToSvcGraphEdges", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findAllSvcToSvcGraphEdges", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList edges = (BasicDBList) payloadObj.get("edges");
                List<SvcToSvcGraphEdge> edgesList = new ArrayList<>();
                for (Object edge: edges) {
                    BasicDBObject edgeObj = (BasicDBObject) edge;
                    SvcToSvcGraphEdge svcToSvcGraphEdge = objectMapper.readValue(edgeObj.toJson(), SvcToSvcGraphEdge.class);
                    edgesList.add(svcToSvcGraphEdge);
                }
                return edgesList;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findAllSvcToSvcGraphEdges" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    public List<SvcToSvcGraphNode> findSvcToSvcGraphNodes(int startTs, int endTs, int skip, int limit) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("startTs", startTs);
        obj.put("endTs", endTs);
        obj.put("skip", skip);
        obj.put("limit", limit);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/findAllSvcToSvcGraphNodes", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in findAllSvcToSvcGraphNodes", LoggerMaker.LogDb.RUNTIME);
                return null;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList nodes = (BasicDBList) payloadObj.get("nodes");
                List<SvcToSvcGraphNode> nodesList = new ArrayList<>();
                for (Object node: nodes) {
                    BasicDBObject nodeObj = (BasicDBObject) node;
                    SvcToSvcGraphNode svcToSvcGraphNode = objectMapper.readValue(nodeObj.toJson(), SvcToSvcGraphNode.class);
                    nodesList.add(svcToSvcGraphNode);
                }
                return nodesList;
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in findAllSvcToSvcGraphNodes" + e, LoggerMaker.LogDb.RUNTIME);
            return null;
        }
    }

    @Override
    public void updateSvcToSvcGraphEdges(List<SvcToSvcGraphEdge> edges) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("edges", edges);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateSvcToSvcGraphEdges", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateSvcToSvcGraphEdges", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateSvcToSvcGraphEdges" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    @Override
    public void updateSvcToSvcGraphNodes(List<SvcToSvcGraphNode> nodes) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("nodes", nodes);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateSvcToSvcGraphNodes", "", "POST", obj.toString(), headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateSvcToSvcGraphNodes", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateSvcToSvcGraphNodes" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void bulkInsertApiHitCount(List<ApiHitCountInfo> payload) throws Exception {
        BasicDBObject obj = new BasicDBObject();
        obj.put("apiHitCountInfoList", payload);
        String objString = gson.toJson(obj);
        Map<String, List<String>> headers = buildHeaders();
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/bulkInsertApiHitCount", "", "POST", objString, headers, "");
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
        obj.put("log", log);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertDataIngestionLog", "", "POST", obj.toString(), headers, "");
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

    public void insertMCPAuditDataLog(McpAuditInfo auditInfo) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("auditInfo", auditInfo);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/insertMCPAuditDataLog", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in insertMCPAuditDataLog", LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in insertMCPAuditDataLog" + e, LogDb.RUNTIME);
            return;
        }
    }

    public List<McpReconRequest> fetchPendingMcpReconRequests() {
        Map<String, List<String>> headers = buildHeaders();
        List<McpReconRequest> mcpReconRequests = new ArrayList<>();
        loggerMaker.infoAndAddToDb("fetchPendingMcpReconRequests api called ", LoggerMaker.LogDb.RUNTIME);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchPendingMcpReconRequests", "", "POST", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchPendingMcpReconRequests", LoggerMaker.LogDb.RUNTIME);
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);

                BasicDBList pendingMcpReconRequests = (BasicDBList) payloadObj.get("mcp_recon_requests");

                for (Object obj: pendingMcpReconRequests) {
                    BasicDBObject aObj = (BasicDBObject) obj;
                    McpReconRequest col = objectMapper.readValue(aObj.toJson(), McpReconRequest.class);
                    mcpReconRequests.add(col);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchPendingMcpReconRequests" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchPendingMcpReconRequests" + e, LoggerMaker.LogDb.RUNTIME);
        }
        loggerMaker.infoAndAddToDb("fetchPendingMcpReconRequests api called size " + mcpReconRequests.size(), LoggerMaker.LogDb.RUNTIME);
        return mcpReconRequests;
    }

    /**
     * Update MCP recon request status
     */
    public void updateMcpReconRequestStatus(String requestId, String status, int serversFound) {
        Map<String, List<String>> headers = buildHeaders();
        loggerMaker.infoAndAddToDb("updateMcpReconRequestStatus api called for requestId: " + requestId + " status: " + status + " serversFound: " + serversFound, LoggerMaker.LogDb.RUNTIME);

        BasicDBObject obj = new BasicDBObject();
        obj.put("requestId", requestId);
        obj.put("status", status);
        obj.put("serversFound", serversFound);
        if(status.equals(Constants.STATUS_IN_PROGRESS))
            obj.put("startedAt", Context.now());
        else
            obj.put("finishedAt", Context.now());

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/updateMcpReconRequestStatus", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in updateMcpReconRequestStatus", LoggerMaker.LogDb.RUNTIME);
                return;
            }
            loggerMaker.infoAndAddToDb("Successfully updated MCP recon request status for requestId: " + requestId, LoggerMaker.LogDb.RUNTIME);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in updateMcpReconRequestStatus" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    /**
     * Batch store MCP recon results
     */
    public void storeMcpReconResultsBatch(List<McpReconResult> serverDataList) {
        Map<String, List<String>> headers = buildHeaders();
        loggerMaker.infoAndAddToDb("storeMcpReconResultsBatch api called with " + serverDataList.size() + " servers", LoggerMaker.LogDb.RUNTIME);

        BasicDBObject obj = new BasicDBObject();
        obj.put("servers", serverDataList);

        OriginalHttpRequest request = new OriginalHttpRequest(url + "/storeMcpReconResultsBatch", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in storeMcpReconResultsBatch", LoggerMaker.LogDb.RUNTIME);
                return;
            }
            loggerMaker.infoAndAddToDb("Successfully stored " + serverDataList.size() + " MCP recon results in batch", LoggerMaker.LogDb.RUNTIME);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in storeMcpReconResultsBatch" + e, LoggerMaker.LogDb.RUNTIME);
        }
    }

    public List<YamlTemplate> fetchMCPThreatProtectionTemplates(Integer updatedAfter) {
        List<YamlTemplate> templates = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        if (updatedAfter != null) {
            obj.put("updatedAfter", updatedAfter);
        }
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchMCPThreatProtectionTemplates", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchMCPThreatProtectionTemplates", LoggerMaker.LogDb.RUNTIME);
                return templates;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("mcpThreatProtectionTemplates");
                for (Object obj2: objList) {
                    BasicDBObject templateObj = (BasicDBObject) obj2;
                    templates.add(objectMapper.readValue(templateObj.toJson(), YamlTemplate.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchMCPThreatProtectionTemplates" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchMCPThreatProtectionTemplates" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return templates;
    }

    public List<McpAuditInfo> fetchMcpAuditInfo(Integer updatedAfter, List<String> remarksList) {
        List<McpAuditInfo> mcpAuditInfoList = new ArrayList<>();

        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        if (updatedAfter != null) {
            obj.put("updatedAfter", updatedAfter);
        }
        if (remarksList != null && !remarksList.isEmpty()) {
            obj.put("remarksList", remarksList);
        }
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/fetchMcpAuditInfo", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("invalid response in fetchMcpAuditInfo", LoggerMaker.LogDb.RUNTIME);
                return mcpAuditInfoList;
            }
            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                BasicDBList objList = (BasicDBList) payloadObj.get("mcpAuditInfoList");
                for (Object obj2: objList) {
                    BasicDBObject auditInfoObj = (BasicDBObject) obj2;
                    mcpAuditInfoList.add(objectMapper.readValue(auditInfoObj.toJson(), McpAuditInfo.class));
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("error extracting response in fetchMcpAuditInfo" + e, LoggerMaker.LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in fetchMcpAuditInfo" + e, LoggerMaker.LogDb.RUNTIME);
        }
        return mcpAuditInfoList;
    }

    @Override
    public void updateMcpAuditInfo(String type, String resourceName, String mcpHost, ComponentRiskAnalysis componentRiskAnalysis) {

    }

    @Override
    public void storeConversationResults(List<AgentConversationResult> conversationResults) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("conversationResults", conversationResults);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/storeConversationResults", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in storeConversationResults", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in storeConversationResults" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void storeTrace(Trace trace) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("trace", trace);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/storeTrace", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in storeTrace", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in storeTrace" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    public void storeSpans(List<Span> spans) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("spans", spans);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/storeSpans", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in storeSpans", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in storeSpans" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }

    @Override
    public void storeSsrfTestTracking(SsrfTestTracking ssrfTestTracking) {
        Map<String, List<String>> headers = buildHeaders();
        BasicDBObject obj = new BasicDBObject();
        obj.put("ssrfTestTracking", ssrfTestTracking);
        OriginalHttpRequest request = new OriginalHttpRequest(url + "/storeSsrfTestTracking", "", "POST", obj.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("non 2xx response in storeSsrfTestTracking", LoggerMaker.LogDb.RUNTIME);
                return;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error in storeSsrfTestTracking" + e, LoggerMaker.LogDb.RUNTIME);
            return;
        }
    }
}
