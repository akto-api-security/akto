package com.akto.dao.agentic_sessions;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.agentic_sessions.TopicCatalog;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class TopicCatalogDao extends AccountsContextDao<TopicCatalog> {

    public static final TopicCatalogDao instance = new TopicCatalogDao();

    // _id is name.hashCode() on the sanitized topic name — dedups regardless of casing/whitespace.
    public static String buildTopicId(String topic) {
        topic = StringUtils.lowerCase(topic);
        return topic.isEmpty() ? "" : String.valueOf(topic.hashCode());
    }

    // Each TopicCatalog passed in is built directly by the caller during accumulation:
    // its description (if set) only sticks on first-ever insert of this topic, and its
    // samplePhrases here means "the new phrases found this round" — bulkUpsert appends
    // them into the real stored (capped) list rather than overwriting it.
    public void bulkUpsert(List<TopicCatalog> writes, int maxSamplePhrases, long now) {
        if (writes == null || writes.isEmpty()) return;
        List<WriteModel<TopicCatalog>> ops = new ArrayList<>();
        for (TopicCatalog w : writes) {
            if (w.getId() == null || w.getId().isEmpty()) continue;

            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.setOnInsert("_id", w.getId()));
            updates.add(Updates.setOnInsert(TopicCatalog.NAME, w.getName()));
            updates.add(Updates.setOnInsert(TopicCatalog.CREATED_AT, now));
            if (w.getDescription() != null && !w.getDescription().isEmpty()) {
                // Only sticks the very first time this topic is ever inserted.
                updates.add(Updates.setOnInsert(TopicCatalog.DESCRIPTION, w.getDescription()));
            }
            updates.add(Updates.set(TopicCatalog.UPDATED_AT, now));
            if (w.getSamplePhrases() != null && !w.getSamplePhrases().isEmpty()) {
                updates.add(Updates.pushEach(TopicCatalog.SAMPLE_PHRASES, w.getSamplePhrases(),
                    new PushOptions().slice(-maxSamplePhrases)));
            }

            ops.add(new UpdateOneModel<>(
                Filters.eq("_id", w.getId()),
                Updates.combine(updates),
                new UpdateOptions().upsert(true)
            ));
        }
        if (!ops.isEmpty()) bulkWrite(ops, new BulkWriteOptions().ordered(false));
    }

    @Override
    public String getCollName() {
        return "topic_catalog";
    }

    @Override
    public Class<TopicCatalog> getClassT() {
        return TopicCatalog.class;
    }
}
