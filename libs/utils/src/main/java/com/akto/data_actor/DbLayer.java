package com.akto.data_actor;

import com.akto.bulk_update_util.ApiInfoBulkUpdate;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.billing.TokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.*;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;

public class DbLayer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbLayer.class, LoggerMaker.LogDb.DASHBOARD);

    public DbLayer() {
    }

    public static List<CustomDataType> fetchCustomDataTypes() {
        return CustomDataTypeDao.instance.findAll(new BasicDBObject());
    }

    public static List<AktoDataType> fetchAktoDataTypes() {
        return AktoDataTypeDao.instance.findAll(new BasicDBObject());
    }

    public static List<CustomAuthType> fetchCustomAuthTypes() {
        return CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
    }

    public static void updateApiCollectionName(int vxlanId, String name) {
        ApiCollectionsDao.instance.getMCollection().updateMany(
                Filters.eq(ApiCollection.VXLAN_ID, vxlanId),
                Updates.set(ApiCollection.NAME, name)
        );
    }

    public static void updateCidrList(List<String> cidrList) {
        AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(), Updates.addEachToSet("privateCidrList", cidrList),
                new UpdateOptions().upsert(true)
        );
    }

    public static AccountSettings fetchAccountSettings() {
        return AccountSettingsDao.instance.findOne(new BasicDBObject());
    }

    public static AccountSettings fetchAccountSettings(int accountId) {
        Bson filters = Filters.eq("_id", accountId);
        return AccountSettingsDao.instance.findOne(filters);
    }

    public static List<ApiInfo> fetchApiInfos() {
        return ApiInfoDao.instance.findAll(new BasicDBObject());
    }

    public static List<ApiInfo> fetchNonTrafficApiInfos() {
        List<ApiCollection> nonTrafficApiCollections = ApiCollectionsDao.instance.fetchNonTrafficApiCollections();
        List<Integer> apiCollectionIds = new ArrayList<>();
        for (ApiCollection apiCollection: nonTrafficApiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }
        return ApiInfoDao.instance.findAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
    }

    public static void bulkWriteApiInfo(List<ApiInfo> apiInfoList) {
        List<WriteModel<ApiInfo>> writesForApiInfo = ApiInfoBulkUpdate.getUpdatesForApiInfo(apiInfoList);
        ApiInfoDao.instance.getMCollection().bulkWrite(writesForApiInfo);
    }
    public static void bulkWriteSingleTypeInfo(List<WriteModel<SingleTypeInfo>> writesForSingleTypeInfo) {
        BulkWriteResult res = SingleTypeInfoDao.instance.getMCollection().bulkWrite(writesForSingleTypeInfo);
    }

    public static void bulkWriteSampleData(List<WriteModel<SampleData>> writesForSampleData) {
        SampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);
    }

    public static void bulkWriteSensitiveSampleData(List<WriteModel<SensitiveSampleData>> writesForSensitiveSampleData) {
        SensitiveSampleDataDao.instance.getMCollection().bulkWrite(writesForSensitiveSampleData);
    }

    public static void bulkWriteTrafficInfo(List<WriteModel<TrafficInfo>> writesForTrafficInfo) {
        TrafficInfoDao.instance.getMCollection().bulkWrite(writesForTrafficInfo);
    }

    public static void bulkWriteTrafficMetrics(List<WriteModel<TrafficMetrics>> writesForTrafficMetrics) {
        TrafficMetricsDao.instance.getMCollection().bulkWrite(writesForTrafficMetrics);
    }

    public static void bulkWriteSensitiveParamInfo(List<WriteModel<SensitiveParamInfo>> writesForSensitiveParamInfo) {
        SensitiveParamInfoDao.instance.getMCollection().bulkWrite(writesForSensitiveParamInfo);
    }

    public static APIConfig fetchApiconfig(String configName) {
        return APIConfigsDao.instance.findOne(Filters.eq("name", configName));
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

    public static List<SingleTypeInfo> fetchStiBasedOnHostHeaders() {
        Bson filterForHostHeader = SingleTypeInfoDao.filterForHostHeader(-1,false);
        Bson filterQ = Filters.and(filterForHostHeader, Filters.regex(SingleTypeInfo._URL, "STRING|INTEGER"));
        return SingleTypeInfoDao.instance.findAll(filterQ, Projections.exclude(SingleTypeInfo._VALUES));
    }

    public static List<Integer> fetchApiCollectionIds() {
        List<Integer> apiCollectionIds = new ArrayList<>();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
                Projections.include("_id"));
        for (ApiCollection apiCollection: apiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }
        return apiCollectionIds;
    }

    public static long fetchEstimatedDocCount() {
        return SingleTypeInfoDao.instance.getMCollection().estimatedDocumentCount();
    }

    public static List<RuntimeFilter> fetchRuntimeFilters() {
        return RuntimeFilterDao.instance.findAll(new BasicDBObject());
    }

    public static List<Integer> fetchNonTrafficApiCollectionsIds() {
        List<ApiCollection> nonTrafficApiCollections = ApiCollectionsDao.instance.fetchNonTrafficApiCollections();
        List<Integer> apiCollectionIds = new ArrayList<>();
        for (ApiCollection apiCollection: nonTrafficApiCollections) {
            apiCollectionIds.add(apiCollection.getId());
        }

        return apiCollectionIds;
    }

    public static List<SingleTypeInfo> fetchStiOfCollections() {
        List<Integer> apiCollectionIds = fetchNonTrafficApiCollectionsIds();
        Bson filters = Filters.in(SingleTypeInfo._API_COLLECTION_ID, apiCollectionIds);
        List<SingleTypeInfo> stis = new ArrayList<>();
        try {
            stis = SingleTypeInfoDao.instance.findAll(filters);
            for (SingleTypeInfo sti: stis) {
                try {
                    sti.setStrId(sti.getId().toHexString());
                } catch (Exception e) {
                    System.out.println("error" + e);
                }
            }
        } catch (Exception e) {
            System.out.println("error" + e);
        }
        return stis;
    }

    public static List<SensitiveParamInfo> getUnsavedSensitiveParamInfos() {
        return SensitiveParamInfoDao.instance.findAll(
                Filters.and(
                        Filters.or(
                                Filters.eq(SensitiveParamInfo.SAMPLE_DATA_SAVED,false),
                                Filters.not(Filters.exists(SensitiveParamInfo.SAMPLE_DATA_SAVED))
                        ),
                        Filters.eq(SensitiveParamInfo.SENSITIVE, true)
                )
        );
    }

    public static List<SingleTypeInfo> fetchSingleTypeInfo(int lastFetchTimestamp, String lastSeenObjectId, boolean resolveLoop) {
        if (resolveLoop) {
            Bson filters = Filters.eq("timestamp", lastFetchTimestamp);
            if (lastSeenObjectId != null) {
                filters = Filters.and(filters, Filters.gt("_id", new ObjectId(lastSeenObjectId)));
            }
            Bson sort = Sorts.ascending(Arrays.asList("_id"));
            return SingleTypeInfoDao.instance.findAll(filters, 0, 1000, sort, Projections.exclude(SingleTypeInfo._VALUES));
        } else {
            Bson filters = Filters.gte("timestamp", lastFetchTimestamp);
            Bson sort = Sorts.ascending("timestamp");
            return SingleTypeInfoDao.instance.findAll(filters, 0, 1000, sort, Projections.exclude(SingleTypeInfo._VALUES));
        }
    }

    public static List<SingleTypeInfo> fetchAllSingleTypeInfo() {
        return SingleTypeInfoDao.instance.findAll(new BasicDBObject(), Projections.exclude(SingleTypeInfo._VALUES));
    }

    public static void updateRuntimeVersion(String fieldName, String version) {
        AccountSettingsDao.instance.updateOne(
                        AccountSettingsDao.generateFilter(),
                        Updates.set(fieldName, version)
                );
    }

    public static Account fetchActiveAccount() {
        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false)
        );

        return AccountsDao.instance.findOne(activeFilter);
    }

    public static void updateKafkaIp(String currentInstanceIp) {
        AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.CENTRAL_KAFKA_IP, currentInstanceIp+":9092")
        );
    }

    public static List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection() {
        int apiCollectionId = -1;
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId =
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                        .append("url", "$url")
                        .append("method", "$method");

        if (apiCollectionId != -1) {
            pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", apiCollectionId)));
        }

        Bson projections = Projections.fields(
                Projections.include("timestamp", "apiCollectionId", "url", "method")
        );

        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId));
        pipeline.add(Aggregates.sort(Sorts.descending("startTs")));

        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<ApiInfo.ApiInfoKey> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            BasicDBObject v = endpointsCursor.next();
            try {
                BasicDBObject vv = (BasicDBObject) v.get("_id");
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                        (int) vv.get("apiCollectionId"),
                        (String) vv.get("url"),
                        URLMethods.Method.fromString((String) vv.get("method"))
                );
                endpoints.add(apiInfoKey);
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        return endpoints;
    }

    public static void createCollectionSimple(int vxlanId) {
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        ApiCollectionsDao.instance.getMCollection().updateOne(
                Filters.eq("_id", vxlanId),
                Updates.combine(
                        Updates.set(ApiCollection.VXLAN_ID, vxlanId),
                        Updates.setOnInsert("startTs", Context.now()),
                        Updates.setOnInsert("urls", new HashSet<>())
                ),
                updateOptions
        );
    }

    public static void createCollectionForHost(String host, int id) {

        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);

        Bson updates = Updates.combine(
            Updates.setOnInsert("_id", id),
            Updates.setOnInsert("startTs", Context.now()),
            Updates.setOnInsert("urls", new HashSet<>())
        );

        ApiCollectionsDao.instance.getMCollection().findOneAndUpdate(Filters.eq(ApiCollection.HOST_NAME, host), updates, updateOptions);
    }

    public static void insertRuntimeLog(Log log) {
        RuntimeLogsDao.instance.insertOne(log);
    }

    public static void insertAnalyserLog(Log log) {
        AnalyserLogsDao.instance.insertOne(log);
    }

    public static void modifyHybridSaasSetting(boolean isHybridSaas) {
        Integer accountId = Context.accountId.get();
        AccountsDao.instance.updateOne(Filters.eq("_id", accountId), Updates.set(Account.HYBRID_SAAS_ACCOUNT, isHybridSaas));
    }

    public static Setup fetchSetup() {
        Setup setup = SetupDao.instance.findOne(new BasicDBObject());
        return setup;
    }

    public static List<ApiCollection> fetchApiCollections() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(),
                Projections.include("_id"));
        return apiCollections;
    }

    public static Organization fetchOrganization(int accountId) {
        return OrganizationsDao.instance.findOne(Filters.eq(Organization.ACCOUNTS, accountId));
    }

    public static void bulkWriteSuspectSampleData(List<WriteModel<SuspectSampleData>> writesForSingleTypeInfo) {
        SuspectSampleDataDao.instance.getMCollection().bulkWrite(writesForSingleTypeInfo);
    }

    public static List<YamlTemplate> fetchFilterYamlTemplates() {
        return FilterYamlTemplateDao.instance.findAll(Filters.empty());
    }

    public static void insertTestingLog(Log log) {
        LogsDao.instance.insertOne(log);
    }

    public static void insertProtectionLog(Log log) {
        ProtectionLogsDao.instance.insertOne(log);
    }

    public static Set<MergedUrls> fetchMergedUrls() {
        return MergedUrlsDao.instance.getMergedUrls();
    }

    public static void updateTestingRun(String testingRunId) {
        ObjectId testingRunObjId = new ObjectId(testingRunId);
        TestingRunDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq(Constants.ID, testingRunObjId),
            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));
    }

    public static Map<ObjectId, TestingRunResultSummary> fetchTestingRunResultSummaryMap(String testingRunId) {
        ObjectId testingRunObjId = new ObjectId(testingRunId);
        return TestingRunResultSummariesDao.instance.fetchLatestTestingRunResultSummaries(Collections.singletonList(testingRunObjId));
    }

    public static List<TestingRunResult> fetchLatestTestingRunResult(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        return TestingRunResultDao.instance.fetchLatestTestingRunResult(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId), 1);
    }

    public static TestingRunResultSummary fetchTestingRunResultSummary(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        return TestingRunResultSummariesDao.instance.findOne(Filters.eq(TestingRunResultSummary.ID, summaryObjectId));
    }

    public static TestingRunResultSummary markTestRunResultSummaryFailed(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        return TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.ID, summaryObjectId),
                        Filters.eq(TestingRunResultSummary.STATE, TestingRun.State.RUNNING)
                ),
                Updates.set(TestingRunResultSummary.STATE, TestingRun.State.FAILED)
        );
    }

    public static void insertTestingRunResultSummary(TestingRunResultSummary trrs) {
        TestingRunResultSummariesDao.instance.insertOne(trrs);
    }

    public static void updateTestingRunAndMarkCompleted(String testingRunId, int scheduleTimestamp) {
        Bson completedUpdate = Updates.combine(
            Updates.set(TestingRun.STATE, TestingRun.State.COMPLETED),
            Updates.set(TestingRun.END_TIMESTAMP, Context.now())
        );
        if (scheduleTimestamp > 0) {
            completedUpdate = Updates.combine(
                Updates.set(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Updates.set(TestingRun.END_TIMESTAMP, Context.now()),
                Updates.set(TestingRun.SCHEDULE_TIMESTAMP, scheduleTimestamp)
            );
        }
        ObjectId id = new ObjectId(testingRunId);
        TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq("_id", id),  completedUpdate
        );
    }

    public static List<TestingRunIssues> fetchOpenIssues(String summaryId) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        Bson filters = Filters.and(
            Filters.eq("latestTestingRunSummaryId", summaryObjectId),
            Filters.eq("testRunIssueStatus", "OPEN")
        );
        return TestingRunIssuesDao.instance.findAll(filters);
    }

    public static TestingRunResult fetchTestingRunResults(Bson filterForRunResult) {
        return TestingRunResultDao.instance.findOne(filterForRunResult, Projections.include("_id"));
    }

    public static ApiCollection fetchApiCollectionMeta(int apiCollectionId) {
        return ApiCollectionsDao.instance.getMeta(apiCollectionId);
    }

    public static List<ApiCollection> fetchAllApiCollectionsMeta() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(), Projections.exclude("urls", "conditions"));
        return apiCollections;
    }

    public static WorkflowTest fetchWorkflowTest(int workFlowTestId) {
        return WorkflowTestsDao.instance.findOne(
            Filters.eq("_id", workFlowTestId)
        );
    }

    public static void insertWorkflowTestResult(WorkflowTestResult workflowTestResult) {
        WorkflowTestResultsDao.instance.insertOne(workflowTestResult);
    }

    public static void updateIssueCountInTestSummary(String summaryId, Map<String, Integer> totalCountIssues, boolean includeOptions) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        TestingRunResultSummariesDao.instance.updateOne(
                Filters.eq("_id", summaryObjectId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.COMPLETED),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
                )
        );
    }

    public static void updateTestInitiatedCountInTestSummary(String summaryId, int testInitiatedCount) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, summaryObjectId),
            Updates.set(TestingRunResultSummary.TESTS_INITIATED_COUNT, testInitiatedCount));
    }

    public static List<YamlTemplate> fetchYamlTemplates(boolean fetchOnlyActive, int skip) {
        List<Bson> filters = new ArrayList<>();
        if (fetchOnlyActive) {
            filters.add(Filters.exists(YamlTemplate.INACTIVE, false));
            filters.add(Filters.eq(YamlTemplate.INACTIVE, false));
        } else {
            filters.add(new BasicDBObject());
        }
        return YamlTemplateDao.instance.findAll(Filters.or(filters), skip, 50, null);
    }

    public static void updateTestResultsCountInTestSummary(String summaryId, int testResultsCount) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, summaryObjectId),
            Updates.inc(TestingRunResultSummary.TEST_RESULTS_COUNT, testResultsCount));
    }

    public static void updateLastTestedField(int apiCollectionId, String url, String method) {
        URLMethods.Method methodEnum = URLMethods.Method.fromString(method);
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, methodEnum);
        ApiInfoDao.instance.updateLastTestedField(apiInfoKey);
    }

    public static void insertTestingRunResults(TestingRunResult testingRunResult) {
        TestingRunResultDao.instance.insertOne(testingRunResult);
    }

    public static void updateTotalApiCountInTestSummary(String summaryId, int totalApiCount) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        TestingRunResultSummariesDao.instance.updateOne(
            Filters.eq("_id", summaryObjectId),
            Updates.set(TestingRunResultSummary.TOTAL_APIS, totalApiCount));
    }

    public static void insertActivity(int count) {
        ActivitiesDao.instance.insertActivity("High Vulnerability detected", count + " HIGH vulnerabilites detected");
    }

    public static TestingRunResultSummary updateIssueCountInSummary(String summaryId, Map<String, Integer> totalCountIssues) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(Constants.ID, summaryObjectId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.COMPLETED),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)),
                options);
    }

    public static List<TestingRunResult> fetchLatestTestingRunResultBySummaryId(String summaryId, int limit, int skip) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        List<Bson> list = new ArrayList<>();
        list.add(Projections.exclude("testResults.originalMessage", "testResults.nodeResultMap"));
        return TestingRunResultDao.instance
                    .fetchLatestTestingRunResult(
                            Filters.and(
                                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                                    Filters.eq(TestingRunResult.VULNERABLE, true)),
                            limit,
                            skip, list);
    }

    public static List<TestRoles> fetchTestRoles() {
        return TestRolesDao.instance.findAll(new BasicDBObject());
    }

    public static List<SampleData> fetchSampleData(Set<Integer> apiCollectionIds, int skip) {
        Bson filterQ = Filters.in("_id.apiCollectionId", apiCollectionIds);
        return SampleDataDao.instance.findAll(filterQ, skip, 500, null);
    }

    public static TestRoles fetchTestRole(String key) {
        return TestRolesDao.instance.findOne(TestRoles.NAME, key);
    }

    public static TestRoles fetchTestRolesforId(String roleId) {
        return TestRolesDao.instance.findOne(Filters.eq("_id", new ObjectId(roleId)));
    }

    public static Tokens fetchToken(String organizationId, int accountId) {
        Bson filters = Filters.and(
                Filters.eq(Tokens.ORG_ID, organizationId),
                Filters.eq(Tokens.ACCOUNT_ID, accountId)
        );
        return TokensDao.instance.findOne(filters);
    }

    public static List<ApiCollection> findApiCollections(List<String> apiCollectionNames) {
        Bson fQuery = Filters.in(ApiCollection.NAME, apiCollectionNames);
        return ApiCollectionsDao.instance.findAll(fQuery);
    }

    public static boolean apiInfoExists(List<Integer> apiCollectionIds, List<String> urls) {
        Bson urlInCollectionQuery = Filters.and(
            Filters.in(ApiInfo.COLLECTION_IDS, apiCollectionIds),
            Filters.in(ApiInfo.ID_URL, urls)
        );
        return ApiInfoDao.instance.findOne(urlInCollectionQuery) != null;
    }

    public static ApiCollection findApiCollectionByName(String apiCollectionName) {
        return ApiCollectionsDao.instance.findByName(apiCollectionName);
    }

    public static void insertApiCollection(int apiCollectionId, String apiCollectionName) {
        ApiCollection apiCollection = new ApiCollection(apiCollectionId, apiCollectionName, Context.now(),new HashSet<>(), null, apiCollectionId, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);
    }

    public static List<TestingRunIssues> fetchIssuesByIds(Set<TestingIssuesId> issuesIds) {
        Bson inQuery = Filters.in("_id", issuesIds.toArray());
        return TestingRunIssuesDao.instance.findAll(inQuery);
    }

    public static List<SingleTypeInfo> findStiByParam(int apiCollectionId, String param) {
        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", apiCollectionId),
            Filters.regex("param", param),
            Filters.eq("isHeader", false)
        );
        return SingleTypeInfoDao.instance.findAll(filter, 0, 500, null);
    }

    public static SingleTypeInfo findSti(int apiCollectionId, String url, URLMethods.Method method) {
        Bson filters = Filters.and(
            Filters.eq("apiCollectionId", apiCollectionId),
            Filters.regex("url", url),
            Filters.eq("method", method)
        );
        return SingleTypeInfoDao.instance.findOne(filters);
    }

    public static AccessMatrixUrlToRole fetchAccessMatrixUrlToRole(ApiInfo.ApiInfoKey apiInfoKey) {
        Bson filterQ = Filters.eq("_id", apiInfoKey);
        return AccessMatrixUrlToRolesDao.instance.findOne(filterQ);
    }

    public static ApiInfo fetchApiInfo(ApiInfo.ApiInfoKey apiInfoKey) {
        return ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(apiInfoKey));
    }

    public static SampleData fetchSampleDataById(int apiCollectionId, String url, URLMethods.Method method) {
        Bson filterQSampleData = Filters.and(
            Filters.eq("_id.apiCollectionId", apiCollectionId),
            Filters.eq("_id.method", method),
            Filters.eq("_id.url", url)
        );
        return SampleDataDao.instance.findOne(filterQSampleData);
    }

    public static SingleTypeInfo findStiWithUrlParamFilters(int apiCollectionId, String url, String method, int responseCode, boolean isHeader, String param, boolean isUrlParam) {
        Bson urlParamFilters;
        if (!isUrlParam) {
            urlParamFilters = Filters.or(
                Filters.and(
                    Filters.exists("isUrlParam"),
                    Filters.eq("isUrlParam", isUrlParam)
                ),
                Filters.exists("isUrlParam", false)
            );

        } else {
            urlParamFilters = Filters.eq("isUrlParam", isUrlParam);
        }
        Bson filter = Filters.and(
            Filters.eq("apiCollectionId", apiCollectionId),
            Filters.eq("url", url),
            Filters.eq("method", method),
            Filters.eq("responseCode", responseCode),
            Filters.eq("isHeader", isHeader),
            Filters.regex("param", param),
            urlParamFilters
        );

        return SingleTypeInfoDao.instance.findOne(filter);
    }

    public static List<TestRoles> fetchTestRolesForRoleName(String roleFromTask) {
        return TestRolesDao.instance.findAll(TestRoles.NAME, roleFromTask);
    }

    public static List<AccessMatrixTaskInfo> fetchPendingAccessMatrixInfo(int ts) {
        Bson pendingTasks = Filters.lt(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, ts);
        return AccessMatrixTaskInfosDao.instance.findAll(pendingTasks);
    }

    public static void updateAccessMatrixInfo(String taskId, int frequencyInSeconds) {
        ObjectId taskObjId = new ObjectId(taskId);
        Bson q = Filters.eq(Constants.ID, taskObjId);
        Bson update = Updates.combine(
            Updates.set(AccessMatrixTaskInfo.LAST_COMPLETED_TIMESTAMP,Context.now()),
            Updates.set(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now() + frequencyInSeconds)
        );
        AccessMatrixTaskInfosDao.instance.updateOne(q, update);
    }

    public static EndpointLogicalGroup fetchEndpointLogicalGroup(String logicalGroupName) {
        return EndpointLogicalGroupDao.instance.findOne(EndpointLogicalGroup.GROUP_NAME, logicalGroupName);
    }

    public static EndpointLogicalGroup fetchEndpointLogicalGroupById(String endpointLogicalGroupId) {
        return EndpointLogicalGroupDao.instance.findOne("_id", new ObjectId(endpointLogicalGroupId));
    }

    public static void updateAccessMatrixUrlToRoles(ApiInfo.ApiInfoKey apiInfoKey, List<String> ret) {
        Bson q = Filters.eq(Constants.ID, apiInfoKey);
        Bson update = Updates.addEachToSet(AccessMatrixUrlToRole.ROLES, ret);
        UpdateOptions opts = new UpdateOptions().upsert(true);
        AccessMatrixUrlToRolesDao.instance.getMCollection().updateOne(q, update, opts);
    }

    public static List<SingleTypeInfo> fetchMatchParamSti(int apiCollectionId, String param) {
        Bson filters = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.or(
                    Filters.regex("param", param),
                    Filters.regex("param", param.toLowerCase())
                    )
            );

            return SingleTypeInfoDao.instance.findAll(filters, 0, 500, null, Projections.include("url", "method"));
    }

    public static SampleData fetchSampleDataByIdMethod(int apiCollectionId, String url, String method) {
        Bson filterQSampleData = Filters.and(
            Filters.eq("_id.apiCollectionId", apiCollectionId),
            Filters.eq("_id.method", method),
            Filters.eq("_id.url", url)
        );
        return SampleDataDao.instance.findOne(filterQSampleData);
    }

    public static void modifyHybridTestingSetting(boolean hybridTestingEnabled) {
        Integer accountId = Context.accountId.get();
        AccountsDao.instance.updateOne(Filters.eq("_id", accountId), Updates.set(Account.HYBRID_TESTING_ENABLED, hybridTestingEnabled));
    }

    public static List<ApiInfo.ApiInfoKey> fetchLatestEndpointsForTesting(int startTimestamp, int endTimestamp, int apiCollectionId) {
        return SingleTypeInfoDao.fetchLatestEndpointsForTesting(startTimestamp, endTimestamp, apiCollectionId);
    }

    public static void insertRuntimeMetricsData(BasicDBList metricsData) {

        ArrayList<WriteModel<RuntimeMetrics>> bulkUpdates = new ArrayList<>();
        RuntimeMetrics runtimeMetrics;
        for (Object metrics: metricsData) {
            try {
                Map<String, Object> obj = (Map) metrics;
                String name = (String) obj.get("metric_id");
                String instanceId = (String) obj.get("instance_id");
                String version = (String) obj.get("version");
                Long tsVal = (Long) obj.get("timestamp");
                int ts = tsVal.intValue();
                Double val = (Double) obj.get("val");
                if (name == null || name.length() == 0) {
                    continue;
                }
                runtimeMetrics = new RuntimeMetrics(name, ts, instanceId, version, val);
                bulkUpdates.add(new InsertOneModel<>(runtimeMetrics));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("error writing bulk update " + e.getMessage());
            }
        }

        if (bulkUpdates.size() > 0) {
            loggerMaker.infoAndAddToDb("insertRuntimeMetricsData bulk write size " + metricsData.size());
            RuntimeMetricsDao.bulkInsertMetrics(bulkUpdates);
        }
    }
}
