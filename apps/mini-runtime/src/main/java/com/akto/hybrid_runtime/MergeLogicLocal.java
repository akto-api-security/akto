package com.akto.hybrid_runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLTemplate;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.sql.SampleDataAltDb;
import com.akto.testing_db_layer_client.ClientLayer;
import com.mongodb.ConnectionString;

public class MergeLogicLocal {
    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(MergeLogicLocal.class, LogDb.RUNTIME);
    private static final ClientLayer clientLayer = new ClientLayer();

    public static void sendTestSampleDataCron(Map<Integer, APICatalog> dbState) {

        scheduler.scheduleAtFixedRate(new Runnable() {
        public void run() {
                try {
                    loggerMaker.infoAndAddToDb("Running merging job for sql");
                    mergingJob(dbState);
                } catch (Exception e){
                    loggerMaker.errorAndAddToDb(e, "error in sql merge cron");
                }
            }
        }, 0, 2, TimeUnit.MINUTES);
    }

    final static int LIMIT = 1000;

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        Map<Integer, APICatalog> dbState = new HashMap<>();
        dbState.put(0, new APICatalog(0, new HashMap<>(), new HashMap<>()));
        dbState.put(1718641552, new APICatalog(0, new HashMap<>(), new HashMap<>()));
        dbState.put(-86958217, new APICatalog(0, new HashMap<>(), new HashMap<>()));
        dbState.put(-174663758, new APICatalog(0, new HashMap<>(), new HashMap<>()));
        String[] urlTokens = APICatalogSync.tokenize("https://qapi.mpl.live:443/{param_STRING}/pending-invites");
        urlTokens[3] = null;
        SuperType[] types = new SuperType[urlTokens.length];
        types[3] = SuperType.STRING;
        URLTemplate urlTemplate = new URLTemplate(urlTokens, types, Method.POST);
        Map<URLTemplate, RequestTemplate> templateURLToMethods = new HashMap<>();
        templateURLToMethods.put(urlTemplate, null);
        APICatalog apiCatalog = new APICatalog(123, null, templateURLToMethods);
        dbState.put(123, apiCatalog);

        HttpCallParser parser = new HttpCallParser(null, LIMIT, LIMIT, LIMIT, false);
        parser.apiCatalogSync.dbState = dbState;
        mergingJob(parser.apiCatalogSync.dbState);
        parser.apiCatalogSync.dbState.put(1234, apiCatalog);
        mergingJob(parser.apiCatalogSync.dbState);
    }
        
    public static void mergingJob(Map<Integer, APICatalog> dbState) {
        long start = System.currentTimeMillis();
        for (int apiCollectionId : dbState.keySet()) {
            APICatalog apiCatalog = dbState.get(apiCollectionId);

            Set<URLTemplate> templateUrls = apiCatalog.getTemplateURLToMethods().keySet();

            if(templateUrls == null || templateUrls.isEmpty()){
                loggerMaker.infoAndAddToDb(String.format("Skipping for apiCollectionId %d no templates found", apiCollectionId));
                continue;
            }

            int skip = 0;
            while (true) {
                loggerMaker.infoAndAddToDb(String.format("Running for apiCollectionId %d with skip %d", apiCollectionId, skip));
                Map<String, List<String>> updates = new HashMap<>();
                List<SampleDataAlt> data = new ArrayList<>();
                try {
                    if (apiCollectionId == -86954494) {
                        loggerMaker.infoAndAddToDb("hi there");
                    }
                    data = clientLayer.fetchSampleData(apiCollectionId, skip);
                    if (data == null) {
                        loggerMaker.infoAndAddToDb(String.format("didn't find any sample data for collection", apiCollectionId));
                        break;
                    }
                    loggerMaker.infoAndAddToDb(String.format("data size: %d template urls: %d", data.size(), templateUrls.size()));
                    for (SampleDataAlt sampleDataAlt : data) {
                        for (URLTemplate urlTemplate : templateUrls) {
                            String templateUrl = urlTemplate.getTemplateString();
                            if (!sampleDataAlt.getUrl().equals(templateUrl) &&
                                    urlTemplate.match(sampleDataAlt.getUrl(), Method.valueOf(sampleDataAlt.getMethod()))) {
                                List<String> ids = new ArrayList<>();
                                if (updates.containsKey(templateUrl)) {
                                    ids = updates.get(templateUrl);
                                }
                                ids.add(sampleDataAlt.getId().toString());
                                updates.put(templateUrl, ids);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "ERROR in fetching data from postgres");
                    break;
                }

                try {
                    if(updates!=null && !updates.isEmpty()){
                        loggerMaker.infoAndAddToDb(String.format("%d updates found, updating sql db", updates.size()));
                        for (String url : updates.keySet()) {
                            long updateStart = System.currentTimeMillis();
                            clientLayer.updateUrl(updates.get(url), url);
                            AllMetrics.instance.setMergingJobUrlUpdateLatency(System.currentTimeMillis() - updateStart);
                        }
                        AllMetrics.instance.setMergingJobUrlsUpdatedCount(updates.size());
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "ERROR in update data in postgres");
                }

                if (data.size() < LIMIT) {
                    loggerMaker.infoAndAddToDb(String.format("No more data for apiCollectionId %d with skip %d, breaking", apiCollectionId, skip));
                    break;
                }
                skip += LIMIT;
            }
        }
        AllMetrics.instance.setMergingJobLatency(System.currentTimeMillis() - start);
    }
}