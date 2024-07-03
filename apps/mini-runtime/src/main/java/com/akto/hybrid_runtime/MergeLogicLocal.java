package com.akto.hybrid_runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.sql.SampleDataAltDb;

public class MergeLogicLocal {
    final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(MergeLogicLocal.class, LogDb.RUNTIME);

    public static void sendTestSampleDataCron(Map<Integer, APICatalog> dbState) {

        scheduler.scheduleAtFixedRate(new Runnable() {
        public void run() {
                mergingJob(dbState);
            }
        }, 0, 15, TimeUnit.MINUTES);
    }

    final static int LIMIT = 4;

    public static void main(String[] args) {
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
        mergingJob(dbState);
    }
        
    public static void mergingJob(Map<Integer, APICatalog> dbState) {

        for (int apiCollectionId : dbState.keySet()) {
            APICatalog apiCatalog = dbState.get(apiCollectionId);

            Set<URLTemplate> templateUrls = apiCatalog.getTemplateURLToMethods().keySet();

            int skip = 0;
            while (true) {
                Map<String, List<String>> updates = new HashMap<>();
                List<SampleDataAlt> data = new ArrayList<>();
                try {
                    data = SampleDataAltDb.iterateAndGetAll(apiCollectionId, LIMIT, skip);
                    for (SampleDataAlt sampleDataAlt : data) {
                        for (URLTemplate urlTemplate : templateUrls) {
                            if (urlTemplate.match(sampleDataAlt.getUrl(), Method.valueOf(sampleDataAlt.getMethod()))) {
                                String templateUrl = urlTemplate.getTemplateString();
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
                    for (String url : updates.keySet()) {
                        SampleDataAltDb.updateUrl(updates.get(url), url);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "ERROR in update data in postgres");
                }

                if (data.size() < LIMIT) {
                    break;
                }
                skip += LIMIT;
            }
        }
    }
}