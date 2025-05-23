package com.akto.utils.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.SuspectSampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.APICatalogSync;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class MatchingJob {

    static final int LIMIT = 10_000;
    private static final LoggerMaker loggerMaker = new LoggerMaker(MatchingJob.class, LogDb.THREAT_DETECTION);
    private static final LoggerMaker logger = new LoggerMaker(MatchingJob.class);

    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static boolean jobRunning = false;
    public static void MatchingJobRunner() {

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (jobRunning) {
                    return;
                }
                jobRunning = true;

                int now = Context.now();
                logger.debug("Starting MatchingJobRunner for all accounts at " + now);

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            Map<String, FilterConfig> apiFilters = FilterYamlTemplateDao.instance.fetchFilterConfig(false, false);
                            if (apiFilters == null || apiFilters.isEmpty()) {
                                /*
                                 * Skip running job for accounts
                                 * which have not added a threat-filter.
                                 */
                                return;
                            }
                            matchUrlJob();
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in matchUrlJob " + e.toString());
                        }
                    }
                }, "match-url-job");

                int now2 = Context.now();
                int diffNow = now2 - now;
                logger.debug(String.format("Completed MatchingJobRunner for all accounts at %d , time taken : %d", now2,
                        diffNow));
                jobRunning = false;
            }
        }, 0, 30, TimeUnit.MINUTES);

    }

    public static void matchUrlJob() {

        int timestamp = 0;
        int previousTimestamp = 0;
        Map<Integer, APICatalog> dbState = new HashMap<>();
        List<WriteModel<SuspectSampleData>> suspectSampleDataUpdates = new ArrayList<>();
        List<SuspectSampleData> samples = new ArrayList<>();
        do {

            /*
             * using gte would repeat some entries
             * but ensure that all entries are covered.
             */
            Bson finalFilter = Filters.gte(SuspectSampleData._DISCOVERED, timestamp);

            /*
             * 1. Ascending filter because any new entry
             * will be inserted with a higher timestamp,
             * thus the sorted order will not get affected for our use-case.
             * 
             * 2. Filter on timestamp allows for O(n) queries
             * [ using find-limit type pagination ],
             * with the assumption that for any second we will have < 10_000 entries.
             */
            samples = SuspectSampleDataDao.instance.findAll(finalFilter, 0, LIMIT,
                    Sorts.ascending(SuspectSampleData._DISCOVERED, Constants.ID),
                    Projections.exclude(SuspectSampleData._SAMPLE));

            for (SuspectSampleData sample : samples) {
                int apiCollectionId = sample.getApiCollectionId();
                if (!dbState.containsKey(apiCollectionId)) {
                    fillDbState(apiCollectionId, dbState);
                }

                APICatalog apiCatalog = dbState.getOrDefault(apiCollectionId,
                        new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>()));
                Set<URLTemplate> templateUrls = apiCatalog.getTemplateURLToMethods().keySet();
                Set<URLStatic> staticUrls = apiCatalog.getStrictURLToMethods().keySet();
                String matchingUrl = sample.getMatchingUrl();
                /*
                 * Todo: Use method agnostic matching.
                 */
                if (staticUrls.contains(new URLStatic(sample.getUrl(), sample.getMethod()))) {
                    matchingUrl = sample.getUrl();
                } else {
                    for (URLTemplate template : templateUrls) {
                        if (template.match(sample.getUrl(), sample.getMethod())) {
                            matchingUrl = template.getTemplateString();
                            break;
                        }
                    }
                }

                if (!matchingUrl.equals(sample.getMatchingUrl())) {
                    suspectSampleDataUpdates.add(new UpdateOneModel<>(Filters.eq(Constants.ID, sample.getId()),
                            Updates.set(SuspectSampleData.MATCHING_URL, matchingUrl)));
                }
                timestamp = sample.getDiscovered();
            }

            /*
             * To avoid a deadlock.
             */
            if (previousTimestamp == timestamp) {
                timestamp++;
            }
            previousTimestamp = timestamp;

            /*
             * The last query will fetch samples less than LIMIT.
             */
        } while (samples.size() == LIMIT);

        if (!suspectSampleDataUpdates.isEmpty()) {
            SuspectSampleDataDao.instance.getMCollection().bulkWrite(suspectSampleDataUpdates);
        }
    }

    private static void fillDbState(int apiCollectionId, Map<Integer, APICatalog> dbState) {
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);
        Bson filterQ = null;
        if (apiCollection != null && apiCollection.getHostName() == null) {
            filterQ = Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId);
        } else {
            filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        }

        int offset = 0;
        int limit = 10_000;

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        do {
            singleTypeInfos = SingleTypeInfoDao.instance.findAll(filterQ, offset, limit, null,
                    Projections.exclude("values"));
            loggerMaker.debugAndAddToDb("SingleTypeInfo size in fillDbState : " + singleTypeInfos.size());
            Map<Integer, APICatalog> temp = new HashMap<>();
            temp = APICatalogSync.build(singleTypeInfos, null);
            dbState.putAll(temp);
            offset += limit;
        } while (!singleTypeInfos.isEmpty());
    }
}
