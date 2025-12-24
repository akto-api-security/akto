package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.AgentConversation;

public class AgentConversationDao extends AccountsContextDao<AgentConversation> {

    public static final AgentConversationDao instance = new AgentConversationDao();

    @Override
    public String getCollName() {
        return "agent_conversations";
    }

    @Override
    public Class<AgentConversation> getClassT() {
        return AgentConversation.class;
    }

    public void createIndexIfAbsent() {
        String[] fieldNames = { "lastUpdatedAt" };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

}
