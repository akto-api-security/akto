package com.akto;

import com.akto.dao.*;
import com.akto.dao.audit_logs.ApiAuditLogsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.jobs.JobsDao;
import com.akto.dao.loaders.LoadersDao;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dao.monitoring.EndpointShieldLogsDao;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.BidirectionalSyncSettingsDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.SourceCodeVulnerabilitiesDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.traffic_metrics.TrafficAlertsDao;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.*;
import com.akto.dto.data_types.*;
import com.akto.dto.demo.VulnerableRequestForTemplate;
import com.akto.dto.dependency_flow.*;
import com.akto.dto.events.EventsExample;
import com.akto.dto.gpt.AktoGptConfig;
import com.akto.dto.gpt.AktoGptConfigState;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.JobParams;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.loaders.Loader;
import com.akto.dto.loaders.NormalLoader;
import com.akto.dto.loaders.PostmanUploadLoader;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.runtime_filters.FieldExistsFilter;
import com.akto.dto.runtime_filters.ResponseCodeRuntimeFilter;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_editor.TestLibrary;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.testing.config.TestCollectionProperty;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.testing.info.BFLATestInfo;
import com.akto.dto.testing.info.TestInfo;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic_metrics.TrafficAlerts;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.dto.traffic_metrics.TrafficMetricsAlert;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.upload.FileUpload;
import com.akto.dto.upload.FileUploadLog;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.dto.usage.UsageSync;
import com.akto.types.CappedList;
import com.akto.types.CappedSet;
import com.akto.util.DbMode;
import com.akto.util.ConnectionInfo;
import com.akto.util.EnumCodec;
import com.akto.util.LastCronRunInfo;
import com.akto.dto.Attempt.AttemptResult;
import com.akto.dto.CollectionConditions.ConditionsType;
import com.akto.dto.CollectionConditions.MethodCondition;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;
import com.akto.dto.DependencyNode.ParamInfo;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.dto.agents.State;
import com.akto.dto.auth.APIAuth;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.OrganizationFlags;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClients;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.akto.dao.MCollection.clients;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class DaoInit {

    private static final Logger logger = LoggerFactory.getLogger(DaoInit.class);

    public static CodecRegistry createCodecRegistry(){
        ClassModel<Config> configClassModel = ClassModel.builder(Config.class).enableDiscriminator(true).build();
        ClassModel<SignupInfo> signupInfoClassModel = ClassModel.builder(SignupInfo.class).enableDiscriminator(true)
                .build();
        ClassModel<APIAuth> apiAuthClassModel = ClassModel.builder(APIAuth.class).enableDiscriminator(true).build();
        ClassModel<AttemptResult> attempResultModel = ClassModel.builder(AttemptResult.class).enableDiscriminator(true)
                .build();
        ClassModel<URLTemplate> urlTemplateModel = ClassModel.builder(URLTemplate.class).enableDiscriminator(true)
                .build();
        ClassModel<PendingInviteCode> pendingInviteCodeClassModel = ClassModel.builder(PendingInviteCode.class)
                .enableDiscriminator(true).build();
        ClassModel<RBAC> rbacClassModel = ClassModel.builder(RBAC.class).enableDiscriminator(true).build();
        ClassModel<SingleTypeInfo> singleTypeInfoClassModel = ClassModel.builder(SingleTypeInfo.class)
                .enableDiscriminator(true).build();
        ClassModel<KafkaHealthMetric> kafkaHealthMetricClassModel = ClassModel.builder(KafkaHealthMetric.class)
                .enableDiscriminator(true).build();
        ClassModel<ThirdPartyAccess> thirdPartyAccessClassModel = ClassModel.builder(ThirdPartyAccess.class)
                .enableDiscriminator(true).build();
        ClassModel<Credential> credentialClassModel = ClassModel.builder(Credential.class).enableDiscriminator(true)
                .build();
        ClassModel<ApiToken> apiTokenClassModel = ClassModel.builder(ApiToken.class).enableDiscriminator(true).build();
        ClassModel<ApiInfo> apiInfoClassModel = ClassModel.builder(ApiInfo.class).enableDiscriminator(true).build();
        ClassModel<ApiInfo.ApiInfoKey> apiInfoKeyClassModel = ClassModel.builder(ApiInfo.ApiInfoKey.class)
                .enableDiscriminator(true).build();
        ClassModel<CustomFilter> customFilterClassModel = ClassModel.builder(CustomFilter.class)
                .enableDiscriminator(true).build();
        ClassModel<FieldExistsFilter> fieldExistsFilterClassModel = ClassModel.builder(FieldExistsFilter.class)
                .enableDiscriminator(true).build();
        ClassModel<ResponseCodeRuntimeFilter> responseCodeRuntimeFilterClassModel = ClassModel
                .builder(ResponseCodeRuntimeFilter.class).enableDiscriminator(true).build();
        ;
        ClassModel<RuntimeFilter> runtimeFilterClassModel = ClassModel.builder(RuntimeFilter.class)
                .enableDiscriminator(true).build();
        ClassModel<FilterSampleData> filterSampleDataClassModel = ClassModel.builder(FilterSampleData.class)
                .enableDiscriminator(true).build();
        ClassModel<AccountSettings> accountSettingsClassModel = ClassModel.builder(AccountSettings.class)
                .enableDiscriminator(true).build();
        ClassModel<Predicate> predicateClassModel = ClassModel.builder(Predicate.class).enableDiscriminator(true)
                .build();
        ClassModel<RegexPredicate> regexPredicateClassModel = ClassModel.builder(RegexPredicate.class)
                .enableDiscriminator(true).build();
        ClassModel<StartsWithPredicate> startsWithPredicateClassModel = ClassModel.builder(StartsWithPredicate.class)
                .enableDiscriminator(true).build();
        ClassModel<EndsWithPredicate> endsWithPredicateClassModel = ClassModel.builder(EndsWithPredicate.class)
                .enableDiscriminator(true).build();
        ClassModel<EqualsToPredicate> equalsToPredicateClassModel = ClassModel.builder(EqualsToPredicate.class)
                .enableDiscriminator(true).build();
        ClassModel<IsNumberPredicate> isNumberPredicateClassModel = ClassModel.builder(IsNumberPredicate.class)
                .enableDiscriminator(true).build();
        ClassModel<Conditions> conditionsClassModel = ClassModel.builder(Conditions.class).enableDiscriminator(true)
                .build();
        ClassModel<CappedList> cappedListClassModel = ClassModel.builder(CappedList.class).enableDiscriminator(true)
                .build();
        ClassModel<TestingRun> testingRunClassModel = ClassModel.builder(TestingRun.class).enableDiscriminator(true)
                .build();
        ClassModel<TestingRunResult> testingRunResultClassModel = ClassModel.builder(TestingRunResult.class)
                .enableDiscriminator(true).build();
        ClassModel<TestResult> testResultClassModel = ClassModel.builder(TestResult.class).enableDiscriminator(true)
                .build();
        ClassModel<MultiExecTestResult> multiExecTestResultClassModel = ClassModel.builder(MultiExecTestResult.class).enableDiscriminator(true)
                .build();
        ClassModel<GenericTestResult> genericTestResultClassModel = ClassModel.builder(GenericTestResult.class).enableDiscriminator(true)
                .build();
        ClassModel<AuthMechanism> authMechanismClassModel = ClassModel.builder(AuthMechanism.class)
                .enableDiscriminator(true).build();
        ClassModel<Setup> setupClassModel = ClassModel.builder(Setup.class)
                .enableDiscriminator(true).build();
        ClassModel<AuthParam> authParamClassModel = ClassModel.builder(AuthParam.class).enableDiscriminator(true)
                .build();
        ClassModel<HardcodedAuthParam> hardcodedAuthParamClassModel = ClassModel.builder(HardcodedAuthParam.class)
                .enableDiscriminator(true).build();
        ClassModel<LoginRequestAuthParam> loginReqAuthParamClassModel = ClassModel.builder(LoginRequestAuthParam.class)
                .enableDiscriminator(true).build();
        ClassModel<SampleDataAuthParam> sampleDataAuthParamClassModel = ClassModel.builder(SampleDataAuthParam.class)
                .enableDiscriminator(true).build();
        ClassModel<TestingEndpoints> testingEndpointsClassModel = ClassModel.builder(TestingEndpoints.class)
                .enableDiscriminator(true).build();
        ClassModel<CustomTestingEndpoints> customTestingEndpointsClassModel = ClassModel
                .builder(CustomTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<AllTestingEndpoints> allTestingEndpointsClassModel = ClassModel
                .builder(AllTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<CollectionWiseTestingEndpoints> collectionWiseTestingEndpointsClassModel = ClassModel
                .builder(CollectionWiseTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<WorkflowTestingEndpoints> workflowTestingEndpointsClassModel = ClassModel
                .builder(WorkflowTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<WorkflowTestResult> workflowTestResultClassModel = ClassModel.builder(WorkflowTestResult.class)
                .enableDiscriminator(true).build();
        ClassModel<WorkflowTest> workflowTestClassModel = ClassModel.builder(WorkflowTest.class)
                .enableDiscriminator(true).build();
        ClassModel<CappedSet> cappedSetClassModel = ClassModel.builder(CappedSet.class).enableDiscriminator(true)
                .build();
        ClassModel<CustomWebhook> CustomWebhookClassModel = ClassModel.builder(CustomWebhook.class)
                .enableDiscriminator(true).build();
        ClassModel<WorkflowNodeDetails> WorkflowNodeDetailsClassModel = ClassModel.builder(WorkflowNodeDetails.class)
                .enableDiscriminator(true).build();                
        ClassModel<CustomWebhookResult> CustomWebhookResultClassModel = ClassModel.builder(CustomWebhookResult.class)
                .enableDiscriminator(true).build();
        ClassModel<WorkflowTestResult.NodeResult> nodeResultClassModel = ClassModel
                .builder(WorkflowTestResult.NodeResult.class).enableDiscriminator(true).build();
        ClassModel<TestingRunIssues> testingRunIssuesClassModel = ClassModel
                .builder(TestingRunIssues.class).enableDiscriminator(true).build();
        ClassModel<TestingIssuesId> testingIssuesIdClassModel = ClassModel
                .builder(TestingIssuesId.class).enableDiscriminator(true).build();
        ClassModel<TestSourceConfig> testSourceConfigClassModel = ClassModel
                .builder(TestSourceConfig.class).enableDiscriminator(true).build();
        ClassModel<EndpointLogicalGroup> endpointLogicalGroupClassModel = ClassModel
                .builder(EndpointLogicalGroup.class).enableDiscriminator(true).build();
        ClassModel<TestRoles> testRolesClassModel = ClassModel
                .builder(TestRoles.class).enableDiscriminator(true).build();
        ClassModel<LogicalGroupTestingEndpoint> logicalGroupTestingEndpointClassModel = ClassModel
                .builder(LogicalGroupTestingEndpoint.class).enableDiscriminator(true).build();
        ClassModel<CustomAuthType> customAuthTypeModel = ClassModel
                .builder(CustomAuthType.class).enableDiscriminator(true).build();
        ClassModel<ContainsPredicate> containsPredicateClassModel = ClassModel
                .builder(ContainsPredicate.class).enableDiscriminator(true).build();
        ClassModel<NotBelongsToPredicate> notBelongsToPredicateClassModel = ClassModel
                .builder(NotBelongsToPredicate.class).enableDiscriminator(true).build();
        ClassModel<BelongsToPredicate> belongsToPredicateClassModel = ClassModel
                .builder(BelongsToPredicate.class).enableDiscriminator(true).build();
        ClassModel<YamlNodeDetails> yamlNodeDetails = ClassModel
                .builder(YamlNodeDetails.class).enableDiscriminator(true).build();
        ClassModel<UnauthenticatedEndpoint> unauthenticatedEndpointsClassModel = ClassModel
                .builder(UnauthenticatedEndpoint.class).enableDiscriminator(true).build();
        ClassModel<EventsExample> eventsExampleClassModel = ClassModel
                .builder(EventsExample.class).enableDiscriminator(true).build();
        // ClassModel<AwsResource> awsResourceModel =
        // ClassModel.builder(AwsResource.class).enableDiscriminator(true)
        // .build();
        ClassModel<AwsResources> awsResourcesModel = ClassModel.builder(AwsResources.class).enableDiscriminator(true)
                .build();
        ClassModel<AktoDataType> AktoDataTypeClassModel = ClassModel.builder(AktoDataType.class).enableDiscriminator(true).build();
        ClassModel<TestInfo> testInfoClassModel = ClassModel.builder(TestInfo.class).enableDiscriminator(true).build();
        ClassModel<BFLATestInfo> bflaTestInfoClassModel = ClassModel.builder(BFLATestInfo.class).enableDiscriminator(true).build();
        ClassModel<AccessMatrixUrlToRole> accessMatrixUrlToRoleClassModel = ClassModel.builder(AccessMatrixUrlToRole.class).enableDiscriminator(true).build();
        ClassModel<AccessMatrixTaskInfo> accessMatrixTaskInfoClassModel = ClassModel.builder(AccessMatrixTaskInfo.class).enableDiscriminator(true).build();
        ClassModel<Loader> loaderClassModel = ClassModel.builder(Loader.class).enableDiscriminator(true).build();
        ClassModel<NormalLoader> normalLoaderClassModel = ClassModel.builder(NormalLoader.class).enableDiscriminator(true).build();
        ClassModel<PostmanUploadLoader> postmanUploadLoaderClassModel = ClassModel.builder(PostmanUploadLoader.class).enableDiscriminator(true).build();
        ClassModel<AktoGptConfig> aktoGptConfigClassModel = ClassModel.builder(AktoGptConfig.class).enableDiscriminator(true).build();

        ClassModel<LoginFlowStepsData> loginFlowStepsData = ClassModel.builder(LoginFlowStepsData.class)
        .enableDiscriminator(true).build();
        ClassModel<VulnerableRequestForTemplate> vulnerableRequestForTemplateClassModel = ClassModel.builder(VulnerableRequestForTemplate.class).enableDiscriminator(true).build();
        ClassModel<TrafficMetricsAlert> trafficMetricsAlertClassModel = ClassModel.builder(TrafficMetricsAlert.class).enableDiscriminator(true).build();
        ClassModel<JiraIntegration> jiraintegrationClassModel = ClassModel.builder(JiraIntegration.class).enableDiscriminator(true).build();
        ClassModel<MethodCondition> methodConditionClassModel = ClassModel.builder(MethodCondition.class).enableDiscriminator(true).build();
        ClassModel<RegexTestingEndpoints> regexTestingEndpointsClassModel = ClassModel.builder(RegexTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<HostRegexTestingEndpoints> hostRegexTestingEndpointsClassModel = ClassModel.builder(HostRegexTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<TagsTestingEndpoints> tagsTestingEndpointsClassModel = ClassModel.builder(TagsTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<DependencyNode> dependencyNodeClassModel = ClassModel.builder(DependencyNode.class).enableDiscriminator(true).build();
        ClassModel<ParamInfo> paramInfoClassModel = ClassModel.builder(ParamInfo.class).enableDiscriminator(true).build();
        ClassModel<Node> nodeClassModel = ClassModel.builder(Node.class).enableDiscriminator(true).build();
        ClassModel<Connection> connectionClassModel = ClassModel.builder(Connection.class).enableDiscriminator(true).build();
        ClassModel<Edge> edgeClassModel = ClassModel.builder(Edge.class).enableDiscriminator(true).build();
        ClassModel<LastCronRunInfo> cronTimersClassModel = ClassModel.builder(LastCronRunInfo.class)
                .enableDiscriminator(true).build();
        ClassModel<ConnectionInfo> connectionInfoClassModel = ClassModel.builder(ConnectionInfo.class)
                .enableDiscriminator(true).build();
        ClassModel<TestLibrary> testLibraryClassModel = ClassModel.builder(TestLibrary.class).enableDiscriminator(true).build();

        ClassModel<UsageMetric> UsageMetricClassModel = ClassModel.builder(UsageMetric.class).enableDiscriminator(true).build();
        ClassModel<UsageMetricInfo> UsageMetricInfoClassModel = ClassModel.builder(UsageMetricInfo.class).enableDiscriminator(true).build();
        ClassModel<UsageSync> UsageSyncClassModel = ClassModel.builder(UsageSync.class).enableDiscriminator(true).build();
        ClassModel<Organization> OrganizationClassModel = ClassModel.builder(Organization.class).enableDiscriminator(true).build();
        ClassModel<ReplaceDetail> replaceDetailClassModel = ClassModel.builder(ReplaceDetail.class).enableDiscriminator(true).build();
        ClassModel<ModifyHostDetail> modifyHostDetailClassModel = ClassModel.builder(ModifyHostDetail.class).enableDiscriminator(true).build();
        ClassModel<FileUpload> fileUploadClassModel = ClassModel.builder(FileUpload.class).enableDiscriminator(true).build();
        ClassModel<FileUploadLog> fileUploadLogClassModel = ClassModel.builder(FileUploadLog.class).enableDiscriminator(true).build();
        ClassModel<CodeAnalysisCollection> codeAnalysisCollectionClassModel = ClassModel.builder(CodeAnalysisCollection.class).enableDiscriminator(true).build();
        ClassModel<CodeAnalysisApiLocation> codeAnalysisApiLocationClassModel = ClassModel.builder(CodeAnalysisApiLocation.class).enableDiscriminator(true).build();
        ClassModel<CodeAnalysisApiInfo> codeAnalysisApiInfoClassModel = ClassModel.builder(CodeAnalysisApiInfo.class).enableDiscriminator(true).build();
        ClassModel<CodeAnalysisApiInfo.CodeAnalysisApiInfoKey> codeAnalysisApiInfoKeyClassModel = ClassModel.builder(CodeAnalysisApiInfo.CodeAnalysisApiInfoKey.class).enableDiscriminator(true).build();
        ClassModel<RiskScoreTestingEndpoints> riskScoreTestingEndpointsClassModel = ClassModel.builder(RiskScoreTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<OrganizationFlags> OrganizationFlagsClassModel = ClassModel.builder(OrganizationFlags.class).enableDiscriminator(true).build();
        ClassModel<SensitiveDataEndpoints> sensitiveDataEndpointsClassModel = ClassModel.builder(SensitiveDataEndpoints.class).enableDiscriminator(true).build();
        ClassModel<AllAPIsGroup> allApisGroupClassModel = ClassModel.builder(AllAPIsGroup.class).enableDiscriminator(true).build();

        ClassModel<Remediation> remediationClassModel = ClassModel.builder(Remediation.class).enableDiscriminator(true).build();
        ClassModel<ComplianceMapping> complianceMappingModel = ClassModel.builder(ComplianceMapping.class).enableDiscriminator(true).build();
        ClassModel<ComplianceInfo> complianceInfoModel = ClassModel.builder(ComplianceInfo.class).enableDiscriminator(true).build();
        ClassModel<RuntimeMetrics> RuntimeMetricsClassModel = ClassModel.builder(RuntimeMetrics.class).enableDiscriminator(true).build();
        ClassModel<CodeAnalysisApi>  codeAnalysisApiModel = ClassModel.builder(CodeAnalysisApi.class).enableDiscriminator(true).build();
        ClassModel<CodeAnalysisRepo> codeAnalysisRepoModel = ClassModel.builder(CodeAnalysisRepo.class).enableDiscriminator(true).build();
        ClassModel<HistoricalData> historicalDataClassModel = ClassModel.builder(HistoricalData.class).enableDiscriminator(true).build();
        ClassModel<TestConfigsAdvancedSettings> configSettingClassModel = ClassModel.builder(TestConfigsAdvancedSettings.class).enableDiscriminator(true).build();
        ClassModel<ConditionsType> configSettingsConditionTypeClassModel = ClassModel.builder(ConditionsType.class).enableDiscriminator(true).build();
        ClassModel<CustomRole> roleClassModel = ClassModel.builder(CustomRole.class).enableDiscriminator(true).build();
        ClassModel<TestingInstanceHeartBeat> testingInstanceHeartBeat = ClassModel.builder(TestingInstanceHeartBeat.class).enableDiscriminator(true).build();
        ClassModel<JobParams> jobParams = ClassModel.builder(JobParams.class).enableDiscriminator(true).build();
        ClassModel<AutoTicketParams> autoTicketParams = ClassModel.builder(AutoTicketParams.class).enableDiscriminator(true).build();
        ClassModel<Model> agentModel = ClassModel.builder(Model.class).enableDiscriminator(true).build();
        ClassModel<ModuleInfo> ModuleInfoClassModel = ClassModel.builder(ModuleInfo.class).enableDiscriminator(true).build();
        ClassModel<TLSAuthParam> tlsAuthClassModel = ClassModel.builder(TLSAuthParam.class).enableDiscriminator(true).build();
        ClassModel<ApiHitCountInfo> apiHitCountInfoClassModel = ClassModel.builder(ApiHitCountInfo.class).enableDiscriminator(true).build();
        ClassModel<BidirectionalSyncSettings> testingIssueTicketsModel = ClassModel.builder(BidirectionalSyncSettings.class).enableDiscriminator(true).build();
        ClassModel<TicketSyncJobParams> ticketSyncJobParamsClassModel = ClassModel.builder(TicketSyncJobParams.class).enableDiscriminator(true).build();
        ClassModel<CollectionTags> collectionTagsModel = ClassModel.builder(CollectionTags.class).enableDiscriminator(true).build();
        ClassModel<ApiSequences> apiSequencesClassModel = ClassModel.builder(ApiSequences.class).enableDiscriminator(true).build();
        ClassModel<EndpointShieldLog> endpointShieldLogClassModel = ClassModel.builder(EndpointShieldLog.class).enableDiscriminator(true).build();


        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().register(
                configClassModel, signupInfoClassModel, apiAuthClassModel, attempResultModel, urlTemplateModel,
                pendingInviteCodeClassModel, rbacClassModel, kafkaHealthMetricClassModel, singleTypeInfoClassModel,
                thirdPartyAccessClassModel, credentialClassModel, apiTokenClassModel, apiInfoClassModel,
                apiInfoKeyClassModel, customFilterClassModel, runtimeFilterClassModel, filterSampleDataClassModel,
                predicateClassModel, conditionsClassModel, regexPredicateClassModel, startsWithPredicateClassModel,
                endsWithPredicateClassModel,
                fieldExistsFilterClassModel, accountSettingsClassModel, responseCodeRuntimeFilterClassModel,
                cappedListClassModel,
                equalsToPredicateClassModel, isNumberPredicateClassModel, testingRunClassModel,
                testingRunResultClassModel, testResultClassModel, genericTestResultClassModel,
                authMechanismClassModel, authParamClassModel, hardcodedAuthParamClassModel, loginReqAuthParamClassModel, sampleDataAuthParamClassModel,
                testingEndpointsClassModel, customTestingEndpointsClassModel, collectionWiseTestingEndpointsClassModel,
                workflowTestingEndpointsClassModel, workflowTestResultClassModel,
                cappedSetClassModel, CustomWebhookClassModel, WorkflowNodeDetailsClassModel, CustomWebhookResultClassModel,
                nodeResultClassModel, awsResourcesModel, AktoDataTypeClassModel, testingRunIssuesClassModel,
                testingIssuesIdClassModel, testSourceConfigClassModel, endpointLogicalGroupClassModel, testRolesClassModel,
                logicalGroupTestingEndpointClassModel, testInfoClassModel, bflaTestInfoClassModel, customAuthTypeModel,
                containsPredicateClassModel, notBelongsToPredicateClassModel, belongsToPredicateClassModel,
                loginFlowStepsData,
                accessMatrixUrlToRoleClassModel, accessMatrixTaskInfoClassModel,
                loaderClassModel, normalLoaderClassModel, postmanUploadLoaderClassModel, aktoGptConfigClassModel,
                vulnerableRequestForTemplateClassModel, trafficMetricsAlertClassModel, jiraintegrationClassModel,
                setupClassModel,
                cronTimersClassModel, connectionInfoClassModel, testLibraryClassModel,
                methodConditionClassModel, regexTestingEndpointsClassModel, hostRegexTestingEndpointsClassModel,
                tagsTestingEndpointsClassModel, allTestingEndpointsClassModel,
                UsageMetricClassModel, UsageMetricInfoClassModel, UsageSyncClassModel, OrganizationClassModel,
                yamlNodeDetails, multiExecTestResultClassModel, workflowTestClassModel, dependencyNodeClassModel,
                paramInfoClassModel,
                nodeClassModel, connectionClassModel, edgeClassModel, replaceDetailClassModel, modifyHostDetailClassModel,
                fileUploadClassModel, fileUploadLogClassModel, codeAnalysisCollectionClassModel,
                codeAnalysisApiLocationClassModel,
                codeAnalysisApiInfoClassModel, codeAnalysisApiInfoKeyClassModel,
                riskScoreTestingEndpointsClassModel, OrganizationFlagsClassModel, sensitiveDataEndpointsClassModel,
                unauthenticatedEndpointsClassModel, allApisGroupClassModel,
                eventsExampleClassModel, remediationClassModel, complianceInfoModel, complianceMappingModel,
                RuntimeMetricsClassModel, codeAnalysisRepoModel, codeAnalysisApiModel, historicalDataClassModel,
                configSettingClassModel, configSettingsConditionTypeClassModel, roleClassModel, testingInstanceHeartBeat,
                jobParams, autoTicketParams, agentModel, ModuleInfoClassModel, testingIssueTicketsModel, tlsAuthClassModel,
                ticketSyncJobParamsClassModel, apiHitCountInfoClassModel, collectionTagsModel, apiSequencesClassModel, endpointShieldLogClassModel)
            .automatic(true).build());

        final CodecRegistry customEnumCodecs = CodecRegistries.fromCodecs(
                new EnumCodec<>(Conditions.Operator.class),
                new EnumCodec<>(SingleTypeInfo.SuperType.class),
                new EnumCodec<>(Method.class),
                new EnumCodec<>(Credential.Type.class),
                new EnumCodec<>(ApiToken.Utility.class),
                new EnumCodec<>(ApiInfo.AuthType.class),
                new EnumCodec<>(ApiInfo.ApiAccessType.class),
                new EnumCodec<>(TestResult.TestError.class),
                new EnumCodec<>(AuthParam.Location.class),
                new EnumCodec<>(TestingEndpoints.Type.class),
                new EnumCodec<>(TestingRun.State.class),
                new EnumCodec<>(AccountSettings.SetupType.class),
                new EnumCodec<>(WorkflowNodeDetails.Type.class),
                new EnumCodec<>(SingleTypeInfo.Domain.class),
                new EnumCodec<>(CustomWebhook.ActiveStatus.class),
                new EnumCodec<>(TestResult.Confidence.class),
                new EnumCodec<>(SingleTypeInfo.Position.class),
                new EnumCodec<>(TestResult.Confidence.class),
                new EnumCodec<>(GlobalEnums.TestRunIssueStatus.class),
                new EnumCodec<>(GlobalEnums.TestErrorSource.class),
                new EnumCodec<>(GlobalEnums.TestCategory.class),
                new EnumCodec<>(GlobalEnums.IssueTags.class),
                new EnumCodec<>(GlobalEnums.Severity.class),
                new EnumCodec<>(TrafficMetrics.Name.class),
                new EnumCodec<>(MetricData.Name.class),
                new EnumCodec<>(Loader.Type.class),
                new EnumCodec<>(CustomWebhook.WebhookOptions.class),
                new EnumCodec<>(GlobalEnums.YamlTemplateSource.class),
                new EnumCodec<>(AktoGptConfigState.class),
                new EnumCodec<>(CustomWebhook.WebhookOptions.class),
                new EnumCodec<>(TestingEndpoints.Operator.class),
                new EnumCodec<>(MetricTypes.class),
                new EnumCodec<>(User.AktoUIMode.class),
                new EnumCodec<>(TrafficMetricsAlert.FilterType.class),
                new EnumCodec<>(KVPair.KVType.class),
                new EnumCodec<>(ApiCollection.ENV_TYPE.class),
                new EnumCodec<>(FileUpload.UploadType.class),
                new EnumCodec<>(FileUpload.UploadStatus.class),
                new EnumCodec<>(FileUploadLog.UploadLogStatus.class),
                new EnumCodec<>(TestCollectionProperty.Id.class),
                new EnumCodec<>(CustomAuthType.TypeOfToken.class),
                new EnumCodec<>(TrafficAlerts.ALERT_TYPE.class),
                new EnumCodec<>(ApiInfo.ApiType.class),
                new EnumCodec<>(CodeAnalysisRepo.SourceCodeType.class),
                new EnumCodec<>(State.class),
                new EnumCodec<>(ModelType.class),
                new EnumCodec<>(ModuleInfo.ModuleType.class),
                new EnumCodec<>(TLSAuthParam.CertificateType.class),
                new EnumCodec<>(TicketSource.class)
        );

        return fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry,
                customEnumCodecs);
    }

    public static void init(ConnectionString connectionString, ReadPreference readPreference, WriteConcern writeConcern) {
        DbMode.refreshDbType(connectionString.getConnectionString());
        logger.info("DB type: {}", DbMode.dbType);
        DbMode.refreshSetupType(connectionString);
        logger.info("DB setup type: {}", DbMode.setupType);

        CodecRegistry codecRegistry = createCodecRegistry();

        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .readPreference(readPreference)
                .writeConcern(writeConcern)
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .build();

        clients[0] = MongoClients.create(clientSettings);
    }
    public static void init(ConnectionString connectionString) {
        init(connectionString, ReadPreference.secondary(), WriteConcern.ACKNOWLEDGED);
    }

    public static void createIndices() {
        try {
            TestingRunResultDao.instance.convertToCappedCollection();
        } catch (Exception e) {
                logger.error("Error while converting TestingRunResults to capped collection: " + e.getMessage());
        }

        OrganizationsDao.createIndexIfAbsent();
        UsageMetricsDao.createIndexIfAbsent();
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        TrafficMetricsDao.instance.createIndicesIfAbsent();
        TestRolesDao.instance.createIndicesIfAbsent();
        UsersDao.instance.createIndicesIfAbsent();
        AccountsDao.instance.createIndexIfAbsent();

        ApiInfoDao.instance.createIndicesIfAbsent();
        ApiSequencesDao.instance.createIndicesIfAbsent();
        RuntimeLogsDao.instance.createIndicesIfAbsent();
        LogsDao.instance.createIndicesIfAbsent();
        DashboardLogsDao.instance.createIndicesIfAbsent();
        DataIngestionLogsDao.instance.createIndicesIfAbsent();
        AnalyserLogsDao.instance.createIndicesIfAbsent();
        SampleDataDao.instance.createIndicesIfAbsent();
        LoadersDao.instance.createIndicesIfAbsent();
        TestingRunResultDao.instance.createIndicesIfAbsent();
        TestingRunResultSummariesDao.instance.createIndicesIfAbsent();
        TestingRunDao.instance.createIndicesIfAbsent();
        TestingRunPlaygroundDao.instance.createIndicesIfAbsent();
        TestingRunIssuesDao.instance.createIndicesIfAbsent();
        ApiCollectionsDao.instance.createIndicesIfAbsent();
        ActivitiesDao.instance.createIndicesIfAbsent();
        DependencyNodeDao.instance.createIndicesIfAbsent();
        DependencyFlowNodesDao.instance.createIndicesIfAbsent();
        CodeAnalysisCollectionDao.instance.createIndicesIfAbsent();
        CodeAnalysisApiInfoDao.instance.createIndicesIfAbsent();
        RBACDao.instance.createIndicesIfAbsent();
        TrafficAlertsDao.instance.createIndicesIfAbsent();
        RuntimeMetricsDao.instance.createIndicesIfAbsent();
        ApiAuditLogsDao.instance.createIndicesIfAbsent();
        VulnerableTestingRunResultDao.instance.createIndicesIfAbsent();
        CustomRoleDao.instance.createIndicesIfAbsent();
        TestingInstanceHeartBeatDao.instance.createIndexIfAbsent();
        PupeteerLogsDao.instance.createIndicesIfAbsent();
        SourceCodeVulnerabilitiesDao.instance.createIndicesIfAbsent();
        JobsDao.instance.createIndicesIfAbsent();
        BidirectionalSyncSettingsDao.instance.createIndicesIfAbsent();
        MetricDataDao.instance.createIndicesIfAbsent();
        SensitiveSampleDataDao.instance.createIndicesIfAbsent();
        McpAuditInfoDao.instance.createIndicesIfAbsent();
        McpReconRequestDao.instance.createIndicesIfAbsent();
        GuardrailPoliciesDao.instance.createIndicesIfAbsent();
        EndpointShieldLogsDao.instance.createIndicesIfAbsent();
    }
}
