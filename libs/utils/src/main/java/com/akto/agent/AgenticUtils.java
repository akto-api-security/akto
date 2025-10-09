package com.akto.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.TestRoles;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class AgenticUtils {
    private static final AgentClient agentClient = new AgentClient(Constants.AGENT_BASE_URL);

    private static Map<String,String> getMcpAuthPairs() {

        TestRoles role = TestRolesDao.instance.findOne(Filters.eq(TestRoles.NAME, "MCP_AUTHENTICATION_ROLE"));
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
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(Filters.in(Constants.ID, apiCollectionIds), Projections.include(Constants.ID, ApiCollection.TAGS_STRING));
        boolean isMcpCollection = false;
        for(ApiCollection apiCollection : apiCollections){
            if(apiCollection.isMcpCollection()){
                isMcpCollection = true;
                break;
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
}