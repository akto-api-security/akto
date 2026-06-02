package com.akto.action;

import com.akto.dao.OAuthStatesDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.OAuthState;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.CopilotOAuthAuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CopilotStudioAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(CopilotStudioAction.class, LogDb.DASHBOARD);

    private static final String AUTH_ENDPOINT_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";
    private static final String SCOPE = "https://api.powerplatform.com/CopilotStudio.Copilots.Invoke offline_access";

    @Setter private String roleId;

    @Getter private String authorizationUrl;

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
            String nonce = UUID.randomUUID().toString();
            int expiresAt = Context.now() + OAuthState.EXPIRY_SECONDS;
            Map<String, String> stateData = new HashMap<>();
            stateData.put("accountId", String.valueOf(accountId));
            stateData.put("roleId", roleId);
            OAuthStatesDao.instance.insertOne(new OAuthState(nonce, stateData, expiresAt));

            authorizationUrl = String.format(AUTH_ENDPOINT_TEMPLATE, copilotParam.getTenantId())
                + "?response_type=code"
                + "&response_mode=query"
                + "&client_id=" + HttpRequestResponseUtils.encode(copilotParam.getClientId())
                + "&redirect_uri=" + HttpRequestResponseUtils.encode(callbackUrl)
                + "&scope=" + HttpRequestResponseUtils.encode(SCOPE)
                + "&state=" + HttpRequestResponseUtils.encode(nonce);

            logger.infoAndAddToDb("getAuthorizationUrl: generated for accountId=" + accountId + " roleId=" + roleId);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb("getAuthorizationUrl: failed: " + e.getMessage());
            addActionError("Failed to build authorization URL");
            return Action.ERROR.toUpperCase();
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
