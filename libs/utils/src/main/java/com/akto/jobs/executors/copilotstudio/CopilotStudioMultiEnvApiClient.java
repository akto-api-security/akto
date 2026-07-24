package com.akto.jobs.executors.copilotstudio;

import com.akto.dto.CopilotStudioIntegration.Environment;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared client for the Microsoft Entra / Power Platform Admin / Dataverse calls used by the
 * Copilot Studio (Multi Environment) connector. Used by both apps/dashboard (setup + OAuth
 * callback) and apps/account-job-executor (recurring sync) so the HTTP/URL-building logic
 * lives in exactly one place.
 */
public class CopilotStudioMultiEnvApiClient {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioMultiEnvApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build();

    private static final String TOKEN_ENDPOINT_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String REGISTRATION_ENDPOINT_TEMPLATE =
        "https://api.bap.microsoft.com/providers/Microsoft.BusinessAppPlatform/adminApplications/%s?api-version=2020-10-01";
    private static final String ENVIRONMENTS_ENDPOINT =
        "https://api.bap.microsoft.com/providers/Microsoft.BusinessAppPlatform/scopes/admin/environments?api-version=2020-10-01";
    private static final String APP_ONLY_SCOPE = "https://service.powerapps.com/.default";

    /** App-only token via client_credentials — used for env listing, app-user creation, and every recurring run. */
    public String getClientCredentialsToken(String tenantId, String clientId, String clientSecret) throws Exception {
        FormBody formBody = new FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("scope", APP_ONLY_SCOPE)
            .build();
        return requestAccessToken(tenantId, formBody, "client_credentials token");
    }

    /** Delegated token via authorization_code — used exactly once, at OAuth callback time, for the registration call. */
    public String getDelegatedToken(String tenantId, String clientId, String clientSecret, String code,
                                     String redirectUri, String scope) throws Exception {
        FormBody formBody = new FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("scope", scope)
            .build();
        return requestAccessToken(tenantId, formBody, "authorization_code token");
    }

    private String requestAccessToken(String tenantId, FormBody formBody, String description) throws Exception {
        Request request = new Request.Builder()
            .url(String.format(TOKEN_ENDPOINT_TEMPLATE, tenantId))
            .post(formBody)
            .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("Failed to obtain " + description + ": status=" + response.code() + " body=" + body);
            }
            JsonNode json = objectMapper.readTree(body);
            String accessToken = json.has("access_token") ? json.get("access_token").asText() : null;
            if (accessToken == null || accessToken.isEmpty()) {
                throw new Exception("Token response missing access_token for " + description);
            }
            return accessToken;
        }
    }

    /**
     * Registers the app registration as an admin application with Power Platform.
     * Path/params per Microsoft's adminApplications API — verify against current docs before relying on this in production.
     */
    public void registerApplication(String delegatedAccessToken, String clientId) throws Exception {
        Request request = new Request.Builder()
            .url(String.format(REGISTRATION_ENDPOINT_TEMPLATE, clientId))
            .header("Authorization", "Bearer " + delegatedAccessToken)
            .put(RequestBody.create(new byte[0], null))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new Exception("Power Platform app registration rejected: status=" + response.code() + " body=" + body);
            }
        }
    }

    /**
     * Lists every environment in the tenant.
     * Response shape per Microsoft's scopes/admin/environments API — verify against current docs before relying on this in production.
     */
    public List<Environment> listEnvironments(String accessToken) throws Exception {
        Request request = new Request.Builder()
            .url(ENVIRONMENTS_ENDPOINT)
            .header("Authorization", "Bearer " + accessToken)
            .get()
            .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("Failed to list Power Platform environments: status=" + response.code() + " body=" + body);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode values = root.get("value");
            List<Environment> environments = new ArrayList<>();
            if (values != null && values.isArray()) {
                for (JsonNode env : values) {
                    String id = env.has("name") ? env.get("name").asText() : null;
                    JsonNode properties = env.get("properties");
                    String displayName = properties != null && properties.has("displayName")
                        ? properties.get("displayName").asText() : id;
                    String instanceUrl = null;
                    if (properties != null && properties.has("linkedEnvironmentMetadata")) {
                        JsonNode linked = properties.get("linkedEnvironmentMetadata");
                        if (linked.has("instanceUrl")) {
                            instanceUrl = linked.get("instanceUrl").asText();
                        }
                    }
                    if (id != null && instanceUrl != null) {
                        environments.add(new Environment(id, instanceUrl, displayName));
                    }
                }
            }
            logger.info("listEnvironments: discovered {} environments", environments.size());
            return environments;
        }
    }

    /**
     * Creates the app registration as a Dataverse application user in the given environment.
     * Request shape per Dataverse's systemusers Web API — verify against current docs before relying on this in production.
     */
    public void createApplicationUser(String accessToken, String environmentUrl, String appClientId) throws Exception {
        String url = environmentUrl + (environmentUrl.endsWith("/") ? "" : "/") + "api/data/v9.2/systemusers";
        String requestBody = objectMapper.writeValueAsString(Collections.singletonMap("applicationid", appClientId));

        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new Exception("Failed to create Dataverse application user: status=" + response.code() + " body=" + body);
            }
        }
    }
}
