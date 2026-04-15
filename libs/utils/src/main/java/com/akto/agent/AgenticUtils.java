package com.akto.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.test_editor.execution.Operations;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;

public class AgenticUtils {
    private static final AgentClient agentClient = new AgentClient(Constants.AGENT_BASE_URL);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static Map<String,String> getMcpAuthPairs(String roleName) {

        TestRoles role = dataActor.fetchTestRole(roleName);
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

    public static void checkAndInitializeAgent(String conversationId, RawApi rawApi, int apiCollectionId) {
        if (agentClient.performHealthCheck()) {

            ApiCollection apiCollection = dataActor.fetchApiCollectionMeta(apiCollectionId);
            boolean isMcpCollection = apiCollection.isMcpCollection();

            if (rawApi != null && !isMcpCollection) {
                OriginalHttpRequest request = rawApi.getRequest();

                // check for Orchestrator-agent-role as well, in case of auth headers needed for n8n agents
                Map<String, String> authPairs = getMcpAuthPairs("ORCHESTRATOR_AGENT_ROLE");
                if(authPairs != null) {
                    String agentUrl = authPairs.get("x-agent-url");
                    String agentToken = authPairs.get("x-agent-key");
                    if(StringUtils.isEmpty(agentUrl) || StringUtils.isEmpty(agentToken)) {
                        return;
                    }
                    Operations.addHeader(rawApi, "x-agent-key", agentToken);
                    Operations.addHeader(rawApi, "x-agent-url", agentUrl);
                }
                String url = request.getFullUrlWithParams();
                String requestBody = request.getBody();
                String requestHeaders = request.fetchHeadersJsonString();
                String requestMethod = request.getMethod();

                agentClient.initializeAgent(url, requestHeaders, requestBody, requestMethod, conversationId);
                return;
            }
            Map<String, String> authPairs = getMcpAuthPairs("MCP_AUTHENTICATION_ROLE");
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
        TestRoles role = dataActor.fetchTestRole("MCP_AUTHENTICATION_ROLE");
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