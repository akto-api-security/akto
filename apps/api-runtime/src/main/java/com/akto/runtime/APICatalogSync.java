package com.akto.runtime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.TrafficRecorder;
import com.akto.dto.type.URLStatic;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class APICatalogSync {
    
    public int thresh;
    public String userIdentifier;
    private static final Logger logger = LoggerFactory.getLogger(APICatalogSync.class);
    Map<Integer, APICatalog> dbState;
    public Map<Integer, APICatalog> delta;

    public APICatalogSync(String userIdentifier,int thresh) {
        this.thresh = thresh;
        this.userIdentifier = userIdentifier;
        this.dbState = new HashMap<>();
        this.delta = new HashMap<>();
    }


    public void processResponse(RequestTemplate requestTemplate, Collection<HttpResponseParams> responses, List<SingleTypeInfo> deletedInfo) {
        Iterator<HttpResponseParams> iter = responses.iterator();
        while(iter.hasNext()) {
            try {
                processResponse(requestTemplate, iter.next(), deletedInfo);
            } catch (Exception e) {
                logger.error("processResponse: " + e.getMessage());
            }
        }
    }

    public void processResponse(RequestTemplate requestTemplate, HttpResponseParams responseParams, List<SingleTypeInfo> deletedInfo) {
        HttpRequestParams requestParams = responseParams.getRequestParams();
        String urlWithParams = requestParams.getURL();
        String methodStr = requestParams.getMethod();
        URLStatic baseURL = URLAggregator.getBaseURL(urlWithParams, methodStr);
        BasicDBObject queryParams = URLAggregator.getQueryJSON(urlWithParams);
        int statusCode = responseParams.getStatusCode();
        Method method = Method.valueOf(methodStr);
        String userId = extractUserId(responseParams, userIdentifier);

        if (!responseParams.getIsPending()) {
            requestTemplate.processTraffic(responseParams.getTime());
        }
        if (statusCode >= 200 && statusCode < 300) {
            String reqPayload = requestParams.getPayload();

            if (reqPayload == null || reqPayload.isEmpty()) {
                reqPayload = "{}";
            }

            requestTemplate.processHeaders(requestParams.getHeaders(), baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig());
            if(reqPayload.startsWith("[")) {
                reqPayload = "{\"json\": "+reqPayload+"}";
            }
            if (reqPayload.startsWith("{")) {
                BasicDBObject payload = BasicDBObject.parse(reqPayload);
                payload.putAll(queryParams.toMap());
                deletedInfo.addAll(requestTemplate.process2(payload, baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig()));
            }
            requestTemplate.recordMessage(responseParams.getOrig());
        }

        Map<Integer, RequestTemplate> responseTemplates = requestTemplate.getResponseTemplates();
        
        RequestTemplate responseTemplate = responseTemplates.get(statusCode);
        if (responseTemplate == null) {
            responseTemplate = new RequestTemplate(new HashMap<>(), null, new HashMap<>(), new TrafficRecorder(new HashMap<>()));
            responseTemplates.put(statusCode, responseTemplate);
        }

        try {
            String respPayload = responseParams.getPayload();

            if(respPayload.startsWith("[")) {
                respPayload = "{\"json\": "+respPayload+"}";
            }

            BasicDBObject payload = BasicDBObject.parse(respPayload);
            deletedInfo.addAll(responseTemplate.process2(payload, baseURL.getUrl(), methodStr, statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig()));
            responseTemplate.processHeaders(responseParams.getHeaders(), baseURL.getUrl(), method.name(), statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig());
            if (!responseParams.getIsPending()) {
                responseTemplate.processTraffic(responseParams.getTime());
            }
        } catch (JsonParseException e) {

        }
    }

    public static String extractUserId(HttpResponseParams responseParams, String userIdentifier) {
        List<String> token = responseParams.getRequestParams().getHeaders().get(userIdentifier);
        if (token == null || token.size() == 0) {
            return "HC";
        } else {
            return token.get(0);
        }
    }

    int countUsers(Set<HttpResponseParams> responseParamsList) {
        Set<String> users = new HashSet<>();
        for(HttpResponseParams responseParams: responseParamsList) {
            users.add(extractUserId(responseParams, userIdentifier));
        }

        return users.size();
    }

    public void computeDelta(URLAggregator origAggregator, boolean triggerTemplateGeneration, int apiCollectionId) {
        long start = System.currentTimeMillis();

        APICatalog deltaCatalog = this.delta.get(apiCollectionId);
        if (deltaCatalog == null) {
            deltaCatalog = new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>());
            this.delta.put(apiCollectionId, deltaCatalog);
        } 

        APICatalog dbCatalog = this.dbState.get(apiCollectionId);
        if (dbCatalog == null) {
            dbCatalog = new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>());
            this.dbState.put(apiCollectionId, dbCatalog);
        } 

        URLAggregator aggregator = new URLAggregator(origAggregator.urls);
        System.out.println("aggregator: " + (System.currentTimeMillis() - start));
        origAggregator.urls = new ConcurrentHashMap<>();

        start = System.currentTimeMillis();
        processKnownStaticURLs(aggregator, deltaCatalog, dbCatalog);
        System.out.println("processKnownStaticURLs: " + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        Map<URLStatic, RequestTemplate> pendingRequests = createRequestTemplates(aggregator);
        System.out.println("pendingRequests: " + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        tryWithKnownURLTemplates(pendingRequests, deltaCatalog, dbCatalog);
        System.out.println("tryWithKnownURLTemplates: " + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        tryMergingWithKnownStrictURLs(pendingRequests, dbCatalog, deltaCatalog);
        System.out.println("tryMergingWithKnownStrictURLs: " + (System.currentTimeMillis() - start));

        System.out.println("processTime: " + RequestTemplate.insertTime + " " + RequestTemplate.processTime + " " + RequestTemplate.deleteTime);

    }

    private void tryMergingWithKnownStrictURLs(
        Map<URLStatic, RequestTemplate> pendingRequests,
        APICatalog dbCatalog,
        APICatalog deltaCatalog
    ) {
        Iterator<Map.Entry<URLStatic, RequestTemplate>> iterator = pendingRequests.entrySet().iterator();
        Map<Integer, Map<URLStatic, RequestTemplate>> dbSizeToUrlToTemplate = groupByTokenSize(dbCatalog);
        Map<URLStatic, RequestTemplate> deltaTemplates = deltaCatalog.getStrictURLToMethods();
        while (iterator.hasNext()) {
            Map.Entry<URLStatic, RequestTemplate> entry = iterator.next();
            URLStatic newUrl = entry.getKey();
            RequestTemplate newTemplate = entry.getValue();
            String[] tokens = tokenize(newUrl.getUrl());

            if (tokens.length == 1) {
                deltaTemplates.put(newUrl, newTemplate);
                iterator.remove();
                continue;
            }

            boolean matchedInDeltaTemplate = false;
            for(URLTemplate urlTemplate: deltaCatalog.getTemplateURLToMethods().keySet()){
                RequestTemplate deltaTemplate = deltaCatalog.getTemplateURLToMethods().get(urlTemplate);
                if (urlTemplate.match(newUrl)) {
                    matchedInDeltaTemplate = true;
                    deltaTemplate.mergeFrom(newTemplate);
                    break;
                }
            }

            if (matchedInDeltaTemplate) {
                iterator.remove();
                continue;
            }

            Map<URLStatic, RequestTemplate> dbTemplates = dbSizeToUrlToTemplate.get(tokens.length);
            if (dbTemplates != null && dbTemplates.size() > 0) {
                boolean newUrlMatchedInDb = false;
                int countSimilarURLs = 0;
                Map<URLTemplate, Set<RequestTemplate>> potentialMerges = new HashMap<>();
                for(URLStatic dbUrl: dbTemplates.keySet()) {
                    RequestTemplate dbTemplate = dbTemplates.get(dbUrl);
                    URLTemplate mergedTemplate = tryMergeUrls(dbUrl, newUrl);
                    if (mergedTemplate == null) {
                        continue;
                    }

                    if (dbTemplate.compare(newTemplate, mergedTemplate)) {

                        if (countSimilarURLs <= 20) {
                            Set<RequestTemplate> similarTemplates = potentialMerges.get(mergedTemplate);
                            if (similarTemplates == null) {
                                similarTemplates = new HashSet<>();
                                potentialMerges.put(mergedTemplate, similarTemplates);
                            } 
                            similarTemplates.add(dbTemplate);
                            countSimilarURLs++;
                        } else {
                            RequestTemplate alreadyInDelta = deltaCatalog.getTemplateURLToMethods().get(mergedTemplate);

                            if (alreadyInDelta != null) {
                                alreadyInDelta.mergeFrom(newTemplate);
                            } else {
                                RequestTemplate dbCopy = dbTemplate.copy();
                                dbCopy.mergeFrom(newTemplate);    
                                deltaCatalog.getTemplateURLToMethods().put(mergedTemplate, dbCopy);
                            }

                            for (RequestTemplate rt: potentialMerges.getOrDefault(mergedTemplate, new HashSet<>())) {
                                deltaCatalog.getDeletedInfo().addAll(rt.getAllTypeInfo());
                            }
                            deltaCatalog.getDeletedInfo().addAll(dbTemplate.getAllTypeInfo());
                            
                            newUrlMatchedInDb = true;
                            break;    
                        }
                    }
                }

                if (newUrlMatchedInDb) {
                    iterator.remove();
                    continue;
                }
            }

            boolean newUrlMatchedInDelta = false;

            for (URLStatic deltaUrl: deltaCatalog.getStrictURLToMethods().keySet()) {
                RequestTemplate deltaTemplate = deltaTemplates.get(deltaUrl);
                URLTemplate mergedTemplate = tryMergeUrls(deltaUrl, newUrl);
                if (mergedTemplate == null || RequestTemplate.isMergedOnStr(mergedTemplate)) {
                    continue;
                }

                newUrlMatchedInDelta = true;
                deltaCatalog.getDeletedInfo().addAll(deltaTemplate.getAllTypeInfo());
                RequestTemplate alreadyInDelta = deltaCatalog.getTemplateURLToMethods().get(mergedTemplate);

                if (alreadyInDelta != null) {
                    alreadyInDelta.mergeFrom(newTemplate);
                } else {
                    RequestTemplate deltaCopy = deltaTemplate.copy();
                    deltaCopy.mergeFrom(newTemplate);    
                    deltaCatalog.getTemplateURLToMethods().put(mergedTemplate, deltaCopy);
                }
                
                deltaCatalog.getStrictURLToMethods().remove(deltaUrl);
                break;
            }
            
            if (newUrlMatchedInDelta) {
                iterator.remove();
                continue;
            }

            deltaTemplates.put(newUrl, newTemplate);
            iterator.remove();
        }
    }


    private URLTemplate tryMergeUrls(URLStatic dbUrl, URLStatic newUrl) {
        if (dbUrl.getMethod() != newUrl.getMethod()) {
            return null;
        }
        String[] dbTokens = tokenize(dbUrl.getUrl());
        String[] newTokens = tokenize(newUrl.getUrl());

        if (dbTokens.length != newTokens.length) {
            return null;
        }

        SuperType[] newTypes = new SuperType[newTokens.length];
        int templatizedStrTokens = 0;
        for(int i = 0; i < newTokens.length; i ++) {
            String tempToken = newTokens[i];
            String dbToken = dbTokens[i];

            if (tempToken.equalsIgnoreCase(dbToken)) {
                continue;
            }
            
            if (NumberUtils.isParsable(tempToken) && NumberUtils.isParsable(dbToken)) {
                newTypes[i] = SuperType.INTEGER;
                newTokens[i] = null;
            } else {
                newTypes[i] = SuperType.STRING;
                newTokens[i] = null;
                templatizedStrTokens++;
            }
        }

        if (templatizedStrTokens <= 1) {
            return new URLTemplate(newTokens, newTypes, newUrl.getMethod());
        }

        return null;

    }


    private void tryWithKnownURLTemplates(
        Map<URLStatic, RequestTemplate> pendingRequests, 
        APICatalog deltaCatalog,
        APICatalog dbCatalog
    ) {
        Iterator<Map.Entry<URLStatic, RequestTemplate>> iterator = pendingRequests.entrySet().iterator();
        try {
            while (iterator.hasNext()) {
                Map.Entry<URLStatic, RequestTemplate> entry = iterator.next();
                URLStatic newUrl = entry.getKey();
                RequestTemplate newRequestTemplate = entry.getValue();

                for (URLTemplate  urlTemplate: dbCatalog.getTemplateURLToMethods().keySet()) {
                    if (urlTemplate.match(newUrl)) {
                        RequestTemplate alreadyInDelta = deltaCatalog.getTemplateURLToMethods().get(urlTemplate);

                        if (alreadyInDelta != null) {
                            alreadyInDelta.mergeFrom(newRequestTemplate);
                        } else {
                            RequestTemplate dbTemplate = dbCatalog.getTemplateURLToMethods().get(urlTemplate);
                            RequestTemplate dbCopy = dbTemplate.copy();
                            dbCopy.mergeFrom(newRequestTemplate);    
                            deltaCatalog.getTemplateURLToMethods().put(urlTemplate, dbCopy);
                        }
                        iterator.remove();
                        break;
                    }
                }
            }
        } catch (Exception e) {

        }
    }


    private Map<URLStatic, RequestTemplate> createRequestTemplates(URLAggregator aggregator) {
        Map<URLStatic, RequestTemplate> ret = new HashMap<>();
        List<SingleTypeInfo> deletedInfo = new ArrayList<>();
        Iterator<Map.Entry<URLStatic, Set<HttpResponseParams>>> iterator = aggregator.urls.entrySet().iterator();
        try {
            while (iterator.hasNext()) {
                Map.Entry<URLStatic, Set<HttpResponseParams>> entry = iterator.next();
                URLStatic url = entry.getKey();
                Set<HttpResponseParams> responseParamsList = entry.getValue();
                RequestTemplate requestTemplate = ret.get(url);
                if (requestTemplate == null) {
                    requestTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
                    ret.put(url, requestTemplate);
                }
                processResponse(requestTemplate, responseParamsList, deletedInfo);
                iterator.remove();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return ret;
    }

    private void processKnownStaticURLs(URLAggregator aggregator, APICatalog deltaCatalog, APICatalog dbCatalog) {
        Iterator<Map.Entry<URLStatic, Set<HttpResponseParams>>> iterator = aggregator.urls.entrySet().iterator();
        List<SingleTypeInfo> deletedInfo = deltaCatalog.getDeletedInfo();
        try {
            while (iterator.hasNext()) {
                Map.Entry<URLStatic, Set<HttpResponseParams>> entry = iterator.next();
                URLStatic url = entry.getKey();
                Set<HttpResponseParams> responseParamsList = entry.getValue();

                RequestTemplate strictMatch = dbCatalog.getStrictURLToMethods().get(url);
                if (strictMatch != null) {
                    RequestTemplate requestTemplate = deltaCatalog.getStrictURLToMethods().get(url);
                    if (requestTemplate == null) {
                        requestTemplate = strictMatch.copy();
                        deltaCatalog.getStrictURLToMethods().put(url, requestTemplate);
                    }

                    processResponse(requestTemplate, responseParamsList, deletedInfo);
                    iterator.remove();
                }

                RequestTemplate strictMatchInDelta = deltaCatalog.getStrictURLToMethods().get(url);
                if (strictMatchInDelta != null) {
                    processResponse(strictMatchInDelta, responseParamsList, deletedInfo);
                    iterator.remove();
                }

            }
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
        }
    }

    private static String trim(String url) {
        if (url.startsWith("/")) url = url.substring(1, url.length());
        if (url.endsWith("/")) url = url.substring(0, url.length()-1);
        return url;
    }

    private Map<Integer, Map<URLStatic, RequestTemplate>> groupByTokenSize(APICatalog catalog) {
        Map<Integer, Map<URLStatic, RequestTemplate>> sizeToURL = new HashMap<>();
        for(URLStatic rawURL: catalog.getStrictURLToMethods().keySet()) {
            RequestTemplate reqTemplate = catalog.getStrictURLToMethods().get(rawURL);
            if (reqTemplate.getUserIds().size() < 5) {
                String url = trim(rawURL.getUrl());
                String[] tokens = url.split("/");
                Map<URLStatic, RequestTemplate> urlSet = sizeToURL.get(tokens.length);
                urlSet = sizeToURL.get(tokens.length);
                if (urlSet == null) {
                    urlSet = new HashMap<>();
                    sizeToURL.put(tokens.length, urlSet);
                }

                urlSet.put(rawURL, reqTemplate);
            }
        }

        return sizeToURL;
    }

    public static String[] tokenize(String url) {
        return trim(url).split("/");
    }

    Map<String, SingleTypeInfo> convertToMap(List<SingleTypeInfo> l) {
        Map<String, SingleTypeInfo> ret = new HashMap<>();
        for(SingleTypeInfo e: l) {
            ret.put(e.composeKey(), e);
        }

        return ret;
    }

    public ArrayList<WriteModel<SampleData>> getDBUpdatesForSampleData(int apiCollectionId, APICatalog currentDelta) {
        List<SampleData> sampleData = new ArrayList<>();
        for(Map.Entry<URLStatic, RequestTemplate> entry: currentDelta.getStrictURLToMethods().entrySet()) {
            Key key = new Key(apiCollectionId, entry.getKey().getUrl(), entry.getKey().getMethod(), -1, 0, 0);
            sampleData.add(new SampleData(key, entry.getValue().removeAllSampleMessage()));
        }

        for(Map.Entry<URLTemplate, RequestTemplate> entry: currentDelta.getTemplateURLToMethods().entrySet()) {
            Key key = new Key(apiCollectionId, entry.getKey().getTemplateString(), entry.getKey().getMethod(), -1, 0, 0);
            sampleData.add(new SampleData(key, entry.getValue().removeAllSampleMessage()));
        }

        ArrayList<WriteModel<SampleData>> bulkUpdates = new ArrayList<>();
        for (SampleData sample: sampleData) {
            if (sample.getSamples().size() == 0) {
                continue;
            }
            Bson bson = Updates.pushEach("samples", sample.getSamples(), new PushOptions().slice(-10));

            bulkUpdates.add(
                new UpdateOneModel<>(Filters.eq("_id", sample.getId()), bson, new UpdateOptions().upsert(true))
            );
        }

        return bulkUpdates;        
    }




    public ArrayList<WriteModel<TrafficInfo>> getDBUpdatesForTraffic(int apiCollectionId, APICatalog currentDelta) {

        List<TrafficInfo> trafficInfos = new ArrayList<>();
        for(Map.Entry<URLStatic, RequestTemplate> entry: currentDelta.getStrictURLToMethods().entrySet()) {
            trafficInfos.addAll(entry.getValue().removeAllTrafficInfo(apiCollectionId, entry.getKey().getUrl(), entry.getKey().getMethod(), -1));
        }

        for(Map.Entry<URLTemplate, RequestTemplate> entry: currentDelta.getTemplateURLToMethods().entrySet()) {
            trafficInfos.addAll(entry.getValue().removeAllTrafficInfo(apiCollectionId, entry.getKey().getTemplateString(), entry.getKey().getMethod(), -1));
        }

        ArrayList<WriteModel<TrafficInfo>> bulkUpdates = new ArrayList<>();
        for (TrafficInfo trafficInfo: trafficInfos) {
            List<Bson> updates = new ArrayList<>();

            for (Map.Entry<String, Integer> entry: trafficInfo.mapHoursToCount.entrySet()) {
                updates.add(Updates.inc("mapHoursToCount."+entry.getKey(), entry.getValue())); 
            }

            bulkUpdates.add(
                new UpdateOneModel<>(Filters.eq("_id", trafficInfo.get_id()), Updates.combine(updates), new UpdateOptions().upsert(true))
            );
        }

        return bulkUpdates;
    }

    public DbUpdateReturn getDBUpdatesForParams(APICatalog currentDelta, APICatalog currentState) {
        Map<String, SingleTypeInfo> dbInfoMap = convertToMap(currentState.getAllTypeInfo());
        Map<String, SingleTypeInfo> deltaInfoMap = convertToMap(currentDelta.getAllTypeInfo());

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSampleData = new ArrayList<>();
        int now = Context.now();
        for(String key: deltaInfoMap.keySet()) {
            SingleTypeInfo dbInfo = dbInfoMap.get(key);
            SingleTypeInfo deltaInfo = deltaInfoMap.get(key);
            Bson update;

            int inc = deltaInfo.getCount() - (dbInfo == null ? 0 : dbInfo.getCount());

            if (inc == 0) {
                continue;
            }

            int oldTs = dbInfo == null ? 0 : dbInfo.getTimestamp();

            update = Updates.inc("count", inc);

            if (oldTs == 0) {
                update = Updates.combine(update, Updates.set("timestamp", now));
                if (deltaInfo.getExamples() != null && !deltaInfo.getExamples().isEmpty()) {
                    Bson bson = Updates.pushEach(SensitiveSampleData.SAMPLE_DATA, Arrays.asList(deltaInfo.getExamples().toArray()), new PushOptions().slice(-1 *SensitiveSampleData.cap));
                    bulkUpdatesForSampleData.add(
                            new UpdateOneModel<>(
                                    SensitiveSampleDataDao.getFilters(deltaInfo),
                                    bson,
                                    new UpdateOptions().upsert(true)
                            )
                    );
                }
            }

            Bson updateKey = createFilters(deltaInfo);

            bulkUpdates.add(new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true)));
        }

        for(SingleTypeInfo deleted: currentDelta.getDeletedInfo()) {
            bulkUpdates.add(new DeleteOneModel<>(createFilters(deleted), new DeleteOptions()));
            bulkUpdatesForSampleData.add(new DeleteOneModel<>(SensitiveSampleDataDao.getFilters(deleted), new DeleteOptions()));
        }

        return new DbUpdateReturn(bulkUpdates, bulkUpdatesForSampleData);
    }

    public static class DbUpdateReturn {
        public ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo;
        public ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSampleData;

        public DbUpdateReturn(ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo, ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSampleData) {
            this.bulkUpdatesForSingleTypeInfo = bulkUpdatesForSingleTypeInfo;
            this.bulkUpdatesForSampleData = bulkUpdatesForSampleData;
        }
    }

    public static Bson createFilters(SingleTypeInfo info) {
        return Filters.and(
            Filters.eq("url", info.getUrl()),
            Filters.eq("method", info.getMethod()),
            Filters.eq("responseCode", info.getResponseCode()),
            Filters.eq("isHeader", info.getIsHeader()),
            Filters.eq("param", info.getParam()),
            Filters.eq("subType", info.getSubType()),
            Filters.eq("apiCollectionId", info.getApiCollectionId())
        );
    }

    public static URLTemplate createUrlTemplate(String url, Method method) {
        String[] tokens = trim(url).split("/");
        SuperType[] types = new SuperType[tokens.length];
        for(int i = 0; i < tokens.length; i ++ ) {
            String token = tokens[i];

            if (token.equals("STRING")) {
                tokens[i] = null;
                types[i] = SuperType.STRING;
            } else if (token.equals("INTEGER")) {
                tokens[i] = null;
                types[i] = SuperType.INTEGER;
            } else {
                types[i] = null;
            }

        }

        URLTemplate urlTemplate = new URLTemplate(tokens, types, method);

        return urlTemplate;
    }

    public void buildFromDB(boolean calcDiff) {
        List<SingleTypeInfo> allParams = SingleTypeInfoDao.instance.fetchAll();
        this.dbState = build(allParams);
        
        if(calcDiff) {
            for(int collectionId: this.dbState.keySet()) {
                APICatalog newCatalog = this.dbState.get(collectionId);
                Set<String> newURLs = new HashSet<>();
                for(URLTemplate url: newCatalog.getTemplateURLToMethods().keySet()) { 
                    newURLs.add(url.getTemplateString()+ " "+ url.getMethod().name());
                }
                for(URLStatic url: newCatalog.getStrictURLToMethods().keySet()) { 
                    newURLs.add(url.getUrl()+ " "+ url.getMethod().name());
                }

                Bson findQ = Filters.eq("_id", collectionId);

                ApiCollectionsDao.instance.getMCollection().updateOne(findQ, Updates.set("urls", newURLs));
            }
        }
    }

    private static Map<Integer, APICatalog> build(List<SingleTypeInfo> allParams) {
        Map<Integer, APICatalog> ret = new HashMap<>();
        
        for (SingleTypeInfo param: allParams) {
            String url = param.getUrl();
            int collId = param.getApiCollectionId();
            APICatalog catalog = ret.get(collId);

            if (catalog == null) {
                catalog = new APICatalog(collId, new HashMap<>(), new HashMap<>());
                ret.put(collId, catalog);
            }
            RequestTemplate reqTemplate;
            if (url.contains("STRING") || url.contains("INTEGER")) {
                URLTemplate urlTemplate = createUrlTemplate(url, Method.valueOf(param.getMethod()));
                reqTemplate = catalog.getTemplateURLToMethods().get(urlTemplate);

                if (reqTemplate == null) {
                    reqTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
                    catalog.getTemplateURLToMethods().put(urlTemplate, reqTemplate);
                }

            } else {
                URLStatic urlStatic = new URLStatic(url, Method.valueOf(param.getMethod()));
                reqTemplate = catalog.getStrictURLToMethods().get(urlStatic);
                if (reqTemplate == null) {
                    reqTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
                    catalog.getStrictURLToMethods().put(urlStatic, reqTemplate);
                }
            }

            if (param.getResponseCode() > 0) {
                RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(param.getResponseCode());
                if (respTemplate == null) {
                    respTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
                    reqTemplate.getResponseTemplates().put(param.getResponseCode(), respTemplate);
                }

                reqTemplate = respTemplate;
            }

            Map<String, KeyTypes> keyTypesMap = param.getIsHeader() ? reqTemplate.getHeaders() : reqTemplate.getParameters();
            KeyTypes keyTypes = keyTypesMap.get(param.getParam());

            if (keyTypes == null) {
                keyTypes = new KeyTypes(new HashMap<>(), false);

                if (param.getParam() == null) {
                    logger.info("null value - " + param.composeKey());
                }

                keyTypesMap.put(param.getParam(), keyTypes);
            }

            SingleTypeInfo info = keyTypes.getOccurrences().get(param.getSubType());
            if (info != null && info.getTimestamp() > param.getTimestamp()) {
                param = info;
            }

            keyTypes.getOccurrences().put(param.getSubType(), param);
        }

        for (APICatalog catalog: ret.values()) {
            for (RequestTemplate requestTemplate: catalog.getStrictURLToMethods().values()) {
                requestTemplate.buildTrie();

                for (RequestTemplate responseTemplate: requestTemplate.getResponseTemplates().values()) {
                    responseTemplate.buildTrie();
                }    
            }
        }

        return ret;
    }

    int counter = 0;

    public void syncWithDB() {
        List<WriteModel<SingleTypeInfo>> writesForParams = new ArrayList<>();
        List<WriteModel<SensitiveSampleData>> writesForSensitiveSampleData = new ArrayList<>();
        List<WriteModel<TrafficInfo>> writesForTraffic = new ArrayList<>();
        List<WriteModel<SampleData>> writesForSampleData = new ArrayList<>();
        counter++;
        for(int apiCollectionId: this.delta.keySet()) {
            APICatalog deltaCatalog = this.delta.get(apiCollectionId);
            APICatalog dbCatalog = this.dbState.getOrDefault(apiCollectionId, new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>()));
            DbUpdateReturn dbUpdateReturn = getDBUpdatesForParams(deltaCatalog, dbCatalog);
            writesForParams.addAll(dbUpdateReturn.bulkUpdatesForSingleTypeInfo);
            writesForSensitiveSampleData.addAll(dbUpdateReturn.bulkUpdatesForSampleData);
            writesForTraffic.addAll(getDBUpdatesForTraffic(apiCollectionId, deltaCatalog));
            deltaCatalog.setDeletedInfo(new ArrayList<>());
            if (counter < 10 || counter % 10 == 0) {
                writesForSampleData.addAll(getDBUpdatesForSampleData(apiCollectionId, deltaCatalog));
            }
        }

        logger.info("adding " + writesForParams.size() + " updates for params");

        long start = System.currentTimeMillis();

        if (writesForParams.size() >0) {
            
            BulkWriteResult res = 
                SingleTypeInfoDao.instance.getMCollection().bulkWrite(
                    writesForParams
                );

            logger.info((System.currentTimeMillis() - start) + ": " + res.getInserts().size() + " " +res.getUpserts().size());
        }

        logger.info("adding " + writesForTraffic.size() + " updates for traffic");
        if(writesForTraffic.size() > 0) {
            BulkWriteResult res = TrafficInfoDao.instance.getMCollection().bulkWrite(writesForTraffic);

            logger.info(res.getInserts().size() + " " +res.getUpserts().size());

        }
        

        logger.info("adding " + writesForSampleData.size() + " updates for samples");
        if(writesForSampleData.size() > 0) {
            BulkWriteResult res = SampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);

            logger.info(res.getInserts().size() + " " +res.getUpserts().size());

        }

        if (writesForSensitiveSampleData.size() > 0) {
            SensitiveSampleDataDao.instance.getMCollection().bulkWrite(writesForSensitiveSampleData);
        }

        buildFromDB(true);
    }

    public void printNewURLsInDelta(APICatalog deltaCatalog) {
        for(URLStatic s: deltaCatalog.getStrictURLToMethods().keySet()) {
            logger.info(s.getUrl());
        }

        for(URLTemplate s: deltaCatalog.getTemplateURLToMethods().keySet()) {
            logger.info(s.getTemplateString());
        }
    }


    public APICatalog getDelta(int apiCollectionId) {
        return this.delta.get(apiCollectionId);
    }


    public APICatalog getDbState(int apiCollectionId) {
        return this.dbState.get(apiCollectionId);
    }
}
