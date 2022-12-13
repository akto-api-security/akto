package com.akto;

import com.akto.dto.*;
import com.akto.dto.data_types.*;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.content.Content;
import com.akto.dto.runtime_filters.FieldExistsFilter;
import com.akto.dto.FilterSampleData;
import com.akto.dto.runtime_filters.ResponseCodeRuntimeFilter;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.*;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.type.URLTemplate;
import com.akto.types.CappedList;
import com.akto.types.CappedSet;
import com.akto.util.EnumCodec;
import com.akto.dto.Attempt.AttemptResult;
import com.akto.dto.auth.APIAuth;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;

import static com.akto.dao.MCollection.clients;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class DaoInit {

    public static void init(ConnectionString connectionString) {
        ClassModel<MessageQueueEntry> queueEntryClassModel = ClassModel.builder(MessageQueueEntry.class)
                .enableDiscriminator(true).build();
        ClassModel<Config> configClassModel = ClassModel.builder(Config.class).enableDiscriminator(true).build();
        ClassModel<SignupInfo> signupInfoClassModel = ClassModel.builder(SignupInfo.class).enableDiscriminator(true)
                .build();
        ClassModel<Content> contentClassModel = ClassModel.builder(Content.class).enableDiscriminator(true).build();
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
        ClassModel<AuthMechanism> authMechanismClassModel = ClassModel.builder(AuthMechanism.class)
                .enableDiscriminator(true).build();
        ClassModel<AuthParam> authParamClassModel = ClassModel.builder(AuthParam.class).enableDiscriminator(true)
                .build();
        ClassModel<HardcodedAuthParam> hardcodedAuthParamClassModel = ClassModel.builder(HardcodedAuthParam.class)
                .enableDiscriminator(true).build();
        ClassModel<LoginRequestAuthParam> loginReqAuthParamClassModel = ClassModel.builder(LoginRequestAuthParam.class)
                .enableDiscriminator(true).build();
        ClassModel<TestingEndpoints> testingEndpointsClassModel = ClassModel.builder(TestingEndpoints.class)
                .enableDiscriminator(true).build();
        ClassModel<CustomTestingEndpoints> customTestingEndpointsClassModel = ClassModel
                .builder(CustomTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<CollectionWiseTestingEndpoints> collectionWiseTestingEndpointsClassModel = ClassModel
                .builder(CollectionWiseTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<WorkflowTestingEndpoints> workflowTestingEndpointsClassModel = ClassModel
                .builder(WorkflowTestingEndpoints.class).enableDiscriminator(true).build();
        ClassModel<WorkflowTestResult> workflowTestResultClassModel = ClassModel.builder(WorkflowTestResult.class)
                .enableDiscriminator(true).build();
        ClassModel<CappedSet> cappedSetClassModel = ClassModel.builder(CappedSet.class).enableDiscriminator(true)
                .build();
        ClassModel<CustomWebhook> CustomWebhookClassModel = ClassModel.builder(CustomWebhook.class)
                .enableDiscriminator(true).build();
        ClassModel<CustomWebhookResult> CustomWebhookResultClassModel = ClassModel.builder(CustomWebhookResult.class)
                .enableDiscriminator(true).build();
        ClassModel<WorkflowTestResult.NodeResult> nodeResultClassModel = ClassModel
                .builder(WorkflowTestResult.NodeResult.class).enableDiscriminator(true).build();
        ClassModel<TestingRunIssues> testingRunIssuesClassModel = ClassModel
                .builder(TestingRunIssues.class).enableDiscriminator(true).build();
        ClassModel<TestingIssuesId> testingIssuesIdClassModel = ClassModel
                .builder(TestingIssuesId.class).enableDiscriminator(true).build();
        ClassModel<CustomAuthType> customAuthTypeModel = ClassModel
                .builder(CustomAuthType.class).enableDiscriminator(true).build();
        // ClassModel<AwsResource> awsResourceModel =
        // ClassModel.builder(AwsResource.class).enableDiscriminator(true)
        // .build();
        ClassModel<AwsResources> awsResourcesModel = ClassModel.builder(AwsResources.class).enableDiscriminator(true)
                .build();
        ClassModel<AktoDataType> AktoDataTypeClassModel = ClassModel.builder(AktoDataType.class).enableDiscriminator(true).build();

        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().register(queueEntryClassModel,
                configClassModel,
                signupInfoClassModel, contentClassModel, apiAuthClassModel, attempResultModel, urlTemplateModel,
                pendingInviteCodeClassModel, rbacClassModel, kafkaHealthMetricClassModel, singleTypeInfoClassModel,
                thirdPartyAccessClassModel, credentialClassModel, apiTokenClassModel, apiInfoClassModel,
                apiInfoKeyClassModel, customFilterClassModel, runtimeFilterClassModel, filterSampleDataClassModel,
                predicateClassModel, conditionsClassModel, regexPredicateClassModel, startsWithPredicateClassModel,
                endsWithPredicateClassModel,
                fieldExistsFilterClassModel, accountSettingsClassModel, responseCodeRuntimeFilterClassModel,
                cappedListClassModel,
                equalsToPredicateClassModel, isNumberPredicateClassModel, testingRunClassModel,
                testingRunResultClassModel, testResultClassModel,
                authMechanismClassModel, authParamClassModel, hardcodedAuthParamClassModel, loginReqAuthParamClassModel,
                testingEndpointsClassModel, customTestingEndpointsClassModel, collectionWiseTestingEndpointsClassModel,
                workflowTestingEndpointsClassModel, workflowTestResultClassModel,
                cappedSetClassModel, CustomWebhookClassModel, CustomWebhookResultClassModel,
                nodeResultClassModel, awsResourcesModel, AktoDataTypeClassModel, testingRunIssuesClassModel,
                testingIssuesIdClassModel, customAuthTypeModel).automatic(true).build());

        final CodecRegistry customEnumCodecs = CodecRegistries.fromCodecs(
                new EnumCodec<>(Conditions.Operator.class),
                new EnumCodec<>(SingleTypeInfo.SuperType.class),
                new EnumCodec<>(Method.class),
                new EnumCodec<>(RBAC.Role.class),
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
                new EnumCodec<>(GlobalEnums.TestSubCategory.class),
                new EnumCodec<>(GlobalEnums.Severity.class));

        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry,
                customEnumCodecs);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .build();

        clients[0] = MongoClients.create(clientSettings);
    }

}
