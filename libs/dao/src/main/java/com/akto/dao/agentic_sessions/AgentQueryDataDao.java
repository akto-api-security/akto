package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.agentic_sessions.AgentQueryData;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AgentQueryDataDao extends AccountsContextDao<AgentQueryData> {

    public static final AgentQueryDataDao instance = new AgentQueryDataDao();

    private static final long TTL_DAYS = 14;

    public void createIndicesIfAbsent() {
        // TTL index: auto-delete records older than 14 days
        Bson ttlIndex = Indexes.ascending(AgentQueryData.TIME_STAMP);
        IndexOptions ttlOptions = new IndexOptions()
                .name("timeStamp_ttl")
                .expireAfter(TTL_DAYS * 24 * 60 * 60, TimeUnit.SECONDS);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), ttlIndex, ttlOptions);

        // Compound index for cron queries: fetch by user within a time window
        String[] compoundFields = new String[]{
                AgentQueryData.SERVICE_ID,
                AgentQueryData.UNIQUE_USER_ID,
                AgentQueryData.TIME_STAMP
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), compoundFields, false);
    }

    public AgentQueryData createAgentQueryDataFromHttpResponseParams(HttpResponseParams httpResponseParam) {
        AgentQueryData agentQueryData = new AgentQueryData();

        Map<String, List<String>> requestHeaders = httpResponseParam.getRequestParams().getHeaders();

        agentQueryData.setServiceId(getFirstHeader(requestHeaders, "host"));
        agentQueryData.setUserName(getFirstHeader(requestHeaders, (AgentQueryData.HEADER_PREFIX + AgentQueryData.HEADER_USER_EMAIL)));
        agentQueryData.setSessionIdentifier(getFirstHeader(requestHeaders, (AgentQueryData.HEADER_PREFIX + AgentQueryData.HEADER_SESSION_ID)));
        agentQueryData.setConversationId(getFirstHeader(requestHeaders, (AgentQueryData.HEADER_PREFIX + AgentQueryData.HEADER_CONVERSATION_ID)));

        agentQueryData.setQueryPayload(httpResponseParam.getRequestParams().getPayload());
        agentQueryData.setResponsePayload(httpResponseParam.getPayload());
        agentQueryData.setTimeStamp(Context.now());

        return agentQueryData;
    }

    private static String getFirstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null) return null;
        List<String> values = headers.get(name);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    @Override
    public String getCollName() {
        return "agent_query_data";
    }

    @Override
    public Class<AgentQueryData> getClassT() {
        return AgentQueryData.class;
    }
}
