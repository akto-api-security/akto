package com.akto.agent_risk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.kafka.AgentRiskKafkaProducer;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import org.bson.conversions.Bson;

/**
 * Rolls the batch-max composite onto the ApiCollection doc. One ordered
 * bulkWrite per account. upsert=false: skip collections that do not exist yet.
 */
public class AgentRiskApiCollectionWriter {

    private AgentRiskApiCollectionWriter() {}

    public static void persist(List<AgentRiskScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return;
        }
        Map<Integer, Map<Integer, AgentRiskScore>> byAccount = new HashMap<>();
        for (AgentRiskScore s : scores) {
            Integer collectionId = collectionIdOf(s);
            if (collectionId == null) {
                continue;
            }
            Map<Integer, AgentRiskScore> byCollection = byAccount.computeIfAbsent(
                    s.getAccountId(), k -> new TreeMap<>());
            AgentRiskScore prev = byCollection.get(collectionId);
            if (prev == null || s.getComposite() > prev.getComposite()) {
                byCollection.put(collectionId, s);
            }
        }
        if (byAccount.isEmpty()) {
            return;
        }

        int now = Context.now();
        int decayBefore = now - AgentRiskKafkaProducer.getApiInfoDecaySecs();
        Integer previous = Context.accountId.get();
        try {
            for (Map.Entry<Integer, Map<Integer, AgentRiskScore>> accountEntry : byAccount.entrySet()) {
                Context.accountId.set(accountEntry.getKey());
                List<WriteModel<ApiCollection>> updates = new ArrayList<>();
                for (Map.Entry<Integer, AgentRiskScore> collectionEntry : accountEntry.getValue().entrySet()) {
                    AgentRiskScore s = collectionEntry.getValue();
                    int incoming = s.getComposite();
                    Bson idFilter = Filters.eq(ApiCollection.ID, collectionEntry.getKey());
                    Bson shouldWrite = Filters.or(
                            Filters.exists(ApiCollection.AGENT_RISK_SCORE, false),
                            Filters.lt(ApiCollection.AGENT_RISK_SCORE, incoming),
                            Filters.lt(ApiCollection.AGENT_RISK_LAST_CALCULATED_TIME, decayBefore)
                    );
                    updates.add(new UpdateOneModel<>(
                            Filters.and(idFilter, shouldWrite),
                            Updates.combine(
                                    Updates.set(ApiCollection.AGENT_RISK_SCORE, incoming),
                                    Updates.set(ApiCollection.AGENT_RISK_LAST_CALCULATED_TIME, now),
                                    Updates.set(ApiCollection.AGENT_RISK_HASH, s.getHash() == null ? "" : s.getHash())
                            ),
                            new UpdateOptions().upsert(false)
                    ));
                }
                if (!updates.isEmpty()) {
                    ApiCollectionsDao.instance.getMCollection().bulkWrite(updates, new BulkWriteOptions().ordered(true));
                }
            }
        } catch (Exception ignored) {
            // scoring already succeeded; collection rollup is best-effort
        } finally {
            if (previous == null) {
                Context.accountId.remove();
            } else {
                Context.accountId.set(previous);
            }
        }
    }

    static Integer collectionIdOf(AgentRiskScore s) {
        if (s == null || s.getApiCollectionId() == null || s.getApiCollectionId() == 0) {
            return null;
        }
        return s.getApiCollectionId();
    }
}
