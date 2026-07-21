package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agentic_sessions.AgentQueryTopicMapping;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Backs the ADX search backend's topic/subTopic write-back (see {@link AgentQueryTopicMapping}).
 * Only used when SEARCH_BACKEND=AZURE_ADX; the Elasticsearch backend keeps writing topic/subTopic
 * directly onto its own documents and never touches this collection.
 */
public class AgentQueryTopicMappingDao extends AccountsContextDao<AgentQueryTopicMapping> {

    public static final AgentQueryTopicMappingDao instance = new AgentQueryTopicMappingDao();

    public void createIndicesIfAbsent() {
        // Equality fields (topic, subTopic) before the range field (timestamp), per standard
        // compound-index ordering — keeps the topic-filter-push-down lookup time-bounded.
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{AgentQueryTopicMapping.TOPIC, AgentQueryTopicMapping.SUB_TOPIC, AgentQueryTopicMapping.TIMESTAMP},
            true);
    }

    /** Display-attach direction: bounded lookup by docId for an already-narrowed result set. */
    public Map<String, AgentQueryTopicMapping> bulkGet(Collection<String> docIds) {
        if (docIds == null || docIds.isEmpty()) return Collections.emptyMap();
        List<AgentQueryTopicMapping> found = findAll(Filters.in("_id", docIds));
        if (found == null) return Collections.emptyMap();
        Map<String, AgentQueryTopicMapping> result = new HashMap<>();
        for (AgentQueryTopicMapping entry : found) {
            if (entry.getId() != null) result.put(entry.getId(), entry);
        }
        return result;
    }

    /** ADX-side substitute for ElasticSearchClient.bulkUpdateTopics: upserts instead of a field update. */
    public void bulkUpsert(List<AgentQueryTopicMapping> entries) {
        if (entries == null || entries.isEmpty()) return;
        List<WriteModel<AgentQueryTopicMapping>> ops = new ArrayList<>();
        for (AgentQueryTopicMapping entry : entries) {
            if (entry.getId() == null || entry.getId().isEmpty()) continue;
            ops.add(new UpdateOneModel<>(
                Filters.eq("_id", entry.getId()),
                Updates.combine(
                    Updates.setOnInsert("_id", entry.getId()),
                    Updates.set(AgentQueryTopicMapping.TOPIC, entry.getTopic()),
                    Updates.set(AgentQueryTopicMapping.SUB_TOPIC, entry.getSubTopic()),
                    Updates.set(AgentQueryTopicMapping.TIMESTAMP, entry.getTimestamp())
                ),
                new UpdateOptions().upsert(true)
            ));
        }
        if (!ops.isEmpty()) bulkWrite(ops, new BulkWriteOptions().ordered(false));
    }

    /**
     * Filter-push-down direction: docIds tagged with any of {@code topics}/{@code subTopics}
     * within [startMs, endMs). Bounded by {@code limit}; if the true count exceeds it, only
     * {@code limit} ids are returned and {@link TopicLookupResult#truncated} is set so the
     * caller can surface a "narrow your time range" condition instead of silently dropping
     * results.
     */
    public TopicLookupResult findDocIdsByTopics(Collection<String> topics, Collection<String> subTopics,
                                                 long startMs, long endMs, int limit) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.gte(AgentQueryTopicMapping.TIMESTAMP, startMs));
        filters.add(Filters.lt(AgentQueryTopicMapping.TIMESTAMP, endMs));
        if (topics != null && !topics.isEmpty()) filters.add(Filters.in(AgentQueryTopicMapping.TOPIC, topics));
        if (subTopics != null && !subTopics.isEmpty()) filters.add(Filters.in(AgentQueryTopicMapping.SUB_TOPIC, subTopics));

        List<AgentQueryTopicMapping> found = findAll(Filters.and(filters), 0, limit + 1, null);
        if (found == null || found.isEmpty()) return new TopicLookupResult(new ArrayList<>(), false);

        boolean truncated = found.size() > limit;
        List<String> docIds = new ArrayList<>();
        for (int i = 0; i < Math.min(found.size(), limit); i++) {
            AgentQueryTopicMapping m = found.get(i);
            if (m.getId() != null) docIds.add(m.getId());
        }
        return new TopicLookupResult(docIds, truncated);
    }

    /** Distinct topic/subTopic values for the PromptsView column filters (ADX backend only). */
    public List<String> distinctTopics(int limit) {
        return distinctFieldValues(AgentQueryTopicMapping.TOPIC, limit);
    }

    public List<String> distinctSubTopics(int limit) {
        return distinctFieldValues(AgentQueryTopicMapping.SUB_TOPIC, limit);
    }

    private List<String> distinctFieldValues(String field, int limit) {
        Set<String> values = findDistinctFields(field, String.class, Filters.exists(field));
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (v == null || v.isEmpty()) continue;
            out.add(v);
            if (out.size() >= limit) break;
        }
        return out;
    }

    public static class TopicLookupResult {
        public final List<String> docIds;
        public final boolean truncated;
        public TopicLookupResult(List<String> docIds, boolean truncated) {
            this.docIds = docIds;
            this.truncated = truncated;
        }
    }

    @Override
    public String getCollName() {
        return "agent_query_topic_mapping";
    }

    @Override
    public Class<AgentQueryTopicMapping> getClassT() {
        return AgentQueryTopicMapping.class;
    }
}
