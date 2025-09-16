package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.traffic_collector.TrafficCollectorInfoDao;
import com.akto.dao.traffic_collector.TrafficCollectorMetricsDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.graph.SvcToSvcGraphEdge;
import com.akto.dto.graph.SvcToSvcGraphNode;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.settings.DataControlSettings;
import com.akto.dto.test_editor.TestingRunPlayground;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_collector.TrafficCollectorMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.notifications.slack.APITestStatusAlert;
import com.akto.notifications.slack.NewIssuesModel;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.SlackSender;
import com.akto.util.enums.GlobalEnums;
import com.akto.utils.CustomAuthUtil;
import com.akto.utils.KafkaUtils;
import com.akto.utils.RedactAlert;
import com.akto.utils.SampleDataLogs;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import java.util.concurrent.atomic.AtomicInteger;
import com.akto.testing.TestExecutor;
import com.akto.trafficFilter.HostFilter;
import com.akto.trafficFilter.ParamFilter;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.DataInsertionPreChecks;
import com.akto.dto.usage.MetricTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

import lombok.Getter;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import com.google.gson.Gson;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DbAction extends ActionSupport {
    static final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
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
    List<BulkUpdates> writesForSuspectSampleData;
    List<BulkUpdates> writesForOverageInfo;
    List<DependencyNode> dependencyNodeList;
    TestScript testScript;
    String openApiSchema;
    @Getter @Setter
    McpAuditInfo auditInfo;

    private ModuleInfo moduleInfo;

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbAction.class, LogDb.DB_ABS);

    public List<BulkUpdates> getWritesForTestingRunIssues() {
        return writesForTestingRunIssues;
    }

    public void setWritesForTestingRunIssues(List<BulkUpdates> writesForTestingRunIssues) {
        this.writesForTestingRunIssues = writesForTestingRunIssues;
    }

    public List<BulkUpdates> getWritesForOverageInfo() {
        return writesForOverageInfo;
    }

    public void setWritesForOverageInfo(List<BulkUpdates> writesForOverageInfo) {
        this.writesForOverageInfo = writesForOverageInfo;
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
    private String miniTestingName;
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
    List<ApiInfo.ApiInfoKey> newEps;
    String logicalGroupName;
    BasicDBList issuesIds;
    List<YamlTemplate> activeAdvancedFilters;
    Set<MergedUrls> mergedUrls;
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
    private String urlType;
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
    BasicDBObject testingRunResult;
    Tokens token;
    WorkflowTest workflowTest;
    List<YamlTemplate> yamlTemplates;
    SingleTypeInfo sti;
    int scheduleTs;
    TestingRunPlayground testingRunPlayground;
    private String testingRunPlaygroundId;

    private static final Gson gson = new Gson();
    ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false).configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    KafkaUtils kafkaUtils = new KafkaUtils();
    String endpointLogicalGroupId;
    String vpcId;
    @lombok.Getter
    @lombok.Setter
    List<CollectionTags> tagsList;
    private TestingRunPlayground.TestingRunPlaygroundType testingRunPlaygroundType;
    private OriginalHttpResponse originalHttpResponse;
    String metricType;
    List<BasicDBObject> apiHitCountInfoList;

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
    BasicDBList metricsData;

    int deltaPeriodValue;
    String uuid;
    int currTime;
    OtpTestData otpTestData;
    RecordedLoginFlowInput recordedLoginFlowInput;
    LoginFlowStepsData loginFlowStepsData;
    int userId;
    Map<String, Object> valuesMap;
    Node node;
    List<Node> nodes;
    boolean removeZeroLevel;
    private List<MetricData> metricData;

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
            loggerMaker.errorAndAddToDb(e, "error in fetchDataControlSettings " + e.toString());
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
            loggerMaker.errorAndAddToDb(e, "error in fetchCustomDataTypes " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAktoDataTypes() {
        try {
            aktoDataTypes = DbLayer.fetchAktoDataTypes();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchAktoDataTypes " + e.toString());
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
            loggerMaker.errorAndAddToDb(e, "error in fetchCustomAuthTypes " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateApiCollectionNameForVxlan() {
        try {
            DbLayer.updateApiCollectionName(vxlanId, name);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in updateApiCollectionNameForVxlan " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateModuleInfo() {
        try {
            DbLayer.updateModuleInfo(moduleInfo);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in updateApiCollectionNameForVxlan " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateCidrList() {
        try {
            DbLayer.updateCidrList(cidrList);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in updateCidrList " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAccountSettings() {
        try {
            int accountId = Context.accountId.get();
            accountSettings = DbLayer.fetchAccountSettings(accountId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchAccountSettings " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiInfos() {
        try {
            loggerMaker.error("init fetchApiInfos account id: " + Context.accountId.get());
            apiInfos = DbLayer.fetchApiInfos();
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.error("fetchApiInfos account id: " + Context.accountId.get());
            loggerMaker.errorAndAddToDb(e, "error in fetchApiInfos " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    @Getter @Setter
    private ApiInfoKey lastApiInfoKey;
    @Getter @Setter
    List<ApiInfo> apiInfoRateLimits = new ArrayList<>();

    public String fetchApiRateLimits() {
        try {
            loggerMaker.info("init fetchApiRateLimits account id: " + Context.accountId.get());
            apiInfoRateLimits = DbLayer.fetchApiRateLimits(lastApiInfoKey);
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.error("fetchApiRateLimits account id: " + Context.accountId.get());
            loggerMaker.errorAndAddToDb(e, "error in fetchApiRateLimits" + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchNonTrafficApiInfos() {
        try {
            apiInfos = DbLayer.fetchNonTrafficApiInfos();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in fetchNonTrafficApiInfos " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }


    TrafficCollectorMetrics trafficCollectorMetrics = null;
    public String updateTrafficCollectorMetrics() {
        if (trafficCollectorMetrics == null) {
            loggerMaker.errorAndAddToDb("trafficCollectorMetrics is null");
            return Action.SUCCESS.toUpperCase();
        }

        // update heartbeat
        try {
            TrafficCollectorInfoDao.instance.updateHeartbeat(trafficCollectorMetrics.getId(), trafficCollectorMetrics.getRuntimeId());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while updating heartbeat: " + e);
        }

        // update metrics
        try {
            TrafficCollectorMetricsDao.instance.updateCount(trafficCollectorMetrics);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while updating count of traffic collector metrics: " + e);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteApiInfo() {
        int accountId = Context.accountId.get();
        try {
            List<ApiInfo> apiInfos = new ArrayList<>();
            for (BasicDBObject obj: apiInfoList) {
                ApiInfo apiInfo = objectMapper.readValue(obj.toJson(), ApiInfo.class);
                ApiInfoKey id = apiInfo.getId();
                
                // Skip URLs based on validation rules
                if (DataInsertionPreChecks.shouldSkipUrl(accountId, id.getUrl())) {
                    continue;
                }
                
                if (UsageMetricCalculator.getDeactivated().contains(id.getApiCollectionId())) {
                    continue;
                }
                if (URLMethods.Method.OPTIONS.equals(id.getMethod()) || URLMethods.Method.OTHER.equals(id.getMethod())) {
                    continue;
                }
                if (accountId == 1721887185 && (id.getApiCollectionId() == 1991121043 || id.getApiCollectionId() == -1134993740)  && !id.getMethod().equals(Method.OPTIONS))  {
                    loggerMaker.infoAndAddToDb("auth types for endpoint from runtime " + id.getUrl() + " " + id.getMethod() + " : " + apiInfo.getAllAuthTypesFound());
                }
                apiInfos.add(apiInfo);
            }
            if (apiInfos!=null && !apiInfos.isEmpty()) {
                SingleTypeInfo.fetchCustomAuthTypes(accountId);
                service.schedule(new Runnable() {
                    public void run() {
                        Context.accountId.set(accountId);
                        List<CustomAuthType> customAuthTypes = SingleTypeInfo.getCustomAuthType(accountId);
                        CustomAuthUtil.calcAuth(apiInfos, customAuthTypes, accountId == 1721887185);
                        DbLayer.bulkWriteApiInfo(apiInfos);
                    }
                }, 0, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in bulkWriteApiInfo " + e.toString());
            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.insertDataSecondary(apiInfoList, "bulkWriteApiInfo", Context.accountId.get());
            }
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSti() {
        loggerMaker.infoAndAddToDb("bulkWriteSti called");
        int accId = Context.accountId.get();

        Set<Integer> ignoreHosts = new HashSet<>();
        try {
            ignoreHosts = HostFilter.getCollectionSet(accId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in getting ignore host ids " + e.toString());
        }
        if (ignoreHosts == null) {
            ignoreHosts = new HashSet<>();
        }

        if (kafkaUtils.isWriteEnabled()) {

            try {
                    Set<Integer> indicesToDelete = new HashSet<>();
                    int i = 0;
                    for (BulkUpdates bulkUpdate : writesForSti) {
                        boolean ignore = false;
                        int apiCollectionId = -1;
                        String url = null, method = null, param = null;
                        for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                            if (entry.getKey().equalsIgnoreCase(SingleTypeInfo._API_COLLECTION_ID)) {
                                String valStr = entry.getValue().toString();
                                int val = Integer.valueOf(valStr);
                                apiCollectionId = val;
                                if (ignoreHosts.contains(val)) {
                                    ignore = true;
                                }
                                if(UsageMetricCalculator.getDeactivated().contains(apiCollectionId)){
                                    ignore = true;
                                }
                            } else if(entry.getKey().equalsIgnoreCase(SingleTypeInfo._URL)){
                                url = entry.getValue().toString();
                                // Skip URLs based on validation rules
                                if (DataInsertionPreChecks.shouldSkipUrl(accId, url)) {
                                    ignore = true;
                                }
                            } else if(entry.getKey().equalsIgnoreCase(SingleTypeInfo._METHOD)){
                                method = entry.getValue().toString();
                                if ("OPTIONS".equals(method) || "CONNECT".equals(method)) {
                                    ignore = true;
                                }
                            } else if(entry.getKey().equalsIgnoreCase(SingleTypeInfo._PARAM)){
                                param = entry.getValue().toString();
                            }
                        }
                        if (!ignore && apiCollectionId != -1 && url != null && method != null && param!=null) {
                            boolean isNew = ParamFilter.isNewEntry(accId, apiCollectionId, url, method, param);
                            if (!isNew) {
                                ignore = true;
                            }
                        }
                        if(ignore){
                            indicesToDelete.add(i);
                        }
                        i++;
                    }

                    if (writesForSti != null && !writesForSti.isEmpty() &&
                            indicesToDelete != null && !indicesToDelete.isEmpty()) {
                        int size = writesForSti.size();
                        List<BulkUpdates> tempWrites = new ArrayList<>();
                        for (int index = 0; index < size; index++) {
                            if (indicesToDelete.contains(index)) {
                                continue;
                            }
                            tempWrites.add(writesForSti.get(index));
                        }
                        writesForSti = tempWrites;
                        int newSize = writesForSti.size();
                        loggerMaker.infoAndAddToDb(String.format("Original writes: %d Final writes: %d", size, newSize));
                    }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "error in ignore STI updates " + e.toString());
                e.printStackTrace();
            }

            if (writesForSti != null && !writesForSti.isEmpty()) {
                kafkaUtils.insertData(writesForSti, "bulkWriteSti", accId);
            }

        } else {
            loggerMaker.infoAndAddToDb("Entering writes size: " + writesForSti.size());
            try {
                ArrayList<WriteModel<SingleTypeInfo>> writes = new ArrayList<>();
                int ignoreCount =0;
                for (BulkUpdates bulkUpdate: writesForSti) {
                    List<Bson> filters = new ArrayList<>();
                    boolean ignore = false;
                    for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("isUrlParam")) {
                            continue;
                        }
                        if (entry.getKey().equalsIgnoreCase("apiCollectionId")) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            if (ignoreHosts.contains(val)) {
                                ignore = true;
                                break;
                            }
                            filters.add(Filters.eq(entry.getKey(), val));
                        } else if (entry.getKey().equalsIgnoreCase("responseCode")) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            filters.add(Filters.eq(entry.getKey(), val));
                        } else {
                            filters.add(Filters.eq(entry.getKey(), entry.getValue()));
                        }
                    }
                    if (ignore) {
                        ignoreCount++;
                        continue;
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

                loggerMaker.infoAndAddToDb(String.format("Consumer data: %d ignored: %d writes: %d", writesForSti.size(), ignoreCount, writes.size()));

                if (writes != null && !writes.isEmpty()) {
                    DbLayer.bulkWriteSingleTypeInfo(writes);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteSti " + e.toString());
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSampleData() {
        int accId = Context.accountId.get();
        if (kafkaUtils.isWriteEnabled()) {
            kafkaUtils.insertData(writesForSampleData, "bulkWriteSampleData", accId);
        } else {
            try {
                loggerMaker.infoAndAddToDb("bulkWriteSampleData called");
                ArrayList<WriteModel<SampleData>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForSampleData) {
                    Map<String, Object> mObj = (Map) bulkUpdate.getFilters().get("_id");
                    String apiCollectionIdStr = mObj.get("apiCollectionId").toString();
                    int apiCollectionId = Integer.valueOf(apiCollectionIdStr);

                    if(UsageMetricCalculator.getDeactivated().contains(apiCollectionId)){
                        continue;
                    }

                    String bucketEndEpochStr = mObj.get("bucketEndEpoch").toString();
                    int bucketEndEpoch = Integer.valueOf(bucketEndEpochStr);

                    String bucketStartEpochStr = mObj.get("bucketStartEpoch").toString();
                    int bucketStartEpoch = Integer.valueOf(bucketStartEpochStr);

                    String responseCodeStr = mObj.get("responseCode").toString();
                    int responseCode = Integer.valueOf(responseCodeStr);

                    String url = (String) mObj.get("url");
                    String method = (String) mObj.get("method");

                    // Skip URLs based on validation rules
                    if (DataInsertionPreChecks.shouldSkipUrl(accId, url)) {
                        continue;
                    }

                    if ("OPTIONS".equals(method) || "CONNECT".equals(method)) {
                        continue;
                    }

                    Bson filters = Filters.and(Filters.eq("_id.apiCollectionId", apiCollectionId),
                            Filters.eq("_id.bucketEndEpoch", bucketEndEpoch),
                            Filters.eq("_id.bucketStartEpoch", bucketStartEpoch),
                            Filters.eq("_id.method", method),
                            Filters.eq("_id.responseCode", responseCode),
                            Filters.eq("_id.url", url));
                    List<String> updatePayloadList = bulkUpdate.getUpdates();
                    SampleDataLogs.printLog(apiCollectionId, method, url);

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
                        } else if(field.equals(SampleData.SAMPLES)){
                            List<String> dVal = (List) json.get("val");
                            RedactAlert.submitSampleDataForChecking(dVal, apiCollectionId, method, url);
                            SampleDataLogs.insertCount(apiCollectionId, method, url, dVal.size());
                            updatePayload = new UpdatePayload((String) json.get("field"), dVal , (String) json.get("op"));
                            updates.add(Updates.pushEach(updatePayload.getField(), dVal, new PushOptions().slice(-10)));
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
                if(writes!=null && !writes.isEmpty()){
                    DbLayer.bulkWriteSampleData(writes);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteSampleData " + e.toString());
                e.printStackTrace();
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSensitiveSampleData() {
        int accId = Context.accountId.get();
        if (kafkaUtils.isWriteEnabled()) {
            kafkaUtils.insertData(writesForSensitiveSampleData, "bulkWriteSensitiveSampleData", accId);
        } else {
            try {
                loggerMaker.infoAndAddToDb("bulkWriteSensitiveSampleData called");
                ArrayList<WriteModel<SensitiveSampleData>> writes = new ArrayList<>();
                for (BulkUpdates bulkUpdate: writesForSensitiveSampleData) {
                    Bson filters = Filters.empty();
                    int apiCollectionId = 0;
                    boolean ignore = false;
                    String url = null;
                    for (Map.Entry<String, Object> entry : bulkUpdate.getFilters().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase("_id.apiCollectionId") ) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            apiCollectionId = val;
                            if(UsageMetricCalculator.getDeactivated().contains(apiCollectionId)){
                                ignore = true;
                                break;
                            }
                            filters = Filters.and(filters, Filters.eq(entry.getKey(), val));
                        } else if(entry.getKey().equalsIgnoreCase("_id.responseCode")) {
                            String valStr = entry.getValue().toString();
                            int val = Integer.valueOf(valStr);
                            filters = Filters.and(filters, Filters.eq(entry.getKey(), val));
                        } else if(entry.getKey().equalsIgnoreCase("_id.url")) {
                            url = entry.getValue().toString();
                            // Skip URLs based on validation rules
                            if (DataInsertionPreChecks.shouldSkipUrl(accId, url)) {
                                ignore = true;
                                break;
                            }
                            filters = Filters.and(filters, Filters.eq(entry.getKey(), entry.getValue()));
                        } else {
                            filters = Filters.and(filters, Filters.eq(entry.getKey(), entry.getValue()));
                            try {
                                String key = entry.getKey();
                                String value = (String) entry.getValue();
                                if ("_id.method".equals(key)
                                        && ("OPTIONS".equals(value) || "CONNECT".equals(value))) {
                                    ignore = true;
                                    break;
                                }
                            } catch (Exception e){
                            }
                        }
                    }

                    if(ignore){
                        continue;
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
                        RedactAlert.submitSensitiveSampleDataCall(apiCollectionId);
                        writes.add(
                            new UpdateOneModel<>(filters, Updates.combine(updates), new UpdateOptions().upsert(true))
                        );
                    }
                }
                if(writes!=null && !writes.isEmpty()){
                    DbLayer.bulkWriteSensitiveSampleData(writes);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteSensitiveSampleData " + e.toString());
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTrafficInfo() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertDataTraffic(writesForTrafficInfo, "bulkWriteTrafficInfo", accId);
        } else {
            try {
                loggerMaker.infoAndAddToDb("bulkWriteTrafficInfo called");
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
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteTrafficInfo " + e.toString());
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTrafficMetrics() {
        if (kafkaUtils.isWriteEnabled()) {
            int accId = Context.accountId.get();
            kafkaUtils.insertDataTraffic(writesForTrafficMetrics, "bulkWriteTrafficMetrics", accId);
        } else {
            try {
                loggerMaker.infoAndAddToDb("bulkWriteTrafficInfo called");
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
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteTrafficMetrics " + e.toString());
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
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteSensitiveParamInfo " + e.toString());
                return Action.ERROR.toUpperCase();
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteTestingRunIssues() {
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
                        } else if (field.equals(TestingRunIssues.KEY_SEVERITY)) {

                            /*
                             * Fixing severity temp. here,
                             * cause the info. from mini-testing always contains HIGH.
                             * To be fixed in mini-testing.
                             */
                            /*
                             * Severity from info. fixed,
                             * so taking for dynamic_severity,
                             * since rest would be same and for old deployments.
                             */
                            String testSubCategory = idd.getTestSubCategory();
                            YamlTemplate template = YamlTemplateDao.instance
                                    .findOne(Filters.eq(Constants.ID, testSubCategory));
                            String dVal = (String) json.get("val");

                            if (template != null) {
                                String severity = template.getInfo().getSeverity();
                                if (severity != null && !"dynamic_severity".equals(severity)) {
                                    dVal = severity;
                                }
                            }

                            UpdatePayload updatePayload = new UpdatePayload((String) json.get("field"), dVal,
                                    (String) json.get("op"));
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
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteTestingRunIssues " + e.toString());
                if (kafkaUtils.isWriteEnabled()) {
                    kafkaUtils.insertDataSecondary(writesForTestingRunIssues, "bulkWriteTestingRunIssues", Context.accountId.get());
                }
                return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteOverageInfo() {
        try {
            loggerMaker.infoAndAddToDb("bulkWriteOverageInfo called");
            ArrayList<WriteModel<UningestedApiOverage>> writes = new ArrayList<>();
            for (BulkUpdates bulkUpdate: writesForOverageInfo) {
                // Create filter for the document
                if (bulkUpdate.getFilters().get(UningestedApiOverage.METHOD) instanceof String) {
                    String method = (String) bulkUpdate.getFilters().get(UningestedApiOverage.METHOD);
                    if (Method.OPTIONS.name().equalsIgnoreCase(method) || "CONNECT".equalsIgnoreCase(method)) {
                        continue;
                    }
                }
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
            loggerMaker.errorAndAddToDb(e, "Error in findTestSourceConfig " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiConfig() {
        try {
            apiConfig = DbLayer.fetchApiconfig(configName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchApiConfig " + e.toString());
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
            loggerMaker.errorAndAddToDb(e, "Error in fetchStiBasedOnHostHeaders " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchDeactivatedCollections() {
        try {
            apiCollectionIds = DbLayer.fetchDeactivatedCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchDeactivatedCollections " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateUsage() {
        try {
            MetricTypes metric = MetricTypes.valueOf(metricType);
            DbLayer.updateUsage(metric, deltaUsage);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateUsage " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCollectionIds() {
        try {
            apiCollectionIds = DbLayer.fetchApiCollectionIds();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchApiCollectionIds " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    private static final List<Integer> HIGHER_STI_LIMIT_ACCOUNT_IDS = Arrays.asList(1736798101, 1718042191);
    private static final int STI_LIMIT = 20_000_000;
    private static final int higherStiLimit = Integer.parseInt(System.getenv().getOrDefault("HIGHER_STI_LIMIT", String.valueOf(STI_LIMIT)));

    public String fetchEstimatedDocCount() {
        try {
            count = DbLayer.fetchEstimatedDocCount();
            if (HIGHER_STI_LIMIT_ACCOUNT_IDS.contains(Context.accountId.get())) {
                // Mini runtime skips traffic processing for more than 20M STIs, 
                // This will help allowing 20M more STIs to be processed.
                loggerMaker.infoAndAddToDb("HIGH STI customer fetchEstimatedDocCount:" + count + "subtractedCountSent: " + (count - higherStiLimit));
                count = count >= STI_LIMIT ? count - higherStiLimit: count;
            }
        } catch (Exception e){
            loggerMaker.errorAndAddToDb(e, "Error in fetchEstimatedDocCount " + e.toString());
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchRuntimeFilters() {
        try {
            runtimeFilters = DbLayer.fetchRuntimeFilters();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchRuntimeFilters " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchNonTrafficApiCollectionsIds() {
        try {
            apiCollectionIds = DbLayer.fetchNonTrafficApiCollectionsIds();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchNonTrafficApiCollectionsIds " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchStiOfCollections() {
        try {
            stis = DbLayer.fetchStiOfCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchStiOfCollections " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String getUnsavedSensitiveParamInfos() {
        try {
            sensitiveParamInfos = DbLayer.getUnsavedSensitiveParamInfos();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in getUnsavedSensitiveParamInfos " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSingleTypeInfo() {
        try {
            stis = DbLayer.fetchSingleTypeInfo(lastFetchTimestamp, lastSeenObjectId, resolveLoop);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchSingleTypeInfo " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllSingleTypeInfo() {
        try {
            stis = DbLayer.fetchAllSingleTypeInfo();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchAllSingleTypeInfo " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchActiveAccount() {
        try {
            account = DbLayer.fetchActiveAccount();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchActiveAccount " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateRuntimeVersion() {
        try {
            DbLayer.updateRuntimeVersion(fieldName, version);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateRuntimeVersion " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateKafkaIp() {
        try {
            DbLayer.updateKafkaIp(currentInstanceIp);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateKafkaIp " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointsInCollection() {
        try {
            endpoints = DbLayer.fetchEndpointsInCollection();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointsInCollection " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCollections() {
        try {
            apiCollections = DbLayer.fetchApiCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchApiCollections " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllApiCollections() {
        try {
            apiCollections = DbLayer.fetchAllApiCollections();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchAllApiCollections " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionSimple() {
        try {
            DbLayer.createCollectionSimple(vxlanId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in createCollectionSimple " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionForHost() {
        try {
            DbLayer.createCollectionForHost(host, colId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in createCollectionForHost " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertRuntimeLog() {
        try {
            int accId = Context.accountId.get();
            if (accId == 1733164172) {
                return Action.SUCCESS.toUpperCase();
            }
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertRuntimeLog(dbLog);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertRuntimeLog " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertAnalyserLog() {
        try {
            int accId = Context.accountId.get();
            if (accId == 1733164172) {
                return Action.SUCCESS.toUpperCase();
            }
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertAnalyserLog(dbLog);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertAnalyserLog " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String modifyHybridSaasSetting() {
        try {
            DbLayer.modifyHybridSaasSetting(isHybridSaas);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in modifyHybridSaasSetting " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSetup() {
        try {
            setup = DbLayer.fetchSetup();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchSetup " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchOrganization() {
        try {
            organization = DbLayer.fetchOrganization(accountId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchOrganization " + e.toString());
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
            loggerMaker.errorAndAddToDb(e, "Error in createTRRSummaryIfAbsent " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    private void updateTestingRunApisList(TestingRun testingRun) {
        if(testingRun != null){
            if(testingRun.getTestingEndpoints() instanceof CollectionWiseTestingEndpoints){
                CollectionWiseTestingEndpoints ts = (CollectionWiseTestingEndpoints) testingRun.getTestingEndpoints();
                CustomTestingEndpoints endpoints = new CustomTestingEndpoints(ts.returnApis());
                testingRun.setTestingEndpoints(endpoints);
            }
        }
    }

    public String findPendingTestingRun() {
        try {
            testingRun = DbLayer.findPendingTestingRun(delta, miniTestingName);
            updateTestingRunApisList(testingRun);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findPendingTestingRun " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findPendingTestingRunResultSummary() {
        try {
            trrs = DbLayer.findPendingTestingRunResultSummary(now, delta, miniTestingName);
            if (trrs != null) {
                trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findPendingTestingRunResultSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findTestingRunConfig() {
        try {
            testingRunConfig = DbLayer.findTestingRunConfig(testIdConfig);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findTestingRunConfig " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findTestingRun() {
        try {
            testingRun = DbLayer.findTestingRun(testingRunId);
            updateTestingRunApisList(testingRun);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findTestingRun " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String apiInfoExists() {
        try {
            exists = DbLayer.apiInfoExists(apiCollectionIds, urls);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in apiInfoExists " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAccessMatrixUrlToRole() {
        try {
            accessMatrixUrlToRole = DbLayer.fetchAccessMatrixUrlToRole(apiInfoKey);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchAccessMatrixUrlToRole " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllApiCollectionsMeta() {
        try {
           loggerMaker.error("init fetchAllApiCollectionsMeta account id: " + Context.accountId.get());
           apiCollections = DbLayer.fetchAllApiCollectionsMeta();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchAllApiCollectionsMeta " + e.toString() + " " + Context.accountId.get());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCollectionMeta() {
        try {
            apiCollection = DbLayer.fetchApiCollectionMeta(apiCollectionId);
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.error("fetchApiCollectionMeta account id: " + accountId);
            loggerMaker.errorAndAddToDb(e, "Error in fetchApiCollectionMeta " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiInfo() {
        try {
            apiInfo = DbLayer.fetchApiInfo(apiInfoKey);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchApiInfo " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointLogicalGroup() {
        try {
            endpointLogicalGroup = DbLayer.fetchEndpointLogicalGroup(logicalGroupName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointLogicalGroup " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointLogicalGroupById() {
        try {
            endpointLogicalGroup = DbLayer.fetchEndpointLogicalGroupById(endpointLogicalGroupId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointLogicalGroupById " + e.toString());
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
            loggerMaker.errorAndAddToDb(e, "Error in fetchIssuesByIds " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestTestingRunResult() {
        try {
            testingRunResults = DbLayer.fetchLatestTestingRunResult(testingRunResultSummaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchLatestTestingRunResult " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestTestingRunResultBySummaryId() {
        try {
            testingRunResults = DbLayer.fetchLatestTestingRunResultBySummaryId(summaryId, limit, skip);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchLatestTestingRunResultBySummaryId " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchMatchParamSti() {
        try {
            stis = DbLayer.fetchMatchParamSti(apiCollectionId, param);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchMatchParamSti " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchOpenIssues() {
        try {
            testingRunIssues = DbLayer.fetchOpenIssues(summaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchOpenIssues " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchPendingAccessMatrixInfo() {
        try {
            accessMatrixTaskInfos = DbLayer.fetchPendingAccessMatrixInfo(ts);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchPendingAccessMatrixInfo " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleData() {
        try {
            sampleDatas = DbLayer.fetchSampleData(apiCollectionIdsSet, skip);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchSampleData " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleDataById() {
        try {
            sampleData = DbLayer.fetchSampleDataById(apiCollectionId, url, method);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchSampleDataById " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleDataByIdMethod() {
        try {
            sampleData = DbLayer.fetchSampleDataByIdMethod(apiCollectionId, url, methodVal);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchSampleDataByIdMethod " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestAuthenticatedByApiCollectionId() {
        try {
            apiInfo = DbLayer.fetchLatestAuthenticatedByApiCollectionId(apiCollectionId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchLatestAuthenticatedByApiCollectionId " + e.toString());
            return Action.ERROR.toUpperCase();

        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestRole() {
        try {
            testRole = DbLayer.fetchTestRole(key);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestRole " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }
    
    public String fetchTestRoles() {
        try {
            testRoles = DbLayer.fetchTestRoles();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestRoles " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestRolesForRoleName() {
        try {
            testRoles = DbLayer.fetchTestRolesForRoleName(roleFromTask);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestRolesForRoleName " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestRolesforId() {
        try {
            testRole = DbLayer.fetchTestRolesforId(roleId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestRolesforId " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResultSummary() {
        try {
            trrs = DbLayer.fetchTestingRunResultSummary(testingRunResultSummaryId);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestingRunResultSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResultSummaryMap() {
        try {
            testingRunResultSummaryMap = DbLayer.fetchTestingRunResultSummaryMap(testingRunId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestingRunResultSummaryMap " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchTestingRunResults() {
        try {
            // testingRunResult = DbLayer.fetchTestingRunResults(filterForRunResult);
            loggerMaker.errorAndAddToDb("API called fetchTestingRunResults");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestingRunResults " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchToken() {
        try {
            token = DbLayer.fetchToken(organizationId, accountId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchToken " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchWorkflowTest() {
        try {
            workflowTest = DbLayer.fetchWorkflowTest(workFlowTestId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchWorkflowTest " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchYamlTemplates() {
        try {
            yamlTemplates = DbLayer.fetchYamlTemplates(fetchOnlyActive, skip);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchYamlTemplates " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    List<String> ids;

    public String fetchYamlTemplatesWithIds() {
        try {
            yamlTemplates = DbLayer.fetchYamlTemplatesWithIds(ids, fetchOnlyActive);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchYamlTemplatesWithIds " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findApiCollectionByName() {
        try {
            apiCollection = DbLayer.findApiCollectionByName(apiCollectionName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findApiCollectionByName " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findApiCollections() {
        try {
            apiCollections = DbLayer.findApiCollections(apiCollectionNames);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findApiCollections " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findSti() {
        try {
            sti = DbLayer.findSti(apiCollectionId, url, method);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findSti " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findStiByParam() {
        try {
            stis = DbLayer.findStiByParam(apiCollectionId, param);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findStiByParam " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String findStiWithUrlParamFilters() {
        try {
            sti = DbLayer.findStiWithUrlParamFilters(apiCollectionId, url, methodVal, responseCode, isHeader, param, isUrlParam);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findStiWithUrlParamFilters " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertActivity() {
        try {
            DbLayer.insertActivity((int) count);  
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertActivity " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertApiCollection() {
        try {
            DbLayer.insertApiCollection(apiCollectionId, apiCollectionName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertApiCollection " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertTestingRunResultSummary() {
        try {
            DbLayer.insertTestingRunResultSummary(trrs);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertTestingRunResultSummary " + e.toString());
            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.insertDataSecondary(trrs, "insertTestingRunResultSummary", Context.accountId.get());
            }
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertTestingRunResults() {
        try {

            Map<String, WorkflowNodeDetails> data = new HashMap<>();
            try {
                if (this.testingRunResult != null && this.testingRunResult.get("workflowTest") != null) {
                    Map<String, BasicDBObject> x = (Map) (((Map) this.testingRunResult.get("workflowTest"))
                            .get("mapNodeIdToWorkflowNodeDetails"));
                    if (x != null) {
                        for (String tmp : x.keySet()) {
                            ((Map) x.get(tmp)).remove("authMechanism");
                            ((Map) x.get(tmp)).remove("customAuthTypes");
                            data.put(tmp, objectMapper.convertValue(x.get(tmp), YamlNodeDetails.class));
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in insertTestingRunResults mapNodeIdToWorkflowNodeDetails" + e.toString());
                e.printStackTrace();
            }
            TestingRunResult testingRunResult = objectMapper.readValue(this.testingRunResult.toJson(), TestingRunResult.class);

            try {
                if (!data.isEmpty()) {
                    testingRunResult.getWorkflowTest().setMapNodeIdToWorkflowNodeDetails(data);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in insertTestingRunResults mapNodeIdToWorkflowNodeDetails2" + e.toString());
                e.printStackTrace();
            }

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
            loggerMaker.errorAndAddToDb(e, "Error in insertTestingRunResults " + e.toString());
            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.insertDataSecondary(testingRunResult, "insertTestingRunResults", Context.accountId.get());
            }
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertWorkflowTestResult() {
        try {
            DbLayer.insertWorkflowTestResult(workflowTestResult);        
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertWorkflowTestResult " + e.toString());
            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.insertDataSecondary(workflowTestResult, "insertWorkflowTestResult", Context.accountId.get());
            }
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String markTestRunResultSummaryFailed() {
        try {
            trrs = DbLayer.markTestRunResultSummaryFailed(testingRunResultSummaryId);
            trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in markTestRunResultSummaryFailed " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateAccessMatrixInfo() {
        try {
            DbLayer.updateAccessMatrixInfo(taskId, frequencyInSeconds);        
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateAccessMatrixInfo " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateAccessMatrixUrlToRoles() {
        try {
            DbLayer.updateAccessMatrixUrlToRoles(apiInfoKey, ret);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateAccessMatrixUrlToRoles " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateIssueCountInSummary() {
        try {
            ObjectId summaryObjectId = null;
            if (summaryId != null) {
                summaryObjectId = new ObjectId(summaryId);
            }
            if((operator == null || operator.isEmpty()) && summaryId != null){
                totalCountIssues = TestExecutor.calcTotalCountIssues(summaryObjectId);
                trrs = DbLayer.updateIssueCountInSummary(summaryId, totalCountIssues);
            }else{
                trrs = DbLayer.updateIssueCountInSummary(summaryId, totalCountIssues, operator);
            }
            if (trrs != null && trrs.getTestingRunId() != null) {
                trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "Error in updateIssueCountInSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateIssueCountInTestSummary() {
        try {
            DbLayer.updateIssueCountInTestSummary(summaryId, totalCountIssues, false);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateIssueCountInTestSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateLastTestedField() {
        try {
            DbLayer.updateLastTestedField(apiCollectionId, url, methodVal);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateLastTestedField " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestInitiatedCountInTestSummary() {
        try {
            DbLayer.updateTestInitiatedCountInTestSummary(summaryId, testInitiatedCount);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestInitiatedCountInTestSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestResultsCountInTestSummary() {
        try {
            DbLayer.updateTestResultsCountInTestSummary(summaryId, testResultsCount);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestResultsCountInTestSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestRunResultSummary() {
        try {
            DbLayer.updateTestRunResultSummary(summaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestRunResultSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestRunResultSummaryNoUpsert() {
        try {
            DbLayer.updateTestRunResultSummaryNoUpsert(testingRunResultSummaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestRunResultSummaryNoUpsert " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestingRun() {
        try {
            DbLayer.updateTestingRun(testingRunId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestingRun " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestingRunAndMarkCompleted() {
        try {
            DbLayer.updateTestingRunAndMarkCompleted(testingRunId, scheduleTs);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestingRunAndMarkCompleted " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateTotalApiCountInTestSummary() {
        try {
            DbLayer.updateTotalApiCountInTestSummary(summaryId, totalApiCount);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTotalApiCountInTestSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String modifyHybridTestingSetting() {
        try {
            DbLayer.modifyHybridTestingSetting(hybridTestingEnabled);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in modifyHybridTestingSetting " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String ingestMetricsData() {
        try {
            // Then ingest the new metrics
            DbLayer.ingestMetricsData(metricData);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in ingestMetricsData: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertTestingLog() {
        try {
            int accId = Context.accountId.get();
            if (accId == 1733164172) {
                return Action.SUCCESS.toUpperCase();
            }
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));

            // Skip writing cyborg call logs.
            if (dbLog.getLog().contains("ApiExecutor") &&
                    dbLog.getLog().contains("cyborg")) {
                return Action.SUCCESS.toUpperCase();
            }

            DbLayer.insertTestingLog(dbLog);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertTestingLog " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertProtectionLog() {
        try {
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertProtectionLog(dbLog);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertProtectionLog " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteDependencyNodes() {
        try {
            loggerMaker.infoAndAddToDb("bulkWriteDependencyNodes called");
            DbLayer.bulkWriteDependencyNodes(dependencyNodeList);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in bulkWriteDependencyNodes " + e.toString());
            if (kafkaUtils.isWriteEnabled()) {
                kafkaUtils.insertDataSecondary(dependencyNodeList, "bulkWriteDependencyNodes", Context.accountId.get());
            }
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLatestEndpointsForTesting() {
        try {
            newEps = DbLayer.fetchLatestEndpointsForTesting(startTimestamp, endTimestamp, apiCollectionId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchLatestEndpointsForTesting " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertRuntimeMetricsData() {
        try {
            DbLayer.insertRuntimeMetricsData(metricsData);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in insertRuntimeMetricsData " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String bulkWriteSuspectSampleData() {
        int accId = Context.accountId.get();
        if (kafkaUtils.isWriteEnabled()) {
            kafkaUtils.insertData(writesForSuspectSampleData, "bulkWriteSuspectSampleData", accId);
        } else {
            ArrayList<WriteModel<SuspectSampleData>> writes = new ArrayList<>();
            for (BulkUpdates update : writesForSuspectSampleData) {
                List<String> updates = update.getUpdates();
                try {
                    SuspectSampleData sd = objectMapper.readValue(
                            gson.toJson(gson.fromJson(updates.get(0), Map.class).get("val")), SuspectSampleData.class);
                    
                    if (DataInsertionPreChecks.shouldSkipUrl(accId, sd.getUrl())) {
                        loggerMaker.errorAndAddToDb("Skipping SuspectSampleData insert due to pre-checks: " + sd.getUrl());
                        continue;
                    }
                    
                    if (sd.getUrl() == null || sd.getUrl().isEmpty()) {
                        loggerMaker.errorAndAddToDb("Skipping SuspectSampleData insert due to null or empty URL: " + gson.toJson(sd));
                        continue;
                    }
                    
                    writes.add(new InsertOneModel<SuspectSampleData>(sd));
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in bulkWriteSuspectSampleData " + e.toString());
                }
            }
            try {
                DbLayer.bulkWriteSuspectSampleData(writes);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in bulkWriteSuspectSampleData " + e.toString());
            }
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchFilterYamlTemplates() {
        try {
            yamlTemplates = DbLayer.fetchFilterYamlTemplates();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchFilterYamlTemplates " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchActiveAdvancedFilters(){
        try {
            this.activeAdvancedFilters = DbLayer.fetchActiveFilterTemplates();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchActiveFilterTemplates " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchMergedUrls() {
        try {
            this.mergedUrls = DbLayer.fetchMergedUrls();
        } catch (Exception e) {
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchStatusOfTests(){
        try {
            this.currentlyRunningTests = DbLayer.fetchStatusOfTests();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchStatusOfTests " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateIssueCountAndStateInSummary() {
        try {
            if (summaryId != null) {
                ObjectId summaryObjectId = new ObjectId(summaryId);
                totalCountIssues = TestExecutor.calcTotalCountIssues(summaryObjectId);
            }
            trrs = DbLayer.updateIssueCountAndStateInSummary(summaryId, totalCountIssues, state);
            if (trrs != null && trrs.getTestingRunId() != null) {
                trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateIssueCountAndStateInSummary " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        // send slack alert
        try {
            int timeNow = Context.now();
            sendSlack(trrs, totalCountIssues, Context.accountId.get());
            loggerMaker.infoAndAddToDb("Slack alert sent successfully for trrs " + trrs.getId() + " accountId " + Context.accountId.get() + " time taken " + (Context.now() - timeNow));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in sending slack alert for testing" + e);
        }

        return Action.SUCCESS.toUpperCase();
    }

    public static void sendSlack(TestingRunResultSummary trrs, Map<String, Integer> totalCountIssues, int accountId) {
        TestingRun testingRun = DbLayer.findTestingRun(trrs.getTestingRunId().toHexString());
        loggerMaker.infoAndAddToDb("Trying to send slack alert for trrs " + trrs.getId() + " accountId " + accountId);

        if (!testingRun.getSendSlackAlert()) {
            loggerMaker.infoAndAddToDb("Not sending slack alert for trrs " + trrs.getId());
            return;
        }

        String summaryId = trrs.getHexId();

        int totalApis = trrs.getTotalApis();
        String testType =  TestingRun.findTestType(testingRun,trrs);
        int countIssues = totalCountIssues.values().stream().mapToInt(Integer::intValue).sum();
        long nextTestRun = testingRun.getPeriodInSeconds() == 0 ? 0 : ((long) testingRun.getScheduleTimestamp() + (long) testingRun.getPeriodInSeconds());
        long scanTimeInSeconds = Math.abs(Context.now() - trrs.getStartTimestamp());

        String collectionName = null;
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();
        if(testingEndpoints != null && testingEndpoints.getType() != null && testingEndpoints.getType().equals(TestingEndpoints.Type.COLLECTION_WISE)) {
            CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
            int apiCollectionId = collectionWiseTestingEndpoints.getApiCollectionId();
            ApiCollection apiCollection = DbLayer.fetchApiCollectionMeta(apiCollectionId);
            collectionName = apiCollection.getName();
        }

        int newIssues = 0;
        List<TestingRunIssues> testingRunIssuesList = DbLayer.fetchOpenIssues(summaryId);
        Map<String, Integer> apisAffectedCount = new HashMap<>();
        for (TestingRunIssues testingRunIssues: testingRunIssuesList) {
            String testSubCategory = testingRunIssues.getId().getTestSubCategory();
            int totalApisAffected = apisAffectedCount.getOrDefault(testSubCategory, 0)+1;
            apisAffectedCount.put(testSubCategory, totalApisAffected);
            if(testingRunIssues.getCreationTime() > trrs.getStartTimestamp()) newIssues++;
        }

        testingRunIssuesList.sort(Comparator.comparing(TestingRunIssues::getSeverity));

        List<NewIssuesModel> newIssuesModelList = new ArrayList<>();
        for(TestingRunIssues testingRunIssues : testingRunIssuesList) {
            if(testingRunIssues.getCreationTime() > trrs.getStartTimestamp()) {
                String testRunResultId;
                if(newIssuesModelList.size() <= 5) {
                    Bson filterForRunResult = Filters.and(
                            Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, testingRunIssues.getLatestTestingRunSummaryId()),
                            Filters.eq(TestingRunResult.TEST_SUB_TYPE, testingRunIssues.getId().getTestSubCategory()),
                            Filters.eq(TestingRunResult.API_INFO_KEY, testingRunIssues.getId().getApiInfoKey())
                    );
                    TestingRunResult testingRunResult = DbLayer.fetchTestingRunResults(filterForRunResult);
                    testRunResultId = testingRunResult.getHexId();
                } else testRunResultId = "";

                String issueCategory = testingRunIssues.getId().getTestSubCategory();
                newIssuesModelList.add(new NewIssuesModel(
                        issueCategory,
                        testRunResultId,
                        apisAffectedCount.get(issueCategory),
                        testingRunIssues.getCreationTime()
                ));
            }
        }
        loggerMaker.infoAndAddToDb("Build slack payload successfully for trrs " + trrs.getId() + " slack channel id " + testingRun.getSelectedSlackChannelId());
        SlackAlerts apiTestStatusAlert = new APITestStatusAlert(
                testingRun.getName(),
                totalCountIssues.getOrDefault(GlobalEnums.Severity.CRITICAL.name(), 0),
                totalCountIssues.getOrDefault(GlobalEnums.Severity.HIGH.name(), 0),
                totalCountIssues.getOrDefault(GlobalEnums.Severity.MEDIUM.name(), 0),
                totalCountIssues.getOrDefault(GlobalEnums.Severity.LOW.name(), 0),
                countIssues,
                newIssues,
                totalApis,
                collectionName,
                scanTimeInSeconds,
                testType,
                nextTestRun,
                newIssuesModelList,
                testingRun.getHexId(),
                summaryId
        );
        SlackSender.sendAlert(accountId, apiTestStatusAlert, testingRun.getSelectedSlackChannelId());
    }

    private static AtomicInteger tagHitCount = new AtomicInteger(0);
    private static AtomicInteger tagMissCount = new AtomicInteger(0);

    private List<CollectionTags> checkTagsNeedUpdates(List<CollectionTags> tags, int apiCollectionId) {
        
        if(tags == null || tags.isEmpty()){
            return null;
        }

        StringBuilder combinedTags = new StringBuilder();
        tags.sort(Comparator.comparing(CollectionTags::getKeyName));

        for (CollectionTags ctag : tags) {
            if (combinedTags.length() > 0) {
                combinedTags.append(", ");
            }
            combinedTags.append(ctag.getKeyName()).append("=").append(ctag.getValue());

        }

        String singleTagString = combinedTags.toString();
        if (ParamFilter.isNewEntry(Context.accountId.get(), apiCollectionId, "", "", singleTagString)) {
            tagMissCount.incrementAndGet();
            loggerMaker.info("New tags found for apiCollectionId: " + apiCollectionId
                + " accountId: " + Context.accountId.get() + " Bloom filter tagMissCount: " + tagMissCount.get());
            return tags;
        }

        // Monitor bloom filter efficacy
        tagHitCount.incrementAndGet();
        loggerMaker.info("Skipping tags updates, already present for apiCollectionId: " + apiCollectionId
                + " accountId: " + Context.accountId.get() + " Bloom filter tagHitCount: " + tagHitCount.get());
        return null;
    }

    public String createCollectionSimpleForVpc() {
        try {
            DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId, checkTagsNeedUpdates(tagsList, vxlanId));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in createCollectionSimpleForVpc " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createCollectionForHostAndVpc() {
        try {
            DbLayer.createCollectionForHostAndVpc(host, colId, vpcId, checkTagsNeedUpdates(tagsList, colId));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in createCollectionForHostAndVpc " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchEndpointsInCollectionUsingHost() {
        try {
            apiInfoList = DbLayer.fetchEndpointsInCollectionUsingHost(apiCollectionId, skip, deltaPeriodValue);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointsInCollectionUsingHost " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchOtpTestData() {
        try {
            otpTestData = DbLayer.fetchOtpTestData(uuid, currTime);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchOtpTestData " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchRecordedLoginFlowInput() {
        try {
            recordedLoginFlowInput = DbLayer.fetchRecordedLoginFlowInput();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchRecordedLoginFlowInput " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchLoginFlowStepsData() {
        try {
            loginFlowStepsData = DbLayer.fetchLoginFlowStepsData(userId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchLoginFlowStepsData " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String updateLoginFlowStepsData() {
        try {
            DbLayer.updateLoginFlowStepsData(userId, valuesMap);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateLoginFlowStepsData " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchDependencyFlowNodesByApiInfoKey() {
        try {
            node = DbLayer.fetchDependencyFlowNodesByApiInfoKey(apiCollectionId, url, methodVal);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchDependencyFlowNodesByApiInfoKey " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchSampleDataForEndpoints() {
        try {
            sampleDatas = DbLayer.fetchSampleDataForEndpoints(endpoints);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchSampleDataForEndpoints " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchNodesForCollectionIds() {
        try {
            nodes = DbLayer.fetchNodesForCollectionIds(apiCollectionIds,removeZeroLevel, skip);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchNodesForCollectionIds " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String countTestingRunResultSummaries() {
        count = DbLayer.countTestingRunResultSummaries(filter);
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
            loggerMaker.errorAndAddToDb(e, "error in bulkinsertApiHitCount " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String overageApisExists() {
        try {
            exists = DbLayer.overageApisExists(apiCollectionId, this.urlType, method, url);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in overageApisExists " + e.toString());
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
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestScript " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    List<String> testSuiteId;
    List<String> testSuiteTestSubCategories;
    public String findTestSubCategoriesByTestSuiteId() {
        try {
            testSuiteTestSubCategories = DbLayer.findTestSubCategoriesByTestSuiteId(testSuiteId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchTestSubCategoriesByTestSuiteId " + e.toString());
            return Action.ERROR.toUpperCase();
        }
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


    public List<SvcToSvcGraphEdge> svcToSvcGraphEdges;
    public List<SvcToSvcGraphNode> svcToSvcGraphNodes;

    public String findSvcToSvcGraphNodes() {
        try {
            this.svcToSvcGraphNodes = DbLayer.findSvcToSvcGraphNodes(startTimestamp, endTimestamp, skip, limit);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findSvcToSvcGraphNodes " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();

    }

    public String findSvcToSvcGraphEdges() {
        try {
            this.svcToSvcGraphEdges = DbLayer.findSvcToSvcGraphEdges(startTimestamp, endTimestamp, skip, limit);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findSvcToSvcGraphEdges " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String updateSvcToSvcGraphEdges() {
        try {
            DbLayer.updateSvcToSvcGraphEdges(this.svcToSvcGraphEdges);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateSvcToSvcGraphEdges " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String updateSvcToSvcGraphNodes() {
        try {
            DbLayer.updateSvcToSvcGraphNodes(this.svcToSvcGraphNodes);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateSvcToSvcGraphNodes " + e.toString());
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String updateTestingRunPlaygroundStateAndResult(){
        try {
            switch (this.getTestingRunPlaygroundType()) {
                case TEST_EDITOR_PLAYGROUND:
                    Map<String, WorkflowNodeDetails> data = new HashMap<>();
                    try {
                        if (this.testingRunResult != null && this.testingRunResult.get("workflowTest") != null) {
                            Map<String, BasicDBObject> x = (Map) (((Map) this.testingRunResult.get("workflowTest"))
                                    .get("mapNodeIdToWorkflowNodeDetails"));
                            if (x != null) {
                                for (String tmp : x.keySet()) {
                                    ((Map) x.get(tmp)).remove("authMechanism");
                                    ((Map) x.get(tmp)).remove("customAuthTypes");
                                    data.put(tmp, objectMapper.convertValue(x.get(tmp), YamlNodeDetails.class));
                                }
                            }
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in insertTestingRunResults in testingRunPlayground mapNodeIdToWorkflowNodeDetails" + e.toString());
                        e.printStackTrace();
                    }
                    TestingRunResult testingRunResult = objectMapper.readValue(this.testingRunResult.toJson(), TestingRunResult.class);
                    if(testingRunResult.getSingleTestResults()!=null){
                        testingRunResult.setTestResults(new ArrayList<>(testingRunResult.getSingleTestResults()));
                    }else if(testingRunResult.getMultiExecTestResults() !=null){
                        testingRunResult.setTestResults(new ArrayList<>(testingRunResult.getMultiExecTestResults()));
                    }

                    try {
                        if (!data.isEmpty()) {
                            testingRunResult.getWorkflowTest().setMapNodeIdToWorkflowNodeDetails(data);
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error in insertTestingRunResults testingRunPlayground mapNodeIdToWorkflowNodeDetails2" + e.toString());
                        e.printStackTrace();
                    }
                    DbLayer.updateTestingRunPlayground(new ObjectId(this.testingRunPlaygroundId), testingRunResult);
                    break;
                case POSTMAN_IMPORTS:
                    if (this.getOriginalHttpResponse() != null) {
                        DbLayer.updateTestingRunPlayground(new ObjectId(this.testingRunPlaygroundId), this.getOriginalHttpResponse());
                    }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateTestingRunPlaygroundStateAndResult " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchOpenApiSchema() {
        try {
            openApiSchema = DbLayer.fetchOpenApiSchema(apiCollectionId);
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "Error in fetchOpenApiSchema " + e.toString());
            return Action.ERROR.toUpperCase();
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String insertDataIngestionLog() {
        try {
            Log dbLog = new Log(log.getString("log"), log.getString("key"), log.getInt("timestamp"));
            DbLayer.insertDataIngestionLog(dbLog);
        } catch (Exception e) {
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "Error insertDataIngestionLog " + e.toString());
            return Action.ERROR.toUpperCase();
        }
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

    public boolean getHybridTestingEnabled() {
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

    public BasicDBObject getTestingRunResult() {
        return testingRunResult;
    }

    public void setTestingRunResult(BasicDBObject testingRunResult) {
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

    public List<ApiInfo.ApiInfoKey> getNewEps() {
        return newEps;
    }

    public void setNewEps(List<ApiInfo.ApiInfoKey> newEps) {
        this.newEps = newEps;
    }

    public BasicDBList getMetricsData() {
        return metricsData;
    }

    public void setMetricsData(BasicDBList metricsData) {
        this.metricsData = metricsData;
    }

    public void setTrafficCollectorMetrics(TrafficCollectorMetrics trafficCollectorMetrics) {
        this.trafficCollectorMetrics = trafficCollectorMetrics;
    }

    public List<BulkUpdates> getWritesForSuspectSampleData() {
        return writesForSuspectSampleData;
    }

    public void setWritesForSuspectSampleData(List<BulkUpdates> writesForSuspectSampleData) {
        this.writesForSuspectSampleData = writesForSuspectSampleData;
    }

    public List<YamlTemplate> getActiveAdvancedFilters() {
        return activeAdvancedFilters;
    }

    public void setActiveAdvancedFilters(List<YamlTemplate> activeAdvancedFilters) {
        this.activeAdvancedFilters = activeAdvancedFilters;
    }

    public Set<MergedUrls> getMergedUrls() {
        return mergedUrls;
    }

    public void setMergedUrls(Set<MergedUrls> mergedUrls) {
        this.mergedUrls = mergedUrls;
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


    public int getDeltaPeriodValue() {
        return deltaPeriodValue;
    }

    public void setDeltaPeriodValue(int deltaPeriodValue) {
        this.deltaPeriodValue = deltaPeriodValue;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public int getCurrTime() {
        return currTime;
    }

    public void setCurrTime(int currTime) {
        this.currTime = currTime;
    }

    public OtpTestData getOtpTestData() {
        return otpTestData;
    }

    public void setOtpTestData(OtpTestData otpTestData) {
        this.otpTestData = otpTestData;
    }

    public RecordedLoginFlowInput getRecordedLoginFlowInput() {
        return recordedLoginFlowInput;
    }

    public void setRecordedLoginFlowInput(RecordedLoginFlowInput recordedLoginFlowInput) {
        this.recordedLoginFlowInput = recordedLoginFlowInput;
    }

    public LoginFlowStepsData getLoginFlowStepsData() {
        return loginFlowStepsData;
    }

    public void setLoginFlowStepsData(LoginFlowStepsData loginFlowStepsData) {
        this.loginFlowStepsData = loginFlowStepsData;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public Map<String, Object> getValuesMap() {
        return valuesMap;
    }

    public void setValuesMap(Map<String, Object> valuesMap) {
        this.valuesMap = valuesMap;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public boolean getRemoveZeroLevel() {
        return removeZeroLevel;
    }

    public void setRemoveZeroLevel(boolean removeZeroLevel) {
        this.removeZeroLevel = removeZeroLevel;
    }

    public void setFilter(Bson filter) {
        this.filter = filter;
    }

    public TestScript getTestScript() {
        return testScript;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setTestSuiteId(List<String> testSuiteId) {
        this.testSuiteId = testSuiteId;
    }

    public List<String> getTestSuiteTestSubCategories() {
        return testSuiteTestSubCategories;
    }

    public TestingRunPlayground getTestingRunPlayground() {
        return testingRunPlayground;
    }

    public void setTestingRunPlayground(TestingRunPlayground testingRunPlayground) {
        this.testingRunPlayground = testingRunPlayground;
    }

    public List<SvcToSvcGraphEdge> getSvcToSvcGraphEdges() {
        return svcToSvcGraphEdges;
    }
    public void setSvcToSvcGraphEdges(List<SvcToSvcGraphEdge> svcToSvcGraphEdges) {
        this.svcToSvcGraphEdges = svcToSvcGraphEdges;
    }
    public List<SvcToSvcGraphNode> getSvcToSvcGraphNodes() {
        return svcToSvcGraphNodes;
    }
    public void setSvcToSvcGraphNodes(List<SvcToSvcGraphNode> svcToSvcGraphNodes) {
        this.svcToSvcGraphNodes = svcToSvcGraphNodes;
    }

    public String getTestingRunPlaygroundId() {
        return testingRunPlaygroundId;
    }

    public void setTestingRunPlaygroundId(String testingRunPlaygroundId) {
        this.testingRunPlaygroundId = testingRunPlaygroundId;
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public void setModuleInfo(ModuleInfo moduleInfo) {
        this.moduleInfo = moduleInfo;
    }

    public List<BasicDBObject> getApiHitCountInfoList() {
        return apiHitCountInfoList;
    }

    public void setApiHitCountInfoList(List<BasicDBObject> apiHitCountInfoList) {
        this.apiHitCountInfoList = apiHitCountInfoList;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public String getOpenApiSchema() {
        return openApiSchema;
    }

    public void setOpenApiSchema(String openApiSchema) {
        this.openApiSchema = openApiSchema;
    }

    public TestingRunPlayground.TestingRunPlaygroundType getTestingRunPlaygroundType() {
        if (testingRunPlaygroundType == null) {
            return TestingRunPlayground.TestingRunPlaygroundType.TEST_EDITOR_PLAYGROUND;
        }
        return testingRunPlaygroundType;
    }

    public void setTestingRunPlaygroundType(TestingRunPlayground.TestingRunPlaygroundType testingRunPlaygroundType) {
        this.testingRunPlaygroundType = testingRunPlaygroundType;
    }

    public OriginalHttpResponse getOriginalHttpResponse() {
        return originalHttpResponse;
    }

    public void setOriginalHttpResponse(OriginalHttpResponse originalHttpResponse) {
        this.originalHttpResponse = originalHttpResponse;
    }

    public String getMiniTestingName() {
        return miniTestingName;
    }

    public void setMiniTestingName(String miniTestingName) {
        this.miniTestingName = miniTestingName;
    }

    public List<MetricData> getMetricData() {
        return metricData;
    }

    public void setMetricData(List<MetricData> metricData) {
        this.metricData = metricData;
    }

    public String deleteTestRunResultSummary() {
        try {
            DbLayer.deleteTestRunResultSummary(testingRunResultSummaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in deleteTestRunResultSummary: " + e, LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteTestingRunResults() {
        try {
            DbLayer.deleteTestingRunResults(testingRunResultId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in deleteTestingRunResults: " + e, LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String updateStartTsTestRunResultSummary() {
        try {
            DbLayer.updateStartTsTestRunResultSummary(testingRunResultSummaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateStartTsTestRunResultSummary: " + e, LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchRerunTestingRunResult() {
        try {
            this.testingRunResults = DbLayer.fetchRerunTestingRunResult(testingRunResultSummaryId);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchRerunTestingRunResult: " + e, LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchRerunTestingRunResultSummary() {
        try {
            trrs = DbLayer.fetchRerunTestingRunResultSummary(testingRunResultSummaryId);
            if (trrs != null) {
                trrs.setTestingRunHexId(trrs.getTestingRunId().toHexString());
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchRerunTestingRunResultSummary: " + e, LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    @Getter
    List<SlackWebhook> slackWebhooks;

    public String fetchSlackWebhooks() {
        try {
            this.slackWebhooks = DbLayer.fetchSlackWebhooks();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in fetchSlackWebhooks: " + e, LogDb.DB_ABS);
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    private String result;
    private String testingRunResultId;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getTestingRunResultId() {
        return testingRunResultId;
    }

    public void setTestingRunResultId(String testingRunResultId) {
        this.testingRunResultId = testingRunResultId;
    }

    public void setUrlType(String urlType) {
        this.urlType = urlType;
    }
}
