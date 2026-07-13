package com.akto.data_actor;

import static com.akto.util.Constants.ID;

import com.akto.dao.jobs.JobsDao;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dto.jobs.Job;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.akto.bulk_update_util.ApiInfoBulkUpdate;
import com.akto.dao.*;
import com.akto.dao.agent_classifiers.AgentGuardCorpusDao;
import com.akto.dao.agent_classifiers.AgentGuardCorpusQueueDao;
import com.akto.dao.agentic_sessions.AgentQueryDataDao;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dao.graph.SvcToSvcGraphEdgesDao;
import com.akto.dao.graph.SvcToSvcGraphNodesDao;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.agentic_sessions.SessionDocumentDao;
import com.akto.dao.settings.DataControlSettingsDao;
import com.akto.dao.testing.config.TestSuiteDao;
import com.akto.dependency_analyser.DependencyAnalyserUtils;
import com.akto.dto.*;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.graph.SvcToSvcGraphEdge;
import com.akto.dto.graph.SvcToSvcGraphNode;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.agentic_sessions.SessionDocument;
import com.akto.dto.settings.DataControlSettings;
import com.mongodb.BasicDBList;
import com.mongodb.client.model.*;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.billing.TokensDao;
import com.akto.dao.context.Context;
import com.akto.dao.file.FilesDao;
import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.dao.config_field_policy.ConfigFieldPolicyDao;
import com.akto.dto.config_field_policy.ConfigFieldPolicy;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dao.test_editor.TestingRunPlaygroundDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dao.testing.LoginFlowStepsDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dao.testing.TestingRunConfigDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.TestingRunResultSummariesDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing.WorkflowTestResultsDao;
import com.akto.dao.testing.WorkflowTestsDao;
import com.akto.dao.testing.config.TestScriptsDao;
import com.akto.dao.testing.sources.TestSourceConfigsDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dao.tracing.SpanDao;
import com.akto.dao.tracing.TraceDao;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.agent_classifiers.AgentGuardCorpusEntry;
import com.akto.dto.agent_classifiers.AgentGuardCorpusQueueEntry;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.files.File;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
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
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.CopilotOAuthAuthParam;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.tracing.Span;
import com.akto.dto.tracing.Trace;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.akto.metrics.ModuleHeartbeatProfilingMetrics;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.upload.SwaggerFileUpload;
import com.akto.dto.usage.MetricTypes;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.new_relic.NewRelicUtils;
import com.akto.open_telemetry.OpenTelemetryUtils;
import com.akto.dao.billing.UningestedApiOverageDao;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.usage.UsageMetricCalculator;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.Constants;
import com.akto.util.UsageUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;

