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

    /**
     * Filter for the chat-history listing: matches on contextSource, treating
     * documents from before that field existed (or an unset current context) as
     * belonging to API — the system-wide default context (UserDetailsFilter
     * defaults Context.contextSource to API; the frontend defaults
     * dashboardCategory to "API Security") — since Ask Akto conversations predate
     * the contextSource concept entirely and have no prior tab affinity, unlike
     * GuardrailPoliciesDao's AGENTIC fallback which is specific to that feature's
     * Agentic-Security origin.
     */
    public Bson getContextSourceFilter() {
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        if (contextSource == null || contextSource == CONTEXT_SOURCE.API) {
            return Filters.or(
                Filters.eq(GenericAgentConversation.CONTEXT_SOURCE, CONTEXT_SOURCE.API.name()),
                Filters.exists(GenericAgentConversation.CONTEXT_SOURCE, false)
            );
        }
        return Filters.eq(GenericAgentConversation.CONTEXT_SOURCE, contextSource.name());
    }

}
