package com.akto.utils;

import java.net.URL;
import java.util.ArrayList;
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
import com.akto.dto.wiz_integration.WizIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.bson.conversions.Bson;

public class WizIntegrationUtils {

    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
    private static final LoggerMaker loggerMaker = new LoggerMaker(WizIntegrationUtils.class, LogDb.DASHBOARD);
    //todo: remove test endpoint
    //public static final String AUTH_ENDPOINT = "http://localhost:9000/oauth/token";
    public static final String AUTH_ENDPOINT = "https://auth.app.wiz.io/oauth/token";

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
        
        Request request = new Request.Builder()
            .url(AUTH_ENDPOINT)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Accept", "application/json")
            .build();

        Response response = httpClient.newCall(request).execute();

        if (response == null) {
            throw new Exception("Failed to get OAuth token from Wiz - null response");
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

        String accessToken = responseObj.getString("access_token");
        int expiresIn = responseObj.getInt("expires_in", 86400);

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
        

        //todo: remove test endpoint
        //apiUrl = "http://localhost:8080/graphql";

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

        // todo: remove skipSSRF set to true
        OriginalHttpRequest request = new OriginalHttpRequest(apiUrl, "", "POST", graphqlQuery, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        //OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), true);

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

        loggerMaker.infoAndAddToDb(String.format("Successfully retrieved S3 upload URL. Upload ID: %s", uploadId));

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

        // todo: remove skipSSRF set to true
        OriginalHttpRequest request = new OriginalHttpRequest(signedS3Url, "", "PUT", enrichmentJSON, headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        //OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, true, new ArrayList<>(), true);

        if (response == null) {
            throw new Exception("Failed to upload enrichment JSON to s3 - null response");
        }

        int statusCode = response.getStatusCode();

        if (statusCode != 200 ) {
            throw new Exception("Failed to upload enrichment JSON to s3. Status code: " + statusCode);
        }

        loggerMaker.infoAndAddToDb("Successfully uploaded enrichment JSON to S3");
    }

    public static void updateWizFindingUrl(TestingIssuesId testingIssuesId) throws Exception {
        Bson update = Updates.set(TestingRunIssues.WIZ_FINDING_URL, "unavailable");
        TestingRunIssuesDao.instance.updateOne(Filters.eq(Constants.ID, testingIssuesId), update);
    }

    public static BasicDBObject buildAssetDetails(ApiInfoKey apiInfoKey) throws Exception {
        BasicDBObject endpoint = new BasicDBObject();
        if (apiInfoKey != null) {
            try {
                int apiCollectionId = apiInfoKey.getApiCollectionId();
                String url = apiInfoKey.getUrl();
                URLMethods.Method method = apiInfoKey.getMethod();

                URL richUrl = new URL(url);
                String protocol = richUrl.getProtocol();
                String host = richUrl.getHost();
                String path = richUrl.getPath();
                int port = (richUrl.getPort() == -1) ? richUrl.getDefaultPort() : richUrl.getPort(); 
            
                endpoint.put("assetId", String.format("%s#%s#%d", method.toString(), url, apiCollectionId));
                endpoint.put("assetName", String.format("%s %s", method.toString(), path));
                endpoint.put("host", host);
                endpoint.put("port", port);
                endpoint.put("protocol", protocol.toUpperCase());
            } catch (Exception e) {
                throw new Exception("Error building asset details: " + e.getMessage());
            }
        }
        
        BasicDBObject assetDetails = new BasicDBObject("endpoint", endpoint);
        return assetDetails;
    }

    public static BasicDBObject buildAssetAttackSurfaceFinding(TestingIssuesId testingIssuesId, TestingRunResult testingRunResult, YamlTemplate yamlTemplate, Remediation remediation) throws Exception{
        BasicDBObject assetAttackSurfaceFinding = new BasicDBObject();
        if (testingIssuesId != null && yamlTemplate != null) {
            try {
                Info testInfo = yamlTemplate.getInfo();
                
                assetAttackSurfaceFinding.put("id", yamlTemplate.getId());

                assetAttackSurfaceFinding.put("name", testInfo.getName());
                assetAttackSurfaceFinding.put("description", testInfo.getDescription());
                assetAttackSurfaceFinding.put("severity", testInfo.getSeverity()); 
                assetAttackSurfaceFinding.put("vulnerabilities", testInfo.getCve());
                assetAttackSurfaceFinding.put("weaknesses", testInfo.getCwe());

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

    public static TestingRunResult fetchTestingRunResult(String testSubCategory, ApiInfoKey apiInfoKey) {
        TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(Filters.and(
            Filters.in(TestingRunResult.TEST_SUB_TYPE, testSubCategory),
            Filters.in(TestingRunResult.API_INFO_KEY, apiInfoKey)
        ));

        return testingRunResult;
    }

    public static YamlTemplate fetchYamlTemplate(String testSubCategory) {
        Bson filter = Filters.in(Constants.ID, testSubCategory);
        YamlTemplate yamlTemplate = YamlTemplateDao.instance.findOne(filter, Projections.include(YamlTemplate.INFO));
        return yamlTemplate;
    }

    public static Remediation fetchRemediation(String testSubCategory) {
        Remediation remediation = RemediationsDao.instance.findOne(
            Filters.eq(Constants.ID, String.format("tests-library-master/remediation/%s.md", testSubCategory)));

        return remediation;
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

    public static String prepareSingleIssueEnrichmentJSON(TestingIssuesId testingIssuesId) throws Exception {

        String testSubCategory = testingIssuesId.getTestSubCategory();
        ApiInfoKey apiInfoKey = testingIssuesId.getApiInfoKey();

        TestingRunResult testingRunResult = fetchTestingRunResult(testSubCategory, apiInfoKey);
        YamlTemplate yamlTemplate = fetchYamlTemplate(testSubCategory);
        Remediation remediation = fetchRemediation(testSubCategory);

        if (testingRunResult == null || yamlTemplate == null) {
            String errMsg = String.format("Missing data for enrichment JSON. testingRunResult: %s, yamlTemplate: %s, remediation: %s", 
                testingRunResult == null ? "null" : testingRunResult.getHexId(),
                yamlTemplate == null ? "null" : yamlTemplate.getId(),
                remediation == null ? "null" : remediation.getid()
            );
            throw new Exception(errMsg);
        }

        // Prepare asset
        BasicDBObject asset = new BasicDBObject();
        BasicDBObject assetDetails = WizIntegrationUtils.buildAssetDetails(apiInfoKey);

        BasicDBObject assetAttackSurfaceFinding = WizIntegrationUtils.buildAssetAttackSurfaceFinding(testingIssuesId, testingRunResult, yamlTemplate, remediation);
        BasicDBList attackSurfaceFindingsList = new BasicDBList();
        attackSurfaceFindingsList.add(assetAttackSurfaceFinding);

        asset.put("details", assetDetails);
        asset.put("attackSurfaceFindings", attackSurfaceFindingsList);

        BasicDBList assetsList = new BasicDBList();
        assetsList.add(asset);

        // Prepare datasources
        BasicDBObject dataSource = new BasicDBObject();

        dataSource.put("id", String.format("akto-testing-run-result-%s", testingRunResult.getHexId()));
        dataSource.put("assets", assetsList);

        BasicDBList dataSourcesList = new BasicDBList();
        dataSourcesList.add(dataSource);

        // Prepare enrichment JSON
        BasicDBObject enrichmentJSONObj = new BasicDBObject();
        enrichmentJSONObj.put("integrationId", "placeholder-integration-id");
        enrichmentJSONObj.put("dataSources", dataSourcesList);

        String enrichmentJSON = enrichmentJSONObj.toJson();

        return enrichmentJSON;
    }

    public static String prepareMultipleIssueEnrichmentJSON(List<TestingIssuesId> testingIssuesIdList) throws Exception {

        Set<String> testSubCategorySet = new HashSet<>();
        Map<ApiInfoKey, Set<TestingIssuesId>> apiTestingIssuesIdMap = new HashMap<>();

        for (TestingIssuesId testingIssuesId: testingIssuesIdList) {
            try {
                String testSubCategory = testingIssuesId.getTestSubCategory();
                testSubCategorySet.add(testSubCategory);

                ApiInfoKey apiInfoKey = testingIssuesId.getApiInfoKey();
                Set<TestingIssuesId> apiTestingIssuesIdSet;
                if (!apiTestingIssuesIdMap.containsKey(apiInfoKey)) {
                    apiTestingIssuesIdSet = new HashSet<>();
                    apiTestingIssuesIdMap.put(apiInfoKey, apiTestingIssuesIdSet);
                } else {
                    apiTestingIssuesIdSet = apiTestingIssuesIdMap.get(apiInfoKey);
                }
                apiTestingIssuesIdSet.add(testingIssuesId);
            } catch (Exception e) {
                // do nothing
            }
        }

        Map<TestingIssuesId, TestingRunResult> testingRunResultMap = fetchTestingRunResultMap(testingIssuesIdList);
        Map<String, YamlTemplate> yamlTemplateMap = fetchYamlTemplateMap(testSubCategorySet);
        Map<String, Remediation> remediationMap = fetchRemediationMap(testSubCategorySet);

        Map<ApiInfoKey, List<BasicDBObject>> attackSurfaceFindingMap = new HashMap<>();
        for (Map.Entry<ApiInfoKey, Set<TestingIssuesId>> entry : apiTestingIssuesIdMap.entrySet()) {
            ApiInfoKey apiInfoKey = entry.getKey();
            Set<TestingIssuesId> apiTestingIssuesIdSet = entry.getValue();

            List<BasicDBObject> findingList = new ArrayList<>();

            for (TestingIssuesId testingIssuesId: apiTestingIssuesIdSet) {
                try {
                    TestingRunResult testingRunResult = testingRunResultMap.get(testingIssuesId);
                    YamlTemplate yamlTemplate = yamlTemplateMap.get(testingIssuesId.getTestSubCategory());
                    Remediation remediation = remediationMap.get(testingIssuesId.getTestSubCategory());
                    BasicDBObject finding = WizIntegrationUtils.buildAssetAttackSurfaceFinding(testingIssuesId, testingRunResult, yamlTemplate, remediation);
                    findingList.add(finding);
                } catch (Exception e) {
                    // do nothing
                }
            }

            if (!findingList.isEmpty()) {
                attackSurfaceFindingMap.put(apiInfoKey, findingList);
            }
        }

        List<BasicDBObject> assetList = new ArrayList<>();
        for (Map.Entry<ApiInfoKey, List<BasicDBObject>> entry : attackSurfaceFindingMap.entrySet()) {
            ApiInfoKey apiInfoKey = entry.getKey();
            List<BasicDBObject> findingList = entry.getValue();

            try {
                BasicDBObject assetDetails = WizIntegrationUtils.buildAssetDetails(apiInfoKey);

                BasicDBObject asset = new BasicDBObject();
                asset.put("details", assetDetails);
                asset.put("attackSurfaceFindings", findingList);

                assetList.add(asset);
            } catch (Exception e) {
                // do nothing
            }
        }

        BasicDBObject dataSource = new BasicDBObject();
        int accountId = Context.accountId.get();
        dataSource.put("id", String.format("akto-%d", accountId));
        dataSource.put("assets", assetList);

        BasicDBObject enrichmentJSONObj = new BasicDBObject();
        enrichmentJSONObj.put("integrationId", "placeholder-integration-id");
        enrichmentJSONObj.put("dataSources", Collections.singletonList(dataSource));

        String enrichmentJSON = enrichmentJSONObj.toJson();

        return enrichmentJSON;
    }
}

