package com.akto.action.quick_start;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.CopilotOAuthAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.types.ObjectId;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class CopilotStudioAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client;

    private static final String TOKEN_ENDPOINT_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
    private static final String AUTH_ENDPOINT_TEMPLATE  = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String SCOPE = "https://api.powerplatform.com/CopilotStudio.Copilots.Invoke offline_access";

    @Setter private String code;
    @Setter private String state;
    @Setter private String error;
    @Setter private String error_description;
    @Setter private String roleId;

    @Getter private String authorizationUrl;
    @Getter private String redirectUrl;

    public String fetchAuthorizationUrl() {
        int accountId = Context.accountId.get();

        if (roleId == null || roleId.isEmpty()) {
            addActionError("roleId is required");
            return Action.ERROR.toUpperCase();
        }

        TestRoles role = TestRolesDao.instance.findOne(Filters.eq("_id", new ObjectId(roleId)));
        if (role == null) {
            addActionError("TestRole not found");
            return Action.ERROR.toUpperCase();
        }

        CopilotOAuthAuthParam copilotParam = findCopilotParam(role);
        if (copilotParam == null) {
            addActionError("No COPILOT_OAUTH auth param found in TestRole");
            return Action.ERROR.toUpperCase();
        }

        try {
            String callbackUrl = Constants.DEFAULT_AKTO_DASHBOARD_URL + "/copilot/oauth/callback";
            String stateValue  = accountId + "|" + roleId;

            authorizationUrl = String.format(AUTH_ENDPOINT_TEMPLATE, copilotParam.getTenantId())
                + "?response_type=code"
                + "&response_mode=query"
                + "&client_id=" + encode(copilotParam.getClientId())
                + "&redirect_uri=" + encode(callbackUrl)
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(stateValue);

            logger.infoAndAddToDb("getAuthorizationUrl: generated for accountId=" + accountId + " roleId=" + roleId);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("getAuthorizationUrl: failed: " + e.getMessage());
            addActionError("Failed to build authorization URL");
            return Action.ERROR.toUpperCase();
        }
    }

    public String oauthCallback() {
        String dashboardUrl = Constants.DEFAULT_AKTO_DASHBOARD_URL;
        String rolesListUrl = dashboardUrl + "/dashboard/testing/roles";

        if (error != null && !error.isEmpty()) {
            logger.errorAndAddToDb("oauthCallback: OAuth error=" + error + " desc=" + error_description);
            redirectUrl = rolesListUrl + "?copilotOauthError=" + encode(error);
            return Action.ERROR.toUpperCase();
        }

        if (code == null || code.isEmpty() || state == null || state.isEmpty()) {
            logger.errorAndAddToDb("oauthCallback: missing code or state");
            redirectUrl = rolesListUrl + "?copilotOauthError=missing_params";
            return Action.ERROR.toUpperCase();
        }

        String[] parts = state.split("\\|", 2);
        if (parts.length != 2) {
            logger.errorAndAddToDb("oauthCallback: invalid state=" + state);
            redirectUrl = rolesListUrl + "?copilotOauthError=invalid_state";
            return Action.ERROR.toUpperCase();
        }

        int accountId;
        String callbackRoleId;
        try {
            accountId      = Integer.parseInt(parts[0]);
            callbackRoleId = parts[1];
        } catch (NumberFormatException e) {
            logger.errorAndAddToDb("oauthCallback: invalid accountId in state=" + state);
            redirectUrl = rolesListUrl + "?copilotOauthError=invalid_state";
            return Action.ERROR.toUpperCase();
        }

        Context.accountId.set(accountId);

        TestRoles role = TestRolesDao.instance.findOne(Filters.eq("_id", new ObjectId(callbackRoleId)));
        if (role == null) {
            logger.errorAndAddToDb("oauthCallback: TestRole not found roleId=" + callbackRoleId);
            redirectUrl = rolesListUrl + "?copilotOauthError=role_not_found";
            return Action.ERROR.toUpperCase();
        }

        String roleSettingsUrl = dashboardUrl + "/dashboard/testing/roles/details?name=" + encode(role.getName());

        CopilotOAuthAuthParam copilotParam = findCopilotParam(role);
        if (copilotParam == null) {
            logger.errorAndAddToDb("oauthCallback: no COPILOT_OAUTH param in role=" + callbackRoleId);
            redirectUrl = roleSettingsUrl + "&copilotOauthError=param_not_found";
            return Action.ERROR.toUpperCase();
        }

        String callbackUrl = dashboardUrl + "/copilot/oauth/callback";

        try {
            FormBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", copilotParam.getClientId())
                .add("client_secret", copilotParam.getClientSecret())
                .add("redirect_uri", callbackUrl)
                .add("scope", SCOPE)
                .build();

            Request request = new Request.Builder()
                .url(String.format(TOKEN_ENDPOINT_TEMPLATE, copilotParam.getTenantId()))
                .post(formBody)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    logger.errorAndAddToDb("oauthCallback: token exchange failed status=" + response.code());
                    redirectUrl = roleSettingsUrl + "&copilotOauthError=token_exchange_failed";
                    return Action.ERROR.toUpperCase();
                }

                JsonNode json = objectMapper.readTree(body);
                String refreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;

                if (refreshToken == null || refreshToken.isEmpty()) {
                    logger.errorAndAddToDb("oauthCallback: no refresh_token in response for role=" + callbackRoleId);
                    redirectUrl = roleSettingsUrl + "&copilotOauthError=no_refresh_token";
                    return Action.ERROR.toUpperCase();
                }

                copilotParam.setRefreshToken(refreshToken);
                TestRolesDao.instance.updateOneNoUpsert(
                    Filters.eq("_id", new ObjectId(callbackRoleId)),
                    Updates.set(TestRoles.AUTH_WITH_COND_LIST, role.getAuthWithCondList())
                );

                logger.infoAndAddToDb("oauthCallback: refresh token saved for roleId=" + callbackRoleId);
                redirectUrl = roleSettingsUrl + "&copilotOauthSuccess=true";
                return Action.SUCCESS.toUpperCase();
            }

        } catch (Exception e) {
            logger.errorAndAddToDb("oauthCallback: exception: " + e.getMessage());
            redirectUrl = roleSettingsUrl + "&copilotOauthError=exception";
            return Action.ERROR.toUpperCase();
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value != null ? value : "", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value != null ? value : "";
        }
    }

    private static CopilotOAuthAuthParam findCopilotParam(TestRoles role) {
        if (role.getAuthWithCondList() == null) return null;
        for (AuthWithCond authWithCond : role.getAuthWithCondList()) {
            if (authWithCond.getAuthMechanism() == null) continue;
            List<AuthParam> params = authWithCond.getAuthMechanism().getAuthParams();
            if (params == null) continue;
            for (AuthParam param : params) {
                if (param instanceof CopilotOAuthAuthParam) {
                    return (CopilotOAuthAuthParam) param;
                }
            }
        }
        return null;
    }
}
