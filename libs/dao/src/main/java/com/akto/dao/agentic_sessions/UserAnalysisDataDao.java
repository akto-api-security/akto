package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserAnalysisDataDao extends AccountsContextDao<UserAnalysisData> {

    public static final UserAnalysisDataDao instance = new UserAnalysisDataDao();

    public void createIndicesIfAbsent() {
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{UserAnalysisData.LAST_UPDATED_AT}, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{UserAnalysisData.USER_NAME}, false);
    }

    /**
     * Upserts an aggregate row for (serviceId, deviceId). Uses $inc for tokens and topic counts.
     * NOT idempotent on retry — 60s cron slack window keeps double-count rare.
     */
    public void upsertAggregates(String serviceId, String deviceId, String userName,
                                  Map<String, Integer> topicDeltas,
                                  long inputTokensDelta, long outputTokensDelta,
                                  Map<String, Object> harmfulMerge,
                                  String aiSummary,
                                  long now) {
        Bson filter = Filters.and(
            Filters.eq(UserAnalysisData.ID_SERVICE_ID, serviceId),
            Filters.eq(UserAnalysisData.ID_DEVICE_ID, deviceId)
        );

        List<Bson> ops = new ArrayList<>();
        ops.add(Updates.setOnInsert(UserAnalysisData.ID_SERVICE_ID, serviceId));
        ops.add(Updates.setOnInsert(UserAnalysisData.ID_DEVICE_ID, deviceId));
        if (userName != null && !userName.isEmpty()) {
            ops.add(Updates.set(UserAnalysisData.USER_NAME, userName));
        }
        ops.add(Updates.set(UserAnalysisData.LAST_UPDATED_AT, now));
        if (inputTokensDelta != 0) {
            ops.add(Updates.inc(UserAnalysisData.TOTAL_INPUT_TOKENS, inputTokensDelta));
        }
        if (outputTokensDelta != 0) {
            ops.add(Updates.inc(UserAnalysisData.TOTAL_OUTPUT_TOKENS, outputTokensDelta));
        }
        if (aiSummary != null) {
            ops.add(Updates.set(UserAnalysisData.AI_SUMMARY, aiSummary));
        }
        if (topicDeltas != null) {
            for (Map.Entry<String, Integer> e : topicDeltas.entrySet()) {
                String safeKey = sanitizeKey(e.getKey());
                if (safeKey.isEmpty()) continue;
                ops.add(Updates.inc(UserAnalysisData.TOPIC_COUNTS + "." + safeKey, e.getValue()));
            }
        }
        if (harmfulMerge != null) {
            for (Map.Entry<String, Object> e : harmfulMerge.entrySet()) {
                String safeKey = sanitizeKey(e.getKey());
                if (safeKey.isEmpty()) continue;
                ops.add(Updates.set(UserAnalysisData.HARMFUL_TOPICS + "." + safeKey, e.getValue()));
            }
        }

        getMCollection().updateOne(filter, Updates.combine(ops), new UpdateOptions().upsert(true));
    }

    private static String sanitizeKey(String raw) {
        if (raw == null) return "";
        return raw.replace('.', '_').replace('$', '_').trim();
    }

    @Override
    public String getCollName() {
        return "user_analysis_data";
    }

    @Override
    public Class<UserAnalysisData> getClassT() {
        return UserAnalysisData.class;
    }
}
