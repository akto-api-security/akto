package com.akto.data_actor;

import static com.akto.util.Constants.ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.bulk_update_util.ApiInfoBulkUpdate;
import com.akto.dao.*;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.settings.DataControlSettingsDao;
import com.akto.dao.testing.config.TestSuiteDao;
import com.akto.dependency_analyser.DependencyAnalyserUtils;
import com.akto.dto.*;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.settings.DataControlSettings;
import com.akto.dto.testing.config.TestSuites;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.billing.TokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dao.AgentTrafficLogDao;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.dependency_flow.Node;
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
import com.akto.dto.testing.TestingRun.State;
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dao.billing.UningestedApiOverageDao;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.usage.UsageMetricCalculator;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;

public class DbLayer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbLayer.class, LoggerMaker.LogDb.DASHBOARD);

    public DbLayer() {
    }

    public static List<CustomDataType> fetchCustomDataTypes() {
        return CustomDataTypeDao.instance.findAll(new BasicDBObject());
    }

    public static List<TestingRunResult> fetchRerunTestingRunResult(String summaryId) {
        return TestingRunResultDao.instance.findAll(
                Filters.and(
                        Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, new ObjectId(summaryId)),
                        Filters.eq(TestingRunResult.RERUN, true)
                ),
                Projections.include(
                        TestingRunResult.TEST_RUN_ID,
                        TestingRunResult.API_INFO_KEY,
                        TestingRunResult.TEST_SUB_TYPE,
                        TestingRunResult.VULNERABLE,
                        TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID
                )
        );
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
        loggerMaker.debug("bulk write result: del:" + res.getDeletedCount() + " ins:" + res.getInsertedCount() + " match:" + res.getMatchedCount() + " modify:" +res.getModifiedCount());
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

    public static void ingestMetric(List<MetricData> metricData) {
        MetricDataDao.instance.insertMany(metricData);
    }
    public static void bulkWriteTrafficMetrics(List<WriteModel<TrafficMetrics>> writesForTrafficMetrics) {
        TrafficMetricsDao.instance.getMCollection().bulkWrite(writesForTrafficMetrics);
    }

    public static void bulkWriteSensitiveParamInfo(List<WriteModel<SensitiveParamInfo>> writesForSensitiveParamInfo) {
        SensitiveParamInfoDao.instance.getMCollection().bulkWrite(writesForSensitiveParamInfo);
    }

    public static void bulkWriteTestingRunIssues(List<WriteModel<TestingRunIssues>> writeModelList) {
        BulkWriteResult result = TestingRunIssuesDao.instance.bulkWrite(writeModelList,
                new BulkWriteOptions().ordered(false));
        loggerMaker.infoAndAddToDb(String.format("Matched records : %s", result.getMatchedCount()), LogDb.TESTING);
        loggerMaker.infoAndAddToDb(String.format("inserted counts : %s", result.getInsertedCount()), LogDb.TESTING);
        loggerMaker.infoAndAddToDb(String.format("Modified counts : %s", result.getModifiedCount()), LogDb.TESTING);
    }

    public static void bulkWriteOverageInfo(List<WriteModel<UningestedApiOverage>> writeModelList) {
        BulkWriteResult result = UningestedApiOverageDao.instance.bulkWrite(writeModelList,
                new BulkWriteOptions().ordered(false));
        loggerMaker.infoAndAddToDb(String.format("OverageInfo bulk write - Matched: %s, Inserted: %s, Modified: %s", 
            result.getMatchedCount(), result.getInsertedCount(), result.getModifiedCount()), LogDb.RUNTIME);
    }

    public static TestSourceConfig findTestSourceConfig(String subType){
        return TestSourceConfigsDao.instance.getTestSourceConfig(subType);
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

    public static List<SingleTypeInfo> fetchStiBasedOnHostHeaders(ObjectId objectId) {
        Bson filterForHostHeader = SingleTypeInfoDao.filterForHostHeader(-1,false);
        Bson filterForSkip = Filters.gt("_id", objectId);
        Bson finalFilter = objectId == null ? filterForHostHeader : Filters.and(filterForHostHeader, filterForSkip);
        int limit = 1000;
        return SingleTypeInfoDao.instance.findAll(finalFilter, 0, limit, Sorts.ascending("_id"), Projections.exclude(SingleTypeInfo._VALUES));
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
                    loggerMaker.error("error" + e);
                }
            }
        } catch (Exception e) {
            loggerMaker.error("error" + e);
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
        int accountId = Context.accountId.get();
        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false));
        Bson idFilter = Filters.eq(ID, accountId);

        return AccountsDao.instance.findOne(Filters.and(idFilter, activeFilter));
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
                        Updates.set("urls", new HashSet<>())
                ),
                updateOptions
        );
    }

    public static void createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags) {
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        Bson updates = Updates.combine(
            Updates.set(ApiCollection.VXLAN_ID, vxlanId),
            Updates.setOnInsert("startTs", Context.now()),
            Updates.setOnInsert("urls", new HashSet<>()),
            Updates.set("userSetEnvType", vpcId)
        );
        if(tags != null && !tags.isEmpty()) {
            updates = Updates.combine(updates, Updates.set("tagsList", tags));
        }

        ApiCollectionsDao.instance.getMCollection().updateOne(
                Filters.eq("_id", vxlanId),
                updates,
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

    public static void createCollectionForHostAndVpc(String host, int id, String vpcId, List<CollectionTags> tags) {

        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);

        Bson updates = Updates.combine(
            Updates.setOnInsert("_id", id),
            Updates.setOnInsert(ApiCollection.HOST_NAME, host),
            Updates.setOnInsert("startTs", Context.now()),
            Updates.setOnInsert("urls", new HashSet<>()),
            Updates.set("userSetEnvType", vpcId)
        );

        if(tags != null && !tags.isEmpty()) {
            updates = Updates.combine(updates, Updates.set("tagsList", tags));
        }

        ApiCollectionsDao.instance.getMCollection().findOneAndUpdate(Filters.eq(ApiCollection.HOST_NAME, host), updates, updateOptions);
    }

    public static void insertRuntimeLog(Log log) {
        RuntimeLogsDao.instance.insertOne(log);
    }

    public static void insertAnalyserLog(Log log) {
        AnalyserLogsDao.instance.insertOne(log);
    }

    public static void insertTestingLog(Log log) {
        LogsDao.instance.insertOne(log);
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

    public static List<ApiCollection> fetchAllApiCollections() {
        return ApiCollectionsDao.instance.findAll(new BasicDBObject(),
            Projections.exclude("urls"));
    }

    public static Organization fetchOrganization(int accountId) {
        return OrganizationsDao.instance.findOne(Filters.eq(Organization.ACCOUNTS, accountId));
    }

    // testing queries

    public static TestingRunResultSummary createTRRSummaryIfAbsent(String testingRunHexId, int start) {
        ObjectId testingRunId = new ObjectId(testingRunHexId);

        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
                        Filters.eq(TestingRunResultSummary.STATE,TestingRun.State.SCHEDULED)
                ),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.RUNNING),
                        Updates.setOnInsert(TestingRunResultSummary.START_TIMESTAMP, start)
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
    }

    public static TestingRun findPendingTestingRun(int delta) {

        Bson filter1 = Filters.and(Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, Context.now())
        );
        Bson filter2 = Filters.and(
                Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
                Filters.lte(TestingRun.PICKED_UP_TIMESTAMP, delta)
        );

        Bson update = Updates.combine(
                Updates.set(TestingRun.PICKED_UP_TIMESTAMP, Context.now()),
                Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
        );

        // returns the previous state of testing run before the update
        return TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.or(filter1,filter2), update);
    }

    public static TestingRunResultSummary findPendingTestingRunResultSummary(int now, int delta) {

        Bson filter1 = Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
            Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now),
            Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
        );

        Bson filter2 = Filters.and(
            Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
            Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now - 5*60),
            Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
        );

        Bson update = Updates.set(TestingRun.STATE, TestingRun.State.RUNNING);

        TestingRunResultSummary trrs = TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(Filters.or(filter1,filter2), update);

        return trrs;
    }

    public static TestingRunConfig findTestingRunConfig(int testIdConfig) {
        return TestingRunConfigDao.instance.findOne(Constants.ID, testIdConfig);
    }

    public static TestingRun findTestingRun(String testingRunId) {
        ObjectId testingRunObjId = new ObjectId(testingRunId);
        return TestingRunDao.instance.findOne("_id", testingRunObjId);
    }

    public static void deleteTestRunResultSummary(String summaryId) {
        TestingRunResultSummariesDao.instance.deleteAll(Filters.eq(TestingRunResultSummary.ID, new ObjectId(summaryId)));
    }

    public static void deleteTestingRunResults(String testingRunResultId) {
        TestingRunResult trr = TestingRunResultDao.instance.getMCollection().findOneAndDelete(Filters.eq(ID, new ObjectId(testingRunResultId)));
        if (trr.isVulnerable()) {
            Bson filters = Filters.and(
                    Filters.eq(TestingRunResult.API_INFO_KEY, trr.getApiInfoKey()),
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, trr.getTestRunResultSummaryId()),
                    Filters.eq(TestingRunResult.TEST_SUB_TYPE, trr.getTestSubType())
            );
            //VulnerableTestingRunResultDao.instance.deleteAll(filters);
        }
    }

    public static void updateStartTsTestRunResultSummary(String summaryId) {
        TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(TestingRunResultSummary.ID, new ObjectId(summaryId)),
                Updates.set(TestingRunResultSummary.START_TIMESTAMP, Context.now()));
    }

    public static void updateTestRunResultSummaryNoUpsert(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                Filters.eq(Constants.ID, summaryObjectId),
                Updates.set(TestingRunResultSummary.STATE, State.STOPPED)
        );
    }

    public static void updateTestRunResultSummary(String summaryId) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq(Constants.ID, summaryObjectId),
            Updates.set(TestingRun.STATE, TestingRun.State.FAILED));
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

    public static TestingRunResultSummary fetchRerunTestingRunResultSummary(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        return TestingRunResultSummariesDao.instance.findOne(Filters.eq(TestingRunResultSummary.ORIGINAL_TESTING_RUN_SUMMARY_ID, summaryObjectId));
    }


    public static TestingRunResultSummary markTestRunResultSummaryFailed(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        return TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.ID, summaryObjectId),
                        Filters.eq(TestingRunResultSummary.STATE, State.RUNNING)
                ),
                Updates.set(TestingRunResultSummary.STATE, State.FAILED)
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
                        Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
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

    public static List<YamlTemplate> fetchYamlTemplatesWithIds(List<String> ids, boolean fetchOnlyActive) {
        Bson filter = Filters.in(Constants.ID, ids);
        if(fetchOnlyActive) {
            filter = Filters.and(
                Filters.or(
                    Filters.exists(YamlTemplate.INACTIVE, false),
                    Filters.eq(YamlTemplate.INACTIVE, false)
                )
            );
        }
        return YamlTemplateDao.instance.findAll(filter);
    }

    public static void updateTestResultsCountInTestSummary(String summaryId, int testResultsCount) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        TestingRunResultSummariesDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, summaryObjectId),
            Updates.inc(TestingRunResultSummary.TEST_RESULTS_COUNT, testResultsCount));
    }

    public static void updateLastTestedField(int apiCollectionId, String url, String method) {
        URLMethods.Method methodEnum = URLMethods.Method.fromString(method);
        ApiInfo.ApiInfoKey apiInfoKey = new ApiInfoKey(apiCollectionId, url, methodEnum);
        ApiInfoDao.instance.updateLastTestedField(apiInfoKey);
    }

    public static void bulkUpdateLastTestedField(Map<ApiInfo.ApiInfoKey, Integer> testedApisMap) {
        ApiInfoDao.instance.bulkUpdateLastTestedField(testedApisMap);
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

    public static TestingRunResultSummary updateIssueCountInSummary(String summaryId, Map<String, Integer> totalCountIssues, String operator) {
        if(operator == null || !operator.equals("increment")){
            return updateIssueCountInSummary(summaryId,totalCountIssues);
        }else{
            ObjectId summaryObjectId = new ObjectId(summaryId);
            FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
            options.returnDocument(ReturnDocument.AFTER);
            Bson updateIncrement = Updates.combine(
                Updates.inc("countIssues.HIGH", totalCountIssues.getOrDefault("HIGH", 0)),
                Updates.inc("countIssues.MEDIUM", totalCountIssues.getOrDefault("MEDIUM", 0)),
                Updates.inc("countIssues.LOW", totalCountIssues.getOrDefault("LOW", 0))
            );
            return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                    Filters.eq(Constants.ID, summaryObjectId),
                    Updates.combine(
                            Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                            Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                            updateIncrement),options);
        }
    }

    public static TestingRunResultSummary updateIssueCountInSummary(String summaryId, Map<String, Integer> totalCountIssues) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(Constants.ID, summaryObjectId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                        Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)),
                options);
    }

    public static TestingRunResultSummary updateIssueCountAndStateInSummary(String summaryId, Map<String, Integer> totalCountIssues, String state) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions();
        options.returnDocument(ReturnDocument.AFTER);
        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(Constants.ID, summaryObjectId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                        Updates.set(TestingRunResultSummary.STATE, state)
                ),options);
    }

    public static List<Integer> fetchDeactivatedCollections() {
        return new ArrayList<>(UsageMetricCalculator.getDeactivatedLatest());
    }

    public static void updateUsage(MetricTypes metricType, int deltaUsage){
        int accountId = Context.accountId.get();
        UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(metricType, accountId, deltaUsage);
        return;
    }

    public static List<TestingRunResult> fetchLatestTestingRunResultBySummaryId(String summaryId, int limit, int skip) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        return TestingRunResultDao.instance
                    .fetchLatestTestingRunResult(
                            Filters.and(
                                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                                    Filters.eq(TestingRunResult.VULNERABLE, true)),
                            limit,
                            skip,
                            Projections.exclude("testResults.originalMessage", "testResults.nodeResultMap"));
    }

    public static List<TestRoles> fetchTestRoles() {
        return TestRolesDao.instance.findAll(new BasicDBObject());
    }

    public static final int SAMPLE_DATA_LIMIT = 50;

    public static List<SampleData> fetchSampleData(Set<Integer> apiCollectionIds, int skip) {
        Bson filterQ = Filters.and(
                Filters.in("_id.apiCollectionId", apiCollectionIds),
                // only send sample data if sample exists.
                Filters.exists(SampleData.SAMPLES + ".0"));
        /*
         * Since we use only the last sample data,
         * sending only the last sample data to send minimal data.
         */
        Bson projection = Projections.computed(SampleData.SAMPLES,
                Projections.computed("$slice", Arrays.asList("$" + SampleData.SAMPLES, -1)));

        return SampleDataDao.instance.findAll(filterQ, skip, SAMPLE_DATA_LIMIT, Sorts.descending(Constants.ID), projection);
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


    public static DataControlSettings fetchDataControlSettings(String prevResult, String prevCommand) {
        Integer accountId = Context.accountId.get();
        Bson updates = Updates.combine(Updates.set("postgresResult", prevResult), Updates.set("oldPostgresCommand", prevCommand));
        return DataControlSettingsDao.instance.getMCollection().findOneAndUpdate(Filters.eq("_id", accountId), updates);
    }

    public static void bulkWriteDependencyNodes(List<DependencyNode> dependencyNodeList) {
        DependencyAnalyserUtils.syncWithDb(dependencyNodeList);
    }

    public static List<ApiInfo.ApiInfoKey> fetchLatestEndpointsForTesting(int startTimestamp, int endTimestamp, int apiCollectionId) {
        return SingleTypeInfoDao.fetchLatestEndpointsForTesting(startTimestamp, endTimestamp, apiCollectionId);
    }

    public static List<YamlTemplate> fetchActiveFilterTemplates(){
        return AdvancedTrafficFiltersDao.instance.findAll(
            Filters.ne(YamlTemplate.INACTIVE, false)
        );
    }

    public static List<TestingRunResultSummary> fetchStatusOfTests() {
        int timeFilter = Context.now() - 30 * 60;
        List<TestingRunResultSummary> currentRunningTests = TestingRunResultSummariesDao.instance.findAll(
            Filters.gte(TestingRunResultSummary.START_TIMESTAMP, timeFilter),
            Projections.include("_id", TestingRunResultSummary.STATE, TestingRunResultSummary.TESTING_RUN_ID) 
        );
        for (TestingRunResultSummary summary: currentRunningTests) {
            summary.setTestingRunHexId(summary.getTestingRunId().toHexString());
        }
        return currentRunningTests;
    }

    public static Set<MergedUrls> fetchMergedUrls() {
        return MergedUrlsDao.instance.getMergedUrls();
    }

    public static final int ENDPOINT_LIMIT = 50;

    public static List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip, int deltaPeriodValue) {
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);

        if(apiCollection == null){
            return new ArrayList<>();
        }

        if (apiCollection.getHostName() == null || apiCollection.getHostName().length() == 0 ) {
            return ApiCollectionsDao.fetchEndpointsInCollection(apiCollectionId, skip, ENDPOINT_LIMIT, deltaPeriodValue);
        } else {
            List<SingleTypeInfo> allUrlsInCollection = ApiCollectionsDao.fetchHostSTI(apiCollectionId, skip);

            List<BasicDBObject> endpoints = new ArrayList<>();
            for(SingleTypeInfo singleTypeInfo: allUrlsInCollection) {
                BasicDBObject groupId = new BasicDBObject(ApiInfoKey.API_COLLECTION_ID, singleTypeInfo.getApiCollectionId())
                    .append(ApiInfoKey.URL, singleTypeInfo.getUrl())
                    .append(ApiInfoKey.METHOD, singleTypeInfo.getMethod());
                endpoints.add(new BasicDBObject("startTs", singleTypeInfo.getTimestamp()).append(Constants.ID, groupId));
            }

            return endpoints;
        }
    }    

    public static OtpTestData fetchOtpTestData(String uuid, int curTime){
        Bson filters = Filters.and(
            Filters.eq("uuid", uuid),
            Filters.gte("createdAtEpoch", curTime)
        );
        return OtpTestDataDao.instance.findOne(filters);
    }

    public static RecordedLoginFlowInput fetchRecordedLoginFlowInput(){
        return RecordedLoginInputDao.instance.findOne(new BasicDBObject());
    }

    public static LoginFlowStepsData fetchLoginFlowStepsData(int userId) {
        Bson filters = Filters.and(
                Filters.eq("userId", userId));
        return LoginFlowStepsDao.instance.findOne(filters);
    }

    public static void updateLoginFlowStepsData(int userId, Map<String, Object> valuesMap) {
        Bson filter = Filters.and(
                Filters.eq("userId", userId));
        Bson update = Updates.set("valuesMap", valuesMap);
        LoginFlowStepsDao.instance.updateOne(filter, update);
    }

    public static Node fetchDependencyFlowNodesByApiInfoKey(int apiCollectionId, String url, String method) {
        Node node = DependencyFlowNodesDao.instance.findOne(
                Filters.and(
                        Filters.eq("apiCollectionId", apiCollectionId + ""),
                        Filters.eq("url", url),
                        Filters.eq("method", method)));
        return node;
    }

    public static List<SampleData> fetchSampleDataForEndpoints(List<ApiInfo.ApiInfoKey> endpoints) {
        List<Bson> filters = new ArrayList<>();
        for (ApiInfo.ApiInfoKey endpoint : endpoints) {
            filters.add(Filters.and(
                    Filters.eq("_id.apiCollectionId", endpoint.getApiCollectionId()),
                    Filters.eq("_id.url", endpoint.getUrl()),
                    Filters.eq("_id.method", endpoint.getMethod().name())));
        }
        return SampleDataDao.instance.findAll(Filters.or(filters));
    }

    public final static int NODE_LIMIT = 100;

    public static List<Node> fetchNodesForCollectionIds(List<Integer> apiCollectionsIds, boolean removeZeroLevel, int skip) {
        return DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionsIds, removeZeroLevel, skip,
                NODE_LIMIT);
    }

    public static long countTestingRunResultSummaries(Bson filter){
        return TestingRunResultSummariesDao.instance.count(filter);
    }


    public static TestScript fetchTestScript(){
        return TestScriptsDao.instance.fetchTestScript();
    }

    public static List<DependencyNode> findDependencyNodes(int apiCollectionId, String url, String method, String reqMethod) {
        Bson filterQ = DependencyNodeDao.generateChildrenFilter(apiCollectionId, url, Method.valueOf(method));
        // TODO: Handle cases where the delete API does not have the delete method
        Bson delFilterQ = Filters.and(filterQ, Filters.eq(DependencyNode.METHOD_REQ, reqMethod));
        return DependencyNodeDao.instance.findAll(delFilterQ);
    }

    public static List<String> findTestSubCategoriesByTestSuiteId(List<String> testSuiteId) {
        List<ObjectId> testSuiteIds = new ArrayList<>();
        for (String testSuiteIdStr : testSuiteId) {
            testSuiteIds.add(new ObjectId(testSuiteIdStr));
        }
        List<TestSuites> testSuites = TestSuiteDao.instance.findAll(Filters.in(ID, testSuiteIds));
        if(testSuites == null || testSuites.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> subcategorySet = new HashSet<>();
        for (TestSuites testSuite : testSuites) {
            List<String> subcategoryList = testSuite.getSubCategoryList();
            if(subcategoryList != null && !subcategoryList.isEmpty()) {
                subcategorySet.addAll(subcategoryList);
            }
        }

        return new ArrayList<>(subcategorySet);
    }

    public static TestingRunResultSummary findLatestTestingRunResultSummary(Bson filter){
        return TestingRunResultSummariesDao.instance.findLatestOne(filter);
    }

    public static TestingRunPlayground getCurrentTestingRunDetailsFromEditor(int timestamp){
        return TestingRunPlaygroundDao.instance.findOne(
                Filters.and(
                        Filters.gte(TestingRunPlayground.CREATED_AT, timestamp),
                        Filters.eq(TestingRunPlayground.STATE, State.SCHEDULED)
                )
        );
    }

    public static void updateTestingRunPlayground(TestingRunPlayground testingRunPlayground) {
        TestingRunPlaygroundDao.instance.updateOne(
                Filters.eq(Constants.ID, testingRunPlayground.getId()),
                Updates.combine(
                        Updates.set(TestingRunPlayground.STATE, State.COMPLETED),
                        Updates.set(TestingRunPlayground.TESTING_RUN_RESULT, testingRunPlayground.getTestingRunResult())));
    }

    public static List<SlackWebhook> fetchSlackWebhooks() {
        return SlackWebhooksDao.instance.findAll(
            Filters.empty()
        );
    }

    public static void insertMCPAuditDataLog(McpAuditInfo auditInfo) {
        McpAuditInfoDao.instance.insertOne(auditInfo);
    }

    public static List<McpReconRequest> fetchPendingMcpReconRequests() {
        // Fetch all requests where status is "Pending"
        Bson filter = Filters.eq(McpReconRequest.STATUS, Constants.STATUS_PENDING);
        return McpReconRequestDao.instance.findAll(filter);
    }

    public static void updateMcpReconRequestStatus(String requestId, String newStatus, int serversFound) {
        ObjectId requestObjId = new ObjectId(requestId);
        Bson filter = Filters.eq(McpReconRequest.ID, requestObjId);
        Bson updates;
        if (newStatus.equals(Constants.STATUS_IN_PROGRESS)) {
            updates = Updates.combine(
                Updates.set(McpReconRequest.STATUS, newStatus),
                Updates.set(McpReconRequest.STARTED_AT, Context.now())
            );
        } else {   // For completed or failed status
            updates = Updates.combine(
                Updates.set(McpReconRequest.STATUS, newStatus),
                Updates.set(McpReconRequest.SERVERS_FOUND, serversFound),
                Updates.set(McpReconRequest.FINISHED_AT, Context.now())
            );
        }
        McpReconRequestDao.instance.updateOneNoUpsert(filter, updates);
    }
    
    public static void storeMcpReconResultsBatch(List<McpReconResult> serverDataList) {
        // Batch store MCP server discovery results using DAO
        McpReconResultDao.instance.insertMany(serverDataList);
    }

    public static void storeConversationResults(List<AgentConversationResult> conversationResults) {
        AgentConversationResultDao.instance.insertMany(conversationResults);
    }

    public static void bulkWriteAgentTrafficLogs(List<AgentTrafficLog> agentTrafficLogs) {
        AgentTrafficLogDao.instance.insertMany(agentTrafficLogs);
    }
}
