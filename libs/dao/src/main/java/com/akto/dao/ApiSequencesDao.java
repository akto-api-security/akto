package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiSequences;
import com.akto.dto.SequenceCount;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ApiSequencesDao extends AccountsContextDao<ApiSequences> {

    public static final ApiSequencesDao instance = new ApiSequencesDao();

    public static final int BATCH_SIZE = 1000;
    public static final int HALF_LIFE_SECONDS = 24 * 60 * 60;
    public static final int TTL_DAYS = 7;
    public static final String SEPARATOR = "|";
    public static final int RULES_LIMIT = 50;
    public static final int RULES_MIN_COUNT = 100;
    public static final double RULES_MIN_PRECEDENCE_LOW = 0.99;
    private static final String LAST_SEEN_TTL_INDEX = "lastSeen_ttl";
    private static final double DECAY_PER_SECOND = Math.log(2) / HALF_LIFE_SECONDS;
    private static final double Z = 1.645;
    private static final int DUPLICATE_KEY = 11000;
    private static final BulkWriteOptions UNORDERED = new BulkWriteOptions().ordered(false);

    static final Set<Integer> INDEXED_ACCOUNTS = ConcurrentHashMap.newKeySet();

    private ApiSequencesDao() {}

    @Override
    public String getCollName() {
        return "api_sequences";
    }

    @Override
    public Class<ApiSequences> getClassT() {
        return ApiSequences.class;
    }


    public boolean createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            db.createCollection(getCollName());
        }

        MCollection.createUniqueIndex(getDBName(), getCollName(), new String[] { ApiSequences.SEQ_KEY }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.SCORE }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.PRECEDENCE_LOW }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), Indexes.ascending(ApiSequences.LAST_SEEN),
                new IndexOptions().name(LAST_SEEN_TTL_INDEX).expireAfter((long) TTL_DAYS, TimeUnit.DAYS));
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.CREATED_AT }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.LAST_UPDATED_AT }, false);
        return hasWriteIndexes();
    }

    // createIndexIfAbsent returns false both on failure and on a fresh create, so check what actually exists
    private boolean hasWriteIndexes() {
        String seqKeyIndex = MCollection.generateIndexName(new String[] { ApiSequences.SEQ_KEY }, true);
        boolean seqKey = false, ttl = false;
        for (Document index : getMCollection().listIndexes()) {
            String name = index.getString("name");
            if (seqKeyIndex.equals(name) && Boolean.TRUE.equals(index.getBoolean("unique"))) seqKey = true;
            if (LAST_SEEN_TTL_INDEX.equals(name)) ttl = true;
        }
        return seqKey && ttl;
    }

    // Adds session sequence counts. Work per call depends only on the batch, never on collection size.
    public void incrementSequences(List<SequenceCount> sequenceCounts) {
        if (sequenceCounts == null || sequenceCounts.isEmpty()) return;
        Integer accountId = Context.accountId.get();
        if (accountId == null) throw new IllegalStateException("account id not set");
        if (!INDEXED_ACCOUNTS.contains(accountId)) {
            if (!createIndicesIfAbsent()) throw new IllegalStateException("api_sequences indexes could not be created, not writing");
            INDEXED_ACCOUNTS.add(accountId);
        }
        for (int from = 0; from < sequenceCounts.size(); from += BATCH_SIZE) {
            writeBatch(sequenceCounts.subList(from, Math.min(from + BATCH_SIZE, sequenceCounts.size())));
        }
    }

    private static class Row {
        int apiCollectionId;
        List<String> paths;
        List<String> context;
        String next;
        double count;
        int ts;
    }

    private void writeBatch(List<SequenceCount> sequenceCounts) {
        Map<String, Row> rows = new LinkedHashMap<>();
        for (SequenceCount sc : sequenceCounts) {
            if (sc == null || sc.getNext() == null || sc.getCount() <= 0) continue;
            List<String> context = sc.getContext() == null ? new ArrayList<String>() : sc.getContext();
            List<String> paths = new ArrayList<>(context);
            paths.add(sc.getNext());
            String seqKey = String.join(SEPARATOR, paths);
            Row row = rows.get(seqKey);
            if (row == null) {
                row = new Row();
                row.apiCollectionId = sc.getApiCollectionId();
                row.paths = paths;
                row.context = context;
                row.next = sc.getNext();
                rows.put(seqKey, row);
            }
            row.count += sc.getCount();
            row.ts = Math.max(row.ts, sc.getBucketStart());
        }
        if (rows.isEmpty()) return;

        int now = Context.now();
        Date lastSeen = new Date();
        List<WriteModel<ApiSequences>> counts = new ArrayList<>();
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            counts.add(new UpdateOneModel<ApiSequences>(Filters.eq(ApiSequences.SEQ_KEY, e.getKey()),
                    countPipeline(e.getKey(), e.getValue(), now, lastSeen), new UpdateOptions().upsert(true)));
        }
        bulkWriteRetryingDuplicates(counts);

        Set<String> keys = new HashSet<>(rows.keySet());
        for (Row row : rows.values()) {
            if (row.context.isEmpty()) continue;
            keys.add(String.join(SEPARATOR, row.context));
            keys.add(row.next);
        }
        Map<String, Document> stored = new HashMap<>();
        for (Document d : getMCollection().find(Filters.in(ApiSequences.SEQ_KEY, keys), Document.class)
                .projection(Projections.include(ApiSequences.SEQ_KEY, ApiSequences.WEIGHT, ApiSequences.LAST_TS))) {
            stored.put(d.getString(ApiSequences.SEQ_KEY), d);
        }

        List<WriteModel<ApiSequences>> ratios = new ArrayList<>();
        for (Map.Entry<String, Row> e : rows.entrySet()) {
            Row row = e.getValue();
            if (row.context.isEmpty()) continue;
            Document self = stored.get(e.getKey());
            Document prev = stored.get(String.join(SEPARATOR, row.context));
            Document last = stored.get(row.next);
            if (self == null) continue;
            int at = Math.max(lastTs(self), Math.max(lastTs(prev), lastTs(last)));
            double k = weightAt(self, at);
            double prevWeight = weightAt(prev, at);
            double lastWeight = weightAt(last, at);
            double precedence = lastWeight > 0 ? Math.min(1, k / lastWeight) : 0;
            double precedenceLow = lowerBound(k, lastWeight);
            ratios.add(new UpdateOneModel<ApiSequences>(Filters.eq(ApiSequences.SEQ_KEY, e.getKey()), Updates.combine(
                    Updates.set(ApiSequences.PREV_STATE_COUNT, (int) Math.round(prevWeight)),
                    Updates.set(ApiSequences.LAST_STATE_COUNT, (int) Math.round(lastWeight)),
                    Updates.set(ApiSequences.PROBABILITY, prevWeight > 0 ? (float) Math.min(1, k / prevWeight) : 0f),
                    Updates.set(ApiSequences.PRECEDENCE_SCORE, (float) precedence),
                    Updates.set(ApiSequences.PRECEDENCE_LOW, precedenceLow),
                    Updates.set(ApiSequences.SCORE, k * precedenceLow))));
        }
        if (!ratios.isEmpty()) getMCollection().bulkWrite(ratios, UNORDERED);
    }

    private static List<Bson> countPipeline(String seqKey, Row row, int now, Date lastSeen) {
        Document age = new Document("$max", Arrays.asList(0, new Document("$subtract", Arrays.asList(row.ts,
                new Document("$ifNull", Arrays.asList("$" + ApiSequences.LAST_TS, row.ts))))));
        Document decayed = new Document("$multiply", Arrays.asList(
                new Document("$ifNull", Arrays.asList("$" + ApiSequences.WEIGHT, 0.0)),
                new Document("$exp", new Document("$multiply", Arrays.asList(-DECAY_PER_SECOND, age)))));
        Document counts = new Document()
                .append(ApiSequences.WEIGHT, new Document("$add", Arrays.asList(decayed, row.count)))
                .append(ApiSequences.LAST_TS, new Document("$max", Arrays.asList(
                        new Document("$ifNull", Arrays.asList("$" + ApiSequences.LAST_TS, row.ts)), row.ts)))
                .append(ApiSequences.SEQ_KEY, literal(seqKey))
                .append(ApiSequences.CONTEXT_KEY, literal(String.join(SEPARATOR, row.context)))
                .append(ApiSequences.NEXT, literal(row.next))
                .append(ApiSequences.ORDER, row.context.size())
                .append(ApiSequences.PATHS, literal(row.paths))
                .append(ApiSequences.API_COLLECTION_ID, row.apiCollectionId)
                .append(ApiSequences.LAST_SEEN, lastSeen)
                .append(ApiSequences.LAST_UPDATED_AT, now)
                .append(ApiSequences.CREATED_AT, new Document("$ifNull", Arrays.asList("$" + ApiSequences.CREATED_AT, now)))
                .append(ApiSequences.IS_ACTIVE, new Document("$ifNull", Arrays.asList("$" + ApiSequences.IS_ACTIVE, true)));
        Document rounded = new Document(ApiSequences.TRANSITION_COUNT,
                new Document("$toInt", new Document("$round", Arrays.asList("$" + ApiSequences.WEIGHT, 0))));
        return Arrays.<Bson>asList(new Document("$set", counts), new Document("$set", rounded));
    }

    private static Document literal(Object value) {
        return new Document("$literal", value);
    }

    private static int lastTs(Document d) {
        return d == null ? 0 : ((Number) d.get(ApiSequences.LAST_TS)).intValue();
    }

    private static double weightAt(Document d, int at) {
        if (d == null) return 0;
        double weight = ((Number) d.get(ApiSequences.WEIGHT)).doubleValue();
        return weight * Math.exp(-DECAY_PER_SECOND * Math.max(0, at - lastTs(d)));
    }

    // Wilson score lower bound (one-sided, ~95%)
    static double lowerBound(double k, double n) {
        if (n <= 0) return 0;
        double p = Math.min(1, k / n);
        double z2 = Z * Z;
        double centre = p + z2 / (2 * n);
        double margin = Z * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n));
        return Math.max(0, (centre - margin) / (1 + z2 / n));
    }

    // Two cyborg pods inserting the same new row at once: the loser gets a duplicate key error; retry it as an update
    private void bulkWriteRetryingDuplicates(List<WriteModel<ApiSequences>> models) {
        try {
            getMCollection().bulkWrite(models, UNORDERED);
        } catch (MongoBulkWriteException e) {
            List<WriteModel<ApiSequences>> retry = new ArrayList<>();
            for (BulkWriteError error : e.getWriteErrors()) {
                if (error.getCode() != DUPLICATE_KEY) throw e;
                retry.add(models.get(error.getIndex()));
            }
            getMCollection().bulkWrite(retry, UNORDERED);
        }
    }

    // Sequences where the last API almost always follows the earlier ones: "B requires A", "C requires A, B".
    // An order-2 rule is dropped when its order-1 suffix is already a rule.
    public List<ApiSequences> findSequenceRules() {
        List<ApiSequences> candidates = findAll(Filters.and(
                Filters.gte(ApiSequences.PRECEDENCE_LOW, RULES_MIN_PRECEDENCE_LOW),
                Filters.gte(ApiSequences.ORDER, 1),
                Filters.gte(ApiSequences.TRANSITION_COUNT, RULES_MIN_COUNT)), 0, RULES_LIMIT * 4, Sorts.descending(ApiSequences.SCORE));
        Set<String> keys = new HashSet<>();
        for (ApiSequences c : candidates) keys.add(c.getSeqKey());
        List<ApiSequences> rules = new ArrayList<>();
        for (ApiSequences c : candidates) {
            if (c.getOrder() >= 2 && keys.contains(String.join(SEPARATOR, c.getPaths().subList(1, c.getPaths().size())))) continue;
            rules.add(c);
            if (rules.size() == RULES_LIMIT) break;
        }
        return rules;
    }

    public List<ApiSequences> findByApiCollectionId(int apiCollectionId) {

        try {
            List<ApiSequences> result = ApiSequencesDao.instance.findAll(Filters.eq(ApiSequences.API_COLLECTION_ID, apiCollectionId));
            return result;
        } catch (Exception e) {
            System.err.println("Error in findByApiCollectionId: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


}
