package com.akto.utils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.ConfigsDao;
import com.akto.dao.WizIntegrationDao;
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
                assetAttackSurfaceFinding.put("externalFindingLink", String.format("%s/reports/issues?=%s", dashboardUrl, testingRunResult.getHexId()));
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
        YamlTemplate yamlTemplate = YamlTemplateDao.instance.findOne(
            Filters.in(Constants.ID, testSubCategory),
            Projections.include(YamlTemplate.INFO+".description", YamlTemplate.INFO+".name", YamlTemplate.INFO+".severity", YamlTemplate.INFO+".cve", YamlTemplate.INFO+".cwe")
        );

        return yamlTemplate;
    }

    public static Remediation fetchRemediation(String testSubCategory) {
        Remediation remediation = RemediationsDao.instance.findOne(
            Filters.eq(Constants.ID, String.format("tests-library-master/remediation/%s.md", testSubCategory)));

        return remediation;
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

        Map<String, YamlTemplate> yamlTemplateMap = new HashMap<>();
        Map<String, Remediation> remediationMap = new HashMap<>();

        BasicDBList dataSourcesList = new BasicDBList();

        for (TestingIssuesId testingIssuesId : testingIssuesIdList) {
            if (testingIssuesId == null) continue;

            try {
                String testSubCategory = testingIssuesId.getTestSubCategory();
                ApiInfoKey apiInfoKey = testingIssuesId.getApiInfoKey();

                TestingRunResult testingRunResult = WizIntegrationUtils.fetchTestingRunResult(testSubCategory, apiInfoKey);

                YamlTemplate yamlTemplate;
                Remediation remediation;

                if (!yamlTemplateMap.containsKey(testSubCategory)) {
                    yamlTemplate = WizIntegrationUtils.fetchYamlTemplate(testSubCategory);
                    yamlTemplateMap.put(testSubCategory, yamlTemplate);
                }

                if (!remediationMap.containsKey(testSubCategory)) {
                    remediation = WizIntegrationUtils.fetchRemediation(testSubCategory);
                    remediationMap.put(testSubCategory, remediation);
                }

                yamlTemplate = yamlTemplateMap.get(testSubCategory);
                remediation = remediationMap.get(testSubCategory);

                // Prepare asset
                BasicDBObject asset = new BasicDBObject();
                BasicDBObject assetDetails = WizIntegrationUtils.buildAssetDetails(apiInfoKey);

                BasicDBObject assetAttackSurfaceFinding = WizIntegrationUtils.buildAssetAttackSurfaceFinding(testingIssuesId, testingRunResult, yamlTemplate, remediation);
                BasicDBList attackSurfaceFindingsList = new BasicDBList();
                attackSurfaceFindingsList.add(assetAttackSurfaceFinding);

                asset.put("details", assetDetails);
                asset.put("attackSurfaceFindings", attackSurfaceFindingsList);

                // Add to assets list
                BasicDBList assetsList = new BasicDBList();
                assetsList.add(asset);

                // Prepare datasource
                BasicDBObject dataSource = new BasicDBObject();

                dataSource.put("id", String.format("akto-testing-run-result-%s", testingRunResult.getHexId()));
                dataSource.put("assets", assetsList);

                // Add to dataSources list
                dataSourcesList.add(dataSource);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Missing data for creating enrichment for issue: " + testingIssuesId);
            }
        }

        if (dataSourcesList.isEmpty()) {
            throw new Exception("No valid issues found to create enrichment JSON. Check logs for details.");
        }

        // Prepare enrichment JSON
        BasicDBObject enrichmentJSONObj = new BasicDBObject();
        enrichmentJSONObj.put("integrationId", "placeholder-integration-id");
        enrichmentJSONObj.put("dataSources", dataSourcesList);

        String enrichmentJSON = enrichmentJSONObj.toJson();

        return enrichmentJSON;
    }
}
