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

        fieldNames = new String[] { GenericAgentConversation.USER_ID, "lastUpdatedAt" };
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

    /**
     * Strict match on the requesting user, unlike getContextSourceFilter(): a chat's
     * content is private to the user who had it, so conversations from before this
     * field existed (userId missing) are intentionally excluded rather than shown to
     * everyone, to avoid leaking one user's chat history to another.
     */
    public Bson getUserFilter() {
        Integer userId = Context.userId.get();
        if (userId == null) {
            return Filters.exists(GenericAgentConversation.USER_ID, false);
        }
        return Filters.eq(GenericAgentConversation.USER_ID, userId);
    }

}
