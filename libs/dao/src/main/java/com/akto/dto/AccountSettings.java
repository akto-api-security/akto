package com.akto.dto;

import com.akto.dto.settings.DefaultPayload;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.akto.dto.type.CollectionReplaceDetails;

import com.akto.util.ConnectionInfo;
import com.akto.util.LastCronRunInfo;

import lombok.Getter;
import lombok.Setter;

import com.akto.dto.test_editor.TestLibrary;

public class AccountSettings {
    private int id;
    public static final String PRIVATE_CIDR_LIST = "privateCidrList";
    private List<String> privateCidrList;
    public static final String REDACT_PAYLOAD = "redactPayload";
    private boolean redactPayload;
    public static final String SAMPLE_DATA_COLLECTION_DROPPED = "sampleDataCollectionDropped";
    private boolean sampleDataCollectionDropped;
    public static final String DASHBOARD_VERSION = "dashboardVersion";
    private String dashboardVersion;
    public static final String API_RUNTIME_VERSION = "apiRuntimeVersion";
    private String apiRuntimeVersion;
    public static final String SETUP_TYPE = "setupType";
    private SetupType setupType = SetupType.PROD;

    public static final String CENTRAL_KAFKA_IP = "centralKafkaIp";
    private String centralKafkaIp;

    public static final String MERGE_ASYNC_OUTSIDE = "mergeAsyncOutside";
    private boolean mergeAsyncOutside;

    private int demoCollectionCreateTime = 0;
    public static final String DEMO_COLLECTION_CREATE_TIME = "demoCollectionCreateTime";

    private boolean showOnboarding;
    public static final String SHOW_ONBOARDING = "showOnboarding";

    private boolean urlRegexMatchingEnabled;

    public static final String URL_REGEX_MATCHING_ENABLED = "urlRegexMatchingEnabled";

    private String initStackType;

    private boolean enableDebugLogs;
    public static final String ENABLE_DEBUG_LOGS = "enableDebugLogs";

    public static final String INIT_STACK_TYPE = "initStackType";

    private Map<String, String> filterHeaderValueMap;
    public static final String FILTER_HEADER_VALUE_MAP = "filterHeaderValueMap";
    public static final String RUNTIME_ENV_OVERRIDES = "runtimeEnvOverrides";
    @Getter
    @Setter
    private Map<String, String> runtimeEnvOverrides;
    public static final String DELTA_IGNORE_TIME_FOR_SCHEDULED_SUMMARIES = "timeForScheduledSummaries";
    private int timeForScheduledSummaries;
    private Map<String, CollectionReplaceDetails> apiCollectionNameMapper;
    public static final String API_COLLECTION_NAME_MAPPER = "apiCollectionNameMapper";
    public static final String GLOBAL_RATE_LIMIT = "globalRateLimit";
    private int globalRateLimit;

    public static final String TEST_RATE_LIMIT_USAGE_DAY = "testRateLimitUsageDay";
    private int testRateLimitUsageDay;
    public static final String TEST_RATE_LIMIT_USAGE_COUNT = "testRateLimitUsageCount";
    private int testRateLimitUsageCount;

    public static final String GLOBAL_RATE_LIMIT_AGENTIC = "globalRateLimitAgentic";
    private int globalRateLimitAgentic;

    public static final String AGENTIC_TEST_RATE_LIMIT_USAGE_DAY = "agenticTestRateLimitUsageDay";
    private int agenticTestRateLimitUsageDay;
    public static final String AGENTIC_TEST_RATE_LIMIT_USAGE_COUNT = "agenticTestRateLimitUsageCount";
    private int agenticTestRateLimitUsageCount;

    public static final String ENABLE_TELEMETRY = "enableTelemetry";

    public static final String TELEMETRY_SETTINGS = "telemetrySettings";

    private TelemetrySettings telemetrySettings;

    private Map<String, Integer> telemetryUpdateSentTsMap;
    public static final String TELEMETRY_UPDATE_SENT_TS_MAP = "telemetryUpdateSentTsMap";


    public static final String GITHUB_APP_SECRET_KEY = "githubAppSecretKey";
    private String githubAppSecretKey;
    public static final String GITHUB_APP_ID = "githubAppId";
    private String githubAppId;
    private int trafficAlertThresholdSeconds = defaultTrafficAlertThresholdSeconds;
    public static final String TRAFFIC_ALERT_THRESHOLD_SECONDS = "trafficAlertThresholdSeconds";
    public static final int defaultTrafficAlertThresholdSeconds = 60*60*4;

