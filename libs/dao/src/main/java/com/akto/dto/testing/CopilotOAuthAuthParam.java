package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.util.TokenPayloadModifier;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import java.util.Arrays;

public class CopilotOAuthAuthParam extends AuthParam {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client;

    private static final String TOKEN_ENDPOINT_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SCOPE = "https://api.powerplatform.com/CopilotStudio.Copilots.Invoke offline_access";

    @Getter
    @Setter
    private String tenantId;
    @Getter
    @Setter
    private String clientId;
    @Getter
    @Setter
    private String clientSecret;
    @Getter
    @Setter
    private String refreshToken;
    @Getter
    @Setter
    private String roleId;

    // runtime-only, not persisted to DB
    private transient String cachedAccessToken;
    private transient long cachedAccessTokenExpiresAt;
    // set to true when refresh token was rotated, so caller can persist via dataActor
    @Getter
    @Setter
    private transient boolean refreshTokenRotated = false;

    public CopilotOAuthAuthParam() {}

    public CopilotOAuthAuthParam(String tenantId, String clientId, String clientSecret,
            String roleId) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.roleId = roleId;
    }

    @Override
    boolean addAuthTokens(OriginalHttpRequest request) {
        String accessToken = getValidAccessToken();
        if (StringUtils.isEmpty(accessToken))
            return false;
        if (request.getHeaders() == null)
            return false;
        request.getHeaders().put(AUTHORIZATION_HEADER.toLowerCase(), Arrays.asList(accessToken));
        return true;
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        return TokenPayloadModifier.tokenPayloadModifier(request, AUTHORIZATION_HEADER, null,
                Location.HEADER);
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        return Utils.isRequestKeyPresent(AUTHORIZATION_HEADER, request, Location.HEADER);
    }

    @Override
    public Location getWhere() {
        return Location.HEADER;
    }

    @Override
    public String getKey() {
        return AUTHORIZATION_HEADER;
    }

    @Override
    public String getValue() {
        return cachedAccessToken;
    }

    @Override
    public void setValue(String v) { /* not applicable */ }

    @Override
    public Boolean getShowHeader() {
        return true;
    }

    private String getValidAccessToken() {
        if (cachedAccessToken != null
                && System.currentTimeMillis() < cachedAccessTokenExpiresAt - 5 * 60 * 1000L) {
            System.out.println("CopilotOAuthAuthParam: Copilot access token cache hit");
            return cachedAccessToken;
        }

        System.out.println("CopilotOAuthAuthParam: Copilot access token cache miss, refreshing via Microsoft token endpoint");

        if (refreshToken == null || refreshToken.isEmpty()) {
            System.out.println("CopilotOAuthAuthParam: Copilot refresh token is missing, cannot obtain access token");
            return null;
        }

        try {
            FormBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("scope", SCOPE)
            .build();

            Request request = new Request.Builder()
                    .url(String.format(TOKEN_ENDPOINT_TEMPLATE, tenantId)).post(formBody).build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    System.out.println("CopilotOAuthAuthParam: Copilot token refresh failed. status=" + response.code() + " body=" + body);
                    return null;
                }

                JsonNode json = objectMapper.readTree(body);
                String newAccessToken =
                        json.has("access_token") ? json.get("access_token").asText() : null;
                String newRefreshToken =
                        json.has("refresh_token") ? json.get("refresh_token").asText() : null;
                long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 180L;

                if (newAccessToken == null || newAccessToken.isEmpty()) {
                    System.out.println("CopilotOAuthAuthParam: Copilot token refresh response missing access_token. body=" + body);
                    return null;
                }

                cachedAccessToken = newAccessToken;
                cachedAccessTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;

                // Microsoft rotates refresh tokens — flag for caller to persist
                if (newRefreshToken != null && !newRefreshToken.isEmpty()
                        && !newRefreshToken.equals(refreshToken)) {
                    System.out.println("CopilotOAuthAuthParam: Copilot refresh token rotated for roleId=" + roleId);
                    refreshToken = newRefreshToken;
                    refreshTokenRotated = true;
                }

                return newAccessToken;
            }
        } catch (Exception e) {
            System.out.println("CopilotOAuthAuthParam: Exception during Copilot token refresh: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
