package com.akto.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;

public class AgenticUtils {
    private static final AgentClient agentClient = new AgentClient(Constants.AGENT_BASE_URL);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static Map<String,String> getMcpAuthPairs() {

        TestRoles role = dataActor.fetchTestRole("MCP_AUTHENTICATION_ROLE");
        AuthMechanism authMechanism = role.findMatchingAuthMechanism(null);

        List<AuthParam> authParams = authMechanism.getAuthParams();
        Map<String,String> authPairs = new HashMap<>();
        for(AuthParam authParam : authParams) {
            authPairs.put(authParam.getKey(), authParam.getValue());
        }
        return authPairs;
    }

    public static void checkAndInitializeAgent(Set<Integer> apiCollectionIds, boolean isTestEditor, FilterNode filterNode) {
        if(apiCollectionIds.isEmpty()){
            return;
        }
        boolean isMcpCollection = false;
        if(apiCollectionIds.size() == 1){
            int apiCollectionId = 0;
            for(int i : apiCollectionIds){
                apiCollectionId = i;
            }
            ApiCollection collection = dataActor.fetchApiCollectionMeta(apiCollectionId);
            isMcpCollection = collection.isMcpCollection();
        }else{
            List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();  
            for(ApiCollection apiCollection : apiCollections){
                if(apiCollection.isMcpCollection() && apiCollectionIds.contains(apiCollection.getId())){
                    isMcpCollection = true;
                    break;
                }
            }
        }

        boolean shouldInitializeAgent = isMcpCollection;
        if(isTestEditor){
            shouldInitializeAgent = shouldInitializeAgent && ConfigParser.isFilterNodeValidForAgenticTest(filterNode);
        }
        
        if(shouldInitializeAgent){
            if(agentClient.performHealthCheck()){
                Map<String,String> authPairs = getMcpAuthPairs();
                String sseUrl = authPairs.get("sseCallBackUrl");
                String authorization = authPairs.get("authorization");
                if(StringUtils.isEmpty(sseUrl) || StringUtils.isEmpty(authorization)){
                    return;
                }
                agentClient.initializeAgent(sseUrl, authorization);
            }
        }
    }

    public static String getTestModeFromRole() {
        TestRoles role = dataActor.fetchTestRole("MCP_AUTHENTICATION_ROLE");
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