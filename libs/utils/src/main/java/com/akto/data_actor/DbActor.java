package com.akto.data_actor;

import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.*;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.settings.DataControlSettings;
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
import com.akto.dto.testing.config.TestScript;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.jobs.JobScheduler;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;

public class DbActor extends DataActor {

    public AccountSettings fetchAccountSettings() {
        return DbLayer.fetchAccountSettings();
    }

    public long fetchEstimatedDocCount() {
        return DbLayer.fetchEstimatedDocCount();
    }

    public void updateCidrList(List<String> cidrList) {
        DbLayer.updateCidrList(cidrList);
    }

    public void updateApiCollectionNameForVxlan(int vxlanId, String name) {
        DbLayer.updateApiCollectionName(vxlanId, name);
    }

    public APIConfig fetchApiConfig(String configName) {
        return DbLayer.fetchApiconfig(configName);
    }

    public void bulkWriteSingleTypeInfo(List<Object> writesForApiInfo) {
        ArrayList<WriteModel<SingleTypeInfo>> writes = new ArrayList<>();
        for (Object obj: writesForApiInfo) {
            WriteModel<SingleTypeInfo> write = (WriteModel<SingleTypeInfo>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSingleTypeInfo(writes);
    }

    public void bulkWriteSensitiveParamInfo(List<Object> writesForSensitiveParamInfo) {
        ArrayList<WriteModel<SensitiveParamInfo>> writes = new ArrayList<>();
        for (Object obj: writesForSensitiveParamInfo) {
            WriteModel<SensitiveParamInfo> write = (WriteModel<SensitiveParamInfo>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSensitiveParamInfo(writes);
    }

    public void bulkWriteSampleData(List<Object> writesForSampleData) {
        ArrayList<WriteModel<SampleData>> writes = new ArrayList<>();
        for (Object obj: writesForSampleData) {
            WriteModel<SampleData> write = (WriteModel<SampleData>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSampleData(writes);
    }

    public void bulkWriteSensitiveSampleData(List<Object> writesForSensitiveSampleData) {
        ArrayList<WriteModel<SensitiveSampleData>> writes = new ArrayList<>();
        for (Object obj: writesForSensitiveSampleData) {
            WriteModel<SensitiveSampleData> write = (WriteModel<SensitiveSampleData>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteSensitiveSampleData(writes);
    }

    public void bulkWriteTrafficInfo(List<Object> writesForTrafficInfo) {
        ArrayList<WriteModel<TrafficInfo>> writes = new ArrayList<>();
        for (Object obj: writesForTrafficInfo) {
            WriteModel<TrafficInfo> write = (WriteModel<TrafficInfo>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteTrafficInfo(writes);
    }

    public void bulkWriteTrafficMetrics(List<Object> writesForTrafficMetrics) {
        ArrayList<WriteModel<TrafficMetrics>> writes = new ArrayList<>();
        for (Object obj: writesForTrafficMetrics) {
            WriteModel<TrafficMetrics> write = (WriteModel<TrafficMetrics>)obj;
            writes.add(write);
        }
        DbLayer.bulkWriteTrafficMetrics(writes);
    }

    public void bulkWriteTestingRunIssues(List<Object> writesForTestingRunIssues) {
        ArrayList<WriteModel<TestingRunIssues>> writes = new ArrayList<>();
        for (Object obj : writesForTestingRunIssues) {
            WriteModel<TestingRunIssues> write = (WriteModel<TestingRunIssues>) obj;
            writes.add(write);
        }
        DbLayer.bulkWriteTestingRunIssues(writes);
    }

    public void bulkWriteOverageInfo(List<Object> writesForOverageInfo) {
        ArrayList<WriteModel<UningestedApiOverage>> writes = new ArrayList<>();
        for (Object obj : writesForOverageInfo) {
            WriteModel<UningestedApiOverage> write = (WriteModel<UningestedApiOverage>) obj;
            writes.add(write);
        }
        DbLayer.bulkWriteOverageInfo(writes);
    }

    public boolean overageApisExists(int apiCollectionId, String urlType, Method method, String url) {
        return com.akto.dao.billing.UningestedApiOverageDao.instance.exists(apiCollectionId, urlType, method, url);
    }

    public TestSourceConfig findTestSourceConfig(String subType){
        return DbLayer.findTestSourceConfig(subType);
    }

    public List<SingleTypeInfo> fetchStiOfCollections(int batchCount, int lastStiFetchTs) {
        return DbLayer.fetchStiOfCollections();
    }

    public List<SingleTypeInfo> fetchAllStis() {
        List<SingleTypeInfo> allParams = DbLayer.fetchStiBasedOnHostHeaders(null);
        allParams.addAll(DbLayer.fetchAllSingleTypeInfo());
        return allParams;
    }

    public List<com.akto.dto.traffic.Key> fetchAllSampleDataKeys() {
        return DbLayer.fetchAllSampleDataKeys();
    }

    public List<SensitiveParamInfo> getUnsavedSensitiveParamInfos() {
        return DbLayer.getUnsavedSensitiveParamInfos();
    }

    public List<CustomDataType> fetchCustomDataTypes() {
        return DbLayer.fetchCustomDataTypes();
    }

    public List<AktoDataType> fetchAktoDataTypes() {
        return DbLayer.fetchAktoDataTypes();
    }

    public List<CustomAuthType> fetchCustomAuthTypes() {
        return DbLayer.fetchCustomAuthTypes();
    }

    public List<ApiInfo> fetchApiInfos() {
        return DbLayer.fetchApiInfos();
    }

    public List<ApiInfo> fetchNonTrafficApiInfos() {
        return DbLayer.fetchNonTrafficApiInfos();
    }

    public void bulkWriteApiInfo(List<ApiInfo> apiInfoList) {
        DbLayer.bulkWriteApiInfo(apiInfoList);
    }

    public List<ApiInfo.ApiInfoKey> fetchApiIds() {
        return DbLayer.fetchAllApiInfoKeys();
    }

    public void ensureCollections(List<Integer> collectionIds) {
        DbLayer.ensureCollections(collectionIds);
    }

    public List<RuntimeFilter> fetchRuntimeFilters() {
        return DbLayer.fetchRuntimeFilters();
    }

    public void updateRuntimeVersion(String fieldName, String version) {
        DbLayer.updateRuntimeVersion(fieldName, version);
    }

    public Account fetchActiveAccount() {
        return DbLayer.fetchActiveAccount();
    }

    public void updateKafkaIp(String currentInstanceIp) {
        DbLayer.updateKafkaIp(currentInstanceIp);
    }

    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection() {
        return DbLayer.fetchEndpointsInCollection();
    }

    public List<ApiCollection> fetchApiCollections() {
        return DbLayer.fetchApiCollections();
    }

    public List<ApiCollection> fetchAllApiCollections() {
        return DbLayer.fetchAllApiCollections();
    }

    public void createCollectionSimple(int vxlanId) {
        DbLayer.createCollectionSimple(vxlanId);
    }

    public void createCollectionForHost(String host, int colId) {
        DbLayer.createCollectionForHost(host, colId);
    }

    public AccountSettings fetchAccountSettingsForAccount(int accountId) {
        return DbLayer.fetchAccountSettings(accountId);
    }

    public void insertRuntimeLog(Log log) {
        DbLayer.insertRuntimeLog(log);
    }

    public void insertAnalyserLog(Log log) {
        DbLayer.insertAnalyserLog(log);
    }

    public void modifyHybridSaasSetting(boolean isHybridSaas) {
        DbLayer.modifyHybridSaasSetting(isHybridSaas);
    }

    public Setup fetchSetup() {
        return DbLayer.fetchSetup();
    }

    public Organization fetchOrganization(int accountId) {
        return DbLayer.fetchOrganization(accountId);
    }

    // testing queries

    public TestingRunResultSummary createTRRSummaryIfAbsent(String testingRunHexId, int start) {
        return DbLayer.createTRRSummaryIfAbsent(testingRunHexId, start);
    }

    @Override
    public void ingestMetricData(List<MetricData> metricData) {
        DbLayer.ingestMetric(metricData);
    }

    public TestingRun findPendingTestingRun(int delta, String miniTestingName) {
        return DbLayer.findPendingTestingRun(delta);
    }

    public TestingRunResultSummary findPendingTestingRunResultSummary(int now, int delta, String miniTestingName) {
        return DbLayer.findPendingTestingRunResultSummary(now, delta);
    }

    public TestingRunConfig findTestingRunConfig(int testIdConfig) {
        return DbLayer.findTestingRunConfig(testIdConfig);
    }

    public TestingRun findTestingRun(String testingRunId) {
        return DbLayer.findTestingRun(testingRunId);
    }

    public boolean apiInfoExists(List<Integer> apiCollectionIds, List<String> urls) {
        return DbLayer.apiInfoExists(apiCollectionIds, urls);
    }

    public AccessMatrixUrlToRole fetchAccessMatrixUrlToRole(ApiInfo.ApiInfoKey apiInfoKey) {
        return DbLayer.fetchAccessMatrixUrlToRole(apiInfoKey);
    }

    public List<ApiCollection> fetchAllApiCollectionsMeta() {
        return DbLayer.fetchAllApiCollectionsMeta();
    }

    public ApiCollection fetchApiCollectionMeta(int apiCollectionId) {
        return DbLayer.fetchApiCollectionMeta(apiCollectionId);
    }

    public ApiInfo fetchApiInfo(ApiInfoKey apiInfoKey) {
        return DbLayer.fetchApiInfo(apiInfoKey);
    }

    public EndpointLogicalGroup fetchEndpointLogicalGroup(String logicalGroupName) {
        return DbLayer.fetchEndpointLogicalGroup(logicalGroupName);
    }

    public DataControlSettings fetchDataControlSettings(String prevResult, String prevCommand) {
        return DbLayer.fetchDataControlSettings(prevResult, prevCommand);
    }
    public EndpointLogicalGroup fetchEndpointLogicalGroupById(String endpointLogicalGroupId) {
        return DbLayer.fetchEndpointLogicalGroupById(endpointLogicalGroupId);
    }

    public List<TestingRunIssues> fetchIssuesByIds(Set<TestingIssuesId> issuesIds) {
        return DbLayer.fetchIssuesByIds(issuesIds);
    }

    public List<TestingRunResult> fetchLatestTestingRunResult(String testingRunResultSummaryId) {
        return DbLayer.fetchLatestTestingRunResult(testingRunResultSummaryId);
    }

    @Override
    public List<TestingRunResult> fetchRerunTestingRunResult(String testingRunResultSummaryId) {
        return DbLayer.fetchRerunTestingRunResult(testingRunResultSummaryId);
    }

    public List<TestingRunResult> fetchLatestTestingRunResultBySummaryId(String summaryId, int limit, int skip) {
        return DbLayer.fetchLatestTestingRunResultBySummaryId(summaryId, limit, skip);
    }

    public List<SingleTypeInfo> fetchMatchParamSti(int apiCollectionId, String param) {
        return DbLayer.fetchMatchParamSti(apiCollectionId, param);
    }

    public List<TestingRunIssues> fetchOpenIssues(String summaryId) {
        return DbLayer.fetchOpenIssues(summaryId);
    }

    public List<AccessMatrixTaskInfo> fetchPendingAccessMatrixInfo(int ts) {
        return DbLayer.fetchPendingAccessMatrixInfo(ts);
    }

    public List<SampleData> fetchSampleData(Set<Integer> apiCollectionIdsSet, int skip) {
        return DbLayer.fetchSampleData(apiCollectionIdsSet, skip);
    }

    public SampleData fetchSampleDataById(int apiCollectionId, String url, Method method) {
        return DbLayer.fetchSampleDataById(apiCollectionId, url, method);
    }

    public SampleData fetchSampleDataByIdMethod(int apiCollectionId, String url, String method) {
        return DbLayer.fetchSampleDataByIdMethod(apiCollectionId, url, method);
    }

    public TestRoles fetchTestRole(String key) {
        return DbLayer.fetchTestRole(key);
    }

    public List<TestRoles> fetchTestRoles() {
        return DbLayer.fetchTestRoles();
    }

    public List<TestRoles> fetchTestRolesForRoleName(String roleFromTask) {
        return DbLayer.fetchTestRolesForRoleName(roleFromTask);
    }

    public TestRoles fetchTestRolesforId(String roleId) {
        return DbLayer.fetchTestRolesforId(roleId);
    }

    public TestingRunResultSummary fetchTestingRunResultSummary(String testingRunResultSummaryId) {
        return DbLayer.fetchTestingRunResultSummary(testingRunResultSummaryId);
    }

    @Override
    public TestingRunResultSummary fetchRerunTestingRunResultSummary(String originalTestingRunResultSummaryId) {
        return DbLayer.fetchRerunTestingRunResultSummary(originalTestingRunResultSummaryId);
    }

    public Map<ObjectId, TestingRunResultSummary> fetchTestingRunResultSummaryMap(String testingRunId) {
        return DbLayer.fetchTestingRunResultSummaryMap(testingRunId);
    }

    public TestingRunResult fetchTestingRunResults(Bson filterForRunResult) {
        return DbLayer.fetchTestingRunResults(filterForRunResult);
    }

    public Tokens fetchToken(String organizationId, int accountId) {
        return DbLayer.fetchToken(organizationId, accountId);
    }

    public WorkflowTest fetchWorkflowTest(int workFlowTestId) {
        return DbLayer.fetchWorkflowTest(workFlowTestId);
    }

    public List<YamlTemplate> fetchYamlTemplates(boolean fetchOnlyActive, int skip) {
        return DbLayer.fetchYamlTemplates(fetchOnlyActive, skip);
    }

    public List<YamlTemplate> fetchYamlTemplatesWithIds(List<String> ids, boolean fetchOnlyActive){
        return DbLayer.fetchYamlTemplatesWithIds(ids, fetchOnlyActive);
    }

    public ApiCollection findApiCollectionByName(String apiCollectionName) {
        return DbLayer.findApiCollectionByName(apiCollectionName);
    }

    public List<ApiCollection> findApiCollections(List<String> apiCollectionNames) {
        return DbLayer.findApiCollections(apiCollectionNames);
    }

    public SingleTypeInfo findSti(int apiCollectionId, String url, Method method) {
        return DbLayer.findSti(apiCollectionId, url, method);
    }

    public List<SingleTypeInfo> findStiByParam(int apiCollectionId, String param) {
        return DbLayer.findStiByParam(apiCollectionId, param);
    }

    public SingleTypeInfo findStiWithUrlParamFilters(int apiCollectionId, String url, String method, int responseCode,
            boolean isHeader, String param, boolean isUrlParam) {
        return DbLayer.findStiWithUrlParamFilters(apiCollectionId, url, method, responseCode, isHeader, param, isUrlParam);
    }

    public void insertActivity(int count) {
        DbLayer.insertActivity(count);   
    }

    public void insertApiCollection(int apiCollectionId, String apiCollectionName) {
        DbLayer.insertApiCollection(apiCollectionId, apiCollectionName);
    }

    public void insertTestingRunResultSummary(TestingRunResultSummary trrs) {
        DbLayer.insertTestingRunResultSummary(trrs);
    }

    public void insertTestingRunResults(TestingRunResult testingRunResults) {
        DbLayer.insertTestingRunResults(testingRunResults);
    }

    public void insertWorkflowTestResult(WorkflowTestResult workflowTestResult) {
        DbLayer.insertWorkflowTestResult(workflowTestResult);
    }

    public TestingRunResultSummary markTestRunResultSummaryFailed(String testingRunResultSummaryId) {
        return DbLayer.markTestRunResultSummaryFailed(testingRunResultSummaryId);
    }

    public void updateAccessMatrixInfo(String taskId, int frequencyInSeconds) {
        DbLayer.updateAccessMatrixInfo(taskId, frequencyInSeconds);
    }

    public void updateAccessMatrixUrlToRoles(ApiInfoKey apiInfoKey, List<String> ret) {
        DbLayer.updateAccessMatrixUrlToRoles(apiInfoKey, ret);
    }

    public TestingRunResultSummary updateIssueCountInSummary(String summaryId,
            Map<String, Integer> totalCountIssues) {
        return DbLayer.updateIssueCountInSummary(summaryId, totalCountIssues);
    }

    public TestingRunResultSummary updateIssueCountInSummary(String summaryId,
            Map<String, Integer> totalCountIssues, String operator) {
        return DbLayer.updateIssueCountInSummary(summaryId, totalCountIssues, operator);
    }

    public TestingRunResultSummary updateIssueCountAndStateInSummary(String summaryId, Map<String, Integer> totalCountIssues, String state) {
        return DbLayer.updateIssueCountAndStateInSummary(summaryId, totalCountIssues, state);
    }

    public List<Integer> fetchDeactivatedCollections() {
        return DbLayer.fetchDeactivatedCollections();
    }

    public void updateUsage(MetricTypes metricType,int deltaUsage){
        DbLayer.updateUsage(metricType, deltaUsage);
        return;
    }


    public void updateIssueCountInTestSummary(String summaryId, Map<String, Integer> totalCountIssues) {
        DbLayer.updateIssueCountInTestSummary(summaryId, totalCountIssues, false);
    }

    public void updateLastTestedField(int apiCollectionId, String url, String method) {
        DbLayer.updateLastTestedField(apiCollectionId, url, method);
    }

    @Override
    public void bulkUpdateLastTestedField(Map<ApiInfo.ApiInfoKey, Integer> testedApisMap) {
        DbLayer.bulkUpdateLastTestedField(testedApisMap);
    }

    public void updateTestInitiatedCountInTestSummary(String summaryId, int testInitiatedCount) {
        DbLayer.updateTestInitiatedCountInTestSummary(summaryId, testInitiatedCount);
    }

    public void updateTestResultsCountInTestSummary(String summaryId, int testResultsCount) {
        DbLayer.updateTestResultsCountInTestSummary(summaryId, testResultsCount);
    }

    public void updateTestRunResultSummary(String summaryId) {
        DbLayer.updateTestRunResultSummary(summaryId);
    }

    @Override
    public void deleteTestRunResultSummary(String summaryId) {
        DbLayer.deleteTestRunResultSummary(summaryId);
    }

    @Override
    public void deleteTestingRunResults(String testingRunResultId) {
        DbLayer.deleteTestingRunResults(testingRunResultId);
    }

    @Override
    public void updateStartTsTestRunResultSummary(String summaryId) {
        DbLayer.updateStartTsTestRunResultSummary(summaryId);
    }

    public void updateTestRunResultSummaryNoUpsert(String testingRunResultSummaryId) {
        DbLayer.updateTestRunResultSummaryNoUpsert(testingRunResultSummaryId);
    }

    public void updateTestingRun(String testingRunId) {
        DbLayer.updateTestingRun(testingRunId);
    }

    public void updateTestingRunAndMarkCompleted(String testingRunId, int scheduleTs) {
        DbLayer.updateTestingRunAndMarkCompleted(testingRunId, scheduleTs);
    }

    public void updateTotalApiCountInTestSummary(String summaryId, int totalApiCount) {
        DbLayer.updateTotalApiCountInTestSummary(summaryId, totalApiCount);
    }

    public void modifyHybridTestingSetting(boolean hybridTestingEnabled) {
        DbLayer.modifyHybridTestingSetting(hybridTestingEnabled);
    }

    public void insertTestingLog(Log log) {
        DbLayer.insertTestingLog(log);
    }

    public void bulkWriteDependencyNodes(List<DependencyNode> dependencyNodeList) {
        DbLayer.bulkWriteDependencyNodes(dependencyNodeList);
    }

    public List<ApiInfo.ApiInfoKey> fetchLatestEndpointsForTesting(int startTimestamp, int endTimestamp, int apiCollectionId) {
        return DbLayer.fetchLatestEndpointsForTesting(startTimestamp, endTimestamp, apiCollectionId);
    }

    public List<YamlTemplate> fetchActiveAdvancedFilters(){
        return DbLayer.fetchActiveFilterTemplates();
    }

    public List<TestingRunResultSummary> fetchStatusOfTests() {
        return DbLayer.fetchStatusOfTests();
    }
    
    public Set<MergedUrls> fetchMergedUrls() {
        return DbLayer.fetchMergedUrls();
    }

    public void createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags) {
        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId, tags);
    }

    public void createCollectionForHostAndVpc(String host, int colId, String vpcId, List<CollectionTags> tags) {
        DbLayer.createCollectionForHostAndVpc(host, colId, vpcId, tags);
    }

    public List<BasicDBObject> fetchEndpointsInCollectionUsingHost(int apiCollectionId, int skip, int deltaPeriodValue) {
        return DbLayer.fetchEndpointsInCollectionUsingHost(apiCollectionId, skip, deltaPeriodValue);
    }

    public OtpTestData fetchOtpTestData(String uuid, int curTime){
        return DbLayer.fetchOtpTestData(uuid, curTime);
    }

    public RecordedLoginFlowInput fetchRecordedLoginFlowInput(){
        return DbLayer.fetchRecordedLoginFlowInput();
    }

    public LoginFlowStepsData fetchLoginFlowStepsData(int userId) {
        return DbLayer.fetchLoginFlowStepsData(userId);
    }

    public void updateLoginFlowStepsData(int userId, Map<String, Object> valuesMap){
        DbLayer.updateLoginFlowStepsData(userId, valuesMap);
    }

    public Node fetchDependencyFlowNodesByApiInfoKey(int apiCollectionId, String url, String method) {
        return DbLayer.fetchDependencyFlowNodesByApiInfoKey(apiCollectionId, url, method);
    }

    public List<SampleData> fetchSampleDataForEndpoints(List<ApiInfo.ApiInfoKey> endpoints){
        return DbLayer.fetchSampleDataForEndpoints(endpoints);
    }

    public List<Node> fetchNodesForCollectionIds(List<Integer> apiCollectionsIds, boolean removeZeroLevel, int skip){
        return DbLayer.fetchNodesForCollectionIds(apiCollectionsIds, removeZeroLevel, skip);
    }

    public long countTestingRunResultSummaries(Bson filter){
        return DbLayer.countTestingRunResultSummaries(filter);
    }

    public TestScript fetchTestScript(){
        return DbLayer.fetchTestScript();
    }

    public List<DependencyNode> findDependencyNodes(int apiCollectionId, String url, String method, String reqMethod){
        return DbLayer.findDependencyNodes(apiCollectionId, url, method, reqMethod);
    }

    public List<String> findTestSubCategoriesByTestSuiteId(List<String> testSuiteId) {
        return DbLayer.findTestSubCategoriesByTestSuiteId(testSuiteId);
    }

    public TestingRunResultSummary findLatestTestingRunResultSummary(Bson filter){
        return DbLayer.findLatestTestingRunResultSummary(filter);
    }

    public TestingRunPlayground getCurrentTestingRunDetailsFromEditor(int timestamp){
        return DbLayer.getCurrentTestingRunDetailsFromEditor(timestamp);
    }

    public void updateTestingRunPlayground(TestingRunPlayground testingRunPlayground) {
        DbLayer.updateTestingRunPlayground(testingRunPlayground);
    }

    public void scheduleAutoCreateTicketsJob(int accountId, JobParams params, JobExecutorType jobExecutorType) {
        JobScheduler.scheduleRunOnceJob(accountId, params, jobExecutorType);
    }

    @Override
    public List<SlackWebhook> fetchSlackWebhooks() {
        return DbLayer.fetchSlackWebhooks();
    }

    @Override
    public String getLLMPromptResponse(JSONObject promptPayload) {
        // no implementation needed.
        return null;
    }

    @Override
    public String getLLMResponseV2(JSONObject promptPayload) {
        // no implementation needed.
        return null;
    }

    @Override
    public ModuleInfo updateModuleInfo(ModuleInfo moduleInfo) {
        // no implementation needed.
        return null;
    }

    public void insertMCPAuditDataLog(McpAuditInfo auditInfo) {
        DbLayer.insertMCPAuditDataLog(auditInfo);
    }

    public List<McpReconRequest> fetchPendingMcpReconRequests() {
        return DbLayer.fetchPendingMcpReconRequests();
    }

    public void updateMcpReconRequestStatus(String requestId, String status, int serversFound) {
        DbLayer.updateMcpReconRequestStatus(requestId, status, serversFound);
    }
    
    public void storeMcpReconResultsBatch(List<McpReconResult> serverDataList) {
        DbLayer.storeMcpReconResultsBatch(serverDataList);
    }

    @Override
    public void storeConversationResults(List<AgentConversationResult> conversationResults) {
        DbLayer.storeConversationResults(conversationResults);
    }

    @Override
    public void bulkWriteAgentTrafficLogs(List<Object> trafficLogs) {
        ArrayList<AgentTrafficLog> agentTrafficLogs = new ArrayList<>();
        for (Object obj : trafficLogs) {
            AgentTrafficLog log = (AgentTrafficLog) obj;
            agentTrafficLogs.add(log);
        }
        DbLayer.bulkWriteAgentTrafficLogs(agentTrafficLogs);
    }
    
    public YamlTemplate fetchCommonWordList() {
        return YamlTemplateDao.instance.findOne(Filters.empty());
    }
}