    public static final String DEFAULT_PAYLOADS = "defaultPayloads";
    private Map<String, DefaultPayload> defaultPayloads;

    public static final String LAST_UPDATED_CRON_INFO = "lastUpdatedCronInfo";
    private LastCronRunInfo lastUpdatedCronInfo;

    public static final String CONNECTION_INTEGRATIONS_INFO = "connectionIntegrationsInfo";
    private Map<String,ConnectionInfo> connectionIntegrationsInfo = new HashMap<>();

    public static final String TEST_LIBRARIES = "testLibraries";
    private List<TestLibrary> testLibraries;

    public static final String PARTNER_IP_LIST = "partnerIpList";
    private List<String> partnerIpList;

    public static final String ALLOW_REDUNDANT_ENDPOINTS_LIST = "allowRedundantEndpointsList";
    private List<String> allowRedundantEndpointsList;

    public static final String ALLOW_MERGING_ON_VERSIONS = "allowMergingOnVersions";
    @Getter
    @Setter
    private boolean allowMergingOnVersions;

    @Getter
    @Setter
    private boolean blockLogs;
    public static final String BLOCK_LOGS = "blockLogs";

    @Getter
    @Setter
    private List<String> filterLogPolicy;
    public static final String FILTER_LOG_POLICY = "filterLogPolicy";

    // Used by mini-runtime to send to threat topic.
    public static final String THREAT_KAFKA_PARTITION_KEY = "threatKafkaPartitionKey";

    /*
     * External detection corrector. Local detection can tell that a value looks like an email or a
     * card; it cannot tell whose it is, because that lives in a system outside Akto. When enabled,
     * values whose locally detected type is listed in detectionCorrectorTriggerTypes are sent to
     * detectionCorrectorUrl, which may return a more specific data type for the ones it recognises.
     *
     * The auth token is deliberately not stored here. It is read from the AKTO_DETECTION_CORRECTOR_TOKEN
     * environment variable on the runtime pod so the secret never travels with account settings.
     */
    public static final String DETECTION_CORRECTOR_ENABLED = "detectionCorrectorEnabled";
    private boolean detectionCorrectorEnabled;

    public static final String DETECTION_CORRECTOR_URL = "detectionCorrectorUrl";
    private String detectionCorrectorUrl;

    public static final String DETECTION_CORRECTOR_TRIGGER_TYPES = "detectionCorrectorTriggerTypes";
    private List<String> detectionCorrectorTriggerTypes;

    public static final String DETECTION_CORRECTOR_TIMEOUT_MS = "detectionCorrectorTimeoutMs";
    private int detectionCorrectorTimeoutMs;

    public static final String DETECTION_CORRECTOR_MAX_BATCH_SIZE = "detectionCorrectorMaxBatchSize";
    private int detectionCorrectorMaxBatchSize;

    public static final String DETECTION_CORRECTOR_AUTH_TOKEN = "detectionCorrectorAuthToken";
    private String detectionCorrectorAuthToken;

    /*
     * Akto data type name -> the name the classifier expects on the wire, e.g. CREDIT_CARD -> CARD.
     * Detection type vocabularies differ between systems, and a name the classifier does not
     * recognise is silently ignored: it answers 200 with an empty corrections list, exactly as it
     * would for "I looked and found nothing". Without a mapping that failure is invisible.
     */
    public static final String DETECTION_CORRECTOR_TYPE_ALIASES = "detectionCorrectorTypeAliases";
    private Map<String, String> detectionCorrectorTypeAliases;

    /* Per-value logging, including raw values. Off unless deliberately switched on. */
    public static final String DETECTION_CORRECTOR_DEBUG = "detectionCorrectorDebug";
    private boolean detectionCorrectorDebug;

    public String getDetectionCorrectorAuthToken() {
        return detectionCorrectorAuthToken;
    }

    public void setDetectionCorrectorAuthToken(String detectionCorrectorAuthToken) {
        this.detectionCorrectorAuthToken = detectionCorrectorAuthToken;
    }

    public Map<String, String> getDetectionCorrectorTypeAliases() {
        return detectionCorrectorTypeAliases;
    }

