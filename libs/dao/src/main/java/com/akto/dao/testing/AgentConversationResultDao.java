package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.testing.GenericAgentConversation;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentConversationResultDao extends AccountsContextDao<AgentConversationResult> {

    public static final AgentConversationResultDao instance = new AgentConversationResultDao();
    private static final int MESSAGE_MAX_CHARS = 400;

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
     * Argus (AGENTIC) red-team read: the real validated (validation=true) verdict for a small,
     * already-scoped conversationId list — see AgentFindingGroup#getSample, whose ids (for the
     * red-team vuln groups) come from an RBAC/dashboard-context-scoped vulnerable_testing_run_
     * results read. This collection carries NO RBAC scoping of its own (it's a plain
     * AccountsContextDao, not AccountsContextDaoWithRbac), so an empty id list is refused outright
     * rather than ever risking an unscoped `$in: []` that Mongo would treat as "match nothing"
     * today but that a future caller could get wrong. Off the existing unique `conversationId`
     * index. Returns the existing AgentConversationResult DTO directly, with only
     * conversationId/validationMessage/remediationMessage/lastUpdatedAt populated — no new class
     * needed for a row this DTO already models.
     *
     * Multiple turn documents can share one conversationId (one per message in the exchange) —
     * grouped down to a single row per conversationId (`$first` after sorting newest-first) rather
     * than returned as N near-duplicate rows. Messages are truncated to MESSAGE_MAX_CHARS server-
     * side; `conversation`/`response`/`prompt` are never projected at all.
     */
    public List<AgentConversationResult> findValidatedSummaries(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyList();
        }

        Bson filter = Filters.in(GenericAgentConversation._CONVERSATION_ID, conversationIds);

        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        pipeline.add(Aggregates.sort(Sorts.descending(GenericAgentConversation._TIMESTAMP)));
        pipeline.add(Aggregates.project(Projections.fields(
                Projections.excludeId(),
                Projections.include(GenericAgentConversation._CONVERSATION_ID, GenericAgentConversation._TIMESTAMP),
                Projections.computed("validationMessage", new BasicDBObject("$substrCP", java.util.Arrays.asList(
                        new BasicDBObject("$ifNull", java.util.Arrays.asList("$" + AgentConversationResult.VALIDATION_MESSAGE, "")), 0, MESSAGE_MAX_CHARS))),
                Projections.computed("remediationMessage", new BasicDBObject("$substrCP", java.util.Arrays.asList(
                        new BasicDBObject("$ifNull", java.util.Arrays.asList("$" + AgentConversationResult.REMEDIATION_MESSAGE, "")), 0, MESSAGE_MAX_CHARS))))));
        pipeline.add(Aggregates.group("$" + GenericAgentConversation._CONVERSATION_ID,
                Accumulators.first("validationMessage", "$" + AgentConversationResult.VALIDATION_MESSAGE),
                Accumulators.first("remediationMessage", "$" + AgentConversationResult.REMEDIATION_MESSAGE),
                Accumulators.first(GenericAgentConversation._TIMESTAMP, "$" + GenericAgentConversation._TIMESTAMP)));

        List<AgentConversationResult> result = new ArrayList<>();
        for (Object o : getMCollection().aggregate(pipeline, BasicDBObject.class).into(new ArrayList<>())) {
            BasicDBObject doc = (BasicDBObject) o;
            AgentConversationResult row = new AgentConversationResult();
            row.setConversationId(doc.getString("_id"));
            row.setValidationMessage(doc.getString("validationMessage"));
            row.setRemediationMessage(doc.getString("remediationMessage"));
            row.setLastUpdatedAt(doc.getInt(GenericAgentConversation._TIMESTAMP));
            result.add(row);
        }
        return result;
    }
}
