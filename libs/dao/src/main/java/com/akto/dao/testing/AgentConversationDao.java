package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.testing.GenericAgentConversation;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class AgentConversationDao extends AccountsContextDao<GenericAgentConversation> {

    public static final AgentConversationDao instance = new AgentConversationDao();

    @Override
    public String getCollName() {
        return "generic_agent_conversations";
    }

    @Override
    public Class<GenericAgentConversation> getClassT() {
        return GenericAgentConversation.class;
    }

    public void createIndexIfAbsent() {
        String[] fieldNames = { "lastUpdatedAt" };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[] { "conversationId" };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[] { GenericAgentConversation.CONTEXT_SOURCE, "lastUpdatedAt" };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public Bson getContextSourceFilter() {
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        if (contextSource == null) {
            return Filters.exists(GenericAgentConversation.CONTEXT_SOURCE, false);
        }
        return Filters.or(
            Filters.eq(GenericAgentConversation.CONTEXT_SOURCE, contextSource.name()),
            Filters.exists(GenericAgentConversation.CONTEXT_SOURCE, false)
        );
    }

}