    public void setDetectionCorrectorTypeAliases(Map<String, String> detectionCorrectorTypeAliases) {
        this.detectionCorrectorTypeAliases = detectionCorrectorTypeAliases;
    }

    public boolean isDetectionCorrectorDebug() {
        return detectionCorrectorDebug;
    }

    public void setDetectionCorrectorDebug(boolean detectionCorrectorDebug) {
        this.detectionCorrectorDebug = detectionCorrectorDebug;
    }

    public boolean isDetectionCorrectorEnabled() {
        return detectionCorrectorEnabled;
    }

    public void setDetectionCorrectorEnabled(boolean detectionCorrectorEnabled) {
        this.detectionCorrectorEnabled = detectionCorrectorEnabled;
    }

    public String getDetectionCorrectorUrl() {
        return detectionCorrectorUrl;
    }

    public void setDetectionCorrectorUrl(String detectionCorrectorUrl) {
        this.detectionCorrectorUrl = detectionCorrectorUrl;
    }

    public List<String> getDetectionCorrectorTriggerTypes() {
        return detectionCorrectorTriggerTypes;
    }

    public void setDetectionCorrectorTriggerTypes(List<String> detectionCorrectorTriggerTypes) {
        this.detectionCorrectorTriggerTypes = detectionCorrectorTriggerTypes;
    }

    public int getDetectionCorrectorTimeoutMs() {
        return detectionCorrectorTimeoutMs;
    }

    public void setDetectionCorrectorTimeoutMs(int detectionCorrectorTimeoutMs) {
        this.detectionCorrectorTimeoutMs = detectionCorrectorTimeoutMs;
    }

    public int getDetectionCorrectorMaxBatchSize() {
        return detectionCorrectorMaxBatchSize;
    }

    public void setDetectionCorrectorMaxBatchSize(int detectionCorrectorMaxBatchSize) {
        this.detectionCorrectorMaxBatchSize = detectionCorrectorMaxBatchSize;
    }
    private ThreatKafkaPartitionKey threatKafkaPartitionKey;

    public enum ThreatKafkaPartitionKey {
        IP
    }

    public AccountSettings() {
    }

    public AccountSettings(int id, List<String> privateCidrList, Boolean redactPayload, SetupType setupType) {
        this.id = id;
        this.privateCidrList = privateCidrList;
        this.redactPayload = redactPayload;
        this.setupType = setupType;
    }

    public int getGlobalRateLimit() {
        return globalRateLimit;
    }

    public void setGlobalRateLimit(int globalRateLimit) {
        this.globalRateLimit = globalRateLimit;
    }

    public int getTestRateLimitUsageDay() {
        return testRateLimitUsageDay;
    }

    public void setTestRateLimitUsageDay(int testRateLimitUsageDay) {
        this.testRateLimitUsageDay = testRateLimitUsageDay;
    }

    public int getTestRateLimitUsageCount() {
        return testRateLimitUsageCount;
    }

    public void setTestRateLimitUsageCount(int testRateLimitUsageCount) {
        this.testRateLimitUsageCount = testRateLimitUsageCount;
    }

    public int getGlobalRateLimitAgentic() {
        return globalRateLimitAgentic;
    }

    public void setGlobalRateLimitAgentic(int globalRateLimitAgentic) {
        this.globalRateLimitAgentic = globalRateLimitAgentic;
    }

    public int getAgenticTestRateLimitUsageDay() {
        return agenticTestRateLimitUsageDay;
    }

    public void setAgenticTestRateLimitUsageDay(int agenticTestRateLimitUsageDay) {
        this.agenticTestRateLimitUsageDay = agenticTestRateLimitUsageDay;
    }

    public int getAgenticTestRateLimitUsageCount() {
        return agenticTestRateLimitUsageCount;
    }

    public void setAgenticTestRateLimitUsageCount(int agenticTestRateLimitUsageCount) {
        this.agenticTestRateLimitUsageCount = agenticTestRateLimitUsageCount;
    }

    public String getGithubAppSecretKey() {
        return githubAppSecretKey;
    }

    public void setGithubAppSecretKey(String githubAppSecretKey) {
        this.githubAppSecretKey = githubAppSecretKey;
    }

    public String getGithubAppId() {
        return githubAppId;
    }

