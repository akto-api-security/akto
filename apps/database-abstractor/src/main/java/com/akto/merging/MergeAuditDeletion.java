package com.akto.merging;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.merging.MergeAuditLogDao;
import com.akto.dto.merging.MergeAuditLog;
import com.akto.log.LoggerMaker;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;

public class MergeAuditDeletion {

    private static final LoggerMaker loggerMaker = new LoggerMaker(MergeAuditDeletion.class, LoggerMaker.LogDb.CYBORG);
    private static final int BATCH_SIZE = 1000;

    public static void runDeletion(int accountId) {
        loggerMaker.infoAndAddToDb("Starting merge audit deletion for account " + accountId);

        Bson incompleteFilter = Filters.or(
                Filters.ne(MergeAuditLog.STI_DELETED, true),
                Filters.ne(MergeAuditLog.API_INFO_DELETED, true),
                Filters.ne(MergeAuditLog.SAMPLE_DATA_DELETED, true)
        );

        List<MergeAuditLog> batch = new ArrayList<>();
        int totalProcessed = 0;

        try (MongoCursor<MergeAuditLog> cursor = MergeAuditLogDao.instance.getMCollection()
                .find(incompleteFilter).cursor()) {

            while (cursor.hasNext()) {
                batch.add(cursor.next());

                if (batch.size() >= BATCH_SIZE) {
                    totalProcessed += processBatch(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                totalProcessed += processBatch(batch);
            }
        }

        loggerMaker.infoAndAddToDb("Merge audit deletion complete for account " + accountId
                + ", processed " + totalProcessed + " docs");
    }

    private static int processBatch(List<MergeAuditLog> batch) {
        // Group URLs by (apiCollectionId, method) for efficient $in deletes
        Map<String, GroupedUrls> groups = new HashMap<>();
        List<ObjectId> allDocIds = new ArrayList<>(batch.size());

        for (MergeAuditLog doc : batch) {
            if (doc.getId() != null) allDocIds.add(doc.getId());
            if (doc.getMatchedStaticUrls() == null || doc.getMatchedStaticUrls().isEmpty()) continue;

            for (String staticUrl : doc.getMatchedStaticUrls()) {
                String[] parts = staticUrl.split(" ", 2);
                if (parts.length < 2) continue;

                String groupKey = doc.getApiCollectionId() + "|" + parts[0];
                groups.computeIfAbsent(groupKey, k -> new GroupedUrls(doc.getApiCollectionId(), parts[0]))
                        .urls.add(parts[1]);
            }
        }

        // Delete one entity type at a time across all groups, then bulk-mark the boolean
        boolean stiOk = deleteAllGroups(groups, EntityType.STI);
        boolean apiInfoOk = deleteAllGroups(groups, EntityType.API_INFO);
        boolean sampleDataOk = deleteAllGroups(groups, EntityType.SAMPLE_DATA);

        // One updateMany per entity type for the entire batch
        bulkMarkDeleted(allDocIds, stiOk, apiInfoOk, sampleDataOk);

        return batch.size();
    }

    private enum EntityType { STI, API_INFO, SAMPLE_DATA }

    private static boolean deleteAllGroups(Map<String, GroupedUrls> groups, EntityType type) {
        boolean allOk = true;
        for (GroupedUrls group : groups.values()) {
            try {
                Bson filter;
                DeleteResult result;
                switch (type) {
                    case STI:
                        filter = Filters.and(
                                Filters.eq("apiCollectionId", group.apiCollectionId),
                                Filters.eq("method", group.method),
                                Filters.in("url", group.urls));
                        result = SingleTypeInfoDao.instance.deleteAll(filter);
                        break;
                    case API_INFO:
                        filter = Filters.and(
                                Filters.eq("_id.apiCollectionId", group.apiCollectionId),
                                Filters.eq("_id.method", group.method),
                                Filters.in("_id.url", group.urls));
                        result = ApiInfoDao.instance.deleteAll(filter);
                        break;
                    case SAMPLE_DATA:
                        filter = Filters.and(
                                Filters.eq("_id.apiCollectionId", group.apiCollectionId),
                                Filters.eq("_id.method", group.method),
                                Filters.in("_id.url", group.urls));
                        result = SampleDataDao.instance.deleteAll(filter);
                        break;
                    default:
                        continue;
                }
                loggerMaker.infoAndAddToDb(type + " deleted " + result.getDeletedCount()
                        + " for col=" + group.apiCollectionId + " method=" + group.method);
            } catch (Exception e) {
                allOk = false;
                loggerMaker.errorAndAddToDb("Error deleting " + type + " for col=" + group.apiCollectionId
                        + " method=" + group.method + ": " + e.getMessage(), LoggerMaker.LogDb.CYBORG);
            }
        }
        return allOk;
    }

    private static void bulkMarkDeleted(List<ObjectId> docIds, boolean stiOk, boolean apiInfoOk, boolean sampleDataOk) {
        if (docIds.isEmpty()) return;
        Bson idFilter = Filters.in("_id", docIds);
        try {
            List<Bson> updates = new ArrayList<>();
            if (stiOk) updates.add(Updates.set(MergeAuditLog.STI_DELETED, true));
            if (apiInfoOk) updates.add(Updates.set(MergeAuditLog.API_INFO_DELETED, true));
            if (sampleDataOk) updates.add(Updates.set(MergeAuditLog.SAMPLE_DATA_DELETED, true));
            if (!updates.isEmpty()) {
                MergeAuditLogDao.instance.updateMany(idFilter, Updates.combine(updates));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error bulk-marking audit docs: " + e.getMessage(), LoggerMaker.LogDb.CYBORG);
        }
    }

    private static class GroupedUrls {
        final int apiCollectionId;
        final String method;
        final List<String> urls = new ArrayList<>();

        GroupedUrls(int apiCollectionId, String method) {
            this.apiCollectionId = apiCollectionId;
            this.method = method;
        }
    }
}
