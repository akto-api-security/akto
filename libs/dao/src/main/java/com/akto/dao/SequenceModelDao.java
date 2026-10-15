package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.SequenceCount;
import com.akto.dto.SequenceModel;
import com.akto.util.SequenceVomc;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SequenceModelDao extends AccountsContextDao<SequenceModel> {

    public static final SequenceModelDao instance = new SequenceModelDao();

    public static final int WINDOW_BUCKETS = 24;

    private SequenceModelDao() {}

    @Override
    public String getCollName() {
        return "sequence_model";
    }

    @Override
    public Class<SequenceModel> getClassT() {
        return SequenceModel.class;
    }

    public void createIndicesIfAbsent() {
        createCollectionIfAbsent(getDBName(), getCollName(), new CreateCollectionOptions());
        MCollection.createUniqueIndex(getDBName(), getCollName(),
                new String[] { SequenceModel.CONTEXT_KEY, SequenceModel.NEXT }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SequenceModel.COLLAPSED, SequenceModel.ORDER }, true);
    }

    public static class ContextStats {
        String parentContextKey;
        List<String> context;
        final Map<String, Long> counts = new HashMap<>();
        final Map<String, Integer> apiCollectionIds = new HashMap<>();
    }

    public void writeSequenceCounts(List<SequenceCount> sequenceCounts) {
        Map<String, String> touched = SequenceCountDao.instance.incrementCounts(sequenceCounts);
        if (touched.isEmpty()) return;
        recompute(touched, Context.now());
    }

    public void recompute(Map<String, String> touched, int now) {
        int windowStart = (now / SequenceCountDao.BUCKET_SECONDS) * SequenceCountDao.BUCKET_SECONDS
                - (WINDOW_BUCKETS - 1) * SequenceCountDao.BUCKET_SECONDS;

        Set<String> affected = new HashSet<>(touched.keySet());
        SequenceCountDao.instance.getMCollection().distinct(SequenceCount.CONTEXT_KEY,
                Filters.and(
                        Filters.in(SequenceCount.PARENT_CONTEXT_KEY, touched.keySet()),
                        Filters.gte(SequenceCount.BUCKET_START, windowStart)
                ), String.class).into(affected);

        Set<String> needed = new HashSet<>(affected);
        needed.add("");
        for (String parent : touched.values()) {
            if (parent != null) needed.add(parent);
        }

        Map<String, ContextStats> stats = aggregateCounts(needed, windowStart);
        List<SequenceModel> models = buildModels(affected, stats, now);

        List<WriteModel<SequenceModel>> writeModels = new ArrayList<>();
        for (SequenceModel m : models) {
            Bson filter = Filters.and(
                    Filters.eq(SequenceModel.CONTEXT_KEY, m.getContextKey()),
                    Filters.eq(SequenceModel.NEXT, m.getNext())
            );
            Bson update = Updates.combine(
                    Updates.set(SequenceModel.API_COLLECTION_ID, m.getApiCollectionId()),
                    Updates.set(SequenceModel.CONTEXT, m.getContext()),
                    Updates.set(SequenceModel.PARENT_CONTEXT_KEY, m.getParentContextKey()),
                    Updates.set(SequenceModel.ORDER, m.getOrder()),
                    Updates.set(SequenceModel.COUNT_24H, m.getCount24h()),
                    Updates.set(SequenceModel.CONTEXT_TOTAL_24H, m.getContextTotal24h()),
                    Updates.set(SequenceModel.PROBABILITY, m.getProbability()),
                    Updates.set(SequenceModel.CI_LOW, m.getCiLow()),
                    Updates.set(SequenceModel.CI_HIGH, m.getCiHigh()),
                    Updates.set(SequenceModel.COLLAPSED, m.isCollapsed()),
                    Updates.set(SequenceModel.PRECEDENCE, m.getPrecedence()),
                    Updates.set(SequenceModel.UPDATED_AT, m.getUpdatedAt())
            );
            writeModels.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }
        if (!writeModels.isEmpty()) {
            getMCollection().bulkWrite(writeModels, new BulkWriteOptions().ordered(false));
        }
        getMCollection().deleteMany(Filters.and(
                Filters.in(SequenceModel.CONTEXT_KEY, affected),
                Filters.lt(SequenceModel.UPDATED_AT, now)
        ));
    }

    private Map<String, ContextStats> aggregateCounts(Set<String> contextKeys, int windowStart) {
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.and(
                        Filters.in(SequenceCount.CONTEXT_KEY, contextKeys),
                        Filters.gte(SequenceCount.BUCKET_START, windowStart)
                )),
                Aggregates.group(
                        new Document("c", "$" + SequenceCount.CONTEXT_KEY).append("n", "$" + SequenceCount.NEXT),
                        Accumulators.sum(SequenceCount.COUNT, "$" + SequenceCount.COUNT),
                        Accumulators.first(SequenceCount.API_COLLECTION_ID, "$" + SequenceCount.API_COLLECTION_ID),
                        Accumulators.first(SequenceCount.CONTEXT, "$" + SequenceCount.CONTEXT),
                        Accumulators.first(SequenceCount.PARENT_CONTEXT_KEY, "$" + SequenceCount.PARENT_CONTEXT_KEY)
                )
        );
        Map<String, ContextStats> stats = new HashMap<>();
        for (Document doc : SequenceCountDao.instance.getMCollection().aggregate(pipeline, Document.class)) {
            Document id = (Document) doc.get("_id");
            String contextKey = id.getString("c");
            String next = id.getString("n");
            ContextStats s = stats.get(contextKey);
            if (s == null) {
                s = new ContextStats();
                s.parentContextKey = doc.getString(SequenceCount.PARENT_CONTEXT_KEY);
                s.context = doc.getList(SequenceCount.CONTEXT, String.class);
                stats.put(contextKey, s);
            }
            Number count = (Number) doc.get(SequenceCount.COUNT);
            Number apiCollectionId = (Number) doc.get(SequenceCount.API_COLLECTION_ID);
            s.counts.put(next, count == null ? 0L : count.longValue());
            s.apiCollectionIds.put(next, apiCollectionId == null ? 0 : apiCollectionId.intValue());
        }
        return stats;
    }

    public static List<SequenceModel> buildModels(Set<String> contextKeys, Map<String, ContextStats> stats, int now) {
        List<SequenceModel> models = new ArrayList<>();
        ContextStats order0 = stats.get("");
        for (String contextKey : contextKeys) {
            ContextStats s = stats.get(contextKey);
            if (s == null) continue;
            long total = SequenceVomc.sum(s.counts);
            if (total <= 0) continue;
            List<String> context = s.context == null ? new ArrayList<String>() : s.context;
            int order = context.size();
            ContextStats parent = s.parentContextKey == null ? null : stats.get(s.parentContextKey);
            boolean collapsed = SequenceVomc.isCollapsed(order, s.counts, parent == null ? null : parent.counts);
            for (Map.Entry<String, Long> e : s.counts.entrySet()) {
                String next = e.getKey();
                long k = e.getValue();
                double[] ci = SequenceVomc.credibleInterval(k, total);
                Long order0Count = order0 == null ? null : order0.counts.get(next);

                SequenceModel m = new SequenceModel();
                m.setApiCollectionId(s.apiCollectionIds.get(next));
                m.setContext(context);
                m.setContextKey(contextKey);
                m.setParentContextKey(s.parentContextKey);
                m.setNext(next);
                m.setOrder(order);
                m.setCount24h(k);
                m.setContextTotal24h(total);
                m.setProbability((double) k / total);
                m.setCiLow(ci[0]);
                m.setCiHigh(ci[1]);
                m.setCollapsed(collapsed);
                m.setPrecedence(SequenceVomc.precedence(k, order0Count == null ? 0 : order0Count));
                m.setUpdatedAt(now);
                models.add(m);
            }
        }
        return models;
    }
}
