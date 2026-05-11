package com.akto.wiz;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.dao.ConfigsDao;
import com.akto.dao.WizIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.RemediationsDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.Config.AktoHostUrlConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.Remediation;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods;
import com.akto.dto.wiz_integration.WizEndpointAsset;
import com.akto.dto.wiz_integration.WizFinding;
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.bson.conversions.Bson;

public class WizIntegrationUtils {

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final LoggerMaker loggerMaker = new LoggerMaker(WizIntegrationUtils.class, LogDb.DASHBOARD);
    
    public static final String AUTH_ENDPOINT = "https://auth.app.wiz.io/oauth/token";

    public static boolean isWizDevMode() {
        String wizDevMode = System.getenv("WIZ_DEV_MODE");
        return wizDevMode != null && wizDevMode.equalsIgnoreCase("true");
    }

    public static String getWizDevAuthEndpoint() throws Exception {
        String wizDevAuthEndpoint = System.getenv("WIZ_DEV_AUTH_ENDPOINT");
        if (wizDevAuthEndpoint != null && !wizDevAuthEndpoint.isEmpty()) {
            return wizDevAuthEndpoint;
        } else {
            throw new Exception("WIZ_DEV_AUTH_ENDPOINT environment variable is required in Wiz dev mode");
        }
    }

    public static String getWizDevGraphQLEndpoint() throws Exception {
        String wizDevGraphQLEndpoint = System.getenv("WIZ_DEV_GRAPHQL_ENDPOINT");
        if (wizDevGraphQLEndpoint != null && !wizDevGraphQLEndpoint.isEmpty()) {
            return wizDevGraphQLEndpoint;
        } else {
            throw new Exception("WIZ_DEV_GRAPHQL_ENDPOINT environment variable is required in Wiz dev mode");
        }
    }

    public static String generateAccessToken(String clientId, String clientSecret) throws Exception {
        if (clientId == null || clientSecret == null) {
            throw new Exception("Client ID and Client Secret are required");
        }

        loggerMaker.infoAndAddToDb("Generating Wiz access token");

        // Build form-urlencoded request body using OkHttp FormBody
        FormBody.Builder formBuilder = new FormBody.Builder();
        formBuilder.add("grant_type", "client_credentials");
        formBuilder.add("audience", "wiz-api");
        formBuilder.add("client_id", clientId);
        formBuilder.add("client_secret", clientSecret);

        RequestBody requestBody = formBuilder.build();

        String authEndpoint = AUTH_ENDPOINT;

        if (WizIntegrationUtils.isWizDevMode()) {
            String devAuthEndpoint = getWizDevAuthEndpoint();
            loggerMaker.infoAndAddToDb("Wiz dev mode enabled. Using dev auth endpoint: " + devAuthEndpoint);
            authEndpoint = devAuthEndpoint;
        }
        
        Request request = new Request.Builder()
            .url(authEndpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Accept", "application/json")
            .build();

        String accessToken;
        int expiresIn;
        try (Response response = httpClient.newCall(request).execute()) {
            if (response == null) {
                throw new Exception("Failed to get OAuth token from Wiz - null response");
            }

            if (response.body() == null) {
                throw new Exception("Failed to get OAuth token from Wiz - null response body");
            }

            String responsePayload = response.body().string();
            int statusCode = response.code();

            if (statusCode != 200 || responsePayload == null) {
                String errorMsg = String.format("OAuth token request failed with status code: %d", statusCode);
                if (statusCode == 400) {
                    errorMsg += " (access_denied - Unauthorized)";
                }
                throw new Exception(errorMsg);
            }

            BasicDBObject responseObj = BasicDBObject.parse(responsePayload);

            if (!responseObj.containsField("access_token")) {
                throw new Exception("No access_token in OAuth response");
            }

            accessToken = responseObj.getString("access_token");
            expiresIn = responseObj.getInt("expires_in", 86400);
        }

        if (accessToken == null || accessToken.isEmpty()) { 
            throw new Exception("Received empty access token from Wiz"); 
        }

        loggerMaker.infoAndAddToDb(String.format("Successfully generated Wiz access token. Expires in: %d seconds", expiresIn));

        long tokenExpiryTs = System.currentTimeMillis() + (expiresIn * 1000L);

        Bson tokenUpdate = Updates.combine(
            Updates.set(WizIntegration.ACCESS_TOKEN,accessToken),
            Updates.set(WizIntegration.TOKEN_EXPIRY_TS, tokenExpiryTs)
        );

        WizIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            tokenUpdate
        );

        return accessToken;
    }

