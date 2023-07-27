package com.akto.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.akto.dto.type.CollectionReplaceDetails;

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

    public static final String AKTO_IGNORE_FLAG = "x-akto-ignore";

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

    private Map<String, CollectionReplaceDetails> apiCollectionNameMapper;
    public static final String API_COLLECTION_NAME_MAPPER = "apiCollectionNameMapper";
    public static final String GLOBAL_RATE_LIMIT = "globalRateLimit";
    private int globalRateLimit;
    public static final String ENABLE_TELEMETRY = "enableTelemetry";
    private boolean enableTelemetry;

    private Map<String, Integer> telemetryUpdateSentTsMap;
    public static final String TELEMETRY_UPDATE_SENT_TS_MAP = "telemetryUpdateSentTsMap";


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

    public enum SetupType {
        PROD, QA, STAGING, DEV
    }

    public Map<Pattern, String> convertApiCollectionNameMapperToRegex() {
        
        Map<Pattern, String> ret = new HashMap<>();

        if (apiCollectionNameMapper == null) return ret;
        
        for(CollectionReplaceDetails collectionReplaceDetails: apiCollectionNameMapper.values()) {
            try {

                ret.put(Pattern.compile(collectionReplaceDetails.getRegex()), collectionReplaceDetails.getNewName());
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

    public boolean isEnableTelemetry() {
        return enableTelemetry;
    }

    public void setEnableTelemetry(boolean enableTelemetry) {
        this.enableTelemetry = enableTelemetry;
    }

    public Map<String, Integer> getTelemetryUpdateSentTsMap() {
        return telemetryUpdateSentTsMap;
    }

    public void setTelemetryUpdateSentTsMap(Map<String, Integer> telemetryUpdateSentTsMap) {
        this.telemetryUpdateSentTsMap = telemetryUpdateSentTsMap;
    }
}
