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
     * order), carrying only what was sent to the target and what it replied. A conversation with no stored turns is
     * absent from the map. Served by the conversationId index.
     */
    public Map<String, List<AgentConversationResult>> findTurns(List<String> conversationIds) {
        Map<String, List<AgentConversationResult>> turns = new LinkedHashMap<>();
        if (conversationIds == null || conversationIds.isEmpty()) return turns;
        List<AgentConversationResult> rows = findAll(Filters.in(CONVERSATION_ID, conversationIds),
                0, conversationIds.size() * MAX_TURNS_PER_CONVERSATION, Sorts.ascending("_id"),
                Projections.include(CONVERSATION_ID, FINAL_SENT_PROMPT, RESPONSE));
        for (AgentConversationResult row : rows) {
            List<AgentConversationResult> conversation = turns.computeIfAbsent(row.getConversationId(), id -> new ArrayList<>());
            if (conversation.size() < MAX_TURNS_PER_CONVERSATION) conversation.add(row);
        }
        return turns;
    }
}
