package com.akto.data_actor;

import com.akto.dto.*;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.settings.DataControlSettings;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.akto.dto.testing.AccessMatrixUrlToRole;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.WorkflowTest;
import com.akto.dto.testing.WorkflowTestResult;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBList;
import com.mongodb.client.model.WriteModel;
import com.akto.dto.usage.MetricTypes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public abstract class DataActor {

    public abstract AccountSettings fetchAccountSettings();

    public abstract long fetchEstimatedDocCount();

    public abstract void updateCidrList(List<String> cidrList);

    public abstract void updateApiCollectionNameForVxlan(int vxlanId, String name);

    public abstract APIConfig fetchApiConfig(String configName);

    public abstract void bulkWriteSingleTypeInfo(List<Object> writesForApiInfo);

    public abstract void bulkWriteSensitiveParamInfo(List<Object> writesForSensitiveParamInfo);

    public abstract void bulkWriteSampleData(List<Object> writesForSampleData);

    public abstract void bulkWriteSensitiveSampleData(List<Object> writesForSensitiveSampleData);

    public abstract void bulkWriteTrafficInfo(List<Object> writesForTrafficInfo);

    public abstract void bulkWriteTrafficMetrics(List<Object> writesForTrafficInfo);

    public abstract List<SingleTypeInfo> fetchStiOfCollections(int batchCount, int lastStiFetchTs);

    public abstract List<SingleTypeInfo> fetchAllStis();

    public abstract List<SensitiveParamInfo> getUnsavedSensitiveParamInfos();

    public abstract List<CustomDataType> fetchCustomDataTypes();

    public abstract List<AktoDataType> fetchAktoDataTypes();

    public abstract List<CustomAuthType> fetchCustomAuthTypes();

    public abstract List<ApiInfo> fetchApiInfos();

    public abstract List<ApiInfo> fetchNonTrafficApiInfos();

    public abstract void bulkWriteApiInfo(List<ApiInfo> apiInfoList);

    public abstract List<RuntimeFilter> fetchRuntimeFilters();

    public abstract void updateRuntimeVersion(String fieldName, String version);

    public abstract Account fetchActiveAccount();

    public abstract void updateKafkaIp(String currentInstanceIp);

    public abstract List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection();

    public abstract List<ApiCollection> fetchApiCollections();

    public abstract void createCollectionSimple(int vxlanId);

    public abstract void createCollectionForHost(String host, int colId);

    public abstract AccountSettings fetchAccountSettingsForAccount(int accountId);
    
    public abstract void insertRuntimeLog(Log log);

    public abstract void insertAnalyserLog(Log log);

    public abstract void modifyHybridSaasSetting(boolean isHybridSaas);

    public abstract Setup fetchSetup();

    public abstract Organization fetchOrganization(int accountId);

    public abstract TestingRunResultSummary createTRRSummaryIfAbsent(String testingRunHexId, int start);

    public abstract TestingRun findPendingTestingRun(int delta);

    public abstract TestingRunResultSummary findPendingTestingRunResultSummary(int now, int delta);

    public abstract TestingRun findTestingRun(String testingRunId);

    public abstract void updateTestRunResultSummaryNoUpsert(String testingRunResultSummaryId);

    public abstract void updateTestingRun(String testingRunId);

    public abstract void updateTestRunResultSummary(String summaryId);

    public abstract List<TestingRunResult> fetchLatestTestingRunResult(String testingRunResultSummaryId);

    public abstract TestingRunResultSummary markTestRunResultSummaryFailed(String testingRunResultSummaryId);

    public abstract void insertTestingRunResultSummary(TestingRunResultSummary trrs);

    public abstract void bulkWriteTestingRunIssues(List<Object> writesForTestingRunIssues);

    public abstract TestSourceConfig findTestSourceConfig(String subType);

    public abstract void updateTestingRunAndMarkCompleted(String testingRunId, int scheduleTs);

    public abstract Map<ObjectId, TestingRunResultSummary> fetchTestingRunResultSummaryMap(String testingRunId);

    public abstract TestingRunConfig findTestingRunConfig(int testIdConfig);

    public abstract List<TestingRunIssues> fetchOpenIssues(String summaryId);

    public abstract TestingRunResult fetchTestingRunResults(Bson filterForRunResult);

    public abstract ApiCollection fetchApiCollectionMeta(int apiCollectionId);

    public abstract TestingRunResultSummary fetchTestingRunResultSummary(String testingRunResultSummaryId);

    public abstract List<ApiCollection> fetchAllApiCollectionsMeta();

    public abstract WorkflowTest fetchWorkflowTest(int workFlowTestId);

    public abstract void insertWorkflowTestResult(WorkflowTestResult workflowTestResult);

    public abstract void updateIssueCountInTestSummary(String summaryId, Map<String, Integer> totalCountIssues);

    public abstract void updateTestInitiatedCountInTestSummary(String summaryId, int testInitiatedCount);

    public abstract List<YamlTemplate> fetchYamlTemplates(boolean fetchOnlyActive, int skip);

    public abstract void updateTestResultsCountInTestSummary(String summaryId, int testResultsCount);

    public abstract void updateLastTestedField(int apiCollectionId, String url, String method);

    public abstract void insertTestingRunResults(TestingRunResult testingRunResults);

    public abstract void updateTotalApiCountInTestSummary(String summaryId, int totalApiCount);

    public abstract void insertActivity(int count);

    public abstract TestingRunResultSummary updateIssueCountInSummary(String summaryId, Map<String, Integer> totalCountIssues);

    public abstract List<TestingRunResult> fetchLatestTestingRunResultBySummaryId(String summaryId, int limit, int skip);

    public abstract List<TestRoles> fetchTestRoles();

    public abstract List<SampleData> fetchSampleData(Set<Integer> apiCollectionIds, int skip);

    public abstract TestRoles fetchTestRole(String key);

    public abstract TestRoles fetchTestRolesforId(String roleId);

    public abstract Tokens fetchToken(String organizationId, int accountId);

    public abstract List<ApiCollection> findApiCollections(List<String> apiCollectionNames);

    public abstract boolean apiInfoExists(List<Integer> apiCollectionIds, List<String> urls);

    public abstract ApiCollection findApiCollectionByName(String apiCollectionName);

    public abstract void insertApiCollection(int apiCollectionId, String apiCollectionName);

    public abstract List<TestingRunIssues> fetchIssuesByIds(Set<TestingIssuesId> issuesIds);

    public abstract List<Integer> fetchDeactivatedCollections();

    public abstract void updateUsage(MetricTypes metricType, int deltaUsage);

    public abstract List<SingleTypeInfo> findStiByParam(int apiCollectionId, String param);

    public abstract SingleTypeInfo findSti(int apiCollectionId, String url, URLMethods.Method method);

    public abstract AccessMatrixUrlToRole fetchAccessMatrixUrlToRole(ApiInfo.ApiInfoKey apiInfoKey);

    public abstract ApiInfo fetchApiInfo(ApiInfo.ApiInfoKey apiInfoKey);

    public abstract SampleData fetchSampleDataById(int apiCollectionId, String url, URLMethods.Method method);

    public abstract SingleTypeInfo findStiWithUrlParamFilters(int apiCollectionId, String url, String method, int responseCode, boolean isHeader, String param, boolean isUrlParam);

    public abstract List<TestRoles> fetchTestRolesForRoleName(String roleFromTask);

    public abstract List<AccessMatrixTaskInfo> fetchPendingAccessMatrixInfo(int ts);

    public abstract void updateAccessMatrixInfo(String taskId, int frequencyInSeconds);

    public abstract EndpointLogicalGroup fetchEndpointLogicalGroup(String logicalGroupName);

    public abstract void updateAccessMatrixUrlToRoles(ApiInfo.ApiInfoKey apiInfoKey, List<String> ret);

    public abstract List<SingleTypeInfo> fetchMatchParamSti(int apiCollectionId, String param);

    public abstract SampleData fetchSampleDataByIdMethod(int apiCollectionId, String url, String method);

    public abstract void modifyHybridTestingSetting(boolean hybridTestingEnabled);

    public abstract void insertTestingLog(Log log);

    public abstract EndpointLogicalGroup fetchEndpointLogicalGroupById(String endpointLogicalGroupId);

    public abstract DataControlSettings fetchDataControlSettings(String prevResult, String prevCommand);

    public abstract void bulkWriteDependencyNodes(List<DependencyNode> dependencyNodeList);
    
    public abstract List<ApiInfo.ApiInfoKey> fetchLatestEndpointsForTesting(int startTimestamp, int endTimestamp, int apiCollectionId);

    public abstract void insertRuntimeMetricsData(BasicDBList metricsData);

}
