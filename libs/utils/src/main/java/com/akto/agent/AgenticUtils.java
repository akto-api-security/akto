package com.akto.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.mongodb.client.model.Filters;

public class AgenticUtils {
    private static final AgentClient agentClient = new AgentClient(Constants.AGENT_BASE_URL);

    private static Map<String,String> getMcpAuthPairs() {
        TestRoles role = TestRolesDao.instance.findOne(Filters.eq(TestRoles.NAME, "MCP_AUTHENTICATION_ROLE"));
        if (role == null) {
            return null;
        }
        AuthMechanism authMechanism = role.findMatchingAuthMechanism(null);
        List<AuthParam> authParams = authMechanism.getAuthParams();
        Map<String,String> authPairs = new HashMap<>();
        for(AuthParam authParam : authParams) {
            authPairs.put(authParam.getKey(), authParam.getValue());
        }
        return authPairs;
    }

    // TODO: run test with role for test editor.
    public static void checkAndInitializeAgent(String conversationId, RawApi rawApi, int apiCollectionId) {
        if (agentClient.performHealthCheck()) {

            ApiCollection apiCollection = ApiCollectionsDao.instance.getMetaForId(apiCollectionId);
            boolean isMcpCollection = apiCollection.isMcpCollection();

            if (rawApi != null && !isMcpCollection) {
                OriginalHttpRequest request = rawApi.getRequest();
                String url = request.getFullUrlWithParams();
                String requestBody = request.getBody();
                String requestHeaders = request.fetchHeadersJsonString();
                agentClient.initializeAgent(url, requestHeaders, requestBody, conversationId);
                return;
            }
            Map<String, String> authPairs = getMcpAuthPairs();
            String sseUrl = authPairs.get("sseCallBackUrl");
            String authorization = authPairs.get("authorization");
            if (StringUtils.isEmpty(sseUrl) || StringUtils.isEmpty(authorization)) {
                return;
            }
            agentClient.initializeAgent(sseUrl, authorization);
        }
    }

    // TODO: this is not actually being used.
    // DELETE IT.
    public static String getTestModeFromRole() {
        TestRoles role = TestRolesDao.instance.findOne(Filters.eq(TestRoles.NAME, "MCP_AUTHENTICATION_ROLE"));
        if (role == null) {
            return "auto";
        }
        AuthMechanism authMechanism = role.findMatchingAuthMechanism(null);
        if(authMechanism == null || authMechanism.getType() == null){
            return "auto";
        }

        if(authMechanism.getType().equals(LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString())){
            return "userMcpAgent";
        }else{
            return "userAiAgent";
        }
    }
}