public class DbLayer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DbLayer.class, LoggerMaker.LogDb.DASHBOARD);
    public static final String DEFAULT_MINI_TESTING_NAME = "Default_";

    private static final ConcurrentHashMap<Integer, Integer> lastUpdatedTsMap = new ConcurrentHashMap<>();

    // Cache for routing collection IDs (with routing tags)
    private static volatile Set<Integer> routingCollectionIdsCache = null;
    private static volatile long routingCollectionIdsCacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 60 minutes

    // Cache for merged URLs filtering
    private static volatile Set<MergedUrls> mergedUrlsCache = null;
    private static volatile long mergedUrlsCacheTimestamp = 0;
    private static final long MERGED_URLS_CACHE_TTL_MS = 15 * 60 * 1000; // 15 minutes

    // Collection cleanup configuration
    private static final ConcurrentHashMap<String, Integer> collectionCleanupCache = new ConcurrentHashMap<>();
    private static final int CLEANUP_INTERVAL_SECONDS = 30 * 60; // 30 minutes
    private static final int CLEANUP_JITTER_SECONDS = 3 * 60; // 3 minutes max jitter
    private static final long COLLECTION_SIZE_THRESHOLD = 100_000;

    private static final ExecutorService telemetryForwardingExecutorService = Executors.newFixedThreadPool(1);

    private static int getLastUpdatedTsForAccount(int accountId) {
        return lastUpdatedTsMap.computeIfAbsent(accountId, k -> 0);
    }

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

    public static ModuleInfo updateModuleInfo(ModuleInfo moduleInfo) {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        updateOptions.returnDocument(ReturnDocument.AFTER);

        if (Boolean.TRUE.equals(Context.tokenExpired.get())) {
            if (moduleInfo.getAdditionalData() == null) {
                moduleInfo.setAdditionalData(new HashMap<>());
            }
            moduleInfo.getAdditionalData().put("tokenExpired", true);
        }

        int accountId = Context.accountId.get();
        boolean forwardToNewRelic = NewRelicIntegrationDao.instance.findOne(new BasicDBObject()) != null;
        boolean forwardToOpenTelemetry = OpenTelemetryIntegrationDao.instance.findOne(new BasicDBObject()) != null;

        if (forwardToNewRelic) {
            loggerMaker.infoAndAddToDb(String.format("Forwarding module heartbeat to New Relic for module %s (account %d)", moduleInfo.getName(), accountId), LogDb.DB_ABS);
            try {
                telemetryForwardingExecutorService.submit(() -> {
                    Context.accountId.set(accountId);
                    NewRelicUtils.forwardModuleHeartbeatEvent(moduleInfo);
                });
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error submitting NR heartbeat task: " + e.getMessage(), LogDb.DB_ABS);
            }
        }

        if (forwardToOpenTelemetry) {
            loggerMaker.infoAndAddToDb(String.format("Forwarding module heartbeat to OpenTelemetry for module %s (account %d)", moduleInfo.getName(), accountId), LogDb.DB_ABS);
            try {
                telemetryForwardingExecutorService.submit(() -> {
                    Context.accountId.set(accountId);
                    OpenTelemetryUtils.forwardModuleHeartbeatEvent(moduleInfo);
                });
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error submitting OT heartbeat task: " + e.getMessage(), LogDb.DB_ABS);
            }
        }
        
        List<Bson> updateList = new ArrayList<>();
        updateList.add(Updates.setOnInsert("_t", moduleInfo.getClass().getName()));
        updateList.add(Updates.setOnInsert(ModuleInfo.MODULE_TYPE, moduleInfo.getModuleType()));
        updateList.add(Updates.setOnInsert(ModuleInfo.STARTED_TS, moduleInfo.getStartedTs()));
        updateList.add(Updates.set(ModuleInfo.CURRENT_VERSION, moduleInfo.getCurrentVersion()));
        updateList.add(Updates.setOnInsert(ModuleInfo.NAME, moduleInfo.getName()));
        updateList.add(Updates.setOnInsert(ModuleInfo.EXPIRES_AT, new java.util.Date(System.currentTimeMillis() + ModuleInfoDao.MODULE_INFO_TTL_MS)));
        updateList.add(Updates.set(ModuleInfo.LAST_HEARTBEAT_RECEIVED, moduleInfo.getLastHeartbeatReceived()));
        updateList.addAll(buildAdditionalDataUpdates(moduleInfo.getModuleType(), moduleInfo.getAdditionalData()));

        ModuleInfo result = ModuleInfoDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(ModuleInfoDao.ID, moduleInfo.getId()),
                Updates.combine(updateList),
                updateOptions);

        syncAgentUserFromModuleInfo(moduleInfo);

        return result;
    }

    private static List<Bson> buildAdditionalDataUpdates(ModuleInfo.ModuleType moduleType, Map<String, Object> additionalData) {
        List<Bson> updates = new ArrayList<>();
        if (additionalData == null || additionalData.isEmpty()) {
            return updates;
        }
        if (moduleType == ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD) {
            for (Map.Entry<String, Object> entry : additionalData.entrySet()) {
                updates.add(Updates.set(ModuleInfo.ADDITIONAL_DATA + "." + entry.getKey(), entry.getValue()));
            }
        } else {
            updates.add(Updates.set(ModuleInfo.ADDITIONAL_DATA, additionalData));
        }
        return updates;
    }

    private static void syncAgentUserFromModuleInfo(ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            return;
        }
        Map<String, Object> additionalData = moduleInfo.getAdditionalData();
        if (additionalData == null || !additionalData.containsKey("username")) {
            return;
        }
        try {
            String userName = String.valueOf(additionalData.get("username"));
            Object teamObj = additionalData.get("team");
            Object roleObj = additionalData.get("userRole");
            String teamName = teamObj == null ? null : StringUtils.trimToNull(teamObj.toString());
            String userRole = roleObj == null ? null : StringUtils.trimToNull(roleObj.toString());
            String device = moduleInfo.getName();
            AgentUsersDao.instance.upsertAgentUser(userName, teamName, userRole, device, Context.now());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error syncing agent user from module info: " + e.getMessage(), LogDb.DB_ABS);
        }
    }

    public static void bulkUpdateModuleInfo(List<ModuleInfo> moduleInfoList) {
        if (moduleInfoList == null || moduleInfoList.isEmpty()) {
            return;
        }

        List<WriteModel<ModuleInfo>> bulkUpdates = new ArrayList<>();
        UpdateOptions updateOptions = new UpdateOptions().upsert(true);

        for (ModuleInfo moduleInfo : moduleInfoList) {
            Bson filter = Filters.and(
                Filters.eq(ModuleInfoDao.ID, moduleInfo.getId()),
                Filters.eq(ModuleInfo._REBOOT, false)
            );
            Bson updates = Updates.combine(
                    //putting class name because findOneAndUpdate doesn't put class name by default
                    Updates.setOnInsert("_t", moduleInfo.getClass().getName()),
                    Updates.setOnInsert(ModuleInfo.MODULE_TYPE, moduleInfo.getModuleType().name()),
                    Updates.setOnInsert(ModuleInfo.STARTED_TS, moduleInfo.getStartedTs()),
                    Updates.setOnInsert(ModuleInfo.CURRENT_VERSION, moduleInfo.getCurrentVersion()),
                    Updates.setOnInsert(ModuleInfo.NAME, moduleInfo.getName()),
                    Updates.setOnInsert(ModuleInfo.EXPIRES_AT, new java.util.Date(System.currentTimeMillis() + ModuleInfoDao.MODULE_INFO_TTL_MS)),
                    Updates.set(ModuleInfo.ADDITIONAL_DATA, moduleInfo.getAdditionalData()),
                    Updates.set(ModuleInfo.MINI_RUNTIME_NAME, moduleInfo.getMiniRuntimeName()),
                    Updates.set(ModuleInfo.LAST_HEARTBEAT_RECEIVED, moduleInfo.getLastHeartbeatReceived()),
                    Updates.set(ModuleInfo.DELETE_TOPIC_AND_REBOOT, moduleInfo.isDeleteTopicAndReboot())
            );
            bulkUpdates.add(new UpdateOneModel<>(filter, updates, updateOptions));
        }

        ModuleInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);

        for (ModuleInfo moduleInfo : moduleInfoList) {
            syncAgentUserFromModuleInfo(moduleInfo);
        }

        ModuleHeartbeatProfilingMetrics.recordGaugeUpdates(moduleInfoList);
    }

    public static List<ModuleInfo> fetchAndUpdateModuleForReboot(ModuleInfo.ModuleType moduleType, String miniRuntimeName) {
        return fetchAndUpdateModuleForReboot(moduleType, miniRuntimeName, null);
    }

    public static List<ModuleInfo> fetchAndUpdateModuleForReboot(ModuleInfo.ModuleType moduleType, String miniRuntimeName, String moduleId) {
        if (moduleType == null) {
            return new ArrayList<>();
        }

        if (moduleType != ModuleInfo.ModuleType.THREAT_DETECTION &&
                moduleType != ModuleInfo.ModuleType.AKTO_AGENT_GATEWAY &&
                moduleType != ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD &&
                (miniRuntimeName == null || miniRuntimeName.isEmpty())) {
            return new ArrayList<>();
        }

        List<Bson> filterConditions = new ArrayList<>();
        filterConditions.add(Filters.eq(ModuleInfo._REBOOT, true));
        filterConditions.add(Filters.eq(ModuleInfo.MODULE_TYPE, moduleType.name()));

        if (miniRuntimeName != null && !miniRuntimeName.isEmpty()) {
            filterConditions.add(Filters.eq(ModuleInfo.MINI_RUNTIME_NAME, miniRuntimeName));
        }

        if (moduleId != null && !moduleId.isEmpty()) {
            filterConditions.add(Filters.eq("_id", moduleId));
        }

        Bson filter = Filters.and(filterConditions);

        List<ModuleInfo> moduleInfoList = ModuleInfoDao.instance.findAll(filter);

        if (moduleInfoList != null && !moduleInfoList.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (ModuleInfo moduleInfo : moduleInfoList) {
                ids.add(moduleInfo.getId());
            }

            Bson updateFilter = Filters.in("_id", ids);
            Bson updates = Updates.set(ModuleInfo._REBOOT, false);
            ModuleInfoDao.instance.updateMany(updateFilter, updates);
        }

        return moduleInfoList != null ? moduleInfoList : new ArrayList<>();
    }
    
    public static ModuleInfo updateModuleInfoForHeartbeatV2(ModuleInfo moduleInfo) {
        try {
            // Clean up expired heartbeats (older than 6 minutes)
            int sixMinutesAgo = (int) (System.currentTimeMillis() / 1000) - 360;
            ModuleInfoDao.instance.getMCollection().deleteMany(
                Filters.lt(ModuleInfo.LAST_HEARTBEAT_RECEIVED, sixMinutesAgo)
            );
        } catch (Exception e) {
            System.err.println("Failed to clean up expired heartbeats: " + e.getMessage());
        }

        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        updateOptions.returnDocument(ReturnDocument.AFTER);

        Bson filter = Filters.and(
            Filters.eq(ModuleInfo.NAME, moduleInfo.getName()),
            Filters.eq(ModuleInfo.MODULE_TYPE, moduleInfo.getModuleType())
        );

        return ModuleInfoDao.instance.getMCollection().findOneAndUpdate(filter,
                Updates.combine(
                        //putting class name because findOneAndUpdate doesn't put class name by default
                        Updates.setOnInsert("_t", moduleInfo.getClass().getName()),
                        Updates.setOnInsert(ID, moduleInfo.getId()),
                        Updates.set(ModuleInfo.MODULE_TYPE, moduleInfo.getModuleType()),
                        Updates.set(ModuleInfo.STARTED_TS, moduleInfo.getStartedTs()),
                        Updates.set(ModuleInfo.CURRENT_VERSION, moduleInfo.getCurrentVersion()),
                        Updates.set(ModuleInfo.NAME, moduleInfo.getName()),
                        Updates.set(ModuleInfo.ADDITIONAL_DATA, moduleInfo.getAdditionalData()),
                        Updates.set(ModuleInfo.LAST_HEARTBEAT_RECEIVED, moduleInfo.getLastHeartbeatReceived())
                ), updateOptions);
    }

    public static void updateDeviceDomainListDelta(String deviceId, String domainKey,
            List<String> toAdd, List<String> toRemove) {
        String fieldPath = DeviceDomainConfig.DOMAIN_LISTS + "." + domainKey;
        Bson filter = Filters.eq("_id", deviceId);
        UpdateOptions upsertOpt = new UpdateOptions().upsert(true);
        if (toAdd != null && !toAdd.isEmpty()) {
            DeviceDomainConfigDao.instance.getMCollection().updateOne(
                filter,
                Updates.combine(
                    Updates.addEachToSet(fieldPath, toAdd),
                    Updates.set(DeviceDomainConfig.UPDATED_AT, Context.now())
                ),
                upsertOpt
            );
        }
        if (toRemove != null && !toRemove.isEmpty()) {
            DeviceDomainConfigDao.instance.getMCollection().updateOne(
                filter,
                Updates.combine(
                    Updates.pullAll(fieldPath, toRemove),
                    Updates.set(DeviceDomainConfig.UPDATED_AT, Context.now())
                ),
                upsertOpt
            );
        }
    }

    public static DeviceDomainConfig fetchDeviceDomainConfig(String deviceId) {
        return DeviceDomainConfigDao.instance.findOne(Filters.eq("_id", deviceId));
    }

    public static void updateAccountDomainsDelta(String domainKey, List<String> toAdd, List<String> toRemove) {
        Bson filter = AccountSettingsDao.generateFilter();
        if (toAdd != null && !toAdd.isEmpty()) {
            List<String> uniqueToAdd = new ArrayList<>(new HashSet<>(toAdd));
            AccountSettingsDao.instance.getMCollection().updateOne(filter, Updates.addEachToSet(domainKey, uniqueToAdd));
        }
        if (toRemove != null && !toRemove.isEmpty()) {
            AccountSettingsDao.instance.getMCollection().updateOne(filter, Updates.pullAll(domainKey, toRemove));
        }
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

    public static Config.AutomatedAiTestingKeyConfig fetchModelApiKey() {
        return (Config.AutomatedAiTestingKeyConfig) ConfigsDao.instance.findOne(
            Filters.eq("_id", Config.AutomatedAiTestingKeyConfig.CONFIG_ID)
        );
    }

    private static final int FETCH_API_INFO_LAST_SEEN_CUTOFF_DAYS = 15;

    public static List<ApiInfo> fetchApiInfos() {
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(new BasicDBObject(), Projections.exclude(ApiInfo.RATELIMITS));
        Set<Integer> deactivated = UsageMetricCalculator.getDeactivated();
        int cutoffEpoch = Context.now() - FETCH_API_INFO_LAST_SEEN_CUTOFF_DAYS * 24 * 60 * 60;

        apiInfos.removeIf(apiInfo -> {
            // Never skip template URLs — they prevent URL explosion in the policy engine
            if (APICatalog.isTemplateUrl(apiInfo.getId().getUrl())) {
                return false;
            }
            // Skip static URLs from deactivated collections
            if (!deactivated.isEmpty() && deactivated.contains(apiInfo.getId().getApiCollectionId())) {
                return true;
            }
            // Skip static URLs with lastSeen older than cutoff
            return apiInfo.getLastSeen() < cutoffEpoch;
        });

        return apiInfos;
    }

    public static List<ApiInfo> fetchApiInfosByCollection(int apiCollectionId) {
        return ApiInfoDao.instance.findAll(
            Filters.eq("_id.apiCollectionId", apiCollectionId),
            Projections.exclude(ApiInfo.RATELIMITS)
        );
    }

    /**
     * Page size for cursor-based pagination (used by fetchApiRateLimits and fetchAllApiInfoKeys).
     */
    private static final int API_INFO_PAGE_SIZE = 1000;

    /**
     * Build the standard sort order for ApiInfo cursor-based pagination.
     * Sorts by compound _id key: apiCollectionId, method, url (all ascending).
     *
     * @return Bson sort specification
     */
    private static Bson buildApiInfoSortOrder() {
        return Sorts.orderBy(
            Sorts.ascending("_id.apiCollectionId"),
            Sorts.ascending("_id.method"),
            Sorts.ascending("_id.url")
        );
    }

    /**
     * Build cursor-based pagination filter for ApiInfo compound key (_id).
     * This is the common pagination logic used by fetchApiRateLimits and fetchAllApiInfoKeys.
     *
     * @param lastApiInfoKey The cursor (last record from previous page), null for first page
     * @return Bson filter for cursor-based pagination
     */
    private static Bson buildApiInfoCursorFilter(ApiInfo.ApiInfoKey lastApiInfoKey) {
        if (lastApiInfoKey == null) {
            return null;  // No cursor filter needed for first page
        }

        // Cursor-based pagination using compound key comparison
        // Returns records where: apiCollectionId > cursor.apiCollectionId
        //                     OR (apiCollectionId = cursor.apiCollectionId AND method > cursor.method)
        //                     OR (apiCollectionId = cursor.apiCollectionId AND method = cursor.method AND url > cursor.url)
        return Filters.or(
            Filters.gt("_id.apiCollectionId", lastApiInfoKey.getApiCollectionId()),
            Filters.and(
                Filters.eq("_id.apiCollectionId", lastApiInfoKey.getApiCollectionId()),
                Filters.gt("_id.method", lastApiInfoKey.getMethod())
            ),
            Filters.and(
                Filters.eq("_id.apiCollectionId", lastApiInfoKey.getApiCollectionId()),
                Filters.eq("_id.method", lastApiInfoKey.getMethod()),
                Filters.gt("_id.url", lastApiInfoKey.getUrl())
            )
        );
    }

    /**
     * Fetch all API IDs (lightweight projection) for Bloom filter initialization.
     * Returns only apiCollectionId, url, and method fields.
     *
     * This is a backward-compatible wrapper that calls the paginated version with null cursor.
     */
    public static List<Map<String, Object>> fetchAllApiInfoKeys() {
        return fetchAllApiInfoKeys(null);
    }

    /**
     * Fetch API info keys with cursor-based pagination (keyset pagination).
     * Uses the same pattern as fetchApiRateLimits for consistency.
     *
     * @param lastApiInfoKey The last API info key from previous page (null for first page)
     * @return List of API info keys (max 1000 records)
     */
    public static List<Map<String, Object>> fetchAllApiInfoKeys(ApiInfo.ApiInfoKey lastApiInfoKey) {
        // Build filter based on cursor position using common helper
        Bson cursorFilter = buildApiInfoCursorFilter(lastApiInfoKey);
        Bson filters = (cursorFilter != null) ? cursorFilter : Filters.exists("_id");

        // Use common sort order
        Bson sort = buildApiInfoSortOrder();

        // Projection: only fetch _id field
        Bson projection = Projections.include("_id");

        // Execute query with common page size
        com.mongodb.client.FindIterable<ApiInfo> cursor = ApiInfoDao.instance
            .getMCollection()
            .find(filters)
            .projection(projection)
            .sort(sort)
            .limit(API_INFO_PAGE_SIZE)
            .batchSize(API_INFO_PAGE_SIZE);

        // Convert to list of maps
        List<Map<String, Object>> apiIdsList = new ArrayList<>();
        for (ApiInfo apiInfo : cursor) {
            if (apiInfo != null && apiInfo.getId() != null) {
                ApiInfo.ApiInfoKey key = apiInfo.getId();
                Map<String, Object> apiId = new HashMap<>();
                apiId.put("apiCollectionId", key.getApiCollectionId());
                apiId.put("url", key.getUrl());
                apiId.put("method", key.getMethod().name());
                apiIdsList.add(apiId);
            }
        }

        return apiIdsList;
    }

    /**
     * Fetch all sample data keys (lightweight projection) for Bloom filter initialization.
     * Returns only _id fields: apiCollectionId, url, method, responseCode, isHeader, ts.
     * Limited to 10,000 records.
     */
    public static List<Map<String, Object>> fetchAllSampleDataKeysAsMaps() {
        com.mongodb.client.FindIterable<SampleData> cursor = SampleDataDao.instance
            .getMCollection()
            .find()
            .projection(Projections.include("_id"))
            .limit(10000)
            .batchSize(10000);

        List<Map<String, Object>> keysList = new ArrayList<>();
        for (SampleData sampleData : cursor) {
            if (sampleData != null && sampleData.getId() != null) {
                com.akto.dto.traffic.Key keyObj = sampleData.getId();
                Map<String, Object> key = new HashMap<>();
                key.put("apiCollectionId", keyObj.getApiCollectionId());
                key.put("url", keyObj.getUrl());
                key.put("method", keyObj.getMethod().name());
                key.put("responseCode", keyObj.getResponseCode());
                key.put("isHeader", keyObj.getBucketStartEpoch());
                key.put("ts", keyObj.getBucketEndEpoch());
                keysList.add(key);
            }
        }
        return keysList;
    }

    public static List<ApiInfo> fetchApiRateLimits(ApiInfo.ApiInfoKey lastApiInfoKey) {

        Bson existsFilter = Filters.empty();
        Bson filters = Filters.empty();
        // if(Context.accountId.get() == 1758179941){
        //     loggerMaker.info("Fetch all api infos for Dil");
        // }else {
        //     existsFilter = Filters.and(
        //         Filters.ne("rateLimits", null),
        //         Filters.ne("rateLimitConfidence", null)
        //     );
        // }
        // Filter for documents that have both rateLimits and rateLimitConfidence fields

        // Add pagination filter using common helper
        Bson paginationFilter = buildApiInfoCursorFilter(lastApiInfoKey);
        if (paginationFilter != null) {
            filters = Filters.and(existsFilter, paginationFilter);
        } else {
            filters = existsFilter;
        }

        Bson projection = Projections.fields(
            Projections.include("_id", "rateLimits", "rateLimitConfidence")
        );

        // Use common sort order
        Bson sort = buildApiInfoSortOrder();

        return ApiInfoDao.instance.findAll(filters, 0, API_INFO_PAGE_SIZE, sort, projection);
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
        System.out.println("bulk write result: del:" + res.getDeletedCount() + " ins:" + res.getInsertedCount() + " match:" + res.getMatchedCount() + " modify:" +res.getModifiedCount());
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

    public static boolean overageApisExists(int apiCollectionId, String urlType, Method method, String url) {
        return UningestedApiOverageDao.instance.exists(apiCollectionId, urlType, method, url);
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
        List<SingleTypeInfo> results = new ArrayList<>();
        ObjectId currentObjectId = objectId;
        Set<Integer> routingCollectionIds = null;
        Set<MergedUrls> mergedUrlsSet = null;

        if (Context.accountId.get() == Constants.ROUTING_SKIP_ACCOUNT_ID) {
            routingCollectionIds = getRoutingCollectionIds();
        }

        if (Context.accountId.get() == Constants.MERGED_URLS_FILTER_ACCOUNT_ID) {
            mergedUrlsSet = getMergedUrlsSet();
        }

        // Loop and discard template URLs from collections belonging to routing tags
        // Keep fetching until we get a non-empty list or exhaust documents
        while (results.isEmpty()) {
            Bson filterForHostHeader = SingleTypeInfoDao.filterForHostHeader(-1, false);
            Bson filterForSkip = currentObjectId == null ? null : Filters.gt("_id", currentObjectId);
            Bson finalFilter = filterForSkip == null ? filterForHostHeader : Filters.and(filterForHostHeader, filterForSkip);

            int limit = 1000;
            List<SingleTypeInfo> batch = SingleTypeInfoDao.instance.findAll(finalFilter, 0, limit, Sorts.ascending("_id"), Projections.exclude(SingleTypeInfo._VALUES));

            if (batch.isEmpty()) {
                break; // Exhausted all documents
            }

            // Always update cursor to last item in batch
            currentObjectId = batch.get(batch.size() - 1).getId();

            // Filter out STRING template URLs from collections that should skip merging
            // Also filter out STIs belonging to merged URLs for specific account
            if (routingCollectionIds != null || mergedUrlsSet != null) {
                for (SingleTypeInfo sti : batch) {
                    boolean shouldKeep = true;

                    // Check routing collection filter
                    if (routingCollectionIds != null) {
                        if (routingCollectionIds.contains(sti.getApiCollectionId())) {
                            // From skip-merging collection
                            if (APICatalog.isTemplateUrl(sti.getUrl()) && APICatalog.isStringTemplateUrl(sti.getUrl())) {
                                // STRING template URL from routing collection - discard
                                shouldKeep = false;
                            }
                        }
                    }

                    // Check merged URLs filter (only if not already filtered)
                    if (shouldKeep && mergedUrlsSet != null && !mergedUrlsSet.isEmpty()) {
                        MergedUrls stiAsUrl = new MergedUrls(sti.getUrl(), sti.getMethod(), sti.getApiCollectionId());
                        if (mergedUrlsSet.contains(stiAsUrl)) {
                            // STI belongs to merged URL - discard
                            shouldKeep = false;
                        }
                    }

                    if (shouldKeep) {
                        results.add(sti);
                    }
                    // From skip-merging collection AND STRING template URL - discard
                }
            } else {
                results.addAll(batch);
            }

            if (batch.size() < limit) {
                break; // Last page reached
            }
        }

        return results;
    }

    private static Set<Integer> getRoutingCollectionIds() {
        long currentTime = System.currentTimeMillis();

        // Check if cache is valid
        if (routingCollectionIdsCache != null &&
            (currentTime - routingCollectionIdsCacheTimestamp) < CACHE_TTL_MS) {
            return routingCollectionIdsCache;
        }

        // Cache miss or expired - rebuild cache
        synchronized (DbLayer.class) {
            // Double-check after acquiring lock
            if (routingCollectionIdsCache != null &&
                (currentTime - routingCollectionIdsCacheTimestamp) < CACHE_TTL_MS) {
                return routingCollectionIdsCache;
            }

            List<Integer> apiCollectionIds = fetchApiCollectionIds();
            Set<Integer> routingCollectionIds = new HashSet<>();

            for (Integer collectionId : apiCollectionIds) {
                ApiCollection collection = ApiCollectionsDao.instance.getMeta(collectionId);
                if (ApiCollectionsDao.shouldSkipMerging(collection)) {
                    routingCollectionIds.add(collectionId);
                }
            }

            // Update cache
            routingCollectionIdsCache = routingCollectionIds;
            routingCollectionIdsCacheTimestamp = currentTime;

            return routingCollectionIds;
        }
    }

    private static Set<MergedUrls> getMergedUrlsSet() {
        long currentTime = System.currentTimeMillis();

        // Check if cache is valid
        if (mergedUrlsCache != null &&
            (currentTime - mergedUrlsCacheTimestamp) < MERGED_URLS_CACHE_TTL_MS) {
            return mergedUrlsCache;
        }

        // Cache miss or expired - rebuild cache
        synchronized (DbLayer.class) {
            // Double-check after acquiring lock
            if (mergedUrlsCache != null &&
                (currentTime - mergedUrlsCacheTimestamp) < MERGED_URLS_CACHE_TTL_MS) {
                return mergedUrlsCache;
            }

            // Fetch merged URLs from database
            Set<MergedUrls> mergedUrls = MergedUrlsDao.instance.getMergedUrls();

            // Update cache
            mergedUrlsCache = mergedUrls;
            mergedUrlsCacheTimestamp = currentTime;

            return mergedUrls;
        }
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
                        Updates.setOnInsert("urls", new HashSet<>())
                ),
                updateOptions
        );
    }

    private static List<CollectionTags> getFilteredTags(ApiCollection apiCollection, List<CollectionTags> tags) {
        if(tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> igNoreList = Arrays.asList("pod-template-hash");
        // Ignore tags from the ignore list
        tags.removeIf(tag -> tag.getKeyName() != null && igNoreList.contains(tag.getKeyName()));

        if (apiCollection == null || apiCollection.getTagsList() == null || apiCollection.getTagsList().isEmpty()) {
            return tags;
        }

        Set<CollectionTags> mergedTags = new HashSet<>(tags);
        mergedTags.addAll(apiCollection.getTagsList());

        tags = new ArrayList<>(mergedTags);

        return tags;
    }

    public static void createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags, String accessType) {
        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        Bson filters = Filters.eq("_id", vxlanId);

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(filters);
        String userEnv = vpcId;
        boolean vpcIdAlreadyExists = false;
        
        if (userEnv != null && apiCollection != null && apiCollection.getUserSetEnvType() != null) {
            userEnv = apiCollection.getUserSetEnvType();
            if (!userEnv.contains(vpcId)) {
                userEnv += ", " + vpcId;
            } else {
                vpcIdAlreadyExists = true;
            }
        }

        boolean accessTypeChanged = false;
        if (apiCollection != null && accessType != null) {
            String existingAccessType = apiCollection.getAccessType();
            if (!accessType.equals(existingAccessType)) {
                accessTypeChanged = true;
            }
        }

        // Skip update for existing apiCollection if vpc and tags are same.
        if ( apiCollection != null && (vpcId == null || vpcIdAlreadyExists) && (tags == null || tags.isEmpty()) && !accessTypeChanged) {
            loggerMaker.info("No new tags or vpcId or accessType, Updates skipped for collectionId: " + vxlanId);
            return;
        }

        Bson update = Updates.combine(
                Updates.set(ApiCollection.VXLAN_ID, vxlanId),
                Updates.setOnInsert("startTs", Context.now()),
                Updates.setOnInsert("urls", new HashSet<>())
        );

        if (userEnv != null && Constants.SHOULD_SAVE_TAGS) {
            update = Updates.combine(update, Updates.set(ApiCollection.USER_ENV_TYPE, userEnv));
        }

        if (tags != null && !tags.isEmpty() && Constants.SHOULD_SAVE_TAGS) {
            // Update the entire tagsList
            update = Updates.combine(update, Updates.set(ApiCollection.TAGS_STRING, getFilteredTags(apiCollection, tags)));
        }

        if(accessType != null && !accessType.isEmpty()) {
            update = Updates.combine(update, Updates.set(ApiCollection.ACCESS_TYPE, accessType));
        }

        ApiCollectionsDao.instance.getMCollection().updateOne(
                filters,
                update,
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

    public static void createCollectionForHostAndVpc(String host, int id, String vpcId, List<CollectionTags> tags, String accessType) {
        createCollectionForHostAndVpc(host, id, vpcId, tags, accessType, null);
    }

    public static void createCollectionForHostAndVpc(String host, int id, String vpcId, List<CollectionTags> tags, String accessType, List<String> skills) {

        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.HOST_NAME, host));
        String userEnv = vpcId;
        boolean vpcIdAlreadyExists = false;
        if (userEnv != null && apiCollection != null && apiCollection.getUserSetEnvType() != null) {
            userEnv = apiCollection.getUserSetEnvType();
            if (!userEnv.contains(vpcId)) {
                userEnv += ", " + vpcId;
            } else {
                vpcIdAlreadyExists = true;
            }
        }

        boolean accessTypeChanged = false;
        if (apiCollection != null && accessType != null) {
            String existingAccessType = apiCollection.getAccessType();
            if (!accessType.equals(existingAccessType)) {
                accessTypeChanged = true;
            }
        }

        if (skills != null) {
            skills.removeIf(s -> s == null || s.trim().isEmpty());
        }
        boolean hasSkills = skills != null && !skills.isEmpty();

        // Skip update for existing apiCollection if vpc, tags, skills, and accessType are same.
        if (apiCollection != null && (vpcId == null || vpcIdAlreadyExists) && (tags == null || tags.isEmpty()) && !accessTypeChanged && !hasSkills) {
            loggerMaker.info("No new tags or vpcId or accessType or skills, Updates skipped for collectionId: " + id);
            return;
        }

        Bson updates = Updates.combine(
            Updates.setOnInsert("_id", id),
            Updates.setOnInsert(ApiCollection.HOST_NAME, host),
            Updates.setOnInsert("startTs", Context.now()),
            Updates.setOnInsert("urls", new HashSet<>())
        );

        if (userEnv != null && Constants.SHOULD_SAVE_TAGS) {
            updates = Updates.combine(updates, Updates.set(ApiCollection.USER_ENV_TYPE, userEnv));
        }

        if(tags != null && !tags.isEmpty() && Constants.SHOULD_SAVE_TAGS) {
            updates = Updates.combine(updates, Updates.set(ApiCollection.TAGS_STRING, getFilteredTags(apiCollection, tags)));
        }

        if(accessType != null && !accessType.isEmpty()) {
            updates = Updates.combine(updates, Updates.set(ApiCollection.ACCESS_TYPE, accessType));
        }

        if (hasSkills) {
            updates = Updates.combine(updates, Updates.addEachToSet(ApiCollection.SKILLS, skills));
        }

        ApiCollectionsDao.instance.getMCollection().findOneAndUpdate(Filters.eq(ApiCollection.HOST_NAME, host), updates, updateOptions);
    }

    // Atomic operation to add hostname to service tag collection
    // Uses $addToSet to prevent duplicates and handle concurrent updates from multiple mini-runtime instances
    public static void addHostNameToServiceTagCollection(int collectionId, String hostName) {
        if (hostName == null || hostName.isEmpty()) {
            return;
        }
        ApiCollectionsDao.instance.updateOne(
            Filters.eq(Constants.ID, collectionId),
            Updates.addToSet(ApiCollection.HOST_NAMES, hostName)
        );
    }

    // Atomic operation to update tags for service tag collection
    // Uses $set to replace the entire tagsList
    public static void updateServiceTagCollectionTags(int collectionId, List<CollectionTags> tagsList) {
        if (tagsList == null || tagsList.isEmpty() || !Constants.SHOULD_SAVE_TAGS) {
            return;
        }
        ApiCollectionsDao.instance.updateOne(
            Filters.eq(Constants.ID, collectionId),
            Updates.set(ApiCollection.TAGS_STRING, tagsList)
        );
    }

    // Similar to createCollectionForHostAndVpc but for service-tag based collections
    public static void createCollectionForServiceTag(int id, String serviceTagValue, List<String> hostNames, List<CollectionTags> tags, String hostName, String accessType) {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);

        Bson updates = Updates.combine(
            Updates.setOnInsert(Constants.ID, id),
            Updates.setOnInsert(ApiCollection.NAME, serviceTagValue),
            Updates.setOnInsert(ApiCollection.START_TS, Context.now()),
            Updates.setOnInsert(ApiCollection.URLS_STRING, new HashSet<>()),
            Updates.setOnInsert(ApiCollection.SERVICE_TAG, serviceTagValue),
            Updates.setOnInsert(ApiCollection.HOST_NAMES, hostNames),
            Updates.setOnInsert(ApiCollection.REDACT, false),
            Updates.setOnInsert(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, true),
            Updates.setOnInsert(ApiCollection.HOST_NAME, hostName)
        );

        if(tags != null && !tags.isEmpty() && Constants.SHOULD_SAVE_TAGS) {
            updates = Updates.combine(updates, Updates.set(ApiCollection.TAGS_STRING, tags));
        }

        if(accessType != null && !accessType.isEmpty() && Constants.SHOULD_SAVE_TAGS) {
            updates = Updates.combine(updates, Updates.set(ApiCollection.ACCESS_TYPE, accessType));
        }

        ApiCollectionsDao.instance.getMCollection().findOneAndUpdate(
            Filters.eq(Constants.ID, id),
            updates,
            updateOptions
        );
    }

    public static void insertRuntimeLog(Log log) {
        RuntimeLogsDao runtimeLogsDao = RuntimeLogsDao.instance;
        cleanupCollectionIfNeeded(runtimeLogsDao.getCollName(), runtimeLogsDao);
        runtimeLogsDao.insertOne(log);
    }

    private static void cleanupCollectionIfNeeded(String collectionName, AccountsContextDao<?> dao) {
        try {
            int accountId = Context.accountId.get();
            String cacheKey = accountId + "_" + collectionName;

            int currentTime = Context.now();
            Integer nextCleanupTime = collectionCleanupCache.get(cacheKey);

            if (nextCleanupTime != null && currentTime < nextCleanupTime) {
                return;
            }

            long estimatedCount = dao.getMCollection().estimatedDocumentCount();

            if (estimatedCount >= COLLECTION_SIZE_THRESHOLD) {
                List<String> slackMessages = new ArrayList<>();

                String beforeDropMsg = String.format(
                    "Dropping collection - collection=%s, account=%d, estimatedCount=%d, threshold=%d, timestamp=%d",
                    collectionName, accountId, estimatedCount, COLLECTION_SIZE_THRESHOLD, Context.now()
                );
                loggerMaker.infoAndAddToDb(beforeDropMsg);
                slackMessages.add(beforeDropMsg);

                long startDropTime = System.currentTimeMillis();
                dao.getMCollection().drop();
                long endDropTime = System.currentTimeMillis();

                String afterDropMsg = String.format(
                    "Successfully dropped collection=%s, account=%d, timestamp=%d, timeTakenMilliseconds=%d",
                    collectionName, accountId, Context.now(), endDropTime - startDropTime
                );
                loggerMaker.infoAndAddToDb(afterDropMsg);
                slackMessages.add(afterDropMsg);

                // Send combined message to Slack
                String combinedMessage = String.join("\n", slackMessages);
                loggerMaker.sendCyborgSlackAsync(combinedMessage);
            }

            // Calculate next cleanup time with jitter (0 to 3 minutes)
            int jitter = (int) (Math.random() * CLEANUP_JITTER_SECONDS);
            int nextScheduledCleanup = currentTime + CLEANUP_INTERVAL_SECONDS + jitter;
            collectionCleanupCache.put(cacheKey, nextScheduledCleanup);

        } catch (Exception e) {
            String errorMessage = String.format(
                "Error during cleanup of collection=%s, accountId=%d, errorMessage=%s",
                collectionName, Context.accountId.get(), e.getMessage());
            loggerMaker.sendCyborgSlackAsync(errorMessage);
            loggerMaker.errorAndAddToDb(e, errorMessage);
        }
    }

    public static void insertPersistentRuntimeLog(Log log) {
        RuntimePersistentLogsDao.instance.insertOne(log);
    }

    public static void insertAnalyserLog(Log log) {
        AnalyserLogsDao.instance.insertOne(log);
    }

    public static void insertTestingLog(Log log) {
        LogsDao.instance.insertOne(log);
    }

    public static void insertAgenticTestingLog(Log log) {
        AgenticTestingLogsDao.instance.insertOne(log);
    }

    public static void insertProtectionLog(Log log) {
        ProtectionLogsDao.instance.insertOne(log);
    }

    public static void insertCyborgLog(Log log) {
        CyborgLogsDao.instance.insertOne(log);
    }

    public static void insertAwsApiGatewayLog(Log log) {
        AwsApiGatewayLogsDao.instance.insertOne(log);
    }

    public static void insertGuardrailsServiceLog(Log log) {
        GuardrailsServiceLogsDao.instance.insertOne(log);
    }

    public static void insertEndpointShieldLog(LogsEndpointShield log) {
        // todo: temp disable endpoint shield logs to avoid collection size issues, will re-enable after fixing the issue
        // LogsEndpointShieldDao.instance.insertOne(log);
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
            Projections.exclude("urls", "conditions", "envType"));
    }

    public static List<ApiCollection> fetchApiCollectionsByIds(List<Integer> apiCollectionIds) {
        return ApiCollectionsDao.instance.findAll(Filters.in("_id", apiCollectionIds),
            Projections.exclude("urls", "conditions", "envType"));
    }

    public static Organization fetchOrganization(int accountId) {
        return OrganizationsDao.instance.findOne(Filters.eq(Organization.ACCOUNTS, accountId));
    }

    // testing queries

    public static TestingRunResultSummary createTRRSummaryIfAbsent(String testingRunHexId, int start) {
        ObjectId testingRunId = new ObjectId(testingRunHexId);

        // since the extra field is not used in mini-testing explicitly, we can just update the summary here
        // it is only used in dashboard for querying data from new collection

        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.and(
                        Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, testingRunId),
                        Filters.eq(TestingRunResultSummary.STATE,TestingRun.State.SCHEDULED)
                ),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.STATE, TestingRun.State.RUNNING),
                        Updates.setOnInsert(TestingRunResultSummary.START_TIMESTAMP, start),
                        Updates.set(TestingRunResultSummary.IS_NEW_TESTING_RUN_RESULT_SUMMARY, true)
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
    }

    private static String validateAndGetMiniTestingService(String serviceName, String miniTestingName) {
        if (StringUtils.isEmpty(serviceName)) {
            return miniTestingName;
        }

        if (!serviceName.startsWith(DEFAULT_MINI_TESTING_NAME)) {
            return serviceName.equals(miniTestingName) ? miniTestingName : null;
        }

        boolean isCurrentMiniTestingAlive = ModuleInfoDao.instance.checkIsModuleActiveUsingName(
                ModuleInfo.ModuleType.MINI_TESTING, serviceName);

        if (!isCurrentMiniTestingAlive) {
            loggerMaker.infoAndAddToDb("Current mini-testing service is not alive: " + serviceName);
            return miniTestingName;
        }

        return serviceName.equals(miniTestingName) ? miniTestingName : null;
    }

    public static TestingRun findPendingTestingRunBase(Bson baseFilter, int delta, String miniTestingName) {
        try {
            Bson filter = Filters.or(
                Filters.and(
                    Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                    Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, Context.now())
                ),
                Filters.and(
                    Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
                    Filters.lte(TestingRun.PICKED_UP_TIMESTAMP, delta)
                )
            );

            if(baseFilter != null) {
                filter = Filters.and(filter, baseFilter);
            }

            TestingRun testingRun = TestingRunDao.instance.findOne(
                filter,
                Projections.include(TestingRun.MINI_TESTING_SERVICE_NAME, TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, ID)
            );

            if (testingRun == null) return null;

            // Handle legacy case
            if (StringUtils.isEmpty(miniTestingName)) {
                List<String> allowedList = testingRun.getAllowedMiniTestingServiceNames();
                if (StringUtils.isEmpty(testingRun.getMiniTestingServiceName()) && (allowedList == null || allowedList.isEmpty())) {
                    Bson update = Updates.combine(
                            Updates.set(TestingRun.PICKED_UP_TIMESTAMP, Context.now()),
                            Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
                    );
                    return TestingRunDao.instance.getMCollection().findOneAndUpdate(
                            Filters.eq(ID, testingRun.getId()),
                            update
                    );
                } else {
                    return null;
                }
            }

            // Priority 1: Find testing run with matching miniTestingName (new list field or legacy single string)
            Bson matchingFilter = Filters.and(
                filter,
                Filters.or(
                    Filters.in(TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, miniTestingName),
                    Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, miniTestingName)
                )
            );

            testingRun = TestingRunDao.instance.findOne(
                matchingFilter,
                Projections.include(TestingRun.MINI_TESTING_SERVICE_NAME, TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, ID)
            );

            // Priority 2: Find testing run without any module assignment (both old and new fields unset)
            if (testingRun == null) {
                Bson unassignedFilter = Filters.and(
                    filter,
                    Filters.or(
                        Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, null),
                        Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, "")
                    ),
                    Filters.or(
                        Filters.eq(TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, null),
                        Filters.size(TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, 0)
                    )
                );
                testingRun = TestingRunDao.instance.findOne(
                    unassignedFilter,
                    Projections.include(TestingRun.MINI_TESTING_SERVICE_NAME, TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, ID)
                );
            }

            // Priority 3: Backward compat — legacy runs (no allowedMiniTestingServiceNames) where assigned module is dead
            if (testingRun == null) {
                TestingRun anyRun = TestingRunDao.instance.findOne(
                    filter,
                    Projections.include(TestingRun.MINI_TESTING_SERVICE_NAME, TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, ID)
                );
                if (anyRun != null) {
                    List<String> anyRunAllowed = anyRun.getAllowedMiniTestingServiceNames();
                    // Only handle legacy runs with no allowedMiniTestingServiceNames
                    if (anyRunAllowed == null || anyRunAllowed.isEmpty()) {
                        String validated = validateAndGetMiniTestingService(
                            anyRun.getMiniTestingServiceName(), miniTestingName);
                        if (validated != null) {
                            testingRun = anyRun;
                        }
                    }
                }
            }

            if (testingRun == null) return null;

            Bson update = Updates.combine(
                Updates.set(TestingRun.PICKED_UP_TIMESTAMP, Context.now()),
                Updates.set(TestingRun.STATE, TestingRun.State.RUNNING),
                Updates.set(TestingRun.MINI_TESTING_SERVICE_NAME, miniTestingName)
            );

            return TestingRunDao.instance.getMCollection().findOneAndUpdate(
                Filters.and(Filters.eq(ID, testingRun.getId()), filter),
                update
            );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in findPendingTestingRun: " + e.getMessage());
            return null;
        }
    }
    
    public static TestingRun findPendingTestingRun(int delta, String miniTestingName) {
        return findPendingTestingRunBase(null, delta, miniTestingName);
    }

    public static TestingRun findPendingAgenticTestingRun(int delta, String miniTestingName) {
        try {
            List<Integer> apiCollectionIds = ApiCollectionsDao.instance.findAll(
                    Filters.elemMatch(ApiCollection.TAGS_STRING,
                            Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GEN_AI_TAG)),
                    Projections.include(ApiCollection.ID)).stream().map(ApiCollection::getId)
                    .collect(Collectors.toList());
            Bson filter = Filters.or(
                    Filters.in(TestingRun._API_COLLECTION_ID, apiCollectionIds),
                    Filters.in(TestingRun._API_COLLECTION_ID_IN_LIST, apiCollectionIds));
            return findPendingTestingRunBase(filter, delta, miniTestingName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in findPendingAgenticTestingRun: " + e.getMessage());
            return null;
        }
    }

    private static TestingRunResultSummary findPendingTestingRunResultSummaryBasedOnPriority(Bson filter, String miniTestingName) {

        Bson testingRunsProjections = Projections.include(TestingRun.MINI_TESTING_SERVICE_NAME, TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, ID);
        Bson trrsProjections = Projections.include(TestingRunResultSummary.TESTING_RUN_ID, ID, TestingRunResultSummary.ORIGINAL_TESTING_RUN_SUMMARY_ID);
        List<TestingRun> testingRuns = TestingRunDao.instance.findAll(
                Filters.or(
                    Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, miniTestingName),
                    Filters.in(TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, miniTestingName)
                ), testingRunsProjections);

        TestingRunResultSummary trrs = TestingRunResultSummariesDao.instance.findOne(
                Filters.and(filter,
                        Filters.in(TestingRunResultSummary.TESTING_RUN_ID,
                                testingRuns.stream().map(TestingRun::getId).collect(Collectors.toList()))),
                trrsProjections);
        if (trrs != null)
            return trrs;

        testingRuns = TestingRunDao.instance.findAll(
                Filters.and(
                    Filters.or(
                        Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, null),
                        Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, "")
                    ),
                    Filters.or(
                        Filters.eq(TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, null),
                        Filters.size(TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES, 0)
                    )
                ),
                testingRunsProjections);

        trrs = TestingRunResultSummariesDao.instance.findOne(
                Filters.and(filter,
                        Filters.in(TestingRunResultSummary.TESTING_RUN_ID,
                                testingRuns.stream().map(TestingRun::getId).collect(Collectors.toList()))),
                trrsProjections);
        if (trrs != null)
            return trrs;

        trrs = TestingRunResultSummariesDao.instance.findOne(
                filter,
                trrsProjections);

        return trrs;
    }

    public static TestingRunResultSummary findPendingTestingRunResultSummary(int now, int delta, String miniTestingName) {
        try {

            Bson filter = Filters.or(
                    Filters.and(
                            Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                            Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, Context.now())));

            TestingRun testingRun = TestingRunDao.instance.findOne(
                    filter,
                    Projections.include(TestingRun.MINI_TESTING_SERVICE_NAME, ID));

            // prioritize SCHEDULED testing runs
            if (testingRun != null) {
                return null;
            }

            // Combine filters for better query efficiency
            Bson filterScheduled = Filters.and(
                    Filters.eq(TestingRun.STATE, TestingRun.State.SCHEDULED),
                    Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now)
            );

            TestingRunResultSummary trrs = findPendingTestingRunResultSummaryBasedOnPriority(filterScheduled, miniTestingName);

            if (trrs == null) {
                Bson filterRunning = Filters.and(
                        Filters.eq(TestingRun.STATE, TestingRun.State.RUNNING),
                        Filters.lte(TestingRunResultSummary.START_TIMESTAMP, now - 5 * 60),
                        Filters.gt(TestingRunResultSummary.START_TIMESTAMP, delta)
                );

                trrs = findPendingTestingRunResultSummaryBasedOnPriority(filterRunning, miniTestingName);
            }

            if (trrs == null) return null;

            testingRun = TestingRunDao.instance.findOne(
                Filters.eq(ID, trrs.getTestingRunId()),
                Projections.include(ID, TestingRun.MINI_TESTING_SERVICE_NAME, TestingRun.ALLOWED_MINI_TESTING_SERVICE_NAMES)
            );

            if (testingRun == null) return null;

            // Handle legacy case
            if (StringUtils.isEmpty(miniTestingName)) {
                List<String> allowedListLegacy = testingRun.getAllowedMiniTestingServiceNames();
                if (StringUtils.isEmpty(testingRun.getMiniTestingServiceName()) && (allowedListLegacy == null || allowedListLegacy.isEmpty())) {
                    return TestingRunResultSummariesDao.instance.getMCollection()
                            .findOneAndUpdate(
                                    Filters.eq(ID, trrs.getId()),
                                    Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
                            );
                } else {
                    return null;
                }
            }

            // For runs assigned to specific modules via the new list field, verify eligibility
            List<String> allowedRunModulesTrrs = testingRun.getAllowedMiniTestingServiceNames();
            if (allowedRunModulesTrrs != null && !allowedRunModulesTrrs.isEmpty()) {
                boolean eligible = allowedRunModulesTrrs.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(name -> name.equals(miniTestingName));
                if (!eligible) return null;
                // Eligible via new list field — update miniTestingServiceName to this module and claim TRRS
                TestingRunDao.instance.getMCollection().findOneAndUpdate(
                    Filters.eq(ID, trrs.getTestingRunId()),
                    Updates.set(TestingRun.MINI_TESTING_SERVICE_NAME, miniTestingName)
                );
                return TestingRunResultSummariesDao.instance.getMCollection()
                    .findOneAndUpdate(
                        Filters.eq(ID, trrs.getId()),
                        Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
                    );
            }

            // Backward compat: old runs with only single miniTestingServiceName field
            String validatedMiniTestingName = validateAndGetMiniTestingService(
                testingRun.getMiniTestingServiceName(),
                miniTestingName
            );

            if (validatedMiniTestingName == null) return null;

            // Update service name if needed
            if (!validatedMiniTestingName.equals(testingRun.getMiniTestingServiceName())) {
                FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions();
                findOneAndUpdateOptions.returnDocument(ReturnDocument.AFTER);
                findOneAndUpdateOptions.upsert(false);
                TestingRun testingRun1 = TestingRunDao.instance.getMCollection().findOneAndUpdate(
                    Filters.and(
                        Filters.eq(ID, trrs.getTestingRunId()),
                        Filters.eq(TestingRun.MINI_TESTING_SERVICE_NAME, testingRun.getMiniTestingServiceName())
                    ),
                    Updates.set(TestingRun.MINI_TESTING_SERVICE_NAME, validatedMiniTestingName),
                        findOneAndUpdateOptions
                );
                if (testingRun1 == null || !validatedMiniTestingName.equals(testingRun1.getMiniTestingServiceName())) {
                    return null;
                }
            }

            return TestingRunResultSummariesDao.instance.getMCollection()
                .findOneAndUpdate(
                    Filters.eq(ID, trrs.getId()),
                    Updates.set(TestingRun.STATE, TestingRun.State.RUNNING)
                );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in findPendingTestingRunResultSummary: " + e.getMessage());
            return null;
        }
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
            VulnerableTestingRunResultDao.instance.deleteAll(filters);
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

        int nextScheduleTimestamp = 0;
        TestingRun testingRun = TestingRunDao.instance.findOne("_id", testingRunId);
        // If it's a one-time test (periodInSeconds = 0), don't reschedule

        if(testingRun != null) {
            if (testingRun.getPeriodInSeconds() > 0) {

                // Calculate how many periods have passed since original schedule
                int timeSinceOriginal = Context.now() - testingRun.getScheduleTimestamp();
                int periodsPassed = (timeSinceOriginal / testingRun.getPeriodInSeconds()) + 1;

                // Next schedule = original + (periods * interval)
                nextScheduleTimestamp = testingRun.getScheduleTimestamp() + (periodsPassed * testingRun.getPeriodInSeconds());
            }

            ObjectId testingRunObjId = new ObjectId(testingRunId);
            Bson filter = Filters.and(Filters.eq(Constants.ID, testingRunObjId)
            );
            Bson update;
            if (nextScheduleTimestamp > 0) {

                // Update both state and schedule timestamp
                update = Updates.combine(
                        Updates.set(TestingRun.STATE, State.SCHEDULED),
                        Updates.set(TestingRun.SCHEDULE_TIMESTAMP, nextScheduleTimestamp)
                );

            } else {
                // Update only state
                update = Updates.set(TestingRun.STATE, State.FAILED);
            }
            TestingRunDao.instance.updateOneNoUpsert(filter, update);
        }else {
            loggerMaker.infoAndAddToDb("Invalid testingRunId " + testingRunId , LogDb.CYBORG);
        }
    }

    public static Map<ObjectId, TestingRunResultSummary> fetchTestingRunResultSummaryMap(String testingRunId) {
        ObjectId testingRunObjId = new ObjectId(testingRunId);
        return TestingRunResultSummariesDao.instance.fetchLatestTestingRunResultSummaries(Collections.singletonList(testingRunObjId));
    }

    private static List<TestingRunResult> fetchLatestTestingRunResultFromComparison(Bson filter){
        List<TestingRunResult> resultsFromNonVulCollection = TestingRunResultDao.instance.fetchLatestTestingRunResult(filter, 1);
        List<TestingRunResult> resultsFromVulCollection = VulnerableTestingRunResultDao.instance.fetchLatestTestingRunResult(filter, 1);

        if(resultsFromVulCollection != null && !resultsFromVulCollection.isEmpty()){
            if(resultsFromNonVulCollection != null && !resultsFromNonVulCollection.isEmpty()){
                TestingRunResult tr1 = resultsFromVulCollection.get(0);
                TestingRunResult tr2 = resultsFromNonVulCollection.get(0);
                if(tr1.getEndTimestamp() >= tr2.getEndTimestamp()){
                    return resultsFromVulCollection;
                }else{
                    return resultsFromVulCollection;
                }
            }else{
                return resultsFromVulCollection;
            }
        }

        return resultsFromNonVulCollection;
    }

    public static List<TestingRunResult> fetchLatestTestingRunResult(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        return fetchLatestTestingRunResultFromComparison(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId));
    }

    /**
     * True if any {@link TestingRunResult} exists for this summary (standard or vulnerable collection).
     * Uses {@code findOne} + {@code _id} projection only.
     */
    public static boolean hasAnyTestingRunResultForSummary(String testingRunResultSummaryId) {
        ObjectId summaryObjectId = new ObjectId(testingRunResultSummaryId);
        Bson filter = Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId);
        Bson projection = Projections.include(Constants.ID);
        if (TestingRunResultDao.instance.findOne(filter, projection) != null) {
            return true;
        }
        return VulnerableTestingRunResultDao.instance.findOne(filter, projection) != null;
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
        trrs.setNewTestingSummary(true);
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

    public static List<ApiCollection> fetchAllApiCollectionsMeta(boolean includeTagsList) {
        List<String> excludeFields = new ArrayList<>(Arrays.asList("urls", "conditions", "envType"));
        if (!includeTagsList) {
            excludeFields.add("tagsList");
        }
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(ApiCollectionsDao.instance.nonApiGroupFilter(), Projections.exclude(excludeFields));
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
                ),
                filter
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
        // from now store vulnerable results in separate collection also
        if(testingRunResult.isVulnerable()){
            VulnerableTestingRunResultDao.instance.insertOne(testingRunResult);
        }
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
            Bson finalUpdate =  Updates.combine(Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
                                        Updates.set(TestingRunResultSummary.STATE, State.COMPLETED),
                                    Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues)
            );
            Bson updateIncrement = Updates.combine(
                Updates.inc("countIssues.CRITICAL", totalCountIssues.getOrDefault("CRITICAL", 0)),
                Updates.inc("countIssues.HIGH", totalCountIssues.getOrDefault("HIGH", 0)),
                Updates.inc("countIssues.MEDIUM", totalCountIssues.getOrDefault("MEDIUM", 0)),
                Updates.inc("countIssues.LOW", totalCountIssues.getOrDefault("LOW", 0))
            );
            if(!((operator == null || operator.isEmpty()))){
                finalUpdate = updateIncrement;
            }
            return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                    Filters.eq(Constants.ID, summaryObjectId),
                    finalUpdate, options
                );
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
        Bson update = Updates.combine(
            Updates.set(TestingRunResultSummary.END_TIMESTAMP, Context.now()),
            Updates.set(TestingRunResultSummary.STATE, state)
        );
        if(totalCountIssues != null){
            boolean hasAnyIssue = false;
            for(Map.Entry<String, Integer> entry : totalCountIssues.entrySet()){
                if(entry.getValue() > 0){
                    hasAnyIssue = true;
                    break;
                }
            }
            if(hasAnyIssue){
                update = Updates.combine(update, Updates.set(TestingRunResultSummary.COUNT_ISSUES, totalCountIssues));
            }
        }
        return TestingRunResultSummariesDao.instance.getMCollection().findOneAndUpdate(
                Filters.eq(Constants.ID, summaryObjectId),
                update
            ,options);
    }

    public static TestingRunResultSummary updateMetadataInSummary(String summaryId, Map<String, String> metadata) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        return TestingRunResultSummariesDao.instance.updateOneNoUpsert(
                Filters.eq(Constants.ID, summaryObjectId),
                Updates.combine(
                        Updates.set(TestingRunResultSummary.METADATA_STRING, metadata)
                )
        );
    }

    public static List<Integer> fetchDeactivatedCollections() {
        return new ArrayList<>(UsageMetricCalculator.getDeactivated());
    }

    public static void updateUsage(MetricTypes metricType, int deltaUsage){
        int accountId = Context.accountId.get();
        UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(metricType, accountId, deltaUsage);
        return;
    }

    public static List<TestingRunResult> fetchLatestTestingRunResultBySummaryId(String summaryId, int limit, int skip) {
        ObjectId summaryObjectId = new ObjectId(summaryId);
        if(VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(summaryObjectId)){
            return VulnerableTestingRunResultDao.instance
                    .fetchLatestTestingRunResult(
                        Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                        limit,
                        skip,
                        Projections.exclude("testResults.originalMessage", "testResults.nodeResultMap")
                    );
        }else{
            return TestingRunResultDao.instance
                    .fetchLatestTestingRunResult(
                            Filters.and(
                                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryObjectId),
                                    Filters.eq(TestingRunResult.VULNERABLE, true)),
                            limit,
                            skip,
                            Projections.exclude("testResults.originalMessage", "testResults.nodeResultMap"));
        }

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

    public static void updateCopilotRefreshToken(String roleId, String newRefreshToken) {
        TestRoles role = TestRolesDao.instance.findOne(Filters.eq("_id", new ObjectId(roleId)));
        if (role == null || role.getAuthWithCondList() == null) return;
        for (AuthWithCond authWithCond : role.getAuthWithCondList()) {
            if (authWithCond.getAuthMechanism() == null) continue;
            List<AuthParam> params = authWithCond.getAuthMechanism().getAuthParams();
            if (params == null) continue;
            for (AuthParam param : params) {
                if (param instanceof CopilotOAuthAuthParam) {
                    ((CopilotOAuthAuthParam) param).setRefreshToken(newRefreshToken);
                }
            }
        }
        TestRolesDao.instance.updateOneNoUpsert(
            Filters.eq("_id", new ObjectId(roleId)),
            Updates.set(TestRoles.AUTH_WITH_COND_LIST, role.getAuthWithCondList())
        );
    }

    public static Tokens fetchToken(String organizationId, int accountId) {
        Bson filters = Filters.and(
                Filters.eq(Tokens.ORG_ID, organizationId),
                Filters.eq(Tokens.ACCOUNT_ID, accountId)
        );
        Tokens tokens = TokensDao.instance.findOne(filters);
        if (tokens == null || tokens.isOldToken()) {
            Bson updates;
            if (tokens == null) {
                updates = Updates.combine(
                        Updates.set(Tokens.UPDATED_AT, Context.now()),
                        Updates.setOnInsert(Tokens.CREATED_AT, Context.now()),
                        Updates.setOnInsert(Tokens.ORG_ID, organizationId),
                        Updates.setOnInsert(Tokens.ACCOUNT_ID, accountId)
                );
            } else {
                updates = Updates.set(Tokens.UPDATED_AT, Context.now());
            }
            String newToken = organizationId + "_" + accountId + "_" + UUID.randomUUID().toString().replace("-", "");
            UsageUtils.saveToken(organizationId, accountId, updates, filters, newToken);
            tokens = TokensDao.instance.findOne(filters);
        }
        return tokens;
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
            Filters.eq("method", method.name())
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
            Filters.eq("_id.method", method.name()),
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

    public static ApiInfo fetchLatestAuthenticatedByApiCollectionId(int apiCollectionId) {
        // Query: apiCollectionId matches, allAuthTypesFound does NOT contain only UNAUTHENTICATED
        BasicDBObject query = new BasicDBObject("_id.apiCollectionId", apiCollectionId)
                .append("allAuthTypesFound", new BasicDBObject("$not", new BasicDBObject("$size", 1)))
                .append("allAuthTypesFound", new BasicDBObject("$ne", Collections.singleton(Collections.singleton(ApiInfo.AuthType.UNAUTHENTICATED))));
        BasicDBObject sort = new BasicDBObject("lastSeen", -1); // descending

        List<ApiInfo> results = ApiInfoDao.instance.find(query, sort, 0, 1);
        if (results != null && !results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }


    public static void ingestMetricsData(List<MetricData> metricData) {
        // First check if cleanup should be performed
        if (MetricDataDao.instance.shouldPerformCleanup()) {
            long deletedCount = MetricDataDao.instance.deleteOldMetrics();
            loggerMaker.infoAndAddToDb("Deleted " + deletedCount + " old metrics records", LogDb.DASHBOARD);
        }
        MetricDataDao.instance.insertMany(metricData);

        int accountId = Context.accountId.get();
        boolean forwardToNewRelic = NewRelicIntegrationDao.instance.findOne(new BasicDBObject()) != null;
        boolean forwardToOpenTelemetry = OpenTelemetryIntegrationDao.instance.findOne(new BasicDBObject()) != null;

        if (forwardToNewRelic) {
            loggerMaker.infoAndAddToDb(String.format("Forwarding %d metrics to New Relic for account %d", metricData.size(), accountId), LogDb.DB_ABS);
            try {
                telemetryForwardingExecutorService.submit(() -> {
                    Context.accountId.set(accountId);
                    NewRelicUtils.forwardMetrics(metricData);
                });
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error submitting NR metrics task: " + e.getMessage(), LogDb.DB_ABS);
            }
        }

        if (forwardToOpenTelemetry) {
            loggerMaker.infoAndAddToDb(String.format("Forwarding %d metrics to OpenTelemetry for account %d", metricData.size(), accountId), LogDb.DB_ABS);
            try {
                telemetryForwardingExecutorService.submit(() -> {
                    Context.accountId.set(accountId);
                    OpenTelemetryUtils.forwardMetrics(metricData);
                });
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error submitting OT metrics task: " + e.getMessage(), LogDb.DB_ABS);
            }
        }

        // Forward to Akto's own collector (independent of the customer
        // OpenTelemetryIntegration). Gated for gradual rollout by the overall
        // flag + account allowlist (see OpenTelemetryUtils.shouldForwardToAktoInfra).
        if (OpenTelemetryUtils.shouldForwardToAktoInfra(accountId)) {
            try {
                telemetryForwardingExecutorService.submit(() -> {
                    Context.accountId.set(accountId);
                    OpenTelemetryUtils.forwardMetricsToAktoInfra(metricData);
                });
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error submitting Akto infra metrics task: " + e.getMessage(), LogDb.DB_ABS);
            }
        }
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

    public static void insertRuntimeMetricsData(BasicDBList metricsData) {

        ArrayList<WriteModel<RuntimeMetrics>> bulkUpdates = new ArrayList<>();
        RuntimeMetrics runtimeMetrics;
        for (Object metrics: metricsData) {
            try {
                Map<String, Object> obj = (Map) metrics;
                String name = (String) obj.get("metric_id");
                String instanceId = (String) obj.get("instance_id");
                Object tsObj = obj.get("timestamp");
                if (!(tsObj instanceof Number)) {
                    continue;
                }
                int ts = ((Number) tsObj).intValue();
                Object valObj = obj.get("val");
                if (!(valObj instanceof Number)) {
                    continue;
                }
                double val = ((Number) valObj).doubleValue();
                if (name == null || name.length() == 0) {
                    continue;
                }
                runtimeMetrics = new RuntimeMetrics(name, ts, instanceId, val);
                bulkUpdates.add(new InsertOneModel<>(runtimeMetrics));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "error writing bulk update " + e.getMessage());
            }
        }

        if (bulkUpdates.size() > 0) {
            loggerMaker.infoAndAddToDb("insertRuntimeMetricsData bulk write size " + metricsData.size());
            RuntimeMetricsDao.bulkInsertMetrics(bulkUpdates);
        }
    }

    public static void bulkWriteSuspectSampleData(List<WriteModel<SuspectSampleData>> writesForSingleTypeInfo) {
        SuspectSampleDataDao.instance.getMCollection().bulkWrite(writesForSingleTypeInfo);
    }

    public static List<YamlTemplate> fetchFilterYamlTemplates() {
        return FilterYamlTemplateDao.instance.findAll(Filters.empty());
    }

    public static List<YamlTemplate> fetchActiveFilterTemplates(){
        return AdvancedTrafficFiltersDao.instance.findAll(
            Filters.ne(YamlTemplate.INACTIVE, false)
        );
    }

    public static Set<MergedUrls> fetchMergedUrls() {
        return MergedUrlsDao.instance.getMergedUrls();
    }

    public static List<TestingRunResultSummary> fetchStatusOfTests() {
        int timeFilter = Context.now() - 7 * 60 * 60;
        List<TestingRunResultSummary> currentRunningTests = TestingRunResultSummariesDao.instance.findAll(
            Filters.gte(TestingRunResultSummary.START_TIMESTAMP, timeFilter),
            Projections.include("_id", TestingRunResultSummary.STATE, TestingRunResultSummary.TESTING_RUN_ID) 
        );
        for (TestingRunResultSummary summary: currentRunningTests) {
            summary.setTestingRunHexId(summary.getTestingRunId().toHexString());
        }
        return currentRunningTests;
    }

    private static final int ENDPOINT_LIMIT = 50;

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

    public static void persistRecordedLoginFlowScreenshots(String roleName, int userId, List<String> screenshotsBase64) {
        Bson filter = Filters.and(Filters.eq("roleName", roleName), Filters.eq("userId", userId));
        Bson update = Updates.combine(
                Updates.setOnInsert("roleName", roleName),
                Updates.setOnInsert("userId", userId),
                Updates.set("screenshotsBase64", screenshotsBase64 != null ? screenshotsBase64 : Collections.emptyList()),
                Updates.set("updatedAt", Context.now())
        );
        RecordedLoginScreenshotDao.instance.updateOne(filter, update);
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

    final static int NODE_LIMIT = 100;

    public static List<Node> fetchNodesForCollectionIds(List<Integer> apiCollectionsIds, boolean removeZeroLevel, int skip) {
        return DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionsIds, removeZeroLevel, skip,
                NODE_LIMIT);
    }

    public static long countTestingRunResultSummaries(Bson filter){
        return TestingRunResultSummariesDao.instance.count(filter);
    }

    public static TestScript fetchTestScript(TestScript.Type type){
        return TestScriptsDao.instance.fetchTestScript(type);
    }

    public static List<DependencyNode> findDependencyNodes(int apiCollectionId, String url, String method, String reqMethod) {
        Bson filterQ = DependencyNodeDao.generateChildrenFilter(apiCollectionId, url, Method.valueOf(method));
        // TODO: Handle cases where the delete API does not have the delete method
        Bson delFilterQ = Filters.and(filterQ, Filters.eq(DependencyNode.METHOD_REQ, reqMethod));
        return DependencyNodeDao.instance.findAll(delFilterQ);
    }

    public static TestingRunResultSummary findLatestTestingRunResultSummary(String testingRunId){
        if (testingRunId == null || testingRunId.isEmpty()) return null;
        Bson filter = Filters.eq(TestingRunResultSummary.TESTING_RUN_ID, new ObjectId(testingRunId));
        return TestingRunResultSummariesDao.instance.findLatestOne(filter);
    }

    public static List<SvcToSvcGraphEdge> findSvcToSvcGraphEdges(int startTs, int endTs, int skip, int limit) {
        return SvcToSvcGraphEdgesDao.instance.findAll(Filters.and(
                Filters.gte(SvcToSvcGraphEdge.CREATTION_EPOCH, startTs),
                Filters.lte(SvcToSvcGraphEdge.CREATTION_EPOCH, endTs)
        ), skip, limit, Sorts.ascending(SvcToSvcGraphEdge.CREATTION_EPOCH));
    }

    public static List<SvcToSvcGraphNode> findSvcToSvcGraphNodes(int startTs, int endTs, int skip, int limit) {
        return SvcToSvcGraphNodesDao.instance.findAll(Filters.and(
                Filters.gte(SvcToSvcGraphEdge.CREATTION_EPOCH, startTs),
                Filters.lte(SvcToSvcGraphEdge.CREATTION_EPOCH, endTs)
        ), skip, limit, Sorts.ascending(SvcToSvcGraphEdge.CREATTION_EPOCH));
    }

    public static void updateSvcToSvcGraphEdges(List<SvcToSvcGraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        BulkWriteOptions options = new BulkWriteOptions().ordered(false).bypassDocumentValidation(true);
        List<WriteModel<SvcToSvcGraphEdge>> bulkList = new ArrayList<>();
        UpdateOptions updateOptions = new UpdateOptions().upsert(true).bypassDocumentValidation(true);
        for(SvcToSvcGraphEdge edge: edges) {
            Bson updates = Updates.combine(
                Updates.setOnInsert(SvcToSvcGraphEdge.SOURCE, edge.getSource()),
                Updates.setOnInsert(SvcToSvcGraphEdge.TARGET, edge.getTarget()),
                Updates.setOnInsert(SvcToSvcGraphEdge.TYPE, edge.getType().toString()),
                Updates.setOnInsert(SvcToSvcGraphEdge.COUNTER, edge.getCounter()),
                Updates.setOnInsert(SvcToSvcGraphEdge.LAST_SEEN_EPOCH, edge.getLastSeenEpoch()),
                Updates.setOnInsert(SvcToSvcGraphEdge.CREATTION_EPOCH, edge.getCreationEpoch()),
                Updates.setOnInsert(ID, edge.getId())
            );

            bulkList.add(new UpdateOneModel<>(Filters.eq(ID, edge.getId()), updates, updateOptions));
        }

        SvcToSvcGraphEdgesDao.instance.bulkWrite(bulkList, options);
    }

    public static void updateSvcToSvcGraphNodes(List<SvcToSvcGraphNode> nodes) {

        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        UpdateOptions updateOptions = new UpdateOptions().upsert(true).bypassDocumentValidation(true);

        BulkWriteOptions options = new BulkWriteOptions().ordered(false).bypassDocumentValidation(true);
        List<WriteModel<SvcToSvcGraphNode>> bulkList = new ArrayList<>();
        for(SvcToSvcGraphNode node: nodes) {
            Bson updates = Updates.combine(
                Updates.setOnInsert(SvcToSvcGraphEdge.TYPE, node.getType().toString()),
                Updates.setOnInsert(SvcToSvcGraphEdge.COUNTER, node.getCounter()),
                Updates.setOnInsert(SvcToSvcGraphEdge.LAST_SEEN_EPOCH, node.getLastSeenEpoch()),
                Updates.setOnInsert(SvcToSvcGraphEdge.CREATTION_EPOCH, node.getCreationEpoch()),
                Updates.setOnInsert(ID, node.getId())
            );

            bulkList.add(new UpdateOneModel<>(Filters.eq(ID, node.getId()), updates, updateOptions));
        }

        SvcToSvcGraphNodesDao.instance.bulkWrite(bulkList, options);
    }
    public static List<String> findTestSubCategoriesByTestSuiteId(List<String> testSuiteId) {
        List<ObjectId> testSuiteIds = new ArrayList<>();
        for (String testSuiteIdStr : testSuiteId) {
            testSuiteIds.add(new ObjectId(testSuiteIdStr));
        }
        return TestSuiteDao.getAllTestSuitesSubCategories(testSuiteIds);
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
        Bson stateUpdate = Updates.set(TestingRunPlayground.STATE, State.COMPLETED);
        if (testingRunPlayground.getTestingRunPlaygroundType()
                == TestingRunPlayground.TestingRunPlaygroundType.LOGIN_FLOW_TEST) {
            TestingRunPlaygroundDao.instance.updateOne(
                    Filters.eq(Constants.ID, testingRunPlayground.getId()),
                    Updates.combine(
                            stateUpdate,
                            Updates.set(TestingRunPlayground.LOGIN_FLOW_RESPONSE,
                                    testingRunPlayground.getLoginFlowResponse())));
        } else if (testingRunPlayground.getTestingRunPlaygroundType()
                == TestingRunPlayground.TestingRunPlaygroundType.RECORDED_JSON_FLOW) {
            List<Bson> recordedFlowUpdates = new ArrayList<>();
            recordedFlowUpdates.add(stateUpdate);
            if (testingRunPlayground.getRecordedFlowTokenResult() != null) {
                recordedFlowUpdates.add(Updates.set(TestingRunPlayground.RECORDED_FLOW_TOKEN_RESULT,
                        testingRunPlayground.getRecordedFlowTokenResult()));
            }
            if (testingRunPlayground.getRecordedFlowErrorMessage() != null) {
                recordedFlowUpdates.add(Updates.set(TestingRunPlayground.RECORDED_FLOW_ERROR_MESSAGE,
                        testingRunPlayground.getRecordedFlowErrorMessage()));
            }
            TestingRunPlaygroundDao.instance.updateOne(
                    Filters.eq(Constants.ID, new ObjectId(testingRunPlayground.getHexId())),
                    Updates.combine(recordedFlowUpdates));
        } else {
            TestingRunPlaygroundDao.instance.updateOne(
                    Filters.eq(Constants.ID, new ObjectId(testingRunPlayground.getHexId())),
                    Updates.combine(
                            stateUpdate,
                            Updates.set(TestingRunPlayground.TESTING_RUN_RESULT, testingRunPlayground.getTestingRunResult()
                            )
                    )
            );
        }
    }

    public static void updateTestingRunPlayground(ObjectId id, TestingRunResult testingRunResult) {
        TestingRunPlaygroundDao.instance.updateOne(
                Filters.eq(Constants.ID, id),
                Updates.combine(
                        Updates.set(TestingRunPlayground.STATE, State.COMPLETED),
                        Updates.set(TestingRunPlayground.TESTING_RUN_RESULT, testingRunResult)
                )
            );
    }
    public static void updateTestingRunPlayground(ObjectId id, OriginalHttpResponse originalHttpResponse) {
        TestingRunPlaygroundDao.instance.updateOne(
                Filters.eq(Constants.ID, id),
                Updates.combine(
                        Updates.set(TestingRunPlayground.STATE, State.COMPLETED),
                        Updates.set(TestingRunPlayground.ORIGINAL_HTTP_RESPONSE, originalHttpResponse)
                )
            );
    }

    public static void insertJob(Job job) {
        JobsDao.instance.insertOne(job);
    }

    public static void bulkinsertApiHitCount(List<ApiHitCountInfo> apiHitCountInfoList) throws Exception {
        try {
            List<WriteModel<ApiHitCountInfo>> updates = new ArrayList<>();
            for (ApiHitCountInfo apiHitCountInfo: apiHitCountInfoList) {
                // Create a filter to find existing documents with the same key fields
                Bson filter = Filters.and(
                    Filters.eq("apiCollectionId", apiHitCountInfo.getApiCollectionId()),
                    Filters.eq("url", apiHitCountInfo.getUrl()),
                    Filters.eq("method", apiHitCountInfo.getMethod()),
                    Filters.eq("ts", apiHitCountInfo.getTs())
                );

                // Use updateOne with upsert instead of insertOne to ensure uniqueness
                updates.add(new UpdateOneModel<>(
                    filter,
                    Updates.combine(
                        Updates.setOnInsert("apiCollectionId", apiHitCountInfo.getApiCollectionId()),
                        Updates.setOnInsert("url", apiHitCountInfo.getUrl()),
                        Updates.setOnInsert("method", apiHitCountInfo.getMethod()),
                        Updates.setOnInsert("ts", apiHitCountInfo.getTs()),
                        Updates.set("count", apiHitCountInfo.getCount())
                    ),
                    new UpdateOptions().upsert(true)
                ));
            }
            ApiHitCountInfoDao.instance.getMCollection().bulkWrite(updates);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in bulkinsertApiHitCount " + e.toString());
            throw e;
        }
    }

    public static String fetchOpenApiSchema(int apiCollectionId) {

        Bson sort = Sorts.descending("uploadTs");
        SwaggerFileUpload fileUpload = FileUploadsDao.instance.getSwaggerMCollection().find(Filters.eq("collectionId", apiCollectionId)).sort(sort).limit(1).projection(Projections.fields(Projections.include("swaggerFileId"))).first();
        if (fileUpload == null) {
            return null;
        }

        ObjectId objectId = new ObjectId(fileUpload.getSwaggerFileId());

        File file = FilesDao.instance.findOne(Filters.eq("_id", objectId));
        if (file == null) {
            return null;
        }

        return file.getCompressedContent();
    }

    public static void insertDataIngestionLog(Log log) {
        DataIngestionLogsDao.instance.insertOne(log);
    }

    public static void insertMCPAuditDataLog(McpAuditInfo auditInfo) {
        List<Bson> filterList = new ArrayList<>();
        filterList.add(Filters.eq(McpAuditInfo.TYPE, auditInfo.getType()));
        filterList.add(Filters.eq(McpAuditInfo.RESOURCE_NAME, auditInfo.getResourceName()));
        if (auditInfo.getMcpHost() != null) {
            filterList.add(Filters.eq(McpAuditInfo.MCP_HOST, auditInfo.getMcpHost()));
        } else {
            filterList.add(Filters.exists(McpAuditInfo.MCP_HOST, false));
        }
        Bson filter = Filters.and(filterList);

        // For mcp-server entries, resourceName is <device>.<ai-agent>.<mcpservername>.
        // Extract the mcpservername suffix (everything after the first two dot-segments)
        // and search for any existing entry with that same server name suffix that has blockAll:true.
        McpAuditInfo blockedEntry = null;
        if (Constants.AKTO_MCP_SERVER_TAG.equals(auditInfo.getType())) {
            String resourceName = auditInfo.getResourceName();
            int firstDot = resourceName.indexOf('.');
            int secondDot = firstDot >= 0 ? resourceName.indexOf('.', firstDot + 1) : -1;
            String mcpServerName = secondDot >= 0 ? resourceName.substring(secondDot + 1) : resourceName;

            loggerMaker.infoAndAddToDb(String.format(
                "[insertMCPAuditDataLog] Extracted mcpServerName=%s from resourceName=%s, searching for blockAll=true",
                mcpServerName, resourceName
            ), LogDb.DASHBOARD);

            // Match any resourceName ending with .<mcpServerName> or equal to it
            String regexPattern = "(^|\\.)" + java.util.regex.Pattern.quote(mcpServerName) + "$";
            Bson blockAllFilter = Filters.and(
                Filters.regex(McpAuditInfo.RESOURCE_NAME, regexPattern),
                Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_MCP_SERVER_TAG),
                Filters.eq(McpAuditInfo.BLOCK_ALL, true)
            );
            blockedEntry = McpAuditInfoDao.instance.findOne(blockAllFilter);
        } else if (Constants.AKTO_MCP_TOOLS_TAG.equals(auditInfo.getType())) {
            // For mcp-tool, mcpHost is <device>.<clientType>.<serverName>.
            // Extract serverName suffix (same two-dot logic as mcp-server resourceName above)
            // and check if that server has blockAll=true.
            String mcpHost = auditInfo.getMcpHost();
            if (mcpHost != null && !mcpHost.isEmpty()) {
                int firstDot = mcpHost.indexOf('.');
                int secondDot = firstDot >= 0 ? mcpHost.indexOf('.', firstDot + 1) : -1;
                String serverName = secondDot >= 0 ? mcpHost.substring(secondDot + 1) : mcpHost;

                loggerMaker.infoAndAddToDb(String.format(
                    "[insertMCPAuditDataLog] mcp-tool: extracted serverName=%s from mcpHost=%s, checking for blockAll=true",
                    serverName, mcpHost
                ), LogDb.DASHBOARD);

                String regexPattern = "(^|\\.)" + java.util.regex.Pattern.quote(serverName) + "$";
                Bson blockAllFilter = Filters.and(
                    Filters.regex(McpAuditInfo.RESOURCE_NAME, regexPattern),
                    Filters.eq(McpAuditInfo.TYPE, Constants.AKTO_MCP_SERVER_TAG),
                    Filters.eq(McpAuditInfo.BLOCK_ALL, true)
                );
                blockedEntry = McpAuditInfoDao.instance.findOne(blockAllFilter);
            } else {
                loggerMaker.infoAndAddToDb(String.format(
                    "[insertMCPAuditDataLog] mcp-tool has no mcpHost, skipping server blockAll check for resourceName=%s",
                    auditInfo.getResourceName()
                ), LogDb.DASHBOARD);
            }
        } else {
            loggerMaker.infoAndAddToDb(String.format(
                "[insertMCPAuditDataLog] Skipping blockAll check for type=%s resourceName=%s",
                auditInfo.getType(), auditInfo.getResourceName()
            ), LogDb.DASHBOARD);
        }
        loggerMaker.infoAndAddToDb(String.format(
            "[insertMCPAuditDataLog] blockAll check result: %s",
            blockedEntry != null ? "FOUND (resourceName=" + blockedEntry.getResourceName() + " mcpHost=" + blockedEntry.getMcpHost() + ")" : "NOT FOUND"
        ), LogDb.DASHBOARD);

        List<Bson> updateList = new ArrayList<>();
        updateList.add(Updates.set(McpAuditInfo.LAST_DETECTED, Context.now()));
        updateList.add(Updates.setOnInsert(McpAuditInfo.TYPE, auditInfo.getType()));
        updateList.add(Updates.setOnInsert(McpAuditInfo.RESOURCE_NAME, auditInfo.getResourceName()));
        ComponentRiskAnalysis componentRiskAnalysis = auditInfo.getComponentRiskAnalysis();
        if (componentRiskAnalysis != null) {
            updateList.add(Updates.set(McpAuditInfo.COMPONENT_RISK_ANALYSIS, componentRiskAnalysis));
        }
        if (auditInfo.getMcpHost() != null) {
            updateList.add(Updates.set(McpAuditInfo.MCP_HOST, auditInfo.getMcpHost()));
        }
        if (auditInfo.getHostCollectionId() != 0) {
            updateList.add(Updates.set(McpAuditInfo.HOST_COLLECTION_ID, auditInfo.getHostCollectionId()));
        }
        if (StringUtils.isNotBlank(auditInfo.getContextSource())) {
            updateList.add(Updates.set(McpAuditInfo.CONTEXT_SOURCE, auditInfo.getContextSource()));
        }
        if (blockedEntry != null) {
            loggerMaker.infoAndAddToDb(String.format(
                "[insertMCPAuditDataLog] Applying remarks=Rejected to entry for resourceName=%s mcpHost=%s",
                auditInfo.getResourceName(), auditInfo.getMcpHost()
            ), LogDb.DASHBOARD);
            updateList.add(Updates.set(McpAuditInfo.REMARKS, "Rejected"));
            updateList.add(Updates.set(McpAuditInfo.MARKED_BY, "System"));
            if (!Constants.AKTO_MCP_TOOLS_TAG.equals(auditInfo.getType())) {
                updateList.add(Updates.set(McpAuditInfo.BLOCK_ALL, true));
            }
        }

        Bson updates = Updates.combine(updateList.toArray(new Bson[0]));
        loggerMaker.infoAndAddToDb(String.format(
            "[insertMCPAuditDataLog] Upserting entry: resourceName=%s type=%s mcpHost=%s blockAllApplied=%b",
            auditInfo.getResourceName(), auditInfo.getType(), auditInfo.getMcpHost(), blockedEntry != null
        ), LogDb.DASHBOARD);
        McpAuditInfoDao.instance.updateOne(filter, updates);
        loggerMaker.infoAndAddToDb(String.format(
            "[insertMCPAuditDataLog] Upsert complete for resourceName=%s mcpHost=%s",
            auditInfo.getResourceName(), auditInfo.getMcpHost()
        ), LogDb.DASHBOARD);
    }

    public static List<EndpointMcpConfig> fetchEndpointMcpConfig(String tempCollectionName, Integer updatedDate) {
        try {
            List<Bson> filters = new ArrayList<>();
            if (tempCollectionName != null) {
                filters.add(Filters.eq(EndpointMcpConfig.TEMP_COLLECTION_NAME_FIELD, tempCollectionName));
            }
            if (updatedDate != null) {
                filters.add(Filters.gte(EndpointMcpConfig.UPDATED_DATE_FIELD, updatedDate));
            }
            Bson filter = filters.isEmpty() ? Filters.empty() : Filters.and(filters);
            return EndpointMcpConfigDao.instance.findAll(filter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointMcpConfig: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void upsertEndpointMcpConfig(McpServerCollectionInfo info) {
        int now = Context.now();
        Bson filter = Filters.eq(EndpointMcpConfig.COLLECTION_NAME_FIELD, info.getCollectionName());
        List<Bson> updateList = new ArrayList<>();
        updateList.add(Updates.setOnInsert(EndpointMcpConfig.COLLECTION_NAME_FIELD, info.getCollectionName()));
        updateList.add(Updates.setOnInsert(EndpointMcpConfig.DOMAIN_NAME_FIELD, info.getDomainName()));
        updateList.add(Updates.setOnInsert(EndpointMcpConfig.CREATED_DATE_FIELD, now));
        updateList.add(Updates.set(EndpointMcpConfig.TEMP_COLLECTION_NAME_FIELD, info.getTempCollectionName()));
        updateList.add(Updates.set(EndpointMcpConfig.UPDATED_DATE_FIELD, now));
        EndpointMcpConfigDao.instance.updateOne(filter, Updates.combine(updateList));
    }

    public static List<SlackWebhook> fetchSlackWebhooks() {
        return SlackWebhooksDao.instance.findAll(Filters.empty());
    }

    public static List<McpReconRequest> fetchPendingMcpReconRequests() {
        // Fetch all requests where status is "Pending"
        Bson filter = Filters.eq(McpReconRequest.STATUS, Constants.STATUS_PENDING);
        return McpReconRequestDao.instance.findAll(filter);
    }

    public static void updateMcpReconRequestStatus(Object requestId, String newStatus, int serversFound) {
        Bson filter = Filters.eq(McpReconRequest.ID, requestId);
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

    public static List<YamlTemplate> fetchMCPThreatProtectionTemplates(Integer updatedAfter) {
        try {
            // Use regex filter for case-insensitive "contains" match of "mcp" in content field
            Bson contentFilter = Filters.regex(YamlTemplate.CONTENT, "mcp", "i");

            // Build filter based on whether updatedAfter is specified
            Bson filter;
            if (updatedAfter != null && updatedAfter > 0) {
                // Combine content filter with updatedAt filter (greater than specified timestamp)
                Bson updatedAtFilter = Filters.gt(YamlTemplate.UPDATED_AT, updatedAfter);
                filter = Filters.and(contentFilter, updatedAtFilter);
            } else {
                // Only content filter
                filter = contentFilter;
            }

            // Fetch templates matching the filter
            List<YamlTemplate> mcpTemplates = FilterYamlTemplateDao.instance.findAll(filter);

            return mcpTemplates;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchMCPThreatProtectionTemplates: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static List<McpAuditInfo> fetchMcpAuditInfo(Integer updatedAfter, List<String> remarksList) {
        try {
            List<Bson> filters = new ArrayList<>();

            // Add updatedTimestamp filter if specified
            if (updatedAfter != null && updatedAfter > 0) {
                filters.add(Filters.gt("updatedTimestamp", updatedAfter));
            }

            // Add remarks filter if specified (exact match any of the values, case-insensitive)
            if (remarksList != null && !remarksList.isEmpty()) {
                List<Bson> remarksFilters = new ArrayList<>();
                for (String remark : remarksList) {
                    if (remark != null && !remark.isEmpty()) {
                        // Using ^...$ for exact match with case-insensitive flag
                        remarksFilters.add(Filters.regex("remarks", "^" + remark + "$", "i"));
                    }
                }
                // If we have any remarks filters, combine them with OR
                if (!remarksFilters.isEmpty()) {
                    filters.add(Filters.or(remarksFilters));
                }
            }

            // Combine filters with AND (handles 0, 1, or multiple filters)
            Bson finalFilter = filters.isEmpty() ? Filters.empty() : Filters.and(filters);

            // Fetch MCP audit info matching the filter
            List<McpAuditInfo> mcpAuditInfoList = McpAuditInfoDao.instance.findAll(finalFilter);

            return mcpAuditInfoList;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchMcpAuditInfo: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static List<McpAllowlist> fetchMcpAllowlist(Integer timestamp) {
        try {
            Bson filter;
            if (timestamp != null && timestamp != -1) {
                filter = Filters.gte(McpAllowlist.CREATED_AT, timestamp);
            } else {
                filter = Filters.empty();
            }
            return McpAllowlistDao.instance.findAll(filter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchMcpAllowlist: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void updateMcpAuditInfo(String componentType, String componentName, String mcpHost, ComponentRiskAnalysis componentRiskAnalysis) {
        if (componentType == null || componentName == null || componentRiskAnalysis == null || mcpHost == null) {
            return;
        }
        Bson filter = Filters.and(
                Filters.eq(McpAuditInfo.TYPE, componentType),
                Filters.eq(McpAuditInfo.RESOURCE_NAME, componentName),
                Filters.eq(McpAuditInfo.MCP_HOST, mcpHost)
        );
        List<Bson> updateList = new ArrayList<>();
        updateList.add(Updates.set(McpAuditInfo.COMPONENT_RISK_ANALYSIS, componentRiskAnalysis));
        updateList.add(Updates.set(McpAuditInfo.UPDATED_TIMESTAMP, Context.now()));
        Bson updates = Updates.combine(updateList.toArray(new Bson[0]));
        McpAuditInfoDao.instance.updateOneNoUpsert(filter, updates);
    }

    public static List<GuardrailPolicies> fetchGuardrailPolicies(Integer updatedAfter, CONTEXT_SOURCE contextSource) {
        try {
            List<Bson> filters = new ArrayList<>();

            if (updatedAfter != null && updatedAfter > 0) {
                filters.add(Filters.gt("updatedTimestamp", updatedAfter));
            }

            if (contextSource != null) {
                if (contextSource == CONTEXT_SOURCE.AGENTIC) {
                    filters.add(Filters.or(
                        Filters.eq("contextSource", CONTEXT_SOURCE.AGENTIC.name()),
                        Filters.exists("contextSource", false)
                    ));
                } else {
                    filters.add(Filters.eq("contextSource", contextSource.name()));
                }
            }

            Bson finalFilter = Filters.empty();
            if (!filters.isEmpty()) {
                finalFilter = Filters.and(filters);
            }

            return GuardrailPoliciesDao.instance.findAll(finalFilter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchGuardrailPolicies: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static List<String> findDeviceIdsByTeamsAndRoles(List<String> teams, List<String> roles) {
        try {
            return AgentUsersDao.instance.findDeviceIdsByTeamsAndRoles(teams, roles);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in findDeviceIdsByTeamsAndRoles: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // --- Config Field Policy (Misconfigurations) ---

    public static List<ConfigFieldPolicy> fetchActiveConfigFieldPolicies(String toolName) {
        try {
            Bson filter = Filters.and(
                    Filters.eq(ConfigFieldPolicy.STATUS, "ACTIVE"),
                    Filters.eq(ConfigFieldPolicy.TOOL_NAME, toolName));
            return ConfigFieldPolicyDao.instance.findAll(filter);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchActiveConfigFieldPolicies: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static void storeConversationResults(List<AgentConversationResult> conversationResults) {
        AgentConversationResultDao.instance.insertMany(conversationResults);
    }

    public static void bulkWriteAgentTrafficLogs(List<AgentTrafficLog> agentTrafficLogs) {
        if (agentTrafficLogs == null || agentTrafficLogs.isEmpty()) {
            return;
        }
        
        // Convert expireAtEpoch to expiresAt Date before inserting
        Date defaultExpiry = new Date(System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000));
        for (AgentTrafficLog log : agentTrafficLogs) {
            if (log.getExpiresAtEpoch() != null && log.getExpiresAtEpoch() > 0) {
                // Convert epoch seconds to Date
                log.setExpiresAt(new Date(log.getExpiresAtEpoch() * 1000));
            } else {
                // Default 7 days expiry
                log.setExpiresAt(defaultExpiry);
            }
        }
        
        AgentTrafficLogDao.instance.insertMany(agentTrafficLogs);
    }

    public static void storeTrace(Trace trace) {
        TraceDao.instance.insertOne(trace);
    }

    public static void storeSpans(List<Span> spans) {
        SpanDao.instance.insertMany(spans);
    }

    public static void storeTestingRunWebhook(TestingRunWebhook testingRunWebhook) {
        TestingRunWebhookDao.instance.insertOne(testingRunWebhook);
    }

    public static void bulkUpsertAgenticSessionContext(List<SessionDocument> sessionDocuments) {
        if (sessionDocuments == null || sessionDocuments.isEmpty()) {
            return;
        }

        List<WriteModel<SessionDocument>> bulkUpdates = new ArrayList<>();
        UpdateOptions updateOptions = new UpdateOptions().upsert(true);
        long currentTime = Context.now();

        for (SessionDocument sessionDocument : sessionDocuments) {
            if (sessionDocument == null || sessionDocument.getSessionIdentifier() == null || sessionDocument.getSessionIdentifier().isEmpty()) {
                continue;
            }

            Bson filter = Filters.eq(SessionDocument.SESSION_IDENTIFIER, sessionDocument.getSessionIdentifier());
            sessionDocument.setUpdatedAt(currentTime);

            Bson updates = Updates.combine(
                Updates.setOnInsert(SessionDocument.SESSION_IDENTIFIER, sessionDocument.getSessionIdentifier()),
                Updates.setOnInsert(SessionDocument.CREATED_AT, currentTime),
                Updates.set(SessionDocument.SESSION_SUMMARY, sessionDocument.getSessionSummary()),
                Updates.set(SessionDocument.CONVERSATION_INFO, sessionDocument.getConversationInfo()),
                Updates.set(SessionDocument.IS_MALICIOUS, sessionDocument.isMalicious()),
                Updates.set(SessionDocument.BLOCKED_REASON, sessionDocument.getBlockedReason()),
                Updates.set(SessionDocument.UPDATED_AT, sessionDocument.getUpdatedAt())
            );

            bulkUpdates.add(new UpdateOneModel<>(filter, updates, updateOptions));
        }

        if (!bulkUpdates.isEmpty()) {
            SessionDocumentDao.instance.getMCollection().bulkWrite(bulkUpdates);
        }
    }

    public static Map<String, String> fetchDeviceUserMap() {
        List<ModuleInfo> modules = ModuleInfoDao.instance.findAll(
            Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD.name()),
            Projections.include(ModuleInfo.NAME, ModuleInfo.ADDITIONAL_DATA + ".username")
        );
        Map<String, String> result = new HashMap<>();
        if (modules == null) return result;
        for (ModuleInfo m : modules) {
            if (m.getName() == null || m.getAdditionalData() == null) continue;
            Object usernameObj = m.getAdditionalData().get("username");
            if (usernameObj instanceof String && !((String) usernameObj).isEmpty()) {
                result.put(m.getName(), (String) usernameObj);
            }
        }
        return result;
    }

    // --- Endpoint Remote Commands ---

    /**
     * Lazy fan-out: for each ACTIVE command targeting this device that has not yet
     * been executed (or is still PENDING), create/return an execution record.
     * Stamps agentId on newly-created records.
     * Returns at most 10 executions with transient command fields set for the agent response.
     */
    public static List<EndpointRemoteCommandExecution> fetchPendingEndpointRemoteCommandExecutions(
            String agentId, String deviceId) {
        try {
            long now = (long) Context.now();
            List<EndpointRemoteCommand> activeCmds = EndpointRemoteCommandDao.instance.findAll(
                    Filters.and(
                            Filters.eq(EndpointRemoteCommand.STATUS, EndpointRemoteCommand.Status.ACTIVE.name()),
                            Filters.or(
                                    Filters.eq(EndpointRemoteCommand.TARGET_TYPE,
                                            EndpointRemoteCommand.TargetType.ALL.name()),
                                    Filters.in(EndpointRemoteCommand.TARGET_DEVICE_IDS, deviceId))));

            // No expiry check here — the background job transitions expired commands to EXPIRED
            // before agents can poll them, so any ACTIVE command is still within its window.
            List<EndpointRemoteCommandExecution> result = new ArrayList<>();
            for (EndpointRemoteCommand cmd : activeCmds) {
                if (result.size() >= 10) break;

                EndpointRemoteCommandExecution exec = EndpointRemoteCommandExecutionDao.instance.findOne(
                        Filters.and(
                                Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, cmd.getId()),
                                Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId)));

                if (exec == null) {
                    exec = new EndpointRemoteCommandExecution();
                    exec.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                    exec.setCommandId(cmd.getId());
                    exec.setDeviceId(deviceId);
                    exec.setAgentId(agentId);
                    exec.setStatus(EndpointRemoteCommandExecution.Status.PENDING);
                    exec.setCreatedAt(now);
                    exec.setUpdatedAt(now);
                    try {
                        EndpointRemoteCommandExecutionDao.instance.insertOne(exec);
                    } catch (com.mongodb.MongoBulkWriteException | com.mongodb.MongoWriteException dup) {
                        // Race: another request just created it — re-fetch
                        exec = EndpointRemoteCommandExecutionDao.instance.findOne(
                                Filters.and(
                                        Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, cmd.getId()),
                                        Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId)));
                        if (exec == null || exec.getStatus() != EndpointRemoteCommandExecution.Status.PENDING) continue;
                    }
                } else if (exec.getStatus() != EndpointRemoteCommandExecution.Status.PENDING) {
                    continue;
                }

                exec.setCommand(cmd.getCommand());
                exec.setArgs(cmd.getArgs());
                exec.setTimeoutSec(cmd.getTimeoutSec());
                result.add(exec);
            }
            return result;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchPendingEndpointRemoteCommandExecutions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Atomic CAS: PENDING → RUNNING (or PENDING → FAILED).
     * Returns 0 on success, 1 if another agent already claimed it (409), -1 if not found (400).
     */
    public static int claimEndpointRemoteCommandExecution(
            String executionId, String agentId, String deviceId,
            EndpointRemoteCommandExecution.Status newStatus) {
        try {
            com.mongodb.client.result.UpdateResult r =
                    EndpointRemoteCommandExecutionDao.instance.getMCollection().updateOne(
                            Filters.and(
                                    Filters.eq(EndpointRemoteCommandExecution.ID, executionId),
                                    Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId),
                                    Filters.eq(EndpointRemoteCommandExecution.STATUS,
                                            EndpointRemoteCommandExecution.Status.PENDING.name())),
                            Updates.combine(
                                    Updates.set(EndpointRemoteCommandExecution.STATUS, newStatus.name()),
                                    Updates.set(EndpointRemoteCommandExecution.AGENT_ID, agentId),
                                    Updates.set(EndpointRemoteCommandExecution.UPDATED_AT, (long) Context.now())));
            if (r.getMatchedCount() == 0) {
                // Distinguish not-found from already-claimed
                EndpointRemoteCommandExecution exec = EndpointRemoteCommandExecutionDao.instance.findOne(
                        Filters.and(
                                Filters.eq(EndpointRemoteCommandExecution.ID, executionId),
                                Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId)));
                return exec == null ? -1 : 1; // -1 = not found, 1 = conflict
            }
            return 0;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in claimEndpointRemoteCommandExecution: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Upload execution result. Allowed transitions: RUNNING → COMPLETED/FAILED,
     * and PENDING → COMPLETED/FAILED (if the claim call was lost in transit).
     */
    public static boolean updateEndpointRemoteCommandResult(
            String executionId, String deviceId,
            EndpointRemoteCommandExecution.Status newStatus,
            String stdout, String stderr,
            int exitCode, long durationMs, long executedAt, String errorReason) {
        try {
            EndpointRemoteCommandExecution exec = EndpointRemoteCommandExecutionDao.instance.findOne(
                    Filters.and(
                            Filters.eq(EndpointRemoteCommandExecution.ID, executionId),
                            Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId),
                            Filters.in(EndpointRemoteCommandExecution.STATUS,
                                    EndpointRemoteCommandExecution.Status.RUNNING.name(),
                                    EndpointRemoteCommandExecution.Status.PENDING.name())));
            if (exec == null) return false;

            final int maxBytes = 512 * 1024;
            if (stdout != null && stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
                stdout = new String(stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, maxBytes,
                        java.nio.charset.StandardCharsets.UTF_8) + "\n[truncated by server]";
            }
            if (stderr != null && stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
                stderr = new String(stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, maxBytes,
                        java.nio.charset.StandardCharsets.UTF_8) + "\n[truncated by server]";
            }

            EndpointRemoteCommandExecutionDao.instance.getMCollection().updateOne(
                    Filters.and(
                            Filters.eq(EndpointRemoteCommandExecution.ID, executionId),
                            Filters.eq(EndpointRemoteCommandExecution.DEVICE_ID, deviceId)),
                    Updates.combine(
                            Updates.set(EndpointRemoteCommandExecution.STATUS, newStatus.name()),
                            Updates.set(EndpointRemoteCommandExecution.STDOUT, stdout),
                            Updates.set(EndpointRemoteCommandExecution.STDERR, stderr),
                            Updates.set(EndpointRemoteCommandExecution.EXIT_CODE, exitCode),
                            Updates.set(EndpointRemoteCommandExecution.DURATION_MS, durationMs),
                            Updates.set(EndpointRemoteCommandExecution.EXECUTED_AT, executedAt),
                            Updates.set(EndpointRemoteCommandExecution.ERROR_REASON,
                                    errorReason != null ? errorReason : ""),
                            Updates.set(EndpointRemoteCommandExecution.UPDATED_AT, (long) Context.now())));
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in updateEndpointRemoteCommandResult: " + e.getMessage());
            return false;
        }
    }

    /**
     * Create a new command definition. Timestamps stored as milliseconds to match the
     * dashboard document format. Returns the generated commandId (ObjectId hex), or null on error.
     */
    public static String queueEndpointRemoteCommand(
            String command, List<String> args, int timeoutSec, int expirySeconds,
            EndpointRemoteCommand.TargetType targetType, List<String> targetDeviceIds,
            String createdBy) {
        try {
            long nowMs = System.currentTimeMillis();
            EndpointRemoteCommand doc = new EndpointRemoteCommand();
            doc.setCommand(command);
            doc.setArgs(args);
            doc.setTimeoutSec(timeoutSec);
            doc.setExpirySeconds(expirySeconds);
            doc.setTargetType(targetType);
            doc.setTargetDeviceIds(targetDeviceIds);
            doc.setStatus(EndpointRemoteCommand.Status.ACTIVE);
            doc.setCreatedAt(nowMs);
            doc.setExpiresAt(nowMs + (long) expirySeconds * 1000L);
            doc.setUpdatedAt(nowMs);
            doc.setCreatedBy(createdBy);
            EndpointRemoteCommandDao.instance.insertOne(doc);
            return doc.getObjectId() != null ? doc.getObjectId().toHexString() : null;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in queueEndpointRemoteCommand: " + e.getMessage());
            return null;
        }
    }

    /**
     * List command definitions with per-command execution status counts.
     * Sorted by createdAt DESC; default limit 20, max 100.
     */
    public static List<EndpointRemoteCommand> fetchEndpointRemoteCommandList(long createdAfter, int limit) {
        try {
            if (limit <= 0 || limit > 100) limit = 20;
            List<EndpointRemoteCommand> commands = EndpointRemoteCommandDao.instance.findAll(
                    createdAfter > 0
                            ? Filters.gt(EndpointRemoteCommand.CREATED_AT, createdAfter)
                            : Filters.empty(),
                    0, limit, Sorts.descending(EndpointRemoteCommand.CREATED_AT));
            if (commands.isEmpty()) return commands;

            List<String> commandIds = new ArrayList<>();
            for (EndpointRemoteCommand c : commands) commandIds.add(c.getId());

            // Aggregate execution counts: group by (commandId, status)
            List<org.bson.conversions.Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(Filters.in(EndpointRemoteCommandExecution.COMMAND_ID, commandIds)));
            pipeline.add(Aggregates.group(
                    new BasicDBObject("commandId", "$" + EndpointRemoteCommandExecution.COMMAND_ID)
                            .append("status", "$" + EndpointRemoteCommandExecution.STATUS),
                    Accumulators.sum("count", 1)));

            Map<String, EndpointRemoteCommand.ExecutionSummary> summaryMap = new HashMap<>();
            EndpointRemoteCommandExecutionDao.instance.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class)
                    .forEach((BasicDBObject row) -> {
                        BasicDBObject id = (BasicDBObject) row.get("_id");
                        String cmdId = id.getString("commandId");
                        String status = id.getString("status");
                        int count = row.getInt("count", 0);
                        EndpointRemoteCommand.ExecutionSummary s =
                                summaryMap.computeIfAbsent(cmdId, k -> new EndpointRemoteCommand.ExecutionSummary());
                        s.setTotal(s.getTotal() + count);
                        if ("PENDING".equals(status)) s.setPending(s.getPending() + count);
                        else if ("RUNNING".equals(status)) s.setRunning(s.getRunning() + count);
                        else if ("COMPLETED".equals(status)) s.setCompleted(s.getCompleted() + count);
                        else if ("FAILED".equals(status)) s.setFailed(s.getFailed() + count);
                    });

            for (EndpointRemoteCommand c : commands) {
                c.setExecutionSummary(summaryMap.getOrDefault(c.getId(),
                        new EndpointRemoteCommand.ExecutionSummary()));
            }
            return commands;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointRemoteCommandList: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Per-device execution results for one command. Default limit 50, max 100. */
    public static List<EndpointRemoteCommandExecution> fetchEndpointRemoteCommandExecutions(
            String commandId, int limit) {
        try {
            if (limit <= 0 || limit > 100) limit = 50;
            return EndpointRemoteCommandExecutionDao.instance.findAll(
                    Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, commandId),
                    0, limit, Sorts.descending(EndpointRemoteCommandExecution.CREATED_AT));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in fetchEndpointRemoteCommandExecutions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Cancel an ACTIVE command and immediately mark all its PENDING executions FAILED/cancelled.
     * Returns false if the command is already CANCELLED or EXPIRED (caller should return 400).
     * RUNNING executions are left untouched — the agent already claimed them.
     */
    public static boolean cancelEndpointRemoteCommand(String commandId) {
        try {
            long nowMs = System.currentTimeMillis();
            // commandId is always the ObjectId hex string — filter by _id
            com.mongodb.client.result.UpdateResult r =
                    EndpointRemoteCommandDao.instance.getMCollection().updateOne(
                            Filters.and(
                                    Filters.eq("_id", new org.bson.types.ObjectId(commandId)),
                                    Filters.eq(EndpointRemoteCommand.STATUS,
                                            EndpointRemoteCommand.Status.ACTIVE.name())),
                            Updates.combine(
                                    Updates.set(EndpointRemoteCommand.STATUS,
                                            EndpointRemoteCommand.Status.CANCELLED.name()),
                                    Updates.set(EndpointRemoteCommand.UPDATED_AT, nowMs)));
            if (r.getMatchedCount() == 0) return false;

            // Mark all PENDING executions for this command as FAILED/cancelled
            EndpointRemoteCommandExecutionDao.instance.getMCollection().updateMany(
                    Filters.and(
                            Filters.eq(EndpointRemoteCommandExecution.COMMAND_ID, commandId),
                            Filters.eq(EndpointRemoteCommandExecution.STATUS,
                                    EndpointRemoteCommandExecution.Status.PENDING.name())),
                    Updates.combine(
                            Updates.set(EndpointRemoteCommandExecution.STATUS,
                                    EndpointRemoteCommandExecution.Status.FAILED.name()),
                            Updates.set(EndpointRemoteCommandExecution.ERROR_REASON, "cancelled"),
                            Updates.set(EndpointRemoteCommandExecution.UPDATED_AT, nowMs)));
            return true;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in cancelEndpointRemoteCommand: " + e.getMessage());
            return false;
        }
    }

    /**
     * Upsert one NHI identity record. Natural key: (deviceId, source, identityName).
     *
     * Behavior:
     *   - first time we see this (device, file, secret-name): inserts a new doc
     *     with firstSeenAt/createdAt/lastRotatedAt = now, status=ACTIVE, and the
     *     incoming hash.
     *   - same hash as last seen: just bumps lastSeenAt + updatedAt + display fields
     *     (prefix/suffix/risk/etc.) — does NOT touch lastRotatedAt or previousHash.
     *   - hash changed: rotation. Stores the prior hash in previousHash and updates
     *     lastRotatedAt; this is what the dashboard surfaces as "rotated".
     *
     * Compatibility notes (dashboard ↔ cyborg co-write the same collection):
     *   - status is ONLY written on insert. Dashboard's disableNhiIdentity sets
     *     status="INACTIVE"; the scanner must not silently re-enable on next scan.
     *   - createdBy is setOnInsert only; updatedBy is refreshed every scan so the
     *     dashboard can tell user-edits apart from automated scans.
     *   - lastRotatedAt matches the dashboard's field name (same semantics).
     *
     * The raw secret is never written; only the redaction tuple (prefix, suffix, hash).
     */
    public static void upsertNhiIdentity(NhiIdentity identity) {
        if (identity == null
            || StringUtils.isBlank(identity.getIdentityName())
            || StringUtils.isBlank(identity.getDeviceId())
            || StringUtils.isBlank(identity.getSource())
            || StringUtils.isBlank(identity.getHash())) {
            loggerMaker.errorAndAddToDb("upsertNhiIdentity: missing required field, dropping record");
            return;
        }
        NhiIdentityDao.instance.createIndicesIfAbsent();
        int now = Context.now();
        final String scannerActor = "endpoint-shield-scanner";

        Bson filter = Filters.and(
            Filters.eq(NhiIdentity.DEVICE_ID, identity.getDeviceId()),
            Filters.eq(NhiIdentity.SOURCE, identity.getSource()),
            Filters.eq(NhiIdentity.IDENTITY_NAME, identity.getIdentityName())
        );

        NhiIdentity existing = NhiIdentityDao.instance.findOne(filter);
        boolean rotated = existing != null
            && existing.getHash() != null
            && !existing.getHash().equals(identity.getHash());

        List<Bson> updates = new ArrayList<>();
        // immutable on insert
        updates.add(Updates.setOnInsert(NhiIdentity.IDENTITY_NAME, identity.getIdentityName()));
        updates.add(Updates.setOnInsert(NhiIdentity.DEVICE_ID, identity.getDeviceId()));
        updates.add(Updates.setOnInsert(NhiIdentity.SOURCE, identity.getSource()));
        updates.add(Updates.setOnInsert(NhiIdentity.FIRST_SEEN_AT, now));
        updates.add(Updates.setOnInsert(NhiIdentity.CREATED_AT, now));
        updates.add(Updates.setOnInsert(NhiIdentity.CREATED_BY, scannerActor));
        // status is ONLY set on insert — never overwrite dashboard's "INACTIVE".
        updates.add(Updates.setOnInsert(NhiIdentity.STATUS, "ACTIVE"));

        // always refreshed
        updates.add(Updates.set(NhiIdentity.LAST_SEEN_AT, now));
        updates.add(Updates.set(NhiIdentity.UPDATED_AT, now));
        updates.add(Updates.set(NhiIdentity.UPDATED_BY, scannerActor));
        updates.add(Updates.set(NhiIdentity.HASH, identity.getHash()));
        if (StringUtils.isNotBlank(identity.getPrefix())) {
            updates.add(Updates.set(NhiIdentity.PREFIX, identity.getPrefix()));
        }
        if (StringUtils.isNotBlank(identity.getSuffix())) {
            updates.add(Updates.set(NhiIdentity.SUFFIX, identity.getSuffix()));
        }
        if (StringUtils.isNotBlank(identity.getIdentityType())) {
            updates.add(Updates.set(NhiIdentity.IDENTITY_TYPE, identity.getIdentityType()));
        }
        if (StringUtils.isNotBlank(identity.getContextSource())) {
            updates.add(Updates.set(NhiIdentity.CONTEXT_SOURCE, identity.getContextSource()));
        }
        if (StringUtils.isNotBlank(identity.getAgentName())) {
            updates.add(Updates.set(NhiIdentity.AGENT_NAME, identity.getAgentName()));
        }
        if (StringUtils.isNotBlank(identity.getAgentType())) {
            updates.add(Updates.set(NhiIdentity.AGENT_TYPE, identity.getAgentType()));
        }
        if (StringUtils.isNotBlank(identity.getRiskLevel())) {
            updates.add(Updates.set(NhiIdentity.RISK_LEVEL, identity.getRiskLevel()));
        }
        if (StringUtils.isNotBlank(identity.getSourceType())) {
            updates.add(Updates.set(NhiIdentity.SOURCE_TYPE, identity.getSourceType()));
        }
        if (StringUtils.isNotBlank(identity.getDeviceLabel())) {
            updates.add(Updates.set(NhiIdentity.DEVICE_LABEL, identity.getDeviceLabel()));
        }
        if (identity.getMetadata() != null && !identity.getMetadata().isEmpty()) {
            updates.add(Updates.set(NhiIdentity.METADATA, identity.getMetadata()));
        }

        if (rotated) {
            updates.add(Updates.set(NhiIdentity.PREVIOUS_HASH, existing.getHash()));
            updates.add(Updates.set(NhiIdentity.LAST_ROTATED_AT, now));
        } else if (existing == null) {
            updates.add(Updates.setOnInsert(NhiIdentity.LAST_ROTATED_AT, now));
        }

        // Owner: inferred from deviceLabel. Set on insert; backfill for older records
        // that were created before this field was populated (owner == null).
        if (StringUtils.isNotBlank(identity.getDeviceLabel())) {
            if (existing == null) {
                com.akto.dto.nhi_governance.NhiIdentity.Owner o = new com.akto.dto.nhi_governance.NhiIdentity.Owner();
                o.setName(identity.getDeviceLabel());
                updates.add(Updates.setOnInsert(NhiIdentity.OWNER, o));
            } else if (existing.getOwner() == null) {
                com.akto.dto.nhi_governance.NhiIdentity.Owner o = new com.akto.dto.nhi_governance.NhiIdentity.Owner();
                o.setName(identity.getDeviceLabel());
                updates.add(Updates.set(NhiIdentity.OWNER, o));
            }
        }

        // Always mirror the scanner's expiryDate: positive means the scanner extracted
        // a real exp claim (JWT); 0 means opaque token with no known expiry.
        // Using set (not setOnInsert) so stale fake values written by older code are cleared.
        updates.add(Updates.set(NhiIdentity.EXPIRY_DATE, identity.getExpiryDate()));

        NhiIdentityDao.instance.updateOne(filter, Updates.combine(updates));

        // Evaluate policies inline so violations appear immediately after each upsert.
        try {
            com.akto.dto.nhi_governance.NhiIdentity evaled =
                    NhiIdentityDao.instance.findOne(filter);
            if (evaled != null) {
                com.akto.utils.nhi_governance.NhiPolicyEvaluator.evaluate(evaled);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "upsertNhiIdentity: policy eval failed: " + e.getMessage());
        }
    }

    public static List<BasicDBObject> getAgentCorpus(String hostName) {
        return AgentGuardCorpusDao.instance.findBucketsByAgentHost(hostName);
    }
    
    public static void bulkSaveCorpusEntries(List<AgentGuardCorpusQueueEntry> corpusEntries) {
        AgentGuardCorpusQueueDao.instance.insertMany(corpusEntries);
    }

}