    public void setGithubAppId(String githubAppId) {
        this.githubAppId = githubAppId;
    }

    public int getTimeForScheduledSummaries() {
        return timeForScheduledSummaries;
    }

    public void setTimeForScheduledSummaries(int timeForScheduledSummaries) {
        this.timeForScheduledSummaries = timeForScheduledSummaries;
    }

    public enum SetupType {
        PROD, QA, STAGING, DEV
    }

    public ThreatKafkaPartitionKey getThreatKafkaPartitionKey() {
        return threatKafkaPartitionKey;
    }

    public void setThreatKafkaPartitionKey(ThreatKafkaPartitionKey threatKafkaPartitionKey) {
        this.threatKafkaPartitionKey = threatKafkaPartitionKey;
    }

    public Map<String, Map<Pattern, String>> convertApiCollectionNameMapperToRegex() {
        
         Map<String, Map<Pattern, String>> ret = new HashMap<>();

        if (apiCollectionNameMapper == null) return ret;
        
        for(CollectionReplaceDetails collectionReplaceDetails: apiCollectionNameMapper.values()) {
            try {
                String headerName = collectionReplaceDetails.getHeaderName();
                if (StringUtils.isEmpty(headerName)) {
                    headerName = "host";
                }
                headerName = headerName.toLowerCase();

                Map<Pattern, String> regexMapperForGivenHeader = ret.get(headerName);
                if (regexMapperForGivenHeader == null) {
                    regexMapperForGivenHeader = new HashMap<>();
                    ret.put(headerName, regexMapperForGivenHeader);
                }

                regexMapperForGivenHeader.put(Pattern.compile(collectionReplaceDetails.getRegex()), collectionReplaceDetails.getNewName());
            } catch (Exception e) {
                // eat it
            }
        }
        return ret;
        
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<String> getPrivateCidrList() {
        return privateCidrList;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }

    public boolean isRedactPayload() {
        return redactPayload;
    }

    public boolean getRedactPayload() {
        return redactPayload;
    }

    public void setRedactPayload(boolean redactPayload) {
        this.redactPayload = redactPayload;
    }

    public boolean isSampleDataCollectionDropped() {
        return sampleDataCollectionDropped;
    }

    public void setSampleDataCollectionDropped(boolean sampleDataCollectionDropped) {
        this.sampleDataCollectionDropped = sampleDataCollectionDropped;
    }

    public String getDashboardVersion() {
        return dashboardVersion;
    }

    public void setDashboardVersion(String dashboardVersion) {
        this.dashboardVersion = dashboardVersion;
    }

    public String getApiRuntimeVersion() {
        return apiRuntimeVersion;
    }

    public void setApiRuntimeVersion(String apiRuntimeVersion) {
        this.apiRuntimeVersion = apiRuntimeVersion;
    }
    
    public SetupType getSetupType() {
        return setupType;
    }

    public void setSetupType(SetupType setupType) {
        this.setupType = setupType;
    }

    public String getCentralKafkaIp() {
        return centralKafkaIp;
    }

    public void setCentralKafkaIp(String centralKafkaIp) {
        this.centralKafkaIp = centralKafkaIp;
    }

    public boolean getMergeAsyncOutside() {
        return this.mergeAsyncOutside;
    }

    public void setMergeAsyncOutside(boolean mergeAsyncOutside) {
        this.mergeAsyncOutside = mergeAsyncOutside;
    }

    public static final int DEFAULT_CENTRAL_KAFKA_BATCH_SIZE = 999900;
    public static final int DEFAULT_CENTRAL_KAFKA_LINGER_MS = 60_000;

    public static final int DEFAULT_LOCAL_KAFKA_LINGER_MS = 10_000;

    public static final int DEFAULT_CENTRAL_KAFKA_MAX_POLL_RECORDS_CONFIG = 1_000;
    public static final String DEFAULT_CENTRAL_KAFKA_TOPIC_NAME = "akto.central";

    public int getDemoCollectionCreateTime() {
        return demoCollectionCreateTime;
    }

    public void setDemoCollectionCreateTime(int demoCollectionCreateTime) {
        this.demoCollectionCreateTime = demoCollectionCreateTime;
    }

    public boolean isShowOnboarding() {
        return showOnboarding;
    }

    public void setShowOnboarding(boolean showOnboarding) {
        this.showOnboarding = showOnboarding;
    }

    public boolean getUrlRegexMatchingEnabled() {
        return urlRegexMatchingEnabled;
    }

    public void setUrlRegexMatchingEnabled(boolean urlRegexMatchingEnabled) {
        this.urlRegexMatchingEnabled = urlRegexMatchingEnabled;
    }

    public String getInitStackType() {
        return initStackType;
    }

    public void setInitStackType(String initStackType) {
        this.initStackType = initStackType;
    }

    public boolean isEnableDebugLogs() {
        return enableDebugLogs;
    }

    public void setEnableDebugLogs(boolean enableDebugLogs) {
        this.enableDebugLogs = enableDebugLogs;
    }

    public Map<String, String> getFilterHeaderValueMap() {
        return filterHeaderValueMap;
    }

    public void setFilterHeaderValueMap(Map<String, String> filterHeaderValueMap) {
        this.filterHeaderValueMap = filterHeaderValueMap;
    }

    public Map<String,CollectionReplaceDetails> getApiCollectionNameMapper() {
        return this.apiCollectionNameMapper;
    }

    public void setApiCollectionNameMapper(Map<String,CollectionReplaceDetails> apiCollectionNameMapper) {
        this.apiCollectionNameMapper = apiCollectionNameMapper;
    }
    public int getTrafficAlertThresholdSeconds() {
        return trafficAlertThresholdSeconds;
    }

    public void setTrafficAlertThresholdSeconds(int trafficAlertThresholdSeconds) {
        this.trafficAlertThresholdSeconds = trafficAlertThresholdSeconds;
    }

    public Map<String, Integer> getTelemetryUpdateSentTsMap() {
        return telemetryUpdateSentTsMap;
    }

    public void setTelemetryUpdateSentTsMap(Map<String, Integer> telemetryUpdateSentTsMap) {
        this.telemetryUpdateSentTsMap = telemetryUpdateSentTsMap;
    }
    public Map<String, DefaultPayload> getDefaultPayloads() {
        return defaultPayloads;
    }

    public void setDefaultPayloads(Map<String, DefaultPayload> defaultPayloads) {
        this.defaultPayloads = defaultPayloads;
    }
  
    public List<TestLibrary> getTestLibraries() {
        return testLibraries;
    }

    public void setTestLibraries(List<TestLibrary> testLibraries) {
        this.testLibraries = testLibraries;
    }

    public LastCronRunInfo getLastUpdatedCronInfo() {
        return lastUpdatedCronInfo;
    }

    public void setLastUpdatedCronInfo(LastCronRunInfo lastUpdatedCronInfo) {
        this.lastUpdatedCronInfo = lastUpdatedCronInfo;
    }

    public Map<String, ConnectionInfo> getConnectionIntegrationsInfo() {
        return connectionIntegrationsInfo;
    }

    public void setConnectionIntegrationsInfo(Map<String, ConnectionInfo> connectionIntegrationsInfo) {
        this.connectionIntegrationsInfo = connectionIntegrationsInfo;
    }

    public TelemetrySettings getTelemetrySettings() {
        return telemetrySettings;
    }

    public void setTelemetrySettings(TelemetrySettings telemetrySettings) {
        this.telemetrySettings = telemetrySettings;
    }
    
    public List<String> getPartnerIpList() {
		return partnerIpList;
	}

	public void setPartnerIpList(List<String> partnerIpList) {
		this.partnerIpList = partnerIpList;
	}

    public List<String> getAllowRedundantEndpointsList() {
        if(this.allowRedundantEndpointsList == null) {
            List<String> ignoreUrlTypesList = Arrays.asList(
                "htm","html", "css", "js",   // Web formats
                "jpg", "jpeg", "png", "gif", "svg", "webp",  // Image formats
                "mp4", "webm", "ogg", "ogv", "avi", "mov",  // Video formats
                "mp3", "wav", "oga",  // Audio formats
                "woff", "woff2", "ttf", "otf", // Font formats
                ".pptx", ".json" // file formats
            );
            return ignoreUrlTypesList;
        }
        return allowRedundantEndpointsList;
    }

    public void setAllowRedundantEndpointsList(List<String> allowRedundantEndpointsList) {
        this.allowRedundantEndpointsList = allowRedundantEndpointsList;
    }
}