    public static String getValidAccessToken() throws Exception {
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());

        if (wizIntegration == null) {
            throw new Exception("WizIntegration cannot be null");
        }

        if (wizIntegration.isTokenValid()) {
            loggerMaker.infoAndAddToDb("Using cached Wiz access token");
            return wizIntegration.getAccessToken();
        }

        loggerMaker.infoAndAddToDb("Cached access token expired or missing, generating new Wiz access token");

        String clientId = wizIntegration.getClientId(); 
        String clientSecret = wizIntegration.getClientSecret();
        return generateAccessToken(clientId, clientSecret);
    }

    public static Map<String, String> requestSecurityScanUpload(String filename) throws Exception {
        if (filename == null || filename.isEmpty()) {
            throw new Exception("Filename is required");
        }

        loggerMaker.infoAndAddToDb("Requesting S3 upload URL from Wiz for file: " + filename);

        
        // Get valid access token
        String accessToken = getValidAccessToken();

        // Get Wiz integration configuration
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
        if (wizIntegration == null) {
            throw new Exception("WizIntegration cannot be null");
        }

        // Build GraphQL API endpoint
        String apiUrl = String.format(
            WizIntegration.API_BASE_URL_PATTERN,
            wizIntegration.getTenantDataCenter(),
            WizIntegration.ENVIRONMENT
        );

        if (WizIntegrationUtils.isWizDevMode()) {
            apiUrl = getWizDevGraphQLEndpoint();
            loggerMaker.infoAndAddToDb("Wiz dev mode enabled. Using dev GraphQL endpoint: " + apiUrl);
        }

        // Construct GraphQL query
        String graphqlQuery = String.format(
            "{\"query\":\"query RequestSecurityScanUpload($filename: String!) { requestSecurityScanUpload(filename: $filename) { upload { id url systemActivityId } } }\",\"variables\":{\"filename\":\"%s\"}}",
            filename.replace("\"", "\\\"")
        );

        // Set headers
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + accessToken));

        // Make request
        OriginalHttpRequest request = new OriginalHttpRequest(apiUrl, "", "POST", graphqlQuery, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), WizIntegrationUtils.isWizDevMode());

        if (response == null) {
            throw new Exception("Failed to request upload URL from Wiz - null response");
        }

        int statusCode = response.getStatusCode();
        String responsePayload = response.getBody();

        if (statusCode != 200 || responsePayload == null) {
            String errorMsg = String.format("Request security scan upload failed with status code: %d", statusCode);
            if (responsePayload != null) {
                errorMsg += " Response: " + responsePayload;
            }
            throw new Exception(errorMsg);
        }

        // Parse response
        BasicDBObject responseObj = BasicDBObject.parse(responsePayload);

        if (!responseObj.containsField("data")) {
            throw new Exception("Invalid response: missing 'data' field");
        }

        BasicDBObject data = (BasicDBObject) responseObj.get("data");
        if (!data.containsField("requestSecurityScanUpload")) {
            throw new Exception("Invalid response: missing 'requestSecurityScanUpload' field");
        }

        BasicDBObject requestSecurityScanUpload = (BasicDBObject) data.get("requestSecurityScanUpload");
        if (!requestSecurityScanUpload.containsField("upload")) {
            throw new Exception("Invalid response: missing 'upload' field");
        }

        BasicDBObject upload = (BasicDBObject) requestSecurityScanUpload.get("upload");

        String uploadId = upload.getString("id");
        String uploadUrl = upload.getString("url");
        String systemActivityId = upload.getString("systemActivityId");

        loggerMaker.infoAndAddToDb(String.format("Successfully retrieved S3 upload URL. Upload ID: %s. System Activity ID: %s", uploadId, systemActivityId));

        Map<String, String> uploadResponse = new HashMap<>();
        uploadResponse.put("id", uploadId);
        uploadResponse.put("url", uploadUrl);
        uploadResponse.put("systemActivityId", systemActivityId);

        return uploadResponse;
    }

    public static void uploadEnrichmentJSONToS3(String enrichmentJSON, String signedS3Url) throws Exception {

        // Set headers
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));

        // Make request
        OriginalHttpRequest request = new OriginalHttpRequest(signedS3Url, "", "PUT", enrichmentJSON, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), WizIntegrationUtils.isWizDevMode());

        if (response == null) {
            throw new Exception("Failed to upload enrichment JSON to s3 - null response");
        }

        int statusCode = response.getStatusCode();

        if (statusCode != 200 ) {
            throw new Exception("Failed to upload enrichment JSON to s3. Status code: " + statusCode);
        }

        loggerMaker.infoAndAddToDb("Successfully uploaded enrichment JSON to S3");
    }

    public static String fetchWizAssetId(ApiInfoKey apiInfoKey) throws Exception{
        String json = new ObjectMapper().writeValueAsString(apiInfoKey);
        return Base64.getUrlEncoder().withoutPadding()
             .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String fetchWizFindingId(TestingIssuesId testingIssuesId) throws Exception {
        String json = new ObjectMapper().writeValueAsString(testingIssuesId);
        return Base64.getUrlEncoder().withoutPadding()
             .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }   

    public static BasicDBObject buildAssetDetails(WizEndpointAsset endpointAsset) {
        BasicDBObject endpoint = new BasicDBObject();

        if (endpointAsset != null) {
            String host = endpointAsset.getHost();

            endpoint.put("assetId", host);
            endpoint.put("assetName", host);
            endpoint.put("host", host);
            endpoint.put("port", endpointAsset.getPort());
            endpoint.put("protocol", endpointAsset.getProtocol());
        }
        
        BasicDBObject assetDetails = new BasicDBObject("endpoint", endpoint);
        return assetDetails;
    }

    public static BasicDBObject buildAssetAttackSurfaceFinding(TestingIssuesId testingIssuesId, TestingRunIssues testingRunIssues, TestingRunResult testingRunResult, YamlTemplate yamlTemplate, Remediation remediation) throws Exception{
        BasicDBObject assetAttackSurfaceFinding = new BasicDBObject();
        if (testingIssuesId != null && testingRunIssues != null && yamlTemplate != null) {
            try {
                Info testInfo = yamlTemplate.getInfo();
                
                assetAttackSurfaceFinding.put("id", WizIntegrationUtils.fetchWizFindingId(testingIssuesId));

                ApiInfoKey apiInfoKey = testingIssuesId.getApiInfoKey();
                URLMethods.Method method = apiInfoKey.getMethod(); 

                String url = apiInfoKey.getUrl();
                URL richUrl = new URL(url);
                String path = richUrl.getPath();
                
                assetAttackSurfaceFinding.put("name", String.format("%s %s - %s", method.toString(), path, testInfo.getName()));
                assetAttackSurfaceFinding.put("description", testInfo.getDescription());

                String severity = testingRunIssues.getSeverity() != null ? testingRunIssues.getSeverity().toString() : "None";
                severity = severity.substring(0, 1).toUpperCase() + severity.substring(1).toLowerCase();

                assetAttackSurfaceFinding.put("severity", severity); 

                List<String> vulnerabilities = testInfo.getCve() != null ? testInfo.getCve() : new ArrayList<>();
                assetAttackSurfaceFinding.put("vulnerabilities", vulnerabilities);

                List<String> weaknesses = testInfo.getCwe() != null ? testInfo.getCwe() : new ArrayList<>();
                assetAttackSurfaceFinding.put("weaknesses", weaknesses);

                assetAttackSurfaceFinding.put("assessmentDetails", testingIssuesId.getTestErrorSource().name());

                String remediationText = remediation != null ? remediation.getRemediationText() : "No remediation available";
                assetAttackSurfaceFinding.put("remediation", remediationText);

                assetAttackSurfaceFinding.put("type", "DAST");
                
                String dashboardUrl = Constants.DEFAULT_AKTO_DASHBOARD_URL;
                if (DashboardMode.isOnPremDeployment()) {
                    AktoHostUrlConfig aktoUrlConfig = (AktoHostUrlConfig) ConfigsDao.instance.findOne(
                            Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()));
                    if (aktoUrlConfig != null) {
                        dashboardUrl = aktoUrlConfig.getHostUrl();
                    }
                }
                assetAttackSurfaceFinding.put("externalFindingLink", String.format("%s/reports/issues?result=%s", dashboardUrl, testingRunResult.getHexId()));
            } catch (Exception e) {
                throw new Exception("Error building asset attack surface finding: " + e.getMessage());
            }
        }

        return assetAttackSurfaceFinding;
    }

    public static Map<TestingIssuesId,TestingRunResult> fetchTestingRunResultMap(List<TestingIssuesId> testingIssuesIdList) {
        Map<TestingIssuesId, TestingRunResult> testingRunResultMap = new HashMap<>();

        for (TestingIssuesId testingIssuesId: testingIssuesIdList) {
            try {
                Bson filter = Filters.and(
                    Filters.in(TestingRunResult.TEST_SUB_TYPE, testingIssuesId.getTestSubCategory()),
                    Filters.in(TestingRunResult.API_INFO_KEY, testingIssuesId.getApiInfoKey())
                );
                TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(filter);
                if (testingRunResult != null) {
                    testingRunResultMap.put(testingIssuesId, testingRunResult);
                }
            } catch (Exception e) {
                // do nothing
            }
        }

        return testingRunResultMap;
    }

    public static Map<String, YamlTemplate> fetchYamlTemplateMap(Set<String> testSubCategorySet) {
        Map<String, YamlTemplate> yamlTemplateMap = new HashMap<>();

        if (testSubCategorySet != null) {
            Bson filter = Filters.in(Constants.ID, testSubCategorySet);
            List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(filter, Projections.include(YamlTemplate.INFO));
            for (YamlTemplate yamlTemplate : yamlTemplates) {
                yamlTemplateMap.put(yamlTemplate.getId(), yamlTemplate);
            }
        }

        return yamlTemplateMap;
    }

    public static Map<String, Remediation> fetchRemediationMap(Set<String> testSubCategorySet) {
        Map<String, Remediation> remediationMap = new HashMap<>();

        if (testSubCategorySet != null) {
            Map<String, String> remediationIdMap = new HashMap<>();
            for(String testSubCategory : testSubCategorySet) {
                String remediationId = String.format("tests-library-master/remediation/%s.md", testSubCategory);
                remediationIdMap.put(remediationId, testSubCategory);
            }

            Bson filter = Filters.in(Constants.ID, remediationIdMap.keySet());
            List<Remediation> remediations = RemediationsDao.instance.findAll(filter);

            for (Remediation remediation : remediations) {
                String remediationId = remediation.getid();
                String testSubCategory = remediationIdMap.get(remediationId);
                remediationMap.put(testSubCategory, remediation);
            }
        }

        return remediationMap;
    }

    public static String prepareMultipleIssueEnrichmentJSON(List<TestingRunIssues> testingRunIssuesList) throws Exception {

        Map<TestingIssuesId, TestingRunIssues> testingRunIssuesMap = new HashMap<>();

        for (TestingRunIssues testingRunIssues: testingRunIssuesList) {
            if (testingRunIssues == null || testingRunIssues.getId() == null) continue;
            testingRunIssuesMap.put(testingRunIssues.getId(), testingRunIssues);
        }

        List<TestingIssuesId> testingIssuesIdList = new ArrayList<>(testingRunIssuesMap.keySet());

        Set<String> testSubCategorySet = new HashSet<>();
        Map<WizEndpointAsset, Set<TestingIssuesId>> endpointAssetTestingIssuesIdMap = new HashMap<>();

        for (TestingIssuesId testingIssuesId: testingIssuesIdList) {
            try {
                String testSubCategory = testingIssuesId.getTestSubCategory();
                testSubCategorySet.add(testSubCategory);

                ApiInfoKey apiInfoKey = testingIssuesId.getApiInfoKey();
                String url = apiInfoKey.getUrl();
                URL richUrl = new URL(url);

                String protocol = richUrl.getProtocol();
                String host = richUrl.getHost();
                int port = (richUrl.getPort() == -1) ? richUrl.getDefaultPort() : richUrl.getPort();
                WizEndpointAsset endpointAsset = new WizEndpointAsset(host, protocol.toUpperCase(), port);

                Set<TestingIssuesId> testingIssuesIdSet;
                if (!endpointAssetTestingIssuesIdMap.containsKey(endpointAsset)) {
                    testingIssuesIdSet = new HashSet<>();
                    endpointAssetTestingIssuesIdMap.put(endpointAsset, testingIssuesIdSet);
                } else {
                    testingIssuesIdSet = endpointAssetTestingIssuesIdMap.get(endpointAsset);
                }
                testingIssuesIdSet.add(testingIssuesId);
            } catch (Exception e) {
                // do nothing
            }
        }

        Map<TestingIssuesId, TestingRunResult> testingRunResultMap = fetchTestingRunResultMap(testingIssuesIdList);
        Map<String, YamlTemplate> yamlTemplateMap = fetchYamlTemplateMap(testSubCategorySet);
        Map<String, Remediation> remediationMap = fetchRemediationMap(testSubCategorySet);

        Map<WizEndpointAsset, List<BasicDBObject>> attackSurfaceFindingMap = new HashMap<>();
        for (Map.Entry<WizEndpointAsset, Set<TestingIssuesId>> entry : endpointAssetTestingIssuesIdMap.entrySet()) {
            WizEndpointAsset endpointAsset = entry.getKey();
            Set<TestingIssuesId> testingIssuesIdSet = entry.getValue();

            List<BasicDBObject> findingList = new ArrayList<>();

            for (TestingIssuesId testingIssuesId: testingIssuesIdSet) {
                try {
                    TestingRunIssues testingRunIssues = testingRunIssuesMap.get(testingIssuesId);
                    TestingRunResult testingRunResult = testingRunResultMap.get(testingIssuesId);
                    YamlTemplate yamlTemplate = yamlTemplateMap.get(testingIssuesId.getTestSubCategory());
                    Remediation remediation = remediationMap.get(testingIssuesId.getTestSubCategory());
                    BasicDBObject finding = WizIntegrationUtils.buildAssetAttackSurfaceFinding(testingIssuesId, testingRunIssues, testingRunResult, yamlTemplate, remediation);
                    findingList.add(finding);
                } catch (Exception e) {
                    loggerMaker.error("Error building attack surface finding for testing issue ID: " + testingIssuesId.toString() + " - " + e.getMessage());
                }
            }

            if (!findingList.isEmpty()) {
                attackSurfaceFindingMap.put(endpointAsset, findingList);
            }
        }

        List<BasicDBObject> assetList = new ArrayList<>();
        for (Map.Entry<WizEndpointAsset, List<BasicDBObject>> entry : attackSurfaceFindingMap.entrySet()) {
            WizEndpointAsset endpointAsset = entry.getKey();
            List<BasicDBObject> findingList = entry.getValue();

            try {
                BasicDBObject assetDetails = WizIntegrationUtils.buildAssetDetails(endpointAsset);

                BasicDBObject asset = new BasicDBObject();
                asset.put("details", assetDetails);
                asset.put("attackSurfaceFindings", findingList);

                assetList.add(asset);
            } catch (Exception e) {
                loggerMaker.error("Error building asset details for API info key: " + endpointAsset.toString() + " - " + e.getMessage());
            }
        }

        BasicDBObject dataSource = new BasicDBObject();
        int accountId = Context.accountId.get();
        dataSource.put("id", String.format("akto-%d", accountId));
        dataSource.put("assets", assetList);

        BasicDBObject enrichmentJSONObj = new BasicDBObject();
        // integration id for all wiz customers as specified by wiz in the docs.
        enrichmentJSONObj.put("integrationId", "55c176cc-d155-43a2-98ed-aa56873a1ca1");
        enrichmentJSONObj.put("dataSources", Collections.singletonList(dataSource));

        String enrichmentJSON = enrichmentJSONObj.toJson();

        return enrichmentJSON;
    }



    public static void uploadWizDataSource(WizIntegration wizIntegration) {
        if (wizIntegration == null) {
            loggerMaker.infoAndAddToDb("Wiz integration not configured for this account. Skipping sync.");
            return;
        }

        // Find all testing run issues in this account that need to be synced with Wiz
        List<TestingRunIssues> testingRunIssuesList = new ArrayList<>();
        try {
            testingRunIssuesList = TestingRunIssuesDao.instance.findAll(
                Filters.ne(TestingRunIssues.WIZ_FINDING, null),
                Projections.include(Constants.ID, TestingRunIssues.KEY_SEVERITY, TestingRunIssues.WIZ_FINDING)
            );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching testing run issues for Wiz sync: " + e.getMessage());
            return;
        }

        if (testingRunIssuesList == null || testingRunIssuesList.isEmpty()) {
            loggerMaker.infoAndAddToDb("No testing run issues to sync with Wiz. Skipping sync.");
            return;
        }   

        Map<TestingIssuesId, WizFinding> testingRunIssuesMap = new HashMap<>();

        for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
            if (testingRunIssues == null) continue;
            TestingIssuesId testingIssuesId = testingRunIssues.getId();
            WizFinding wizFinding = testingRunIssues.getWizFinding();
            testingRunIssuesMap.put(testingIssuesId, wizFinding);
        }
        
        String enrichmentJSON = null;
        try {
            enrichmentJSON = prepareMultipleIssueEnrichmentJSON(testingRunIssuesList);
        } catch (Exception e) {
            String errMsg = "Error preparing enrichment JSON for Wiz sync: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, errMsg);
            return;
        }

        String systemActivityId = null;

        try {
            Map<String, String> securityScanUploadResult = WizIntegrationUtils.requestSecurityScanUpload("akto-testing-issue.json");
            String signedS3Url = securityScanUploadResult.get("url");
            systemActivityId = securityScanUploadResult.get("systemActivityId");
            WizIntegrationUtils.uploadEnrichmentJSONToS3(enrichmentJSON, signedS3Url);

            List<WriteModel<TestingRunIssues>> bulkUpdatesForNewFindings = new ArrayList<>();
            for (Map.Entry<TestingIssuesId, WizFinding> entry : testingRunIssuesMap.entrySet()) {
                TestingIssuesId testingIssuesId = entry.getKey();
                WizFinding wizFinding = entry.getValue();

                if (wizFinding.getStatus() == WizFinding.Status.CREATION_REQUESTED) {
                    wizFinding.setStatus(WizFinding.Status.CREATION_INITIATED);
                    bulkUpdatesForNewFindings.add(
                        new UpdateOneModel<> (
                            Filters.eq(Constants.ID, testingIssuesId),
                            Updates.set(TestingRunIssues.WIZ_FINDING, wizFinding)
                        )
                    );
                }
            }

            if (!bulkUpdatesForNewFindings.isEmpty()) {
                TestingRunIssuesDao.instance.getMCollection().bulkWrite(bulkUpdatesForNewFindings);
            }
        } catch (Exception e) {
            String errMsg = "Error uploading enrichment JSON to Wiz: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, errMsg);
            return;
        }

        // If we reach here, it means the upload was successful. Update the wiz last upload timestamp
        try {
            WizIntegrationDao.instance.updateOne(
                new BasicDBObject(),
                Updates.combine(
                    Updates.set(WizIntegration.SYSTEM_ACTIVITY_ID, systemActivityId),
                    Updates.set(WizIntegration.LAST_UPLOADED_SCAN_TS, System.currentTimeMillis()/1000L)
                )
            );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating Wiz integration last sync timestamp: " + e.getMessage());
        }
    }

    public static void markIssuesAsWizFinding(List<TestingIssuesId> testingIssuesIdList) throws Exception {
        try {
            List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(
                Filters.and(
                    Filters.in(Constants.ID, testingIssuesIdList),
                    Filters.eq(TestingRunIssues.WIZ_FINDING, null)
                ),
                Projections.include(Constants.ID)  
            );

            List<TestingIssuesId> issueIdsWithoutWizFindings = new ArrayList<>();
            for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
                issueIdsWithoutWizFindings.add(testingRunIssues.getId());     
            }

            WizFinding wizFinding = new WizFinding(WizFinding.Status.CREATION_REQUESTED, null);
            TestingRunIssuesDao.instance.updateMany(
                Filters.in(Constants.ID, issueIdsWithoutWizFindings),
                Updates.set(TestingRunIssues.WIZ_FINDING, wizFinding)
            );
        } catch (Exception e) {
            throw new Exception("Error marking issues as Wiz findings: " + e.getMessage());
        }
    }
}
