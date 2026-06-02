package com.akto.utils.jobs;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

public class EmptyCollectionCleanupJob {

    private static final LoggerMaker logger = new LoggerMaker(EmptyCollectionCleanupJob.class, LogDb.DASHBOARD);
    private static final int SIX_HOURS_IN_SECONDS = 6 * 60 * 60;

    static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void emptyCollectionCleanupJobRunner() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            cleanupEmptyCollections();
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e, "Error in emptyCollectionCleanupJob");
                        }
                    }
                }, "empty-collection-cleanup-job");
            }
        }, 0, 3, TimeUnit.HOURS);
    }

    private static void cleanupEmptyCollections() {
        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        if (accountSettings == null || !accountSettings.isEnableEmptyCollectionCleanup()) {
            return;
        }

        int sixHoursAgo = Context.now() - SIX_HOURS_IN_SECONDS;

        List<ApiCollection> recentCollections = ApiCollectionsDao.instance.findAll(
                Filters.and(
                        Filters.gte(ApiCollection.START_TS, sixHoursAgo),
                        Filters.ne(ApiCollection._TYPE, ApiCollection.Type.API_GROUP.name())
                ),
                Projections.include("_id", ApiCollection.START_TS, ApiCollection._TYPE)
        );

        if (recentCollections == null || recentCollections.isEmpty()) {
            return;
        }

        int deletedCount = 0;
        for (ApiCollection collection : recentCollections) {
            int collectionId = collection.getId();

            long stiCount = SingleTypeInfoDao.instance.getMCollection()
                    .countDocuments(Filters.eq(SingleTypeInfo._API_COLLECTION_ID, collectionId));
            if (stiCount > 0) {
                continue;
            }

            long apiInfoCount = ApiInfoDao.instance.getMCollection()
                    .countDocuments(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId));
            if (apiInfoCount > 0) {
                continue;
            }

            ApiCollectionsDao.instance.getMCollection().deleteOne(Filters.eq("_id", collectionId));
            deletedCount++;
            logger.infoAndAddToDb("Deleted empty collection: " + collectionId + " (name: " + collection.getName() + ")");
        }

        if (deletedCount > 0) {
            logger.infoAndAddToDb("Empty collection cleanup finished. Deleted " + deletedCount + " empty collections.");
        }
    }
}
