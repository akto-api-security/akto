package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.settings.DataControlSettings;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.utils.KafkaUtils;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    List<BulkUpdates> writesForTestingRunIssues;
    List<BulkUpdates> writesForOverageInfo;
    List<DependencyNode> dependencyNodeList;
    TestScript testScript;
    @Setter @Getter
    McpAuditInfo auditInfo;

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbAction.class, LoggerMaker.LogDb.DASHBOARD);
    public List<BulkUpdates> getWritesForTestingRunIssues() {
        return writesForTestingRunIssues;
    }

    public void setWritesForTestingRunIssues(List<BulkUpdates> writesForTestingRunIssues) {
        this.writesForTestingRunIssues = writesForTestingRunIssues;
    }

    String subType;

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    TestSourceConfig testSourceConfig;

    public TestSourceConfig getTestSourceConfig() {
        return testSourceConfig;
    }

    public void setTestSourceConfig(TestSourceConfig testSourceConfig) {
        this.testSourceConfig = testSourceConfig;
    }

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
    String testingRunHexId;
    int start;
    int delta;
    int now;
    int testIdConfig;
    String testingRunId;
    List<String> urls;
    ApiInfo.ApiInfoKey apiInfoKey;
    int apiCollectionId;
    int startTimestamp;
    int endTimestamp;
    int scheduleTimestamp;
    List<ApiInfo.ApiInfoKey> newEps;
    String logicalGroupName;
    BasicDBList issuesIds;
    List<YamlTemplate> activeAdvancedFilters;
    List<TestingRunResultSummary> currentlyRunningTests;
    String state;
    Bson filter;

    String operator;

    public BasicDBList getIssuesIds() {
        return issuesIds;
    }

    public void setIssuesIds(BasicDBList issuesIds) {
        this.issuesIds = issuesIds;
    }

    String testingRunResultSummaryId;
    String summaryId;
    int limit;
    int skip;
    String param;
    int ts;
    Set<Integer> apiCollectionIdsSet;
    String url;
    URLMethods.Method method;
    String methodVal;
    String key;
    String roleFromTask;
    String roleId;
    Bson filterForRunResult;
    String organizationId;
    int workFlowTestId;
    boolean fetchOnlyActive;
    String apiCollectionName;
    List<String> apiCollectionNames;
    int responseCode;
    boolean isHeader;
    boolean isUrlParam;
    TestingRunResultSummary trrs;
    List<TestingRunResult> testingRunResults;
    WorkflowTestResult workflowTestResult;
    String taskId;
    int frequencyInSeconds;
    List<String> ret;
    Map<String, Integer> totalCountIssues;
    int testInitiatedCount;
    int testResultsCount;
    Bson completedUpdate;
    int totalApiCount;
    boolean hybridTestingEnabled;
    TestingRun testingRun;
    TestingRunConfig testingRunConfig;
    Boolean exists;
    AccessMatrixUrlToRole accessMatrixUrlToRole;
    ApiCollection apiCollection;
    ApiInfo apiInfo;
    EndpointLogicalGroup endpointLogicalGroup;
    List<TestingRunIssues> testingRunIssues;
    List<AccessMatrixTaskInfo> accessMatrixTaskInfos;
    List<SampleData> sampleDatas;
    SampleData sampleData;
    TestRoles testRole;
    List<TestRoles> testRoles;
    Map<ObjectId, TestingRunResultSummary> testingRunResultSummaryMap;
    TestingRunResult testingRunResult;
    Tokens token;
    WorkflowTest workflowTest;
    List<YamlTemplate> yamlTemplates;
    SingleTypeInfo sti;
    int scheduleTs;
    TestingRunPlayground testingRunPlayground;

    private static final Gson gson = new Gson();
    ObjectMapper objectMapper = new ObjectMapper();
    KafkaUtils kafkaUtils = new KafkaUtils();
    String endpointLogicalGroupId;
    String vpcId;
    @lombok.Getter
    @lombok.Setter
    List<CollectionTags> tagsList;

    String metricType;

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    int deltaUsage;

    public int getDeltaUsage() {
        return deltaUsage;
    }

    public void setDeltaUsage(int deltaUsage) {
        this.deltaUsage = deltaUsage;
    }

    DataControlSettings dataControlSettings;
    public String fetchDataControlSettings() {
        try {
            String prevCommand = "";
            String prevResult = "";
            if (dataControlSettings != null) {
                prevResult = dataControlSettings.getPostgresResult();
                prevCommand = dataControlSettings.getOldPostgresCommand();
            }
            dataControlSettings = DbLayer.fetchDataControlSettings(prevResult, prevCommand);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

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

        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForSti, "bulkWriteSti", accId);
        } else {
            System.out.println("Entering writes size: " + writesForSti.size());
            try {
                ArrayList<WriteModel<SingleTypeInfo>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForSti) {
                    List<Bson> filters = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("isUrlParam")) {
                            continue;
                        }
                        if (entry.getKey().equalsIgnoreCase("apiCollectionId") || entry.getKey().equalsIgnoreCase("responseCode")) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            filters.add(Filters.eq(entry.getKey(), val));
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
    
                    System.out.println("filters: " + filters.toString());
    
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
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSampleData() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForSampleData, "bulkWriteSampleData", accId);
        } else {
            try {
                System.out.println("called");
                ArrayList<WriteModel<SampleData>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForSampleData) {
                    Map<String, Object> mObj = (Map) bulkUpdate.getFilters().get("_id");
                    String apiCollectionIdStr = mObj.get("apiCollectionId").toString();
                    int apiCollectionId = Integer.valueOf(apiCollectionIdStr);

                    String bucketEndEpochStr = mObj.get("bucketEndEpoch").toString();
                    int bucketEndEpoch = Integer.valueOf(bucketEndEpochStr);

                    String bucketStartEpochStr = mObj.get("bucketStartEpoch").toString();
                    int bucketStartEpoch = Integer.valueOf(bucketStartEpochStr);

                    String responseCodeStr = mObj.get("responseCode").toString();
                    int responseCode = Integer.valueOf(responseCodeStr);
    
                    Bson filters = Filters.and(Filters.eq("_id.apiCollectionId", apiCollectionId),
                            Filters.eq("_id.bucketEndEpoch", bucketEndEpoch),
                            Filters.eq("_id.bucketStartEpoch", bucketStartEpoch),
                            Filters.eq("_id.method", mObj.get("method")),
                            Filters.eq("_id.responseCode", responseCode),
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
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSensitiveSampleData() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForSensitiveSampleData, "bulkWriteSensitiveSampleData", accId);
        } else {
            try {
                System.out.println("bulkWriteSensitiveSampleData called");
                ArrayList<WriteModel<SensitiveSampleData>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForSensitiveSampleData) {
                    Bson filters = Filters.empty();
                    for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("_id.apiCollectionId") || entry.getKey().equalsIgnoreCase("_id.responseCode")) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            filters = Filters.and(filters, Filters.eq(entry.getKey(), val));
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
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTrafficInfo() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForTrafficInfo, "bulkWriteTrafficInfo", accId);
        } else {
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
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTrafficMetrics() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForTrafficMetrics, "bulkWriteTrafficMetrics", accId);
        } else {
            try {
                System.out.println("bulkWriteTrafficInfo called");
                ArrayList<WriteModel<TrafficMetrics>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForTrafficMetrics) {
                    
                    Bson filters = Filters.empty();
                    for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("_id.bucketStartEpoch") || entry.getKey().equalsIgnoreCase("_id.bucketEndEpoch") || entry.getKey().equalsIgnoreCase("_id.vxlanID")) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            filters = Filters.and(filters, Filters.eq(entry.getKey(), val));
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
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSensitiveParamInfo() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForSensitiveParamInfo, "bulkWriteSensitiveParamInfo", accId);
        } else {
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
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTestingRunIssues() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertData(writesForTestingRunIssues, "bulkWriteTestingRunIssues", accId);
        } else {
            try {
                ArrayList<WriteModel<TestingRunIssues>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForTestingRunIssues) {
                    Object filterObj = bulkUpdate.getFilters().get("_id");
                    HashMap<String, Object> filterMap = (HashMap) filterObj;
                    HashMap<String, Object> keyMap = (HashMap) filterMap.get("apiInfoKey");
                    int apiCollectionId = 0;
                    try {
                        apiCollectionId = (int)(long) keyMap.get("apiCollectionId");
                    } catch(Exception f){
                        apiCollectionId = (int) keyMap.get("apiCollectionId");
                    }
                    ApiInfoKey key = new ApiInfoKey(apiCollectionId, (String)keyMap.get("url"), Method.valueOf((String)keyMap.get("method")));
                    TestingIssuesId idd = new TestingIssuesId(key, TestErrorSource.valueOf((String)filterMap.get("testErrorSource")), (String)filterMap.get("testSubCategory"));
                    Bson filters = Filters.eq("_id", idd);
                    List<String> updatePayloadList = bulkUpdate.getUpdates();
    
                    List<Bson> updates = new ArrayList<>();
                    for (String payload: updatePayloadList) {
                        Map<String, Object> json = gson.fromJson(payload, Map.class);
    
                        String field = (String) json.get("field");
                        if (field.equals(TestingRunIssues.COLLECTION_IDS)) {
                            List<Double> dVal = (List) json.get("val");
                            List<Integer> val = new ArrayList<>();
                            for (int i = 0; i < dVal.size(); i++) {
                                val.add(dVal.get(i).intValue());
                            }
                            updates.add(Updates.setOnInsert(field, val));
                        }else if(field.equals(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_HEX_ID)){
                            String val = (String)json.get("val");
                            ObjectId id = new ObjectId(val);
                            updates.add(Updates.set(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, id));
                        } else if(field.equals(TestingRunIssues.UNREAD)){
                            boolean dVal = (boolean) json.get("val");
                            UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal, (String) json.get("op"));
                            updates.add(Updates.set(updatePayload.getField(), dVal));
                        } else if (field.equals(TestingRunIssues.LAST_UPDATED) ||
                                field.equals(TestingRunIssues.LAST_SEEN) ||
                                field.equals(TestingRunIssues.CREATION_TIME)) {
                            Double val = (double) json.get("val");
                            int dVal = val.intValue();
                            UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal, (String) json.get("op"));
                            updates.add(Updates.set(updatePayload.getField(), dVal));
                        } else {
                            String dVal = (String) json.get("val");
                            UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal, (String) json.get("op"));
                            updates.add(Updates.set(updatePayload.getField(), dVal));
                        }
                    }
    
                    writes.add(
                            new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                    );
                }
                DbLayer.bulkWriteTestingRunIssues(writes);
            } catch (Exception e) {
                String err = "Error: ";
                if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                    StackTraceElement stackTraceElement = e.getStackTrace()[0];
                    err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
                } else {
                    err = String.format("Err msg: %s\nStackTrace not available", err);
                    e.printStackTrace();
                }
                System.out.println(err);
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteOverageInfo() {
        try {
            loggerMaker.info("bulkWriteOverageInfo called");
            ArrayList<WriteModel<UningestedApiOverage>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForOverageInfo) {
                // Create filter for the document
                Bson filters = Filters.and(
                    Filters.eq(UningestedApiOverage.API_COLLECTION_ID, bulkUpdate.getFilters().get(UningestedApiOverage.API_COLLECTION_ID)),
                    Filters.eq(UningestedApiOverage.URL_TYPE, bulkUpdate.getFilters().get(UningestedApiOverage.URL_TYPE)),
                    Filters.eq(UningestedApiOverage.METHOD, bulkUpdate.getFilters().get(UningestedApiOverage.METHOD)),
                    Filters.eq(UningestedApiOverage.URL, bulkUpdate.getFilters().get(UningestedApiOverage.URL))
                );
                
                List<String> updatePayloadList = bulkUpdate.getUpdates();
                List<Bson> updates = new ArrayList<>();
                
                for (String payload: updatePayloadList) {
                    Map<String, Object> json = gson.fromJson(payload, Map.class);
                    String field = (String) json.get("field");
                    Object val = json.get("val");
                    String op = (String) json.get("op");
                    
                    if ("setOnInsert".equals(op)) {
                        updates.add(Updates.setOnInsert(field, val));
                    } else if ("set".equals(op)) {
                        updates.add(Updates.set(field, val));
                    }
                }
                
                if (!updates.isEmpty()) {
                    writes.add(
                        new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                    );
                }
            }
            DbLayer.bulkWriteOverageInfo(writes);
        } catch (Exception e) {
            String err = "Error: ";
            if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                err = String.format("Error: %s\nStackTrace: %s", e.getMessage(), e.getStackTrace()[0]);
            } else {
                err = String.format("Err msg: %s\nStackTrace not available", err);
                e.printStackTrace();
            }
            System.out.println(err);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findTestSourceConfig(){
        try {
            testSourceConfig = DbLayer.findTestSourceConfig(subType);
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

    private String lastStiId = null;
    public String fetchStiBasedOnHostHeaders() {
        try {
            ObjectId lastTsObjectId = lastStiId != null ? new ObjectId(lastStiId) : null;
            stis = DbLayer.fetchStiBasedOnHostHeaders(lastTsObjectId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchDeactivatedCollections() {
        try {
            apiCollectionIds = DbLayer.fetchDeactivatedCollections();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateUsage() {
        try {
            MetricTypes metric = MetricTypes.valueOf(metricType);
            DbLayer.updateUsage(metric, deltaUsage);
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

    // testing apis

    public String createTRRSummaryIfAbsent() {
        try {
            trrs = DbLayer.createTRRSummaryIfAbsent(testingRunHexId, start);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findPendingTestingRun() {
        try {
            testingRun = DbLayer.findPendingTestingRun(delta);
            if (testingRun != null) {
                /*
                * There is a db call involved for collectionWiseTestingEndpoints, thus this hack. 
                */
                if(testingRun.getTestingEndpoints() instanceof CollectionWiseTestingEndpoints){
                    CollectionWiseTestingEndpoints ts = (CollectionWiseTestingEndpoints) testingRun.getTestingEndpoints();
                    CustomTestingEndpoints endpoints = new CustomTestingEndpoints(ts.returnApis());
                    testingRun.setTestingEndpoints(endpoints);
                }
            }
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findPendingTestingRunResultSummary() {
        try {
            trrs = DbLayer.findPendingTestingRunResultSummary(now, delta);
            if (trrs != null) {
                trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
            }
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findTestingRunConfig() {
        try {
            testingRunConfig = DbLayer.findTestingRunConfig(testIdConfig);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findTestingRun() {
        try {
            testingRun = DbLayer.findTestingRun(testingRunId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String apiInfoExists() {
        try {
            exists = DbLayer.apiInfoExists(apiCollectionIds, urls);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAccessMatrixUrlToRole() {
        try {
            accessMatrixUrlToRole = DbLayer.fetchAccessMatrixUrlToRole(apiInfoKey);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllApiCollectionsMeta() {
        try {
           apiCollections = DbLayer.fetchAllApiCollectionsMeta();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCollectionMeta() {
        try {
            apiCollection = DbLayer.fetchApiCollectionMeta(apiCollectionId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiInfo() {
        try {
            apiInfo = DbLayer.fetchApiInfo(apiInfoKey);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointLogicalGroup() {
        try {
            endpointLogicalGroup = DbLayer.fetchEndpointLogicalGroup(logicalGroupName);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointLogicalGroupById() {
        try {
            endpointLogicalGroup = DbLayer.fetchEndpointLogicalGroupById(endpointLogicalGroupId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchIssuesByIds() {
        try {
            Set<TestingIssuesId> ids = new HashSet<>();
            for (Object obj : issuesIds) {
                HashMap<String,Object> temp = (HashMap) obj;
                HashMap<String,Object> apiInfoKey = (HashMap) temp.get(TestingIssuesId.API_KEY_INFO);
                ApiInfoKey key = new ApiInfoKey(
                        (int)((long)apiInfoKey.get(ApiInfoKey.API_COLLECTION_ID)),
                        (String)apiInfoKey.get(ApiInfoKey.URL),
                        Method.valueOf((String)apiInfoKey.get(ApiInfoKey.METHOD)));
                TestingIssuesId id = new TestingIssuesId(key,
                        TestErrorSource.valueOf((String)temp.get(TestingIssuesId.TEST_ERROR_SOURCE)),
                        (String)temp.get(TestingIssuesId.TEST_SUB_CATEGORY));
                ids.add(id);
            }
            testingRunIssues = DbLayer.fetchIssuesByIds(ids);
        } catch (Exception e) {
            String err = "Error: ";
            if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                StackTraceElement stackTraceElement = e.getStackTrace()[0];
                err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
            } else {
                err = String.format("Err msg: %s\nStackTrace not available", err);
                e.printStackTrace();
            }
            System.out.println(err);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestTestingRunResult() {
        try {
            testingRunResults = DbLayer.fetchLatestTestingRunResult(testingRunResultSummaryId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestTestingRunResultBySummaryId() {
        try {
            testingRunResults = DbLayer.fetchLatestTestingRunResultBySummaryId(summaryId, limit, skip);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchMatchParamSti() {
        try {
            stis = DbLayer.fetchMatchParamSti(apiCollectionId, param);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchOpenIssues() {
        try {
            testingRunIssues = DbLayer.fetchOpenIssues(summaryId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchPendingAccessMatrixInfo() {
        try {
            accessMatrixTaskInfos = DbLayer.fetchPendingAccessMatrixInfo(ts);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleData() {
        try {
            sampleDatas = DbLayer.fetchSampleData(apiCollectionIdsSet, skip);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleDataById() {
        try {
            sampleData = DbLayer.fetchSampleDataById(apiCollectionId, url, method);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleDataByIdMethod() {
        try {
            sampleData = DbLayer.fetchSampleDataByIdMethod(apiCollectionId, url, methodVal);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestRole() {
        try {
            testRole = DbLayer.fetchTestRole(key);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }
    
    public String fetchTestRoles() {
        try {
            testRoles = DbLayer.fetchTestRoles();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestRolesForRoleName() {
        try {
            testRoles = DbLayer.fetchTestRolesForRoleName(roleFromTask);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestRolesforId() {
        try {
            testRole = DbLayer.fetchTestRolesforId(roleId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResultSummary() {
        try {
            trrs = DbLayer.fetchTestingRunResultSummary(testingRunResultSummaryId);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResultSummaryMap() {
        try {
            testingRunResultSummaryMap = DbLayer.fetchTestingRunResultSummaryMap(testingRunId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResults() {
        try {
            testingRunResult = DbLayer.fetchTestingRunResults(filterForRunResult);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchToken() {
        try {
            token = DbLayer.fetchToken(organizationId, accountId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchWorkflowTest() {
        try {
            workflowTest = DbLayer.fetchWorkflowTest(workFlowTestId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchYamlTemplates() {
        try {
            yamlTemplates = DbLayer.fetchYamlTemplates(fetchOnlyActive, skip);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findApiCollectionByName() {
        try {
            apiCollection = DbLayer.findApiCollectionByName(apiCollectionName);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findApiCollections() {
        try {
            apiCollections = DbLayer.findApiCollections(apiCollectionNames);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findSti() {
        try {
            sti = DbLayer.findSti(apiCollectionId, url, method);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findStiByParam() {
        try {
            stis = DbLayer.findStiByParam(apiCollectionId, param);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findStiWithUrlParamFilters() {
        try {
            sti = DbLayer.findStiWithUrlParamFilters(apiCollectionId, url, methodVal, responseCode, isHeader, param, isUrlParam);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertActivity() {
        try {
            DbLayer.insertActivity((int) count);  
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertApiCollection() {
        try {
            DbLayer.insertApiCollection(apiCollectionId, apiCollectionName);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertTestingRunResultSummary() {
        try {
            DbLayer.insertTestingRunResultSummary(trrs);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertTestingRunResults() {
        try {

            if(testingRunResult.getSingleTestResults()!=null){
                testingRunResult.setTestResults(new ArrayList<>(testingRunResult.getSingleTestResults()));
            }else if(testingRunResult.getMultiExecTestResults() !=null){
                testingRunResult.setTestResults(new ArrayList<>(testingRunResult.getMultiExecTestResults()));
            }

            if (testingRunResult.getTestRunHexId() != null) {
                ObjectId id = new ObjectId(testingRunResult.getTestRunHexId());
                testingRunResult.setTestRunId(id);
            }

            if (testingRunResult.getTestRunResultSummaryHexId() != null) {
                ObjectId id = new ObjectId(testingRunResult.getTestRunResultSummaryHexId());
                testingRunResult.setTestRunResultSummaryId(id);
            }

            DbLayer.insertTestingRunResults(testingRunResult);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertWorkflowTestResult() {
        try {
            DbLayer.insertWorkflowTestResult(workflowTestResult);        
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String markTestRunResultSummaryFailed() {
        try {
            trrs = DbLayer.markTestRunResultSummaryFailed(testingRunResultSummaryId);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateAccessMatrixInfo() {
        try {
            DbLayer.updateAccessMatrixInfo(taskId, frequencyInSeconds);        
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateAccessMatrixUrlToRoles() {
        try {
            DbLayer.updateAccessMatrixUrlToRoles(apiInfoKey, ret);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateIssueCountInSummary() {
        try {
            trrs = DbLayer.updateIssueCountInSummary(summaryId, totalCountIssues, operator);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateIssueCountInTestSummary() {
        try {
            DbLayer.updateIssueCountInTestSummary(summaryId, totalCountIssues, false);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateLastTestedField() {
        try {
            DbLayer.updateLastTestedField(apiCollectionId, url, methodVal);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestInitiatedCountInTestSummary() {
        try {
            DbLayer.updateTestInitiatedCountInTestSummary(summaryId, testInitiatedCount);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestResultsCountInTestSummary() {
        try {
            DbLayer.updateTestResultsCountInTestSummary(summaryId, testResultsCount);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestRunResultSummary() {
        try {
            DbLayer.updateTestRunResultSummary(summaryId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestRunResultSummaryNoUpsert() {
        try {
            DbLayer.updateTestRunResultSummaryNoUpsert(testingRunResultSummaryId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestingRun() {
        try {
            if (state != null && !state.isEmpty()) {
                TestingRun.State stateEnum = TestingRun.State.valueOf(state);
                DbLayer.updateTestingRun(testingRunId, stateEnum, scheduleTimestamp);
            } else {
                DbLayer.updateTestingRun(testingRunId);
            }
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestingRunAndMarkCompleted() {
        try {
            DbLayer.updateTestingRunAndMarkCompleted(testingRunId, scheduleTs);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTotalApiCountInTestSummary() {
        try {
            DbLayer.updateTotalApiCountInTestSummary(summaryId, totalApiCount);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String modifyHybridTestingSetting() {
        try {
            DbLayer.modifyHybridTestingSetting(hybridTestingEnabled);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertTestingLog() {
        try {
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertTestingLog(dbLog);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteDependencyNodes() {
        try {
            System.out.println("bulkWriteDependencyNodes called");
            DbLayer.bulkWriteDependencyNodes(dependencyNodeList);
        } catch (Exception e) {
            String err = "Error bulkWriteDependencyNodes: ";
            if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                StackTraceElement stackTraceElement = e.getStackTrace()[0];
                err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
            } else {
                err = String.format("Err msg: %s\nStackTrace not available", err);
                e.printStackTrace();
            }
            System.out.println(err);
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestEndpointsForTesting() {
        try {
            newEps = DbLayer.fetchLatestEndpointsForTesting(startTimestamp, endTimestamp, apiCollectionId);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchActiveAdvancedFilters(){
        try {
            this.activeAdvancedFilters = DbLayer.fetchActiveFilterTemplates();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchStatusOfTests(){
        try {
            this.currentlyRunningTests = DbLayer.fetchStatusOfTests();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateIssueCountAndStateInSummary(){
        try {
            trrs = DbLayer.updateIssueCountAndStateInSummary(summaryId, totalCountIssues, state);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionSimpleForVpc() {
        try {
            System.out.println("called1 vpcId" + vpcId);
            DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId, tagsList);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionForHostAndVpc() {
        try {
            System.out.println("called2 vpcId" + vpcId);
            DbLayer.createCollectionForHostAndVpc(host, colId, vpcId, tagsList);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    List<DependencyNode> dependencyNodes;

    public List<DependencyNode> getDependencyNodes() {
        return dependencyNodes;
    }

    public void setDependencyNodes(List<DependencyNode> dependencyNodes) {
        this.dependencyNodes = dependencyNodes;
    }

    String reqMethod;
    
    public String getReqMethod() {
        return reqMethod;
    }

    public void setReqMethod(String reqMethod) {
        this.reqMethod = reqMethod;
    }

    public String findDependencyNodes() {
        try {
            dependencyNodes = DbLayer.findDependencyNodes(apiCollectionId, url, methodVal, reqMethod);
        } catch(Exception e){
            e.printStackTrace();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestScript() {
        try {
            testScript = DbLayer.fetchTestScript();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            System.out.println("Error in fetchTestScript " + e.toString());
            return Action.ERROR.toUpperCase();
        }
    }
    
    public String countTestingRunResultSummaries() {
        count = DbLayer.countTestingRunResultSummaries(filter);
        return Action.SUCCESS.toUpperCase();
    }

    public String getCurrentTestingRunDetailsFromEditor(){
        try {
            testingRunPlayground = DbLayer.getCurrentTestingRunDetailsFromEditor(this.ts);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in getCurrentTestingRunDetailsFromEditor " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestingRunPlaygroundStateAndResult(){
        try {
            DbLayer.updateTestingRunPlayground(this.testingRunPlayground);
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findLatestTestingRunResultSummary(){
        trrs = DbLayer.findLatestTestingRunResultSummary(filter);
        return Action.SUCCESS.toUpperCase();
    }

    public String insertMCPAuditDataLog() {
        try {
            DbLayer.insertMCPAuditDataLog(auditInfo);
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "Error insertMCPAuditDataLog " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }


    public String updateTestingRunResultSummaryWithStateAndTimestamp() {
        try {
            TestingRun.State stateEnum = TestingRun.State.valueOf(state);
            trrs = DbLayer.updateTestingRunResultSummaryWithStateAndTimestamp(testingRunResultSummaryId, stateEnum, startTimestamp);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestingRunResultSummaryWithStateAndTimestamp " + e.toString());
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

    public List<BulkUpdates> getWritesForOverageInfo() {
        return writesForOverageInfo;
    }

    public void setWritesForOverageInfo(List<BulkUpdates> writesForOverageInfo) {
        this.writesForOverageInfo = writesForOverageInfo;
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

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

    public int getNow() {
        return now;
    }

    public void setNow(int now) {
        this.now = now;
    }

    public int getTestIdConfig() {
        return testIdConfig;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public String getTestingRunId() {
        return testingRunId;
    }

    public void setTestingRunId(String testingRunId) {
        this.testingRunId = testingRunId;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getLogicalGroupName() {
        return logicalGroupName;
    }

    public void setLogicalGroupName(String logicalGroupName) {
        this.logicalGroupName = logicalGroupName;
    }


    public String getTestingRunResultSummaryId() {
        return testingRunResultSummaryId;
    }

    public void setTestingRunResultSummaryId(String testingRunResultSummaryId) {
        this.testingRunResultSummaryId = testingRunResultSummaryId;
    }

    public String getSummaryId() {
        return summaryId;
    }

    public void setSummaryId(String summaryId) {
        this.summaryId = summaryId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public int getTs() {
        return ts;
    }

    public void setTs(int ts) {
        this.ts = ts;
    }

    public Set<Integer> getApiCollectionIdsSet() {
        return apiCollectionIdsSet;
    }

    public void setApiCollectionIdsSet(Set<Integer> apiCollectionIdsSet) {
        this.apiCollectionIdsSet = apiCollectionIdsSet;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public URLMethods.Method getMethod() {
        return method;
    }

    public void setMethod(URLMethods.Method method) {
        this.method = method;
    }

    public String getMethodVal() {
        return methodVal;
    }

    public void setMethodVal(String methodVal) {
        this.methodVal = methodVal;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getRoleFromTask() {
        return roleFromTask;
    }

    public void setRoleFromTask(String roleFromTask) {
        this.roleFromTask = roleFromTask;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public Bson getFilterForRunResult() {
        return filterForRunResult;
    }

    public void setFilterForRunResult(Bson filterForRunResult) {
        this.filterForRunResult = filterForRunResult;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public int getWorkFlowTestId() {
        return workFlowTestId;
    }

    public void setWorkFlowTestId(int workFlowTestId) {
        this.workFlowTestId = workFlowTestId;
    }

    public boolean isFetchOnlyActive() {
        return fetchOnlyActive;
    }

    public void setFetchOnlyActive(boolean fetchOnlyActive) {
        this.fetchOnlyActive = fetchOnlyActive;
    }

    public String getApiCollectionName() {
        return apiCollectionName;
    }

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public List<String> getApiCollectionNames() {
        return apiCollectionNames;
    }

    public void setApiCollectionNames(List<String> apiCollectionNames) {
        this.apiCollectionNames = apiCollectionNames;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isHeader() {
        return isHeader;
    }

    public void setHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public boolean isUrlParam() {
        return isUrlParam;
    }

    public void setUrlParam(boolean isUrlParam) {
        this.isUrlParam = isUrlParam;
    }

    public TestingRunResultSummary getTrrs() {
        return trrs;
    }

    public void setTrrs(TestingRunResultSummary trrs) {
        this.trrs = trrs;
    }

    public List<TestingRunResult> getTestingRunResults() {
        return testingRunResults;
    }

    public void setTestingRunResults(List<TestingRunResult> testingRunResults) {
        this.testingRunResults = testingRunResults;
    }

    public WorkflowTestResult getWorkflowTestResult() {
        return workflowTestResult;
    }

    public void setWorkflowTestResult(WorkflowTestResult workflowTestResult) {
        this.workflowTestResult = workflowTestResult;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public int getFrequencyInSeconds() {
        return frequencyInSeconds;
    }

    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public List<String> getRet() {
        return ret;
    }

    public void setRet(List<String> ret) {
        this.ret = ret;
    }

    public Map<String, Integer> getTotalCountIssues() {
        return totalCountIssues;
    }

    public void setTotalCountIssues(Map<String, Integer> totalCountIssues) {
        this.totalCountIssues = totalCountIssues;
    }

    public int getTestInitiatedCount() {
        return testInitiatedCount;
    }

    public void setTestInitiatedCount(int testInitiatedCount) {
        this.testInitiatedCount = testInitiatedCount;
    }

    public int getTestResultsCount() {
        return testResultsCount;
    }

    public void setTestResultsCount(int testResultsCount) {
        this.testResultsCount = testResultsCount;
    }

    public Bson getCompletedUpdate() {
        return completedUpdate;
    }

    public void setCompletedUpdate(Bson completedUpdate) {
        this.completedUpdate = completedUpdate;
    }

    public int getTotalApiCount() {
        return totalApiCount;
    }

    public void setTotalApiCount(int totalApiCount) {
        this.totalApiCount = totalApiCount;
    }

    public boolean isHybridTestingEnabled() {
        return hybridTestingEnabled;
    }

    public void setHybridTestingEnabled(boolean hybridTestingEnabled) {
        this.hybridTestingEnabled = hybridTestingEnabled;
    }

    public TestingRun getTestingRun() {
        return testingRun;
    }

    public void setTestingRun(TestingRun testingRun) {
        this.testingRun = testingRun;
    }

    public TestingRunConfig getTestingRunConfig() {
        return testingRunConfig;
    }

    public void setTestingRunConfig(TestingRunConfig testingRunConfig) {
        this.testingRunConfig = testingRunConfig;
    }

    public Boolean getExists() {
        return exists;
    }

    public void setExists(Boolean exists) {
        this.exists = exists;
    }

    public AccessMatrixUrlToRole getAccessMatrixUrlToRole() {
        return accessMatrixUrlToRole;
    }

    public void setAccessMatrixUrlToRole(AccessMatrixUrlToRole accessMatrixUrlToRole) {
        this.accessMatrixUrlToRole = accessMatrixUrlToRole;
    }

    public ApiCollection getApiCollection() {
        return apiCollection;
    }

    public void setApiCollection(ApiCollection apiCollection) {
        this.apiCollection = apiCollection;
    }

    public ApiInfo getApiInfo() {
        return apiInfo;
    }

    public void setApiInfo(ApiInfo apiInfo) {
        this.apiInfo = apiInfo;
    }

    public EndpointLogicalGroup getEndpointLogicalGroup() {
        return endpointLogicalGroup;
    }

    public void setEndpointLogicalGroup(EndpointLogicalGroup endpointLogicalGroup) {
        this.endpointLogicalGroup = endpointLogicalGroup;
    }

    public List<TestingRunIssues> getTestingRunIssues() {
        return testingRunIssues;
    }

    public void setTestingRunIssues(List<TestingRunIssues> testingRunIssues) {
        this.testingRunIssues = testingRunIssues;
    }

    public List<AccessMatrixTaskInfo> getAccessMatrixTaskInfos() {
        return accessMatrixTaskInfos;
    }

    public void setAccessMatrixTaskInfos(List<AccessMatrixTaskInfo> accessMatrixTaskInfos) {
        this.accessMatrixTaskInfos = accessMatrixTaskInfos;
    }

    public List<SampleData> getSampleDatas() {
        return sampleDatas;
    }

    public void setSampleDatas(List<SampleData> sampleDatas) {
        this.sampleDatas = sampleDatas;
    }

    public SampleData getSampleData() {
        return sampleData;
    }

    public void setSampleData(SampleData sampleData) {
        this.sampleData = sampleData;
    }

    public TestRoles getTestRole() {
        return testRole;
    }

    public void setTestRole(TestRoles testRole) {
        this.testRole = testRole;
    }

    public List<TestRoles> getTestRoles() {
        return testRoles;
    }

    public void setTestRoles(List<TestRoles> testRoles) {
        this.testRoles = testRoles;
    }

    public Map<ObjectId, TestingRunResultSummary> getTestingRunResultSummaryMap() {
        return testingRunResultSummaryMap;
    }

    public void setTestingRunResultSummaryMap(Map<ObjectId, TestingRunResultSummary> testingRunResultSummaryMap) {
        this.testingRunResultSummaryMap = testingRunResultSummaryMap;
    }

    public TestingRunResult getTestingRunResult() {
        return testingRunResult;
    }

    public void setTestingRunResult(TestingRunResult testingRunResult) {
        this.testingRunResult = testingRunResult;
    }

    public Tokens getToken() {
        return token;
    }

    public void setToken(Tokens token) {
        this.token = token;
    }

    public WorkflowTest getWorkflowTest() {
        return workflowTest;
    }

    public void setWorkflowTest(WorkflowTest workflowTest) {
        this.workflowTest = workflowTest;
    }

    public List<YamlTemplate> getYamlTemplates() {
        return yamlTemplates;
    }

    public void setYamlTemplates(List<YamlTemplate> yamlTemplates) {
        this.yamlTemplates = yamlTemplates;
    }

    public SingleTypeInfo getSti() {
        return sti;
    }

    public void setSti(SingleTypeInfo sti) {
        this.sti = sti;
    }

    public int getScheduleTs() {
        return scheduleTs;
    }

    public void setScheduleTs(int scheduleTs) {
        this.scheduleTs = scheduleTs;
    }

    public String getEndpointLogicalGroupId() {
        return endpointLogicalGroupId;
    }

    public void setEndpointLogicalGroupId(String endpointLogicalGroupId) {
        this.endpointLogicalGroupId = endpointLogicalGroupId;
    }

    public void setDataControlSettings(DataControlSettings dataControlSettings) {
        this.dataControlSettings = dataControlSettings;
    }

    public DataControlSettings getDataControlSettings() {
        return this.dataControlSettings;
    }

    public void setLastStiId(String lastStiId) {
        this.lastStiId = lastStiId;
    }

    public List<DependencyNode> getDependencyNodeList() {
        return dependencyNodeList;
    }

    public void setDependencyNodeList(List<DependencyNode> dependencyNodeList) {
        this.dependencyNodeList = dependencyNodeList;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public int getScheduleTimestamp() {
        return scheduleTimestamp;
    }

    public void setScheduleTimestamp(int scheduleTimestamp) {
        this.scheduleTimestamp = scheduleTimestamp;
    }

    public List<ApiInfo.ApiInfoKey> getNewEps() {
        return newEps;
    }

    public void setNewEps(List<ApiInfo.ApiInfoKey> newEps) {
        this.newEps = newEps;
    }

    public List<YamlTemplate> getActiveAdvancedFilters() {
        return activeAdvancedFilters;
    }

    public void setActiveAdvancedFilters(List<YamlTemplate> activeAdvancedFilters) {
        this.activeAdvancedFilters = activeAdvancedFilters;
    }

    public List<TestingRunResultSummary> getCurrentlyRunningTests() {
        return currentlyRunningTests;
    }

    public void setCurrentlyRunningTests(List<TestingRunResultSummary> currentlyRunningTests) {
        this.currentlyRunningTests = currentlyRunningTests;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getVpcId() {
        return vpcId;
    }

    public void setVpcId(String vpcId) {
        this.vpcId = vpcId;
    }

    public TestScript getTestScript() {
        return testScript;
    }

    public void setFilter(Bson filter) {
        this.filter = filter;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public TestingRunPlayground getTestingRunPlayground() {
        return testingRunPlayground;
    }

    public void setTestingRunPlayground(TestingRunPlayground testingRunPlayground) {
        this.testingRunPlayground = testingRunPlayground;
    }

}
