package com.akto.wiz;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.testing_utils.TestingUtils;
import com.akto.util.enums.GlobalEnums;
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

import com.mongodb.client.result.UpdateResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class WizIntegrationUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final LoggerMaker loggerMaker = new LoggerMaker(WizIntegrationUtils.class, LogDb.DASHBOARD);

    private static final ConcurrentHashMap<Integer, Object> tokenLocks = new ConcurrentHashMap<>();
    
    public static final String USER_AGENT = "WIN-Akto/1.0";
    public static final String AUTH_ENDPOINT = "https://auth.app.wiz.io/oauth/token";
    private static final int TESTING_RUN_RESULT_BATCH_SIZE = 500;
    private static final int LARGE_TEXT_BATCH_SIZE = 50;
    // MCollection.findAll without paging silently returns at most this many documents
    private static final int WIZ_SYNC_MAX_ISSUES = 1_000_000;

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
            .addHeader("User-Agent", USER_AGENT)
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

        int accountId = Context.accountId.get();
        Object lock = tokenLocks.computeIfAbsent(accountId, k -> new Object());

        synchronized (lock) {
            wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());
            if (wizIntegration == null) {
                throw new Exception("WizIntegration cannot be null");
            }

            if (wizIntegration.isTokenValid()) {
                loggerMaker.infoAndAddToDb("Using cached Wiz access token (post-lock re-check)");
                return wizIntegration.getAccessToken();
            }

            loggerMaker.infoAndAddToDb("Cached access token expired or missing, generating new Wiz access token");
            String clientId = wizIntegration.getClientId();
            String clientSecret = wizIntegration.getClientSecret();
            return generateAccessToken(clientId, clientSecret);
        }
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
        headers.put("User-Agent", Collections.singletonList(USER_AGENT));

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
        String json = objectMapper.writeValueAsString(apiInfoKey);
        return Base64.getUrlEncoder().withoutPadding()
             .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static String fetchWizFindingId(TestingIssuesId testingIssuesId) throws Exception {
        String json = objectMapper.writeValueAsString(testingIssuesId);
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
                assetAttackSurfaceFinding.put("externalFindingLink", String.format("%s/dashboard/reports/issues?result=%s", dashboardUrl, testingRunResult.getHexId()));
            } catch (Exception e) {
                throw new Exception("Error building asset attack surface finding: " + e.getMessage());
            }
        }

        return assetAttackSurfaceFinding;
    }

    public static Map<TestingIssuesId,TestingRunResult> fetchTestingRunResultMap(List<TestingIssuesId> testingIssuesIdList) {
        Map<TestingIssuesId, TestingRunResult> testingRunResultMap = new HashMap<>();

        if (testingIssuesIdList == null || testingIssuesIdList.isEmpty()) {
            return testingRunResultMap;
        }

        // Build reverse lookup map once upfront
        Map<String, Map<ApiInfoKey, TestingIssuesId>> lookupMap = new HashMap<>();
        for (TestingIssuesId id : testingIssuesIdList) {
            lookupMap
                .computeIfAbsent(id.getTestSubCategory(), k -> new HashMap<>())
                .put(id.getApiInfoKey(), id);
        }

        // id is returned implicitly. It is required to obtain the hexId of the testing run result which is needed to link to the issue in the dashboard from wiz.
        Bson projection = Projections.include(TestingRunResult.TEST_SUB_TYPE, TestingRunResult.API_INFO_KEY);

        for (int i = 0; i < testingIssuesIdList.size(); i += TESTING_RUN_RESULT_BATCH_SIZE) {
            int end = Math.min(i + TESTING_RUN_RESULT_BATCH_SIZE, testingIssuesIdList.size());
            List<TestingIssuesId> batch = testingIssuesIdList.subList(i, end);

            List<Bson> orConditions = new ArrayList<>();
            for (TestingIssuesId id : batch) {
                orConditions.add(Filters.and(
                    Filters.eq(TestingRunResult.TEST_SUB_TYPE, id.getTestSubCategory()),
                    Filters.eq(TestingRunResult.API_INFO_KEY, id.getApiInfoKey())
                ));
            }

            try {
                List<TestingRunResult> results = TestingRunResultDao.instance.findAll(Filters.or(orConditions), projection);
                for (TestingRunResult result : results) {
                    Map<ApiInfoKey, TestingIssuesId> inner = lookupMap.get(result.getTestSubType());
                    if (inner != null) {
                        TestingIssuesId matchedId = inner.get(result.getApiInfoKey());
                        if (matchedId != null) {
                            testingRunResultMap.put(matchedId, result);
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.error(String.format("Error fetching TestingRunResult batch [%d-%d]: %s", i, end, e.getMessage()));
            }
        }

        return testingRunResultMap;
    }

    public static Map<String, YamlTemplate> fetchYamlTemplateMap(Set<String> testSubCategorySet) {
        Map<String, YamlTemplate> yamlTemplateMap = new HashMap<>();

        if (testSubCategorySet == null || testSubCategorySet.isEmpty()) {
            return yamlTemplateMap;
        }

        List<String> testSubCategoryList = new ArrayList<>(testSubCategorySet);

        for (int i = 0; i < testSubCategoryList.size(); i += LARGE_TEXT_BATCH_SIZE) {
            int end = Math.min(i + LARGE_TEXT_BATCH_SIZE, testSubCategoryList.size());
            List<String> batch = testSubCategoryList.subList(i, end);

            try {
                Bson filter = Filters.in(Constants.ID, batch);
                List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(filter, Projections.include(YamlTemplate.INFO));
                for (YamlTemplate yamlTemplate : yamlTemplates) {
                    yamlTemplateMap.put(yamlTemplate.getId(), yamlTemplate);
                }
            } catch (Exception e) {
                loggerMaker.error(String.format("Error fetching YamlTemplate batch [%d-%d]: %s", i, end, e.getMessage()));
            }
        }

        return yamlTemplateMap;
    }

    public static Map<String, Remediation> fetchRemediationMap(Set<String> testSubCategorySet) {
        Map<String, Remediation> remediationMap = new HashMap<>();

        if (testSubCategorySet == null || testSubCategorySet.isEmpty()) {
            return remediationMap;
        }

        Map<String, String> remediationIdMap = new HashMap<>();
        for (String testSubCategory : testSubCategorySet) {
            String remediationId = String.format("tests-library-master/remediation/%s.md", testSubCategory);
            remediationIdMap.put(remediationId, testSubCategory);
        }

        List<String> remediationIdList = new ArrayList<>(remediationIdMap.keySet());

        for (int i = 0; i < remediationIdList.size(); i += LARGE_TEXT_BATCH_SIZE) {
            int end = Math.min(i + LARGE_TEXT_BATCH_SIZE, remediationIdList.size());
            List<String> batch = remediationIdList.subList(i, end);

            try {
                Bson filter = Filters.in(Constants.ID, batch);
                List<Remediation> remediations = RemediationsDao.instance.findAll(filter);

                for (Remediation remediation : remediations) {
                    String remediationId = remediation.getid();
                    String testSubCategory = remediationIdMap.get(remediationId);
                    if (testSubCategory == null) {
                        loggerMaker.error("No matching testSubCategory for remediationId: " + remediationId);
                        continue;
                    }
                    remediationMap.put(testSubCategory, remediation);
                }
            } catch (Exception e) {
                loggerMaker.error(String.format("Error fetching remediations batch [%d-%d]: %s", i, end, e.getMessage()));
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
                loggerMaker.error(String.format("Error during processing of testingIssueId %s to create endpointAsset to issuesSet map: %s", testingIssuesId.toString(), e.getMessage()));
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
                     if (finding != null && !finding.isEmpty()) {
                        findingList.add(finding);
                     }
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
        List<TestingRunIssues> testingRunIssuesList;
        Bson wizSyncFilter = Filters.and(
            Filters.ne(TestingRunIssues.WIZ_FINDING_CREATION_STATUS, null),
            Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN)
        );
        Bson wizSyncProjection = Projections.include(Constants.ID, TestingRunIssues.KEY_SEVERITY, TestingRunIssues.WIZ_FINDING_CREATION_STATUS);
        try {
            // Single query, not skip/limit pages: skip re-walks all preceding results on every page
            testingRunIssuesList = TestingRunIssuesDao.instance.findAll(wizSyncFilter, wizSyncProjection);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching testing run issues for Wiz sync: " + e.getMessage());
            return;
        }
        if (testingRunIssuesList.size() >= WIZ_SYNC_MAX_ISSUES) {
            loggerMaker.errorAndAddToDb("Wiz upload: issue fetch hit the findAll limit of " + WIZ_SYNC_MAX_ISSUES + "; results truncated");
        }
        loggerMaker.infoAndAddToDb(String.format("Finished fetching testing run issues for wiz sync. Total: %d", testingRunIssuesList.size()));

        if (testingRunIssuesList == null || testingRunIssuesList.isEmpty()) {
            loggerMaker.infoAndAddToDb("No testing run issues to sync with Wiz. Skipping sync.");
            return;
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
            for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
                String wizFindingCreationStatus = testingRunIssues.getWizFindingCreationStatus();
                if (wizFindingCreationStatus == null || wizFindingCreationStatus.equals(WizIntegration.FindingCreationStatus.CREATION_REQUESTED.toString())) {
                    TestingIssuesId testingIssuesId = testingRunIssues.getId();
                    bulkUpdatesForNewFindings.add(
                        new UpdateOneModel<> (
                            Filters.eq(Constants.ID, testingIssuesId),
                            Updates.set(
                                TestingRunIssues.WIZ_FINDING_CREATION_STATUS, 
                                WizIntegration.FindingCreationStatus.CREATION_INITIATED.toString()
                            )
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

        // If we reach here, it means the upload was successful. Update the wiz findings last upload timestamp
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
            TestingRunIssuesDao.instance.updateMany(
                Filters.and(
                    Filters.in(Constants.ID, testingIssuesIdList),
                    // only issues that are not yet Wiz findings (missing or null), so an already requested/uploaded one is never reset
                    Filters.eq(TestingRunIssues.WIZ_FINDING_CREATION_STATUS, null)
                ),
                Updates.combine(
                    Updates.set(TestingRunIssues.WIZ_FINDING_URL, "unavailable"),
                    Updates.set(TestingRunIssues.WIZ_FINDING_CREATION_STATUS, WizIntegration.FindingCreationStatus.CREATION_REQUESTED.toString())
                )
            );
        } catch (Exception e) {
            throw new Exception("Error marking issues as Wiz findings: " + e.getMessage());
        }
    }

    /**
     * Auto-ticketing for Wiz: marks the OPEN issues of a test run summary, at the given severities, as Wiz findings.
     * No job is scheduled; the recurring Wiz sync job uploads whatever is marked CREATION_REQUESTED.
     * Issues that are already Wiz findings are left alone. Returns the number of issues marked.
     */
    public static int markSummaryIssuesAsWizFindings(ObjectId summaryId, List<String> severities) throws Exception {
        // a null summaryId would match every issue that has no summary id
        if (summaryId == null || severities == null || severities.isEmpty()) {
            return 0;
        }

        if (WizIntegrationDao.instance.findOne(new BasicDBObject()) == null) {
            loggerMaker.infoAndAddToDb("Wiz not integrated. Skipping auto-creation of Wiz findings.");
            return 0;
        }

        // Single write with every eligibility predicate: eligibility is checked on the primary at write time, so
        // there is no stale read (secondary reads in the new-testing path) and no race with the sync job or manual creation.
        try {
            UpdateResult result = TestingRunIssuesDao.instance.updateMany(
                Filters.and(
                    Filters.eq(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, summaryId),
                    Filters.in(TestingRunIssues.KEY_SEVERITY, severities),
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                    // matches a missing or null status: the exact complement of the sync job's `ne(status, null)` filter
                    Filters.eq(TestingRunIssues.WIZ_FINDING_CREATION_STATUS, null)
                ),
                wizFindingRequestedUpdate()
            );
            return (int) result.getModifiedCount();
        } catch (Exception e) {
            throw new Exception("Error marking issues as Wiz findings: " + e.getMessage());
        }
    }

    private static Bson wizFindingRequestedUpdate() {
        return Updates.combine(
            Updates.set(TestingRunIssues.WIZ_FINDING_URL, "unavailable"),
            Updates.set(TestingRunIssues.WIZ_FINDING_CREATION_STATUS, WizIntegration.FindingCreationStatus.CREATION_REQUESTED.toString())
        );
    }

    public static BasicDBObject checkLastUploadScanStatus() throws Exception {
        WizIntegration wizIntegration = WizIntegrationDao.instance.findOne(new BasicDBObject());

        if (wizIntegration == null) {
            throw new Exception("WizIntegration not configured for this account.");
        }

        String systemActivityId = wizIntegration.getSystemActivityId();
        long lastUploadedScanTs = wizIntegration.getLastUploadedScanTs();

        if (systemActivityId == null) {
            throw new Exception("No Wiz findings upload created for this account.");
        }

        // Get valid access token
        String accessToken = getValidAccessToken();

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
            "{\"query\":\"query SystemActivity($id: ID!) { systemActivity(id: $id) { id status statusInfo result {   ...on SystemActivityEnrichmentIntegrationResult{ dataSources {   ... IngestionStatsDetails } findings {   ... IngestionStatsDetails } events {   ... IngestionStatsDetails } tags {   ... IngestionStatsDetails } unresolvedAssets {   ... UnresolvedAssetsDetails }   } } context {   ... on SystemActivityEnrichmentIntegrationContext{ fileUploadId   } } }   }  fragment IngestionStatsDetails on EnrichmentIntegrationStats { incoming handled }  fragment UnresolvedAssetsDetails on EnrichmentIntegrationUnresolvedAssets { count ids }\",\"variables\":{\"id\":\"%s\"}}",
            systemActivityId
        );

        // Set headers
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + accessToken));
        headers.put("User-Agent", Collections.singletonList(USER_AGENT));

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
        if (responseObj == null || responseObj.isEmpty()) {
            throw new Exception("Failed to parse response from Wiz");
        }

        responseObj.put("lastUploadedScanTs", lastUploadedScanTs);

        return responseObj;
    }

    /**
     * Maps a Wiz FindingCommonStatus (OPEN, RESOLVED, REJECTED) to the Akto issue status.
     * Returns null if the Wiz status is null or not recognised.
     */
    public static GlobalEnums.TestRunIssueStatus getAktoIssueStatusFromWizStatus(String wizStatus) {
        if (wizStatus == null) {
            return null;
        }

        switch (wizStatus) {
            case "OPEN":
                return GlobalEnums.TestRunIssueStatus.OPEN;
            case "RESOLVED":
                return GlobalEnums.TestRunIssueStatus.FIXED;
            case "REJECTED":
                return GlobalEnums.TestRunIssueStatus.IGNORED;
            default:
                return null;
        }
    }

    static class StatusChangeResult {
        final Map<TestingIssuesId, GlobalEnums.TestRunIssueStatus> statusChanges;
        final int failedIssues;

        StatusChangeResult(Map<TestingIssuesId, GlobalEnums.TestRunIssueStatus> statusChanges, int failedIssues) {
            this.statusChanges = statusChanges;
            this.failedIssues = failedIssues;
        }
    }

    /**
     * Pure logic (no DB or network access) that decides which Akto issues need a status change,
     * given the status of each Wiz finding keyed by Wiz finding id.
     * Issues with no matching Wiz finding are skipped; issues that fail to process are counted and skipped.
     */
    static StatusChangeResult computeStatusChanges(List<TestingRunIssues> testingRunIssuesList, Map<String, String> wizFindingIdToStatusMap) {
        Map<TestingIssuesId, GlobalEnums.TestRunIssueStatus> statusChanges = new LinkedHashMap<>();

        // Iterate over testing run issues list
        int failedIssues = 0;
        // Unrecognised Wiz statuses seen in this run, with the number of issues skipped for each (logged once at the end)
        Map<String, Integer> unrecognisedWizStatusCounts = new HashMap<>();
        for (TestingRunIssues issue : testingRunIssuesList) {
            try {
                TestingIssuesId id = issue.getId();
                String status = issue.getTestRunIssueStatus().toString();

                // Fetch the corresponding Wiz finding ID for this Akto issue
                String wizFindingId = WizIntegrationUtils.fetchWizFindingId(id);
                if (wizFindingId != null) {
                    String wizFindingStatus = wizFindingIdToStatusMap.get(wizFindingId);
                    if (wizFindingStatus == null) {
                        // Newly created issue in Akto, not yet present in Wiz
                        continue;
                    }
                    GlobalEnums.TestRunIssueStatus convertedStatus = WizIntegrationUtils.getAktoIssueStatusFromWizStatus(wizFindingStatus);
                    if (convertedStatus == null) {
                        // Unknown Wiz status: leave the Akto issue untouched rather than guess a status
                        unrecognisedWizStatusCounts.merge(wizFindingStatus, 1, Integer::sum);
                        continue;
                    }
                    if (!status.equals(convertedStatus.toString())) {
                        statusChanges.put(id, convertedStatus);
                    }
                }
            } catch (Exception e) {
                failedIssues++;
                loggerMaker.error(String.format("Error processing testing run issue %s for Wiz status sync, skipping: %s", issue.getId(), e.getMessage()));
            }
        }

        if (!unrecognisedWizStatusCounts.isEmpty()) {
            loggerMaker.errorAndAddToDb(String.format("Skipped issues with unrecognised Wiz status during status sync (status -> issues skipped): %s", unrecognisedWizStatusCounts));
        }

        return new StatusChangeResult(statusChanges, failedIssues);
    }

    static final String WIZ_REJECTED_IGNORE_REASON = "Rejected in Wiz";

    /**
     * Builds the update applied to an Akto issue when its status changes because of Wiz.
     * Mirrors the dashboard's status update for ignoreReason: set when moving to IGNORED, removed when moving to any other status.
     */
    static Bson buildStatusChangeUpdate(GlobalEnums.TestRunIssueStatus newStatus) {
        Bson update = Updates.combine(
            Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, newStatus.toString()),
            Updates.set(TestingRunIssues.LAST_UPDATED, Context.now())
        );
        if (newStatus == GlobalEnums.TestRunIssueStatus.IGNORED) {
            return Updates.combine(update, Updates.set(TestingRunIssues.IGNORE_REASON, WIZ_REJECTED_IGNORE_REASON));
        }
        return Updates.combine(update, Updates.unset(TestingRunIssues.IGNORE_REASON));
    }

    /**
     * Returns the distinct latest test run summary ids of the issues whose status changed (issues without one are ignored).
     */
    static Set<ObjectId> collectAffectedSummaryIds(List<TestingRunIssues> issues, Set<TestingIssuesId> changedIssueIds) {
        Set<ObjectId> summaryIds = new HashSet<>();
        for (TestingRunIssues issue : issues) {
            if (issue.getId() != null && changedIssueIds.contains(issue.getId()) && issue.getLatestTestingRunSummaryId() != null) {
                summaryIds.add(issue.getLatestTestingRunSummaryId());
            }
        }
        return summaryIds;
    }

    /**
     * Recounts the issue counts of each summary. A failure for one summary is logged and does not stop the others.
     */
    private static void recountSummaries(Set<ObjectId> summaryIds) {
        for (ObjectId summaryId : summaryIds) {
            try {
                TestingUtils.recountAndUpdateSummaryCount(summaryId);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(String.format("Error recounting issues for summary %s after Wiz status sync: %s", summaryId, e.getMessage()));
            }
        }
    }

    /**
     * Syncs the status of Akto testing run issues that were sent to Wiz from the status of the matching Wiz findings.
     *
     * Wiz is the source of truth for status, by design: the sync is one-way (Wiz -> Akto) and Akto status changes
     * are never pushed to Wiz. If a user changes the status of a Wiz-synced issue in Akto, the next sync run
     * overwrites it with the status Wiz has for that finding. To change the status of such an issue, change it in Wiz.
     */
    public static void syncTestingRunIssuesStatusFromWiz(WizIntegration wizIntegration) {
        if (wizIntegration == null) {
            loggerMaker.infoAndAddToDb("Wiz integration not configured for this account. Skipping sync.");
            return;
        }

        try {
            // Find all testing run issues in this account that need to be synced with Wiz
            List<TestingRunIssues> testingRunIssuesList;

            // Only issues already uploaded to Wiz have a finding to sync. The creation status only moves forward
            // (null -> REQUESTED -> INITIATED, see markIssuesAsWizFinding), so REQUESTED issues are not in Wiz yet.
            Bson wizSyncFilter = Filters.eq(
                TestingRunIssues.WIZ_FINDING_CREATION_STATUS,
                WizIntegration.FindingCreationStatus.CREATION_INITIATED.toString()
            );
            Bson wizSyncProjection = Projections.include(Constants.ID, TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID);
            try {
                // Single query, not skip/limit pages: skip re-walks all preceding results on every page
                testingRunIssuesList = TestingRunIssuesDao.instance.findAll(wizSyncFilter, wizSyncProjection);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error fetching testing run issues for Wiz sync: " + e.getMessage());
                return;
            }
            if (testingRunIssuesList.isEmpty()) {
                // Nothing was sent to Wiz yet, so there is nothing to sync: avoid calling Wiz at all
                loggerMaker.info("No testing run issues marked as Wiz findings. Skipping Wiz status sync.");
                return;
            }
            if (testingRunIssuesList.size() >= WIZ_SYNC_MAX_ISSUES) {
                loggerMaker.errorAndAddToDb("Wiz status sync: issue fetch hit the findAll limit of " + WIZ_SYNC_MAX_ISSUES + "; results truncated");
            }

            // Get valid access token
            String accessToken = getValidAccessToken();

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

            List<BasicDBObject> attackSurfaceFindings = WizApiClient.fetchAllAttackSurfaceFindings(apiUrl, accessToken);
            Map<String, String> wizFindingIdToStatusMap = new HashMap<>();
            for (BasicDBObject finding : attackSurfaceFindings) {
                String wizFindingId = finding.getString("id");
                String status = finding.getString("status");
                if (wizFindingId != null && status != null) {
                    wizFindingIdToStatusMap.put(wizFindingId, status);
                }
            }

            StatusChangeResult statusChangeResult = computeStatusChanges(testingRunIssuesList, wizFindingIdToStatusMap);
            int failedIssues = statusChangeResult.failedIssues;

            List<WriteModel<TestingRunIssues>> bulkUpdatesForStatusChanges = new ArrayList<>();
            for (Map.Entry<TestingIssuesId, GlobalEnums.TestRunIssueStatus> change : statusChangeResult.statusChanges.entrySet()) {
                bulkUpdatesForStatusChanges.add(
                    new UpdateOneModel<> (
                        Filters.eq(Constants.ID, change.getKey()),
                        buildStatusChangeUpdate(change.getValue())
                    )
                );
            }

            if (!bulkUpdatesForStatusChanges.isEmpty()) {
                TestingRunIssuesDao.instance.getMCollection().bulkWrite(bulkUpdatesForStatusChanges);

                // The status changes affect the issue counts of the summaries these issues belong to
                recountSummaries(collectAffectedSummaryIds(testingRunIssuesList, statusChangeResult.statusChanges.keySet()));
            }

            loggerMaker.infoAndAddToDb(String.format("Finished syncing testing run issues status from Wiz. Wiz findings: %d, Akto issues checked: %d, status updated: %d, failed to process: %d",
                wizFindingIdToStatusMap.size(), testingRunIssuesList.size(), bulkUpdatesForStatusChanges.size(), failedIssues));

            // If we reach here, the sync completed. Update the last synced finding status timestamp
            try {
                WizIntegrationDao.instance.updateOne(
                    new BasicDBObject(),
                    Updates.set(WizIntegration.LAST_SYNCED_FINDING_STATUS_TS, System.currentTimeMillis()/1000L)
                );
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error updating Wiz integration last synced finding status timestamp: " + e.getMessage());
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error syncing testing run issues status from Wiz, no further issues will be updated in this run: " + e.getMessage());
        }
    }
}
