package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.AgentConversationResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentConversationResultDao extends AccountsContextDao<AgentConversationResult> {

    public static final AgentConversationResultDao instance = new AgentConversationResultDao();

    public static final String CONVERSATION_ID = "conversationId";
    public static final String FINAL_SENT_PROMPT = "finalSentPrompt";
    public static final String RESPONSE = "response";
    /** A conversation holds a handful of turns; this only bounds a read against a corrupt or replayed id. */
    public static final int MAX_TURNS_PER_CONVERSATION = 10;

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

    /**
     * The stored turns of each conversation in the order they were written (one document per turn, inserted in turn
     * order), carrying only what was sent to the target and what it replied. At most MAX_TURNS_PER_CONVERSATION + 1 are
     * read per conversation, so a caller can tell a cut-off conversation from a complete one; a conversation with no
     * stored turns is absent from the map. One indexed query per conversation (the callers pass a few dozen at most),
     * because a single shared limit let one long conversation use up the rows of all the others.
     */
    public Map<String, List<AgentConversationResult>> findTurns(List<String> conversationIds) {
        Map<String, List<AgentConversationResult>> turns = new LinkedHashMap<>();
        if (conversationIds == null) return turns;
        for (String conversationId : conversationIds) {
            List<AgentConversationResult> rows = findAll(Filters.eq(CONVERSATION_ID, conversationId),
                    0, MAX_TURNS_PER_CONVERSATION + 1, Sorts.ascending("_id"),
                    Projections.include(CONVERSATION_ID, FINAL_SENT_PROMPT, RESPONSE));
            if (!rows.isEmpty()) turns.put(conversationId, rows);
        }
        return turns;
    }
}
