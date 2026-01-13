package com.akto.action;

import com.akto.ProtoMessageUtils;
import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.AdxIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.adx_integration.AdxIntegration;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.FormBody;
import com.akto.util.http_util.CoreHTTPClient;
import org.bson.conversions.Bson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.akto.action.threat_detection.utils.ThreatsUtils.getTemplates;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import org.apache.http.HttpHeaders;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.testing.ApiExecutor;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

@Getter
@Setter
public class AdxIntegrationAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker logger = new LoggerMaker(AdxIntegrationAction.class, LogDb.DASHBOARD);
    private static final int MAX_EXPORT_LIMIT = 10000;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Status tracking for async exports (keyed by account ID)
    private static final Map<Integer, ExportStatus> exportStatusMap = new ConcurrentHashMap<>();
    private static final ExecutorService exportExecutor = Executors.newCachedThreadPool();
    
    /**
     * Status class for tracking export progress
     */
    private static class ExportStatus {
        private volatile String status; // PROCESSING, SUCCESS, FAILED
        private volatile String message;
        private volatile int exportedCount;
        
        ExportStatus(String status, String message, int exportedCount) {
            this.status = status;
            this.message = message;
            this.exportedCount = exportedCount;
        }
        
        String getStatus() { return status; }
        String getMessage() { return message; }
        int getExportedCount() { return exportedCount; }
        
        void setStatus(String status) { this.status = status; }
        void setMessage(String message) { this.message = message; }
        void setExportedCount(int exportedCount) { this.exportedCount = exportedCount; }
    }
    
    /**
     * GuardrailActivity table schema for Azure Data Explorer
     * Table name and column definitions
     */
    public static class GuardrailActivityTable {
        public static final String TABLE_NAME = "GuardrailActivity";
        
        // Column names matching the transformEventsToAdxFormat structure
        public static final String COL_ID = "id";
        public static final String COL_FILTER_ID = "filterId";
        public static final String COL_ACTOR = "actor";
        public static final String COL_LATEST_API_IP = "latestApiIp";
        public static final String COL_LATEST_API_ENDPOINT = "latestApiEndpoint";
        public static final String COL_COUNTRY = "country";
        public static final String COL_LATEST_API_METHOD = "latestApiMethod";
        public static final String COL_LATEST_API_ORIG = "latestApiOrig";
        public static final String COL_DETECTED_AT = "detectedAt";
        public static final String COL_LATEST_API_COLLECTION_ID = "latestApiCollectionId";
        public static final String COL_EVENT_TYPE = "eventType";
        public static final String COL_CATEGORY = "category";
        public static final String COL_SUB_CATEGORY = "subCategory";
        public static final String COL_TYPE = "type";
        public static final String COL_REF_ID = "refId";
        public static final String COL_SEVERITY = "severity";
        public static final String COL_METADATA = "metadata";
        public static final String COL_SUCCESSFUL_EXPLOIT = "successfulExploit";
        public static final String COL_STATUS = "status";
        public static final String COL_LABEL = "label";
        public static final String COL_HOST = "host";
        public static final String COL_JIRA_TICKET_URL = "jiraTicketUrl";
        
        /**
         * KQL table creation command with schema definition
         */
        public static final String CREATE_TABLE_COMMAND = String.format(
            ".create table %s (%s: string, %s: string, %s: string, %s: string, %s: string, %s: string, %s: string, %s: string, %s: datetime, %s: int, %s: string, %s: string, %s: string, %s: string, %s: string, %s: string, %s: dynamic, %s: bool, %s: string, %s: string, %s: string, %s: string)",
            TABLE_NAME,
            COL_ID, COL_FILTER_ID, COL_ACTOR, COL_LATEST_API_IP, COL_LATEST_API_ENDPOINT,
            COL_COUNTRY, COL_LATEST_API_METHOD, COL_LATEST_API_ORIG, COL_DETECTED_AT,
            COL_LATEST_API_COLLECTION_ID, COL_EVENT_TYPE, COL_CATEGORY, COL_SUB_CATEGORY,
            COL_TYPE, COL_REF_ID, COL_SEVERITY, COL_METADATA, COL_SUCCESSFUL_EXPLOIT,
            COL_STATUS, COL_LABEL, COL_HOST, COL_JIRA_TICKET_URL
        );
    }
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();

    // ADX configuration fields (for integration setup)
    private String clusterEndpoint;
    private String databaseName;
    private String applicationClientId;
    private String applicationKey;
    private String tenantId;
    
    // ADX integration object (for fetch response)
    private AdxIntegration adxIntegration;

    // Export fields
    private List<String> ips;
    private List<Integer> apiCollectionIds;
    private List<String> urls;
    private List<String> types;
    private List<String> latestAttack;
    private Boolean successfulExploit;
    private String label;
    private List<String> hosts;
    private String latestApiOrigRegex;
    private String statusFilter;
    private int startTimestamp;
    private int endTimestamp;
    private Boolean exportSuccess;
    private String exportMessage;
    private int exportedCount;

    public AdxIntegrationAction() {
        super();
    }

    /**
     * Fetch stored ADX integration configuration
     * Excludes sensitive application key from response
     */
    public String fetchAdxIntegration() {
        adxIntegration = AdxIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(AdxIntegration.APPLICATION_KEY)
        );
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Add or update ADX integration configuration
     */
    public String addAdxIntegration() {
        if (clusterEndpoint == null || clusterEndpoint.isEmpty()) {
            addActionError("Please enter a valid cluster endpoint.");
            return Action.ERROR.toUpperCase();
        }

        if (databaseName == null || databaseName.isEmpty()) {
            addActionError("Please enter a valid database name.");
            return Action.ERROR.toUpperCase();
        }

        if (tenantId == null || tenantId.isEmpty()) {
            addActionError("Please enter a valid tenant ID.");
            return Action.ERROR.toUpperCase();
        }

        if (applicationClientId == null || applicationClientId.isEmpty()) {
            addActionError("Please enter a valid application client ID.");
            return Action.ERROR.toUpperCase();
        }

        // Normalize cluster endpoint (remove trailing slash if present)
        if (clusterEndpoint.endsWith("/")) {
            clusterEndpoint = clusterEndpoint.substring(0, clusterEndpoint.length() - 1);
        }

        // Prepare updates
        Bson combineUpdates = Updates.combine(
            Updates.set(AdxIntegration.CLUSTER_ENDPOINT, clusterEndpoint),
            Updates.set(AdxIntegration.DATABASE_NAME, databaseName),
            Updates.set(AdxIntegration.TENANT_ID, tenantId),
            Updates.set(AdxIntegration.APPLICATION_CLIENT_ID, applicationClientId),
            Updates.setOnInsert(AdxIntegration.CREATED_TS, Context.now()),
            Updates.set(AdxIntegration.UPDATED_TS, Context.now())
        );

        // Handle application key
        if (applicationKey != null && !applicationKey.isEmpty()) {
            Bson applicationKeyUpdate = Updates.set(AdxIntegration.APPLICATION_KEY, applicationKey);
            combineUpdates = Updates.combine(combineUpdates, applicationKeyUpdate);
        } else {
            // If key is not provided, check if it exists in DB
            AdxIntegration existingIntegration = AdxIntegrationDao.instance.findOne(new BasicDBObject());
            if (existingIntegration == null || existingIntegration.getApplicationKey() == null || existingIntegration.getApplicationKey().isEmpty()) {
                addActionError("Please enter a valid application key (client secret).");
                return Action.ERROR.toUpperCase();
            }
        }

        // Save/update integration
        AdxIntegrationDao.instance.updateOne(
            new BasicDBObject(),
            combineUpdates
        );

        logger.infoAndAddToDb("ADX integration saved successfully for cluster: " + clusterEndpoint, LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Remove ADX integration configuration
     */
    public String removeAdxIntegration() {
        AdxIntegrationDao.instance.deleteAll(new BasicDBObject());
        logger.infoAndAddToDb("ADX integration removed successfully", LogDb.DASHBOARD);
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Export guardrail activity data to Azure Data Explorer
     * Uses stored ADX configuration from database
     * Starts export in background thread and returns immediately
     */
    public String exportGuardrailActivityToAdx() {
        Integer accountId = Context.accountId.get();
        if (accountId == null || accountId == 0) {
            addActionError("Account ID not found in context.");
            exportSuccess = false;
            exportMessage = "Account ID not found.";
            exportedCount = 0;
            return Action.ERROR.toUpperCase();
        }
        
        // Fetch stored ADX integration configuration
        AdxIntegration integration = AdxIntegrationDao.instance.findOne(new BasicDBObject());
        
        if (integration == null) {
            addActionError("ADX integration is not configured. Please configure it in Settings → Integrations.");
            exportSuccess = false;
            exportMessage = "ADX integration not configured.";
            exportedCount = 0;
            return Action.ERROR.toUpperCase();
        }

        // Validate stored configuration
        if (integration.getClusterEndpoint() == null || integration.getClusterEndpoint().isEmpty() ||
            integration.getDatabaseName() == null || integration.getDatabaseName().isEmpty() ||
            integration.getTenantId() == null || integration.getTenantId().isEmpty() ||
            integration.getApplicationClientId() == null || integration.getApplicationClientId().isEmpty() ||
            integration.getApplicationKey() == null || integration.getApplicationKey().isEmpty()) {
            addActionError("ADX integration configuration is incomplete. Please update it in Settings → Integrations.");
            exportSuccess = false;
            exportMessage = "ADX integration configuration is incomplete.";
            exportedCount = 0;
            return Action.ERROR.toUpperCase();
        }

        // Initialize status as PROCESSING
        exportStatusMap.put(accountId, new ExportStatus("PROCESSING", "Export under processing", 0));
        
        // Capture current request parameters for background thread
        final AdxIntegration finalIntegration = integration;
        final Integer finalAccountId = accountId;
        final List<String> finalIps = this.ips;
        final List<Integer> finalApiCollectionIds = this.apiCollectionIds;
        final List<String> finalUrls = this.urls;
        final List<String> finalTypes = this.types;
        final List<String> finalLatestAttack = this.latestAttack;
        final Boolean finalSuccessfulExploit = this.successfulExploit;
        final String finalLabel = this.label;
        final List<String> finalHosts = this.hosts;
        final String finalLatestApiOrigRegex = this.latestApiOrigRegex;
        final String finalStatusFilter = this.statusFilter;
        final int finalStartTimestamp = this.startTimestamp;
        final int finalEndTimestamp = this.endTimestamp;
        final CONTEXT_SOURCE finalContextSource = Context.contextSource.get();

        // Start export in background thread
        exportExecutor.submit(() -> {
            try {
                executeExportInBackground(
                    finalAccountId,
                    finalIntegration,
                    finalIps,
                    finalApiCollectionIds,
                    finalUrls,
                    finalTypes,
                    finalLatestAttack,
                    finalSuccessfulExploit,
                    finalLabel,
                    finalHosts,
                    finalLatestApiOrigRegex,
                    finalStatusFilter,
                    finalStartTimestamp,
                    finalEndTimestamp,
                    finalContextSource
                );
            } catch (Exception e) {
                logger.errorAndAddToDb("Error in background export thread: " + e.getMessage(), LogDb.DASHBOARD);
                ExportStatus status = exportStatusMap.get(finalAccountId);
                if (status != null) {
                    status.setStatus("FAILED");
                    status.setMessage("Error exporting data to ADX: " + e.getMessage());
                    status.setExportedCount(0);
                }
            }
        });

        // Return immediately with processing status
        exportSuccess = true;
        exportMessage = "Export under processing";
        exportedCount = 0;
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Execute export in background thread
     */
    private void executeExportInBackground(
            Integer accountId,
            AdxIntegration integration,
            List<String> ips,
            List<Integer> apiCollectionIds,
            List<String> urls,
            List<String> types,
            List<String> latestAttack,
            Boolean successfulExploit,
            String label,
            List<String> hosts,
            String latestApiOrigRegex,
            String statusFilter,
            int startTimestamp,
            int endTimestamp,
             CONTEXT_SOURCE contextSource) {
        
        try {
            // Get application key
            String applicationKey = integration.getApplicationKey();

            // Set context for background thread
            Context.accountId.set(accountId);
            Context.contextSource.set(contextSource);

            // Fetch malicious events from threat detection backend
            // Create a temporary action instance to use the fetch method with captured parameters
            AdxIntegrationAction tempAction = new AdxIntegrationAction();
            tempAction.setIps(ips);
            tempAction.setApiCollectionIds(apiCollectionIds);
            tempAction.setUrls(urls);
            tempAction.setTypes(types);
            tempAction.setLatestAttack(latestAttack);
            tempAction.setSuccessfulExploit(successfulExploit);
            tempAction.setLabel(label);
            tempAction.setHosts(hosts);
            tempAction.setLatestApiOrigRegex(latestApiOrigRegex);
            tempAction.setStatusFilter(statusFilter);
            tempAction.setStartTimestamp(startTimestamp);
            tempAction.setEndTimestamp(endTimestamp);
            
            List<DashboardMaliciousEvent> events = tempAction.fetchMaliciousEventsForExport();
            
            if (events == null || events.isEmpty()) {
                ExportStatus status = exportStatusMap.get(accountId);
                if (status != null) {
                    status.setStatus("SUCCESS");
                    status.setMessage("No guardrail activity data found to export.");
                    status.setExportedCount(0);
                }
                logger.infoAndAddToDb("No guardrail activity data found for export to ADX");
                return;
            }

            // Transform events to ADX format
            List<Map<String, Object>> adxRecords = tempAction.transformEventsToAdxFormat(events);

            // Export to ADX using stored configuration
            logger.infoAndAddToDb(
                String.format("Exporting %d guardrail activity records to ADX (cluster: %s, database: %s, table: %s)",
                    adxRecords.size(),
                    integration.getClusterEndpoint(),
                    integration.getDatabaseName(),
                    GuardrailActivityTable.TABLE_NAME),
                LogDb.DASHBOARD
            );

            // Get Azure AD access token once for all operations
            String accessToken = getAzureAdAccessToken(
                integration.getTenantId(),
                integration.getApplicationClientId(),
                applicationKey
            );

            // Check if table exists, create if it doesn't
            ensureTableExists(
                integration.getClusterEndpoint(),
                integration.getDatabaseName(),
                GuardrailActivityTable.TABLE_NAME,
                accessToken
            );

            // Ingest data to ADX using stored credentials in batches of 10
            ingestToAdx(
                integration.getClusterEndpoint(),
                integration.getDatabaseName(),
                GuardrailActivityTable.TABLE_NAME,
                accessToken,
                adxRecords
            );

            // Update status to success
            ExportStatus status = exportStatusMap.get(accountId);
            if (status != null) {
                status.setStatus("SUCCESS");
                status.setMessage("Exported successfully");
                status.setExportedCount(adxRecords.size());
            }

            logger.infoAndAddToDb(
                String.format("Successfully exported %d guardrail activity records to ADX", adxRecords.size()),
                LogDb.DASHBOARD
            );

        } catch (Exception e) {
            logger.errorAndAddToDb("Error exporting guardrail activity to ADX: " + e.getMessage(), LogDb.DASHBOARD);
            e.printStackTrace();
            
            // Update status to failed
            ExportStatus status = exportStatusMap.get(accountId);
            if (status != null) {
                status.setStatus("FAILED");
                status.setMessage("Error exporting data to ADX: " + e.getMessage());
                status.setExportedCount(0);
            }
        }
    }

    /**
     * Fetch export status for polling
     */
    public String fetchExportStatus() {
        Integer accountId = Context.accountId.get();
        if (accountId == null || accountId == 0) {
            addActionError("Account ID not found in context.");
            exportSuccess = false;
            exportMessage = "Account ID not found.";
            exportedCount = 0;
            return Action.ERROR.toUpperCase();
        }
        
        ExportStatus status = exportStatusMap.get(accountId);
        if (status == null) {
            exportSuccess = false;
            exportMessage = "No export in progress.";
            exportedCount = 0;
            return Action.SUCCESS.toUpperCase();
        }
        
        exportSuccess = "SUCCESS".equals(status.getStatus()) || "PROCESSING".equals(status.getStatus());
        exportMessage = status.getMessage();
        exportedCount = status.getExportedCount();
        
        // Remove status if completed (success or failed) to allow retry
        if ("SUCCESS".equals(status.getStatus()) || "FAILED".equals(status.getStatus())) {
            exportStatusMap.remove(accountId);
        }
        
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Fetch malicious events from threat detection backend
     */
    private List<DashboardMaliciousEvent> fetchMaliciousEventsForExport() throws Exception {
        String url = String.format("%s/api/dashboard/list_malicious_requests", this.getBackendUrl());
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        Map<String, Object> filter = new HashMap<>();
        if (ips != null && !ips.isEmpty()) {
            filter.put("ips", ips);
        }
        if (urls != null && !urls.isEmpty()) {
            filter.put("urls", urls);
        }
        if (types != null && !types.isEmpty()) {
            filter.put("types", types);
        }
        if (successfulExploit != null) {
            filter.put("successfulExploit", successfulExploit);
        }
        if (label != null && !label.isEmpty()) {
            filter.put("label", label);
        } else {
            // Default to GUARDRAIL label for guardrail activity export
            filter.put("label", "GUARDRAIL");
        }
        if (hosts != null && !hosts.isEmpty()) {
            filter.put("hosts", hosts);
        }
        if (latestApiOrigRegex != null && !latestApiOrigRegex.isEmpty()) {
            filter.put("latestApiOrigRegex", latestApiOrigRegex);
        }

        if (latestAttack != null && !latestAttack.isEmpty()) {
            filter.put("latestAttack", latestAttack);
        }

        if (statusFilter != null && !statusFilter.isEmpty()) {
            filter.put("statusFilter", statusFilter);
        }

        Map<String, Integer> time_range = new HashMap<>();
        if (startTimestamp > 0) {
            time_range.put("start", startTimestamp);
        }
        if (endTimestamp > 0) {
            time_range.put("end", endTimestamp);
        }
        filter.put("detected_at_time_range", time_range);

        Map<String, Object> body = new HashMap<String, Object>() {
            {
                put("skip", 0);
                put("limit", MAX_EXPORT_LIMIT);
                put("sort", new HashMap<String, Integer>() {{ put("detectedAt", -1); }});
                put("filter", filter);
            }
        };

        String msg = objectMapper.valueToTree(body).toString();
        RequestBody requestBody = RequestBody.create(msg, JSON);
        Request request = new Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer " + this.getApiToken())
            .addHeader("Content-Type", "application/json")
            .addHeader("x-context-source", Context.contextSource.get().toString())
            .build();

        List<DashboardMaliciousEvent> events = new ArrayList<>();
        try (Response resp = httpClient.newCall(request).execute()) {
            String responseBody = resp.body() != null ? resp.body().string() : "";

            ProtoMessageUtils.<ListMaliciousRequestsResponse>toProtoMessage(
                ListMaliciousRequestsResponse.class, responseBody
            ).ifPresent(m -> {
                events.addAll(m.getMaliciousEventsList().stream()
                    .map(smr -> new DashboardMaliciousEvent(
                        smr.getId(),
                        smr.getActor(),
                        smr.getFilterId(),
                        smr.getEndpoint(),
                        com.akto.dto.type.URLMethods.Method.fromString(smr.getMethod()),
                        smr.getApiCollectionId(),
                        smr.getIp(),
                        smr.getCountry(),
                        smr.getDetectedAt(),
                        smr.getType(),
                        smr.getRefId(),
                        smr.getCategory(),
                        smr.getSubCategory(),
                        smr.getEventTypeVal(),
                        smr.getPayload(),
                        smr.getMetadata(),
                        smr.getSuccessfulExploit(),
                        smr.getStatus(),
                        smr.getLabel(),
                        smr.getHost(),
                        smr.getJiraTicketUrl(),
                        smr.getSeverity()
                    ))
                    .collect(Collectors.toList())
                );
            });
        }

        return events;
    }

    /**
     * Transform malicious events to ADX-compatible format
     * Uses column names from GuardrailActivityTable schema
     */
    private List<Map<String, Object>> transformEventsToAdxFormat(List<DashboardMaliciousEvent> events) {
        return events.stream().map(event -> {
            Map<String, Object> record = new HashMap<>();
            record.put(GuardrailActivityTable.COL_ID, event.getId());
            record.put(GuardrailActivityTable.COL_FILTER_ID, event.getFilterId());
            record.put(GuardrailActivityTable.COL_ACTOR, event.getActor());
            record.put(GuardrailActivityTable.COL_LATEST_API_IP, event.getIp());
            record.put(GuardrailActivityTable.COL_LATEST_API_ENDPOINT, event.getUrl());
            record.put(GuardrailActivityTable.COL_COUNTRY, event.getCountry());
            record.put(GuardrailActivityTable.COL_LATEST_API_METHOD, event.getMethod() != null ? event.getMethod().name() : null);
            record.put(GuardrailActivityTable.COL_LATEST_API_ORIG, event.getPayload());
            record.put(GuardrailActivityTable.COL_DETECTED_AT, new java.util.Date(event.getTimestamp() * 1000L)); // Convert Unix timestamp to Date
            record.put(GuardrailActivityTable.COL_LATEST_API_COLLECTION_ID, event.getApiCollectionId());
            record.put(GuardrailActivityTable.COL_EVENT_TYPE, event.getEventType());
            record.put(GuardrailActivityTable.COL_CATEGORY, event.getCategory());
            record.put(GuardrailActivityTable.COL_SUB_CATEGORY, event.getSubCategory());
            record.put(GuardrailActivityTable.COL_TYPE, event.getType());
            record.put(GuardrailActivityTable.COL_REF_ID, event.getRefId());
            record.put(GuardrailActivityTable.COL_SEVERITY, null); // Severity not available in DashboardMaliciousEvent
            record.put(GuardrailActivityTable.COL_METADATA, event.getMetadata());
            record.put(GuardrailActivityTable.COL_SUCCESSFUL_EXPLOIT, event.getSuccessfulExploit());
            record.put(GuardrailActivityTable.COL_STATUS, event.getStatus());
            record.put(GuardrailActivityTable.COL_LABEL, event.getLabel());
            record.put(GuardrailActivityTable.COL_HOST, event.getHost());
            record.put(GuardrailActivityTable.COL_JIRA_TICKET_URL, event.getJiraTicketUrl());
            return record;
        }).collect(Collectors.toList());
    }

    /**
     * Check if table exists in Azure Data Explorer
     */
    private boolean checkTableExists(String clusterEndpoint, String databaseName, String tableName, String accessToken) throws Exception {
        String queryUrl = String.format("%s/v1/rest/query", clusterEndpoint);
        
        String kqlQuery = String.format(".show tables | where TableName == '%s'", tableName);
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("db", databaseName);
        requestBody.put("csl", kqlQuery);
        
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, java.util.Collections.singletonList("Bearer " + accessToken));
        headers.put(HttpHeaders.CONTENT_TYPE, java.util.Collections.singletonList("application/json"));
        
        OriginalHttpRequest request = new OriginalHttpRequest(
            queryUrl,
            "",
            "POST",
            requestBodyJson,
            headers,
            ""
        );

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            
            if (response.getStatusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> queryResponse = objectMapper.readValue(response.getBody(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tables = (List<Map<String, Object>>) queryResponse.get("Tables");
                if (tables != null && !tables.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<List<Object>> rows = (List<List<Object>>) tables.get(0).get("Rows");
                    return rows != null && !rows.isEmpty();
                }
                return false;
            } else {
                logger.warnAndAddToDb("Failed to check table existence: " + response.getStatusCode() + ", response: " + response.getBody(), LogDb.DASHBOARD);
                return false;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error checking table existence: " + e.getMessage(), LogDb.DASHBOARD);
            throw new Exception("Failed to check if table exists: " + e.getMessage(), e);
        }
    }

    /**
     * Create table in Azure Data Explorer if it doesn't exist
     */
    private void createTableIfNotExists(String clusterEndpoint, String databaseName, String tableName, String accessToken) throws Exception {
        String mgmtUrl = String.format("%s/v1/rest/mgmt", clusterEndpoint);
        
        // Use predefined table schema from GuardrailActivityTable
        String createTableCommand = GuardrailActivityTable.CREATE_TABLE_COMMAND;
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("db", databaseName);
        requestBody.put("csl", createTableCommand);
        
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, java.util.Collections.singletonList("Bearer " + accessToken));
        headers.put(HttpHeaders.CONTENT_TYPE, java.util.Collections.singletonList("application/json"));
        
        OriginalHttpRequest request = new OriginalHttpRequest(
            mgmtUrl,
            "",
            "POST",
            requestBodyJson,
            headers,
            ""
        );

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                logger.infoAndAddToDb(
                    String.format("Successfully created table %s in database %s", tableName, databaseName),
                    LogDb.DASHBOARD
                );
            } else {
                // Check if error is because table already exists
                String responseBody = response.getBody();
                if (responseBody != null && responseBody.contains("already exists")) {
                    logger.infoAndAddToDb(
                        String.format("Table %s already exists in database %s", tableName, databaseName),
                        LogDb.DASHBOARD
                    );
                } else {
                    throw new Exception("Failed to create table: " + response.getStatusCode() + ", response: " + responseBody);
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating table: " + e.getMessage(), LogDb.DASHBOARD);
            throw new Exception("Failed to create table: " + e.getMessage(), e);
        }
    }

    /**
     * Ensure table exists in Azure Data Explorer, create if it doesn't
     */
    private void ensureTableExists(String clusterEndpoint, String databaseName, String tableName, String accessToken) throws Exception {
        boolean tableExists = checkTableExists(clusterEndpoint, databaseName, tableName, accessToken);
        
        if (!tableExists) {
            logger.infoAndAddToDb(
                String.format("Table %s does not exist in database %s. Creating table...", tableName, databaseName),
                LogDb.DASHBOARD
            );
            createTableIfNotExists(clusterEndpoint, databaseName, tableName, accessToken);
        } else {
            logger.infoAndAddToDb(
                String.format("Table %s already exists in database %s", tableName, databaseName),
                LogDb.DASHBOARD
            );
        }
    }

    /**
     * Ingest data to Azure Data Explorer using Queued Ingestion REST API (production-grade approach)
     * Based on: https://learn.microsoft.com/en-us/kusto/api/netfx/kusto-ingest-client-rest
     * Uses pre-obtained access token
     */
    private void ingestToAdx(String clusterEndpoint, String databaseName, String tableName,
                             String accessToken, List<Map<String, Object>> adxRecords) throws Exception {
        
        // Ensure cluster endpoint uses ingest endpoint format
        String ingestEndpoint = clusterEndpoint;
        if (!ingestEndpoint.contains("ingest-")) {
            ingestEndpoint = ingestEndpoint.replace("https://", "https://ingest-");
        }
        
        if (adxRecords == null || adxRecords.isEmpty()) {
            throw new Exception("No valid records to ingest");
        }

        // Batch size constant
        final int BATCH_SIZE = 10;
        int totalRecords = adxRecords.size();
        int totalBatches = (int) Math.ceil((double) totalRecords / BATCH_SIZE);
        int successfulBatches = 0;
        int failedBatches = 0;

        logger.infoAndAddToDb(
            String.format("Ingesting %d records to ADX using queued ingestion in %d batches of %d records each",
                totalRecords, totalBatches, BATCH_SIZE),
            LogDb.DASHBOARD
        );

        // Step 1: Retrieve ingestion resources (queues and blob containers)
        IngestionResources ingestionResources = retrieveIngestionResources(ingestEndpoint, accessToken);
        
        // Step 2: Retrieve Kusto identity token
        String identityToken = retrieveKustoIdentityToken(ingestEndpoint, accessToken);

        // Process records in batches
        for (int i = 0; i < totalRecords; i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, totalRecords);
            List<Map<String, Object>> batch = adxRecords.subList(i, endIndex);
            int batchNumber = (i / BATCH_SIZE) + 1;

            try {
                // Convert batch records to JSON Lines format (NDJSON)
                String jsonData = batch.stream()
            .map(record -> {
                try {
                    return objectMapper.writeValueAsString(record);
                } catch (Exception e) {
                    logger.errorAndAddToDb("Error serializing record to JSON: " + e.getMessage(), LogDb.DASHBOARD);
                    return null;
                }
            })
            .filter(json -> json != null)
            .collect(Collectors.joining("\n"));

        if (jsonData.isEmpty()) {
                    logger.warnAndAddToDb(
                        String.format("Batch %d/%d has no valid records, skipping", batchNumber, totalBatches),
                        LogDb.DASHBOARD
                    );
                    continue;
                }

                // Step 3: Upload data to blob container
                String blobUriWithSas = uploadDataToBlob(
                    jsonData,
                    ingestionResources.tempStorageContainers.get(0), // Use first container, round-robin in production
                    String.format("%s_%d_%d.json", GuardrailActivityTable.TABLE_NAME, System.currentTimeMillis(), batchNumber)
                );

                // Step 4: Compose ingestion message
                String ingestionMessage = prepareIngestionMessage(
                    databaseName,
                    tableName,
                    blobUriWithSas,
                    jsonData.getBytes(StandardCharsets.UTF_8).length,
                    identityToken
                );

                // Step 5: Post ingestion message to queue
                postMessageToQueue(
                    ingestionResources.ingestionQueues.get(0), // Use first queue, round-robin in production
                    ingestionMessage
                );

                successfulBatches++;
                logger.infoAndAddToDb(
                    String.format("Successfully queued batch %d/%d (%d records) for ingestion to ADX table %s.%s",
                        batchNumber, totalBatches, batch.size(), databaseName, tableName),
                    LogDb.DASHBOARD
                );

            } catch (Exception e) {
                failedBatches++;
                logger.errorAndAddToDb(
                    String.format("Error ingesting batch %d/%d: %s", batchNumber, totalBatches, e.getMessage()),
                    LogDb.DASHBOARD
                );
                // Continue with next batch instead of failing completely
            }
        }

        // Summary logging
        if (failedBatches == 0) {
            logger.infoAndAddToDb(
                String.format("Successfully queued all %d records in %d batches for ingestion to ADX table %s.%s",
                    totalRecords, successfulBatches, databaseName, tableName),
                LogDb.DASHBOARD
            );
        } else {
            String errorMsg = String.format(
                "Ingestion completed with errors: %d successful batches, %d failed batches out of %d total batches",
                successfulBatches, failedBatches, totalBatches
            );
            logger.warnAndAddToDb(errorMsg, LogDb.DASHBOARD);
            if (successfulBatches == 0) {
                throw new Exception("All batches failed during ingestion: " + errorMsg);
            }
        }
    }

    /**
     * Container class for ingestion resources
     */
    private static class IngestionResources {
        List<String> ingestionQueues = new ArrayList<>();
        List<String> tempStorageContainers = new ArrayList<>();
    }

    /**
     * Retrieve ingestion resources (queues and blob containers) from Kusto Data Management service
     */
    private IngestionResources retrieveIngestionResources(String ingestEndpoint, String accessToken) throws Exception {
        String mgmtUrl = String.format("%s/v1/rest/mgmt", ingestEndpoint);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("csl", ".get ingestion resources");
        
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, java.util.Collections.singletonList("Bearer " + accessToken));
        headers.put(HttpHeaders.CONTENT_TYPE, java.util.Collections.singletonList("application/json"));
        
        OriginalHttpRequest request = new OriginalHttpRequest(
            mgmtUrl,
            "", 
            "POST", 
            requestBodyJson,
            headers, 
            ""
        );

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        
        if (response.getStatusCode() != 200) {
            throw new Exception("Failed to retrieve ingestion resources: " + response.getStatusCode() + ", response: " + response.getBody());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> responseJson = objectMapper.readValue(response.getBody(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) responseJson.get("Tables");
        
        IngestionResources resources = new IngestionResources();
        
        if (tables != null && !tables.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<List<Object>> rows = (List<List<Object>>) tables.get(0).get("Rows");
            
            if (rows != null) {
                for (List<Object> row : rows) {
                    if (row.size() >= 2) {
                        String resourceType = row.get(0).toString();
                        String resourceUri = row.get(1).toString();
                        
                        if ("SecuredReadyForAggregationQueue".equals(resourceType)) {
                            resources.ingestionQueues.add(resourceUri);
                        } else if ("TempStorage".equals(resourceType)) {
                            resources.tempStorageContainers.add(resourceUri);
                        }
                        // Note: FailedIngestionsQueue and SuccessfulIngestionsQueue are available
                        // but not currently used. They can be added if error checking is needed.
                    }
                }
            }
        }
        
        if (resources.ingestionQueues.isEmpty() || resources.tempStorageContainers.isEmpty()) {
            throw new Exception("Failed to retrieve required ingestion resources. Queues: " + 
                resources.ingestionQueues.size() + ", Containers: " + resources.tempStorageContainers.size());
        }
        
                logger.infoAndAddToDb(
            String.format("Retrieved ingestion resources: %d queues, %d blob containers",
                resources.ingestionQueues.size(), resources.tempStorageContainers.size()),
                    LogDb.DASHBOARD
                );
        
        return resources;
    }

    /**
     * Retrieve Kusto identity token for ingestion messages
     */
    private String retrieveKustoIdentityToken(String ingestEndpoint, String accessToken) throws Exception {
        String mgmtUrl = String.format("%s/v1/rest/mgmt", ingestEndpoint);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("csl", ".get kusto identity token");
        
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, java.util.Collections.singletonList("Bearer " + accessToken));
        headers.put(HttpHeaders.CONTENT_TYPE, java.util.Collections.singletonList("application/json"));
        
        OriginalHttpRequest request = new OriginalHttpRequest(
            mgmtUrl,
            "",
            "POST",
            requestBodyJson,
            headers,
            ""
        );

        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        
        if (response.getStatusCode() != 200) {
            throw new Exception("Failed to retrieve Kusto identity token: " + response.getStatusCode() + ", response: " + response.getBody());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> responseJson = objectMapper.readValue(response.getBody(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tables = (List<Map<String, Object>>) responseJson.get("Tables");
        
        if (tables != null && !tables.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<List<Object>> rows = (List<List<Object>>) tables.get(0).get("Rows");
            if (rows != null && !rows.isEmpty() && rows.get(0).size() > 0) {
                return rows.get(0).get(0).toString();
            }
        }
        
        throw new Exception("Failed to extract identity token from response");
    }

    /**
     * Upload data to Azure Blob Storage container using REST API
     * The container URI includes SAS token for authentication
     * According to: https://learn.microsoft.com/en-us/kusto/api/netfx/kusto-ingest-client-rest
     */
    private String uploadDataToBlob(String data, String containerUriWithSas, String blobName) throws Exception {
        // CRITICAL: Use string manipulation to avoid any URI parsing/encoding issues
        // The SAS token query string must be preserved EXACTLY as provided
        // Format: https://account.blob.core.windows.net/container?SAS_TOKEN
        // We need: https://account.blob.core.windows.net/container/blobname?SAS_TOKEN
        
        // Find where the query string starts (after ?)
        int queryStart = containerUriWithSas.indexOf('?');
        String baseUrl;
        String queryString;
        
        if (queryStart > 0) {
            baseUrl = containerUriWithSas.substring(0, queryStart);
            queryString = containerUriWithSas.substring(queryStart + 1); // Include the ? in final URL
        } else {
            baseUrl = containerUriWithSas;
            queryString = null;
        }
        
        // URL-encode the blob name to ensure special characters are handled correctly
        // Use java.net.URLEncoder but then replace + with %20 for path encoding
        String encodedBlobName = java.net.URLEncoder.encode(blobName, StandardCharsets.UTF_8.toString())
            .replace("+", "%20"); // URLEncoder uses + for spaces, but paths should use %20
        
        // Insert blob name in path before query string
        String blobUrl = baseUrl + "/" + encodedBlobName + (queryString != null ? "?" + queryString : "");
        
        // Upload blob using Azure Storage REST API (Put Blob)
        // Reference: https://learn.microsoft.com/en-us/rest/api/storageservices/put-blob
        MediaType JSON_MEDIA = MediaType.parse("application/json");
        RequestBody requestBody = RequestBody.create(data, JSON_MEDIA);
        Request request = new Request.Builder()
            .url(blobUrl)
            .put(requestBody)
            .addHeader("x-ms-blob-type", "BlockBlob")
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            if (statusCode >= 200 && statusCode < 300) {
                // Extract host for logging
                String host = baseUrl.substring(baseUrl.indexOf("://") + 3);
                host = host.substring(0, host.indexOf('/'));
                logger.infoAndAddToDb("Successfully uploaded blob: " + blobName + " to " + host, LogDb.DASHBOARD);
                return blobUrl;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new Exception("Failed to upload blob: " + statusCode + ", response: " + errorBody);
            }
        }
    }

    /**
     * Prepare ingestion message in the format expected by Kusto Data Management service
     * According to: https://learn.microsoft.com/en-us/kusto/api/netfx/kusto-ingest-client-rest
     * Format must be "multijson" for JSON data (not "json")
     */
    private String prepareIngestionMessage(String databaseName, String tableName, String blobUri, 
                                           long blobSizeBytes, String identityToken) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("Id", java.util.UUID.randomUUID().toString());
        message.put("BlobPath", blobUri);
        message.put("RawDataSize", blobSizeBytes);
        message.put("DatabaseName", databaseName);
        message.put("TableName", tableName);
        message.put("RetainBlobOnSuccess", true);
        message.put("FlushImmediately", true);
        message.put("ReportLevel", 2); // Report failures and successes (0-Failures, 1-None, 2-All)
        message.put("ReportMethod", 0); // Failures are reported to an Azure Queue (0-Queue, 1-Table)
        
        // AdditionalProperties must include authorizationContext (mandatory) and format
        // According to documentation: format should be "multijson" for JSON data
        Map<String, Object> additionalProperties = new HashMap<>();
        additionalProperties.put("authorizationContext", identityToken); // Mandatory - identity token
        additionalProperties.put("format", "multijson"); // Use "multijson" for JSON data per documentation
        message.put("AdditionalProperties", additionalProperties);
        
        return objectMapper.writeValueAsString(message);
    }

    /**
     * Post ingestion message to Azure Queue using REST API
     * The queue URI includes SAS token for authentication
     * According to: https://learn.microsoft.com/en-us/kusto/api/netfx/kusto-ingest-client-rest
     * Reference: https://learn.microsoft.com/en-us/rest/api/storageservices/put-message
     */
    private void postMessageToQueue(String queueUriWithSas, String message) throws Exception {
        // CRITICAL: Use string manipulation to avoid any URI parsing/encoding issues
        // The SAS token query string must be preserved EXACTLY as provided
        // Format: https://account.queue.core.windows.net/queuename?SAS_TOKEN
        // We need: https://account.queue.core.windows.net/queuename/messages?SAS_TOKEN
        
        // Find where the query string starts (after ?)
        int queryStart = queueUriWithSas.indexOf('?');
        String baseUrl;
        String queryString;
        
        if (queryStart > 0) {
            baseUrl = queueUriWithSas.substring(0, queryStart);
            queryString = queueUriWithSas.substring(queryStart + 1); // Include the ? in final URL
        } else {
            baseUrl = queueUriWithSas;
            queryString = null;
        }
        
        // Insert /messages before the query string
        String queueEndpoint = baseUrl + "/messages" + (queryString != null ? "?" + queryString : "");
        
        // Azure Queue messages need to be base64 encoded and XML formatted
        // Format: <QueueMessage><MessageText>base64_encoded_message</MessageText></QueueMessage>
        // Note: .NET storage client versions below v12 encode to base64 by default
        String base64Message = java.util.Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        String xmlMessage = String.format("<QueueMessage><MessageText>%s</MessageText></QueueMessage>", base64Message);
        
        MediaType XML_MEDIA = MediaType.parse("application/xml");
        RequestBody requestBody = RequestBody.create(xmlMessage, XML_MEDIA);
        Request request = new Request.Builder()
            .url(queueEndpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/xml")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            int statusCode = response.code();
            if (statusCode >= 200 && statusCode < 300) {
                // Extract queue name for logging (everything after last / before ?)
                String queueName = baseUrl.substring(baseUrl.lastIndexOf('/') + 1);
                logger.infoAndAddToDb("Successfully posted ingestion message to queue: " + queueName, LogDb.DASHBOARD);
            } else {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new Exception("Failed to post message to queue: " + statusCode + ", response: " + errorBody);
            }
        }
    }
    
    /**
     * Get Azure AD access token for authentication
     */
    private String getAzureAdAccessToken(String tenantId, String clientId, String clientSecret) throws Exception {
        String tokenUrl = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantId);
        
        // Build form-urlencoded request body using OkHttp FormBody
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add("client_id", clientId);
        formBuilder.add("scope", "https://kusto.kusto.windows.net/.default");
        formBuilder.add("client_secret", clientSecret);
        formBuilder.add("grant_type", "client_credentials");
        RequestBody requestBody = formBuilder.build();
        
        Request request = new Request.Builder()
            .url(tokenUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Accept", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (response.code() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenResponse = objectMapper.readValue(responseBody, Map.class);
                String accessToken = (String) tokenResponse.get("access_token");
                if (accessToken == null) {
                    throw new Exception("Failed to obtain access token from Azure AD");
                }
                return accessToken;
            } else {
                throw new Exception("Failed to get Azure AD token: " + response.code() + ", response: " + responseBody);
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error getting Azure AD access token: " + e.getMessage(), LogDb.DASHBOARD);
            throw new Exception("Failed to authenticate with Azure AD: " + e.getMessage(), e);
        }
    }
}

