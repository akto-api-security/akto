package com.akto.runtime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.HttpResponseParams.Source;
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
import com.akto.parsers.HttpCallParser;
import com.akto.types.CappedSet;
import com.akto.utils.RedactSampleData;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.akto.dto.type.KeyTypes.patternToSubType;

public class APICatalogSync {
    
    public int thresh;
    public String userIdentifier;
    private static final Logger logger = LoggerFactory.getLogger(APICatalogSync.class);
    public Map<Integer, APICatalog> dbState;
    public Map<Integer, APICatalog> delta;
    public Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap;
    private static boolean mergeAsyncOutside = false;

    public APICatalogSync(String userIdentifier,int thresh) {
        this.thresh = thresh;
        this.userIdentifier = userIdentifier;
        this.dbState = new HashMap<>();
        this.delta = new HashMap<>();
        this.sensitiveParamInfoBooleanMap = new HashMap<>();
        try {
            mergeAsyncOutside = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter()).getMergeAsyncOutside();
        } catch (Exception e) {
            
        }
        
    }

    public static final int STRING_MERGING_THRESHOLD = 20;

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
        responseParams.requestParams.url = baseURL.getUrl();
        int statusCode = responseParams.getStatusCode();
        Method method = Method.fromString(methodStr);
        String userId = extractUserId(responseParams, userIdentifier);

        if (!responseParams.getIsPending()) {
            requestTemplate.processTraffic(responseParams.getTime());
        }
        if (HttpResponseParams.validHttpResponseCode(statusCode)) {
            String reqPayload = requestParams.getPayload();

            if (reqPayload == null || reqPayload.isEmpty()) {
                reqPayload = "{}";
            }

            requestTemplate.processHeaders(requestParams.getHeaders(), baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap);
            BasicDBObject payload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams);
            if (payload != null) {
                deletedInfo.addAll(requestTemplate.process2(payload, baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap));
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

            if (respPayload == null || respPayload.isEmpty()) {
                respPayload = "{}";
            }

            if(respPayload.startsWith("[")) {
                respPayload = "{\"json\": "+respPayload+"}";
            }


            BasicDBObject payload;
            try {
                payload = BasicDBObject.parse(respPayload);
            } catch (Exception e) {
                payload = BasicDBObject.parse("{}");
            }

            deletedInfo.addAll(responseTemplate.process2(payload, baseURL.getUrl(), methodStr, statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap));
            responseTemplate.processHeaders(responseParams.getHeaders(), baseURL.getUrl(), method.name(), statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap);
            if (!responseParams.getIsPending()) {
                responseTemplate.processTraffic(responseParams.getTime());
            }

        } catch (JsonParseException e) {
            logger.error("Failed to parse json payload " + e.getMessage());
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
        tryWithKnownURLTemplates(pendingRequests, deltaCatalog, dbCatalog, apiCollectionId );
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
                RequestTemplate rt = deltaTemplates.get(newUrl);
                if (rt != null) {
                    rt.mergeFrom(newTemplate);
                } else {
                    deltaTemplates.put(newUrl, newTemplate);
                }
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

                    if (areBothUuidUrls(newUrl,dbUrl,mergedTemplate) || dbTemplate.compare(newTemplate, mergedTemplate)) {
                        Set<RequestTemplate> similarTemplates = potentialMerges.get(mergedTemplate);
                        if (similarTemplates == null) {
                            similarTemplates = new HashSet<>();
                            potentialMerges.put(mergedTemplate, similarTemplates);
                        } 
                        similarTemplates.add(dbTemplate);
                        countSimilarURLs++;
                     }
                }
                     
                if (countSimilarURLs >= STRING_MERGING_THRESHOLD) {
                    URLTemplate mergedTemplate = potentialMerges.keySet().iterator().next();
                    RequestTemplate alreadyInDelta = deltaCatalog.getTemplateURLToMethods().get(mergedTemplate);
                    RequestTemplate dbTemplate = potentialMerges.get(mergedTemplate).iterator().next();

                    if (alreadyInDelta != null) {
                        alreadyInDelta.mergeFrom(newTemplate);
                    } else {
                        RequestTemplate dbCopy = dbTemplate.copy();
                        dbCopy.mergeFrom(newTemplate);    
                        deltaCatalog.getTemplateURLToMethods().put(mergedTemplate, dbCopy);
                    }

                    alreadyInDelta = deltaCatalog.getTemplateURLToMethods().get(mergedTemplate);

                    for (RequestTemplate rt: potentialMerges.getOrDefault(mergedTemplate, new HashSet<>())) {
                        alreadyInDelta.mergeFrom(rt);
                        deltaCatalog.getDeletedInfo().addAll(rt.getAllTypeInfo());
                    }
                    deltaCatalog.getDeletedInfo().addAll(dbTemplate.getAllTypeInfo());
                    
                    newUrlMatchedInDb = true;
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
                if (mergedTemplate == null || (RequestTemplate.isMergedOnStr(mergedTemplate) && !areBothUuidUrls(newUrl,deltaUrl,mergedTemplate))) {
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

            RequestTemplate rt = deltaTemplates.get(newUrl);
            if (rt != null) {
                rt.mergeFrom(newTemplate);
            } else {
                deltaTemplates.put(newUrl, newTemplate);
            }
            iterator.remove();
        }
    }

    public static boolean areBothUuidUrls(URLStatic newUrl, URLStatic deltaUrl, URLTemplate mergedTemplate) {
        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);

        String[] n = tokenize(newUrl.getUrl());
        String[] o = tokenize(deltaUrl.getUrl());
        SuperType[] b = mergedTemplate.getTypes();
        for (int idx =0 ; idx < b.length; idx++) {
            SuperType c = b[idx];
            if (Objects.equals(c, SuperType.STRING) && o.length > idx) {
                String val = n[idx];
                if(!pattern.matcher(val).matches() || !pattern.matcher(o[idx]).matches()) {
                    return false;
                }
            }
        }

        return true;
    }


    public static URLTemplate tryMergeUrls(URLStatic dbUrl, URLStatic newUrl) {
        if (dbUrl.getMethod() != newUrl.getMethod()) {
            return null;
        }
        String[] dbTokens = tokenize(dbUrl.getUrl());
        String[] newTokens = tokenize(newUrl.getUrl());

        if (dbTokens.length != newTokens.length) {
            return null;
        }

        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);

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
            } else if(pattern.matcher(tempToken).matches() && pattern.matcher(dbToken).matches()){
                newTypes[i] = SuperType.STRING;
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
        APICatalog dbCatalog,
        int apiCollectionId
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
                            alreadyInDelta.fillUrlParams(tokenize(newUrl.getUrl()), urlTemplate, apiCollectionId);
                            alreadyInDelta.mergeFrom(newRequestTemplate);
                        } else {
                            RequestTemplate dbTemplate = dbCatalog.getTemplateURLToMethods().get(urlTemplate);
                            RequestTemplate dbCopy = dbTemplate.copy();
                            dbCopy.mergeFrom(newRequestTemplate);
                            dbCopy.fillUrlParams(tokenize(newUrl.getUrl()), urlTemplate, apiCollectionId);
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
                    Map<URLStatic, RequestTemplate> deltaCatalogStrictURLToMethods = deltaCatalog.getStrictURLToMethods();
                    RequestTemplate requestTemplate = deltaCatalogStrictURLToMethods.get(url);
                    if (requestTemplate == null) {
                        requestTemplate = strictMatch.copy(); // to further process the requestTemplate
                        deltaCatalogStrictURLToMethods.put(url, requestTemplate) ;
                        strictMatch.mergeFrom(requestTemplate); // to update the existing requestTemplate in db with new data
                    }

                    processResponse(requestTemplate, responseParamsList, deletedInfo);
                    iterator.remove();
                }

            }
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
        }
    }

    public static String trim(String url) {
        if (mergeAsyncOutside) {
            if ( !(url.startsWith("/") ) && !( url.startsWith("http") || url.startsWith("ftp")) ){
                url = "/" + url;
            }
        } else {
            if (url.startsWith("/")) url = url.substring(1, url.length());
        }
        
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

    public ArrayList<WriteModel<SampleData>> getDBUpdatesForSampleData(int apiCollectionId, APICatalog currentDelta, APICatalog dbCatalog ,boolean redactSampleData, boolean forceUpdate ) {
        List<SampleData> sampleData = new ArrayList<>();
        Map<URLStatic, RequestTemplate> deltaStrictURLToMethods = currentDelta.getStrictURLToMethods();
        Map<URLStatic, RequestTemplate> dbStrictURLToMethods = dbCatalog.getStrictURLToMethods();

        for(Map.Entry<URLStatic, RequestTemplate> entry: deltaStrictURLToMethods.entrySet()) {
            if (forceUpdate || !dbStrictURLToMethods.containsKey(entry.getKey())) {
                Key key = new Key(apiCollectionId, entry.getKey().getUrl(), entry.getKey().getMethod(), -1, 0, 0);
                sampleData.add(new SampleData(key, entry.getValue().removeAllSampleMessage()));
            }
        }

        Map<URLTemplate, RequestTemplate> deltaTemplateURLToMethods = currentDelta.getTemplateURLToMethods();
        Map<URLTemplate, RequestTemplate> dbTemplateURLToMethods = dbCatalog.getTemplateURLToMethods();

        for(Map.Entry<URLTemplate, RequestTemplate> entry: deltaTemplateURLToMethods.entrySet()) {
            if (forceUpdate || !dbTemplateURLToMethods.containsKey(entry.getKey())) {
                Key key = new Key(apiCollectionId, entry.getKey().getTemplateString(), entry.getKey().getMethod(), -1, 0, 0);
                sampleData.add(new SampleData(key, entry.getValue().removeAllSampleMessage()));
            }
        }

        ArrayList<WriteModel<SampleData>> bulkUpdates = new ArrayList<>();
        for (SampleData sample: sampleData) {
            if (sample.getSamples().size() == 0) {
                continue;
            }
            List<String> finalSamples = new ArrayList<>();
            for (String s: sample.getSamples()) {
                boolean finalRedact = redactSampleData;
                if (finalRedact) {
                    try {
                        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(s);
                        Source source = httpResponseParams.getSource();
                        if (source.equals(Source.HAR) || source.equals(Source.PCAP)) finalRedact = false;
                    } catch (Exception e1) {
                        e1.printStackTrace();
                        continue;
                    }
                }

                try {
                    if (finalRedact) {
                        String redact = RedactSampleData.redact(s);
                        finalSamples.add(redact);
                    } else {
                        finalSamples.add(s);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Bson bson = Updates.pushEach("samples", finalSamples, new PushOptions().slice(-10));

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

    public DbUpdateReturn getDBUpdatesForParams(APICatalog currentDelta, APICatalog currentState, boolean redactSampleData) {
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
            long lastSeenDiff = deltaInfo.getLastSeen() - (dbInfo == null ? 0 : dbInfo.getLastSeen());
            boolean minMaxChanged = (dbInfo == null) || (dbInfo.getMinValue() != deltaInfo.getMinValue()) || (dbInfo.getMaxValue() != deltaInfo.getMaxValue());
            boolean valuesChanged = (dbInfo == null) || (dbInfo.getValues().count() != deltaInfo.getValues().count());

            if (inc == 0 && lastSeenDiff < (60*30) && !minMaxChanged && !valuesChanged) {
                continue;
            } else {
                inc = 1;
            }

            int oldTs = dbInfo == null ? 0 : dbInfo.getTimestamp();

            update = Updates.inc("count", inc);

            if (oldTs == 0) {
                update = Updates.combine(update, Updates.set("timestamp", now));
            }

            update = Updates.combine(update, Updates.max(SingleTypeInfo.LAST_SEEN, deltaInfo.getLastSeen()));
            update = Updates.combine(update, Updates.max(SingleTypeInfo.MAX_VALUE, deltaInfo.getMaxValue()));
            update = Updates.combine(update, Updates.min(SingleTypeInfo.MIN_VALUE, deltaInfo.getMinValue()));

            if (dbInfo != null) {
                SingleTypeInfo.Domain domain = dbInfo.getDomain();
                if (domain ==  SingleTypeInfo.Domain.ENUM) {
                    CappedSet<String> values = dbInfo.getValues();
                    Set<String> elements = new HashSet<>();
                    if (values != null) {
                        elements = values.getElements();
                    }
                    int valuesSize = elements.size();
                    if (valuesSize >= SingleTypeInfo.VALUES_LIMIT) {
                        SingleTypeInfo.Domain newDomain;
                        if (dbInfo.getSubType().equals(SingleTypeInfo.INTEGER_32) || dbInfo.getSubType().equals(SingleTypeInfo.INTEGER_64) || dbInfo.getSubType().equals(SingleTypeInfo.FLOAT)) {
                            newDomain = SingleTypeInfo.Domain.RANGE;
                        } else {
                            newDomain = SingleTypeInfo.Domain.ANY;
                        }
                        update = Updates.combine(update, Updates.set(SingleTypeInfo._DOMAIN, newDomain));
                    }
                } else {
                    deltaInfo.setDomain(dbInfo.getDomain());
                    deltaInfo.setValues(new CappedSet<>());
                    if (!dbInfo.getValues().getElements().isEmpty()) {
                        Bson bson = Updates.set(SingleTypeInfo._VALUES +".elements",new ArrayList<>());
                        update = Updates.combine(update, bson);
                    }
                }
            }

            if (dbInfo == null || dbInfo.getDomain() == SingleTypeInfo.Domain.ENUM) {
                CappedSet<String> values = deltaInfo.getValues();
                if (values != null) {
                    Set<String> elements = new HashSet<>();
                    for (String el: values.getElements()) {
                        if (redactSampleData) {
                            elements.add(el.hashCode()+"");
                        } else {
                            elements.add(el);
                        }
                    }
                    Bson bson = Updates.addEachToSet(SingleTypeInfo._VALUES +".elements",new ArrayList<>(elements));
                    update = Updates.combine(update, bson);
                    deltaInfo.setValues(new CappedSet<>());
                }
            }


            if (!redactSampleData && deltaInfo.getExamples() != null && !deltaInfo.getExamples().isEmpty()) {
                Bson bson = Updates.pushEach(SensitiveSampleData.SAMPLE_DATA, Arrays.asList(deltaInfo.getExamples().toArray()), new PushOptions().slice(-1 *SensitiveSampleData.cap));
                bulkUpdatesForSampleData.add(
                        new UpdateOneModel<>(
                                SensitiveSampleDataDao.getFilters(deltaInfo),
                                bson,
                                new UpdateOptions().upsert(true)
                        )
                );
            }

            Bson updateKey = SingleTypeInfoDao.createFilters(deltaInfo);

            bulkUpdates.add(new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true)));
        }

        for(SingleTypeInfo deleted: currentDelta.getDeletedInfo()) {
            currentDelta.getStrictURLToMethods().remove(new URLStatic(deleted.getUrl(), Method.fromString(deleted.getMethod())));
            bulkUpdates.add(new DeleteOneModel<>(SingleTypeInfoDao.createFilters(deleted), new DeleteOptions()));
            bulkUpdatesForSampleData.add(new DeleteOneModel<>(SensitiveSampleDataDao.getFilters(deleted), new DeleteOptions()));
        }


        ArrayList<WriteModel<SensitiveParamInfo>> bulkUpdatesForSensitiveParamInfo = new ArrayList<>();
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfoBooleanMap.keySet()) {
            if (!sensitiveParamInfoBooleanMap.get(sensitiveParamInfo)) continue;
            bulkUpdatesForSensitiveParamInfo.add(
                    new UpdateOneModel<SensitiveParamInfo>(
                            SensitiveParamInfoDao.getFilters(sensitiveParamInfo),
                            Updates.set(SensitiveParamInfo.SAMPLE_DATA_SAVED, true),
                            new UpdateOptions().upsert(false)
                    )
            );
        }

        return new DbUpdateReturn(bulkUpdates, bulkUpdatesForSampleData, bulkUpdatesForSensitiveParamInfo);
    }

    public static class DbUpdateReturn {
        public ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo;
        public ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSampleData;
        public ArrayList<WriteModel<SensitiveParamInfo>> bulkUpdatesForSensitiveParamInfo = new ArrayList<>();

        public DbUpdateReturn(ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo,
                              ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSampleData,
                              ArrayList<WriteModel<SensitiveParamInfo>> bulkUpdatesForSensitiveParamInfo
        ) {
            this.bulkUpdatesForSingleTypeInfo = bulkUpdatesForSingleTypeInfo;
            this.bulkUpdatesForSampleData = bulkUpdatesForSampleData;
            this.bulkUpdatesForSensitiveParamInfo = bulkUpdatesForSensitiveParamInfo;
        }
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

    public static void mergeSTI(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson stiFilter = Filters.and(
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                Filters.eq(SingleTypeInfo._METHOD, method),
                Filters.in(SingleTypeInfo._URL,toMergeUrls)
        );
        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(stiFilter);
        if (singleTypeInfos.isEmpty()) return;

        Set<String> singleTypeInfoSet = new HashSet<>();
        List<SingleTypeInfo> stiResult = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            singleTypeInfo.setUrl(mergedUrl);
            String key = singleTypeInfo.composeKey();
            if (singleTypeInfoSet.contains(key)) continue;
            singleTypeInfoSet.add(singleTypeInfo.composeKey());
            stiResult.add(singleTypeInfo);
        }

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: stiResult) {
            Bson filter = SingleTypeInfoDao.createFilters(singleTypeInfo);
            Bson update = Updates.set("count", 1);
            bulkUpdates.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
        SingleTypeInfoDao.instance.deleteAll(stiFilter);
    }

    public static void mergeApiInfo(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson apiInfoFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(apiInfoFilter);
        if (apiInfos.isEmpty()) return;
        ApiInfo mainApiInfo = apiInfos.get(0);
        mainApiInfo.setId(new ApiInfo.ApiInfoKey(apiCollectionId, mergedUrl, method));
        ApiInfoDao.instance.insertOne(mainApiInfo);
        ApiInfoDao.instance.deleteAll(apiInfoFilter);
    }

    public static void mergeSampleData(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method){
        Bson sampleDataFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(sampleDataFilter);
        if (sampleDataList.isEmpty()) return;
        SampleData mainSampleData = sampleDataList.get(0);
        mainSampleData.setId(new Key(apiCollectionId, mergedUrl,method, -1, 0, 0));
        SampleDataDao.instance.insertOne(mainSampleData);
        SampleDataDao.instance.deleteAll(sampleDataFilter);
    }

    public static void mergeTrafficInfo(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson trafficFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        TrafficInfoDao.instance.deleteAll(trafficFilter);
    }

    public static void mergeSensitiveSampleData(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson sensitiveSampleDataFilter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", method.name()),
                Filters.in("_id.url", new ArrayList<>(toMergeUrls))
        );
        List<SensitiveSampleData> sensitiveSampleList = SensitiveSampleDataDao.instance.findAll(sensitiveSampleDataFilter);
        if (sensitiveSampleList.isEmpty()) return;
        Set<String> sensitiveSetSampleDataKeySet = new HashSet<>();
        List<SensitiveSampleData> resultSensitiveSampleData = new ArrayList<>();
        for (SensitiveSampleData sensitiveSampleData: sensitiveSampleList) {
            SingleTypeInfo.ParamId paramId = sensitiveSampleData.getId();
            paramId.setUrl(mergedUrl);
            SingleTypeInfo dummy = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            String key = dummy.composeKey();
            if (sensitiveSetSampleDataKeySet.contains(key)) continue;
            sensitiveSetSampleDataKeySet.add(key);
            resultSensitiveSampleData.add(sensitiveSampleData);
        }
        SensitiveSampleDataDao.instance.insertMany(resultSensitiveSampleData);
        SensitiveSampleDataDao.instance.deleteAll(sensitiveSampleDataFilter);
    }

    public static void mergeSensitiveParamInfo(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson sensitiveParamInfoFilter = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("method", method),
                Filters.in("url",toMergeUrls)
        );
        List<SensitiveParamInfo> sensitiveParamInfoList = SensitiveParamInfoDao.instance.findAll(sensitiveParamInfoFilter);
        if (sensitiveParamInfoList.isEmpty()) return;
        Set<String> sensitiveParamInfoKeySet = new HashSet<>();
        List<SensitiveParamInfo> resultSensitiveParamInfo = new ArrayList<>();
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfoList) {
            sensitiveParamInfo.setUrl(mergedUrl);
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(sensitiveParamInfo.getParam(), sensitiveParamInfo.getMethod(), sensitiveParamInfo.getResponseCode(), sensitiveParamInfo.isIsHeader(), sensitiveParamInfo.getParam(), SingleTypeInfo.GENERIC, apiCollectionId, false);
            SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            String key = singleTypeInfo.composeKey();

            if (sensitiveParamInfoKeySet.contains(key)) continue;
            sensitiveParamInfoKeySet.add(key);
            resultSensitiveParamInfo.add(sensitiveParamInfo);
        }
        SensitiveParamInfoDao.instance.insertMany(resultSensitiveParamInfo);
        SensitiveParamInfoDao.instance.deleteAll(sensitiveParamInfoFilter);
    }

    public static void mergeFilterSampleData(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        Bson filterSampleDataFilter = Filters.and(
                Filters.eq("_id.apiInfoKey.apiCollectionId", apiCollectionId),
                Filters.eq("_id.apiInfoKey.method",method),
                Filters.in("_id.apiInfoKey.url", toMergeUrls)
        );
        List<FilterSampleData> filterSampleList = FilterSampleDataDao.instance.findAll(filterSampleDataFilter);
        if (filterSampleList.isEmpty()) return;
        Set<String> filterSampleDataKeySet = new HashSet<>();
        List<FilterSampleData> resultFilterSampleList = new ArrayList<>();
        for (FilterSampleData filterSampleData: filterSampleList) {
            FilterSampleData.FilterKey id = filterSampleData.getId();
            ApiInfo.ApiInfoKey apiInfoKey = id.apiInfoKey;
            apiInfoKey.setUrl(mergedUrl);
            int filterId = id.filterId;
            String key = StringUtils.joinWith("@", apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method, filterId);

            if (filterSampleDataKeySet.contains(key)) continue;
            filterSampleDataKeySet.add(key);
            resultFilterSampleList.add(filterSampleData);
        }
        FilterSampleDataDao.instance.insertMany(resultFilterSampleList);
        FilterSampleDataDao.instance.deleteAll(filterSampleDataFilter);
    }


    public static void mergeAndUpdateDb(String mergedUrl, Set<String> toMergeUrls, int apiCollectionId, Method method) {
        mergeSTI(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeApiInfo(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeSampleData(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeTrafficInfo(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeSensitiveSampleData(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeSensitiveParamInfo(mergedUrl, toMergeUrls, apiCollectionId, method);
        mergeFilterSampleData(mergedUrl, toMergeUrls, apiCollectionId, method);
    }


    public void buildFromDB(boolean calcDiff, boolean fetchAllSTI) {
        List<SingleTypeInfo> allParams;
        if (fetchAllSTI) {
            allParams = SingleTypeInfoDao.instance.fetchAll();
        } else {
            List<Integer> apiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
            allParams = SingleTypeInfoDao.instance.fetchStiOfCollections(apiCollectionIds);
        }
        this.dbState = build(allParams);
        this.sensitiveParamInfoBooleanMap = new HashMap<>();
        List<SensitiveParamInfo> sensitiveParamInfos = SensitiveParamInfoDao.instance.getUnsavedSensitiveParamInfos();
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfos) {
            this.sensitiveParamInfoBooleanMap.put(sensitiveParamInfo, false);
        }

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
        } else {

            for(Map.Entry<Integer, APICatalog> entry: this.dbState.entrySet()) {
                int apiCollectionId = entry.getKey();
                APICatalog apiCatalog = entry.getValue();
                for(URLTemplate urlTemplate: apiCatalog.getTemplateURLToMethods().keySet()) {
                    Iterator<Map.Entry<URLStatic, RequestTemplate>> staticURLIterator = apiCatalog.getStrictURLToMethods().entrySet().iterator();
                    while(staticURLIterator.hasNext()){
                        Map.Entry<URLStatic, RequestTemplate> urlXTemplate = staticURLIterator.next();
                        URLStatic urlStatic = urlXTemplate.getKey();
                        RequestTemplate requestTemplate = urlXTemplate.getValue();
                        if (urlTemplate.match(urlStatic)) {
                            if (this.delta == null) {
                                this.delta = new HashMap<>();
                            }

                            if (this.getDelta(apiCollectionId) == null) {
                                this.delta.put(apiCollectionId, new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>()));
                            }

                            this.getDelta(apiCollectionId).getDeletedInfo().addAll(requestTemplate.getAllTypeInfo());
                            staticURLIterator.remove();
                        }
                    }
                }
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
            if (APICatalog.isTemplateUrl(url)) {
                URLTemplate urlTemplate = createUrlTemplate(url, Method.valueOf(param.getMethod()));
                reqTemplate = catalog.getTemplateURLToMethods().get(urlTemplate);

                if (reqTemplate == null) {
                    reqTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
                    catalog.getTemplateURLToMethods().put(urlTemplate, reqTemplate);
                }

            } else {
                URLStatic urlStatic = new URLStatic(url, Method.fromString(param.getMethod()));
                reqTemplate = catalog.getStrictURLToMethods().get(urlStatic);
                if (reqTemplate == null) {
                    reqTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
                    catalog.getStrictURLToMethods().put(urlStatic, reqTemplate);
                }
            }

            if (param.getIsUrlParam()) {
                Map<Integer, KeyTypes> urlParams = reqTemplate.getUrlParams();
                if (urlParams == null) {
                    urlParams = new HashMap<>();
                    reqTemplate.setUrlParams(urlParams);
                }

                String p = param.getParam();
                try {
                    int position = Integer.parseInt(p);
                    KeyTypes keyTypes = urlParams.get(position);
                    if (keyTypes == null) {
                        keyTypes = new KeyTypes(new HashMap<>(), false);
                        urlParams.put(position, keyTypes);
                    }
                    keyTypes.getOccurrences().put(param.getSubType(), param);
                } catch (Exception e) {
                    logger.error("ERROR while parsing url param position: " + p);
                }
                continue;
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
    
    public void syncWithDB(boolean syncImmediately, boolean fetchAllSTI) {
        List<WriteModel<SingleTypeInfo>> writesForParams = new ArrayList<>();
        List<WriteModel<SensitiveSampleData>> writesForSensitiveSampleData = new ArrayList<>();
        List<WriteModel<TrafficInfo>> writesForTraffic = new ArrayList<>();
        List<WriteModel<SampleData>> writesForSampleData = new ArrayList<>();
        List<WriteModel<SensitiveParamInfo>> writesForSensitiveParamInfo = new ArrayList<>();

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());

        boolean redact = false;
        if (accountSettings != null) {
            redact =  accountSettings.isRedactPayload();
        }

        counter++;
        for(int apiCollectionId: this.delta.keySet()) {
            APICatalog deltaCatalog = this.delta.get(apiCollectionId);
            APICatalog dbCatalog = this.dbState.getOrDefault(apiCollectionId, new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>()));
            DbUpdateReturn dbUpdateReturn = getDBUpdatesForParams(deltaCatalog, dbCatalog, redact);
            writesForParams.addAll(dbUpdateReturn.bulkUpdatesForSingleTypeInfo);
            writesForSensitiveSampleData.addAll(dbUpdateReturn.bulkUpdatesForSampleData);
            writesForSensitiveParamInfo.addAll(dbUpdateReturn.bulkUpdatesForSensitiveParamInfo);
            writesForTraffic.addAll(getDBUpdatesForTraffic(apiCollectionId, deltaCatalog));
            deltaCatalog.setDeletedInfo(new ArrayList<>());

            boolean forceUpdate = syncImmediately || counter % 10 == 0;
            writesForSampleData.addAll(getDBUpdatesForSampleData(apiCollectionId, deltaCatalog,dbCatalog, redact, forceUpdate));
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

        if (writesForSensitiveParamInfo.size() > 0) {
            SensitiveParamInfoDao.instance.getMCollection().bulkWrite(writesForSensitiveParamInfo);
        }

        buildFromDB(true, fetchAllSTI);
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
