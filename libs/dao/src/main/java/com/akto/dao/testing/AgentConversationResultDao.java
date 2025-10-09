package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.AgentConversationResult;

public class AgentConversationResultDao extends AccountsContextDao<AgentConversationResult> {

    public static final AgentConversationResultDao instance = new AgentConversationResultDao();

    @Override
    public String getCollName() {
        return "agent_conversation_results";
    }

    @Override
    public Class<AgentConversationResult> getClassT() {
        return AgentConversationResult.class;
    }

    public void createIndexIfAbsent() {
        String[] fieldNames = { "conversationId" };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

}
