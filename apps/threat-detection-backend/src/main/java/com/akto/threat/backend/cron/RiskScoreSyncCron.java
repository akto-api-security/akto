package com.akto.threat.backend.cron;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.ahocorasick.trie.Trie;
import org.ahocorasick.trie.Emit;
import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.RuntimeUtil;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.billing.UsageMetricUtils.getFeatureAccessSaas;

public class RiskScoreSyncCron {
    
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreSyncCron.class, LogDb.THREAT_DETECTION);

    private static final String LFI_OS_FILES_DATA = "/lfi-os-files.data";
    private static final String OS_COMMAND_INJECTION_DATA = "/os-command-injection.data";
    private static final String SSRF_DATA = "/ssrf.data";
    
    private static Trie lfiTrie;
    private static Trie osCommandInjectionTrie;
    private static Trie ssrfTrie;

    static {
        try {
            lfiTrie = generateTrie(LFI_OS_FILES_DATA);
            osCommandInjectionTrie = generateTrie(OS_COMMAND_INJECTION_DATA);
            ssrfTrie = generateTrie(SSRF_DATA);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error initializing static tries: " + e.getMessage());
        }
    }

    private static Trie generateTrie(String fileName) throws Exception {
        Trie.TrieBuilder builder = Trie.builder();
        try (InputStream is = RiskScoreSyncCron.class.getResourceAsStream(fileName);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                builder.addKeyword(line);
            }
        }

        return builder.build();
    }

    private static String removeThreatPatternsFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        String cleanedUrl = url;
        
        cleanedUrl = removeMatchesFromText(cleanedUrl, lfiTrie);
        cleanedUrl = removeMatchesFromText(cleanedUrl, osCommandInjectionTrie);
        cleanedUrl = removeMatchesFromText(cleanedUrl, ssrfTrie);
        cleanedUrl = cleanedUrl.replace("//", "/");
        
        
        return cleanedUrl;
    }

    private static String removeMatchesFromText(String text, Trie trie) {
        if (trie == null || text == null || text.isEmpty()) {
            return text;
        }

        List<Emit> matches = new ArrayList<>();
        for (Emit emit : trie.parseText(text)) {
            if (emit != null && emit.getKeyword() != null) {
                matches.add(emit);
            }
        }

        if (matches.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text);
        for (int i = matches.size() - 1; i >= 0; i--) {
            Emit emit = matches.get(i);
            int start = emit.getStart();
            int end = emit.getEnd() + 1;
            if (start >= 0 && end <= result.length() && start < end) {
                result.delete(start, end);
            }
        }

        return result.toString();
    }

    private static URLTemplate isMatchingUrl(int apiCollectionId, String urlFromEvent, String methodFromEvent, Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates){
        urlFromEvent = ApiInfo.getNormalizedUrl(urlFromEvent);
        urlFromEvent = removeThreatPatternsFromUrl(urlFromEvent);
        List<URLTemplate> urlTemplates = apiCollectionUrlTemplates.get(apiCollectionId);
        if(urlTemplates == null || urlTemplates.isEmpty()){
            return null;
        }
        URLMethods.Method method = URLMethods.Method.fromString(methodFromEvent);
        for(URLTemplate urlTemplate : urlTemplates){
            if(!urlTemplate.matchTemplate(urlFromEvent, method).equals(URLTemplate.MatchResult.NO_MATCH)){
                return urlTemplate;
            }
        }
        return null;
    }

    public void setUpRiskScoreSyncCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        int accountId = t.getId();
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        FeatureAccess featureAccess = getFeatureAccessSaas(accountId, "THREAT_DETECTION");
                        if(!featureAccess.getIsGranted()){
                            loggerMaker.debugAndAddToDb("Feature access not granted for account " + accountId);
                            return;
                        }
                        int startTimestamp = Context.now();
                        loggerMaker.debugAndAddToDb("Risk score sync cron started for account " + accountId + " at " + startTimestamp);
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        int deltaStarTime = 0;
                        int deltaEndTime = Context.now();
                        Bson updateForLastCronRunInfo = Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_THREAT_SCORE_SYNC), deltaEndTime);
                        if(lastRunTimerInfo != null){
                            if(deltaEndTime - lastRunTimerInfo.getLastInfoResetted() <= Constants.ONE_DAY_TIMESTAMP){
                                deltaStarTime = lastRunTimerInfo.getLastThreatScoreSync();
                            }else{
                                updateForLastCronRunInfo = Updates.combine(updateForLastCronRunInfo, Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_INFO_RESETTED), deltaEndTime));
                            }
                        }

                        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$latestApiCollectionId").append("method", "$latestApiMethod").append("endpoint", "$latestApiEndpoint");
                        List<Bson> pipeline = new ArrayList<>();
                        pipeline.add(Aggregates.sort(Sorts.descending("detectedAt")));
                        pipeline.add(Aggregates.match(Filters.and(Filters.gte("detectedAt", deltaStarTime), Filters.lte("detectedAt", deltaEndTime), Filters.eq("successfulExploit", true))));
                        pipeline.add(Aggregates.group(groupedId, Accumulators.addToSet("severities", "$severity")));
                        MongoCursor<BasicDBObject> cursor = MaliciousEventDao.instance.getCollection(String.valueOf(accountId)).aggregate(pipeline, BasicDBObject.class).cursor();

                        Map<String, List<String>> apiInfoKeyToSeverities = new HashMap<>();
                        Set<Integer> apiCollectionIdsFromEvents = new HashSet<>();
                        while(cursor.hasNext()){
                            BasicDBObject document = cursor.next();
                            BasicDBObject id = (BasicDBObject) document.get("_id");
                            int apiCollectionIdFromDoc = id.getInt("apiCollectionId");
                            String method = id.getString("method");
                            String endpoint = id.getString("endpoint");
                            apiCollectionIdsFromEvents.add(apiCollectionIdFromDoc);
                            List<String> severities = (List<String>) document.get("severities");
                            String key = apiCollectionIdFromDoc + ":" + endpoint + ":" + method;
                            apiInfoKeyToSeverities.put(key, severities);
                        }

                        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.in(ApiInfo.ID_API_COLLECTION_ID, apiCollectionIdsFromEvents));
                        Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates = new HashMap<>();

                        RuntimeUtil.fillURLTemplatesMap(apiInfos, null, apiCollectionUrlTemplates);
                        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
                        Map<ApiInfoKey, Float> apiInfoKeyToThreatScore = new HashMap<>();

                        for(String apiInfoKey : apiInfoKeyToSeverities.keySet()){
                            String[] parts = apiInfoKey.split(":");
                            int apiCollectionId = Integer.parseInt(parts[0]);
                            String url = parts[1];
                            String method = parts[2];
                            URLTemplate urlTemplate = isMatchingUrl(apiCollectionId, url, method, apiCollectionUrlTemplates);
                            List<String> severities = apiInfoKeyToSeverities.get(apiInfoKey);
                            float threatScore = MaliciousEventDao.getThreatScoreFromSeverities(severities);
                            if(urlTemplate != null){
                                loggerMaker.warnAndAddToDb("Updating risk score for " + urlTemplate.getTemplateString() + " " + urlTemplate.getMethod().name() + " " + apiCollectionId + " to " + threatScore);
                                ApiInfoKey apiInfoKeyObj = new ApiInfoKey(apiCollectionId, urlTemplate.getTemplateString(), urlTemplate.getMethod());
                                apiInfoKeyToThreatScore.put(apiInfoKeyObj, Math.max(apiInfoKeyToThreatScore.getOrDefault(apiInfoKeyObj, 0.0f), threatScore));
                            }
                        }
                            
                        if(apiInfoKeyToThreatScore.size() > 0){
                            for(ApiInfoKey apiInfoKey : apiInfoKeyToThreatScore.keySet()){
                                float threatScore = apiInfoKeyToThreatScore.get(apiInfoKey);
                                Bson filter = ApiInfoDao.getFilter(apiInfoKey);
                                updates.add(new UpdateManyModel<>(filter, Updates.set(ApiInfo.THREAT_SCORE, threatScore)));
                            }
                        }
                        if(updates.size() > 0){
                            loggerMaker.warnAndAddToDb("Updating risk score for " + updates.size() + " api infos");
                            ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                        }
                        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), updateForLastCronRunInfo);
                        loggerMaker.warnAndAddToDb("Risk score sync cron completed for account " + accountId + " in " + (Context.now() - startTimestamp) + " seconds");
                    }
                }, "risk-score-sync-cron");
            }
        }, 0, 15, TimeUnit.MINUTES);
    }
}
