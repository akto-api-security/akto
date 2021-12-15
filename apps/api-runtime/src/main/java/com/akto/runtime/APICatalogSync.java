package com.akto.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.parsers.HttpCallParser;
import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.math.NumberUtils;

public class APICatalogSync {
    
    public int thresh;
    public String userIdentifier;
    private static final Logger logger = LoggerFactory.getLogger(APICatalogSync.class);
    APICatalog dbState;
    APICatalog delta;
    List<SingleTypeInfo> deletedInfo = new ArrayList<>();

    public APICatalogSync(String userIdentifier,int thresh) {
        this.thresh = thresh;
        this.userIdentifier = userIdentifier;
        this.dbState = new APICatalog(1, new HashMap<>(), new HashMap<>());
        this.delta = new APICatalog(1, new HashMap<>(), new HashMap<>());
    }


    public void processResponse(URLMethods urlMethods, Collection<HttpResponseParams> responses, List<SingleTypeInfo> deletedInfo) {
        Iterator<HttpResponseParams> iter = responses.iterator();
        while(iter.hasNext()) {
            try {
                processResponse(urlMethods, iter.next(), deletedInfo);
            } catch (Exception e) {
                // eat all
            }
        }
    }

    public void processResponse(URLMethods urlMethods, HttpResponseParams responseParams, List<SingleTypeInfo> deletedInfo) {
        String urlWithParams = responseParams.getRequestParams().getURL();
        String baseURL = URLAggregator.getBaseURL(urlWithParams);
        BasicDBObject queryParams = URLAggregator.getQueryJSON(urlWithParams);
        String methodStr = responseParams.getRequestParams().getMethod();
        int statusCode = responseParams.getStatusCode();
        Method method = Method.valueOf(methodStr);
        String userId = extractUserId(responseParams, userIdentifier);
        RequestTemplate requestTemplate = urlMethods.getMethodToRequestTemplate().get(method);
        if (requestTemplate == null) {
            requestTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>());
            urlMethods.getMethodToRequestTemplate().put(method, requestTemplate);
        }

        if (statusCode >= 200 && statusCode < 300) {
            String reqPayload = responseParams.getRequestParams().getPayload();
            requestTemplate.processHeaders(responseParams.getRequestParams().getHeaders(), baseURL, method.name(), -1, userId);
            if (reqPayload.startsWith("{")) {
                BasicDBObject payload = BasicDBObject.parse(reqPayload);
                payload.putAll(queryParams.toMap());
                deletedInfo.addAll(requestTemplate.process2(payload, baseURL, methodStr, -1, userId));
            }
        }

        Map<Integer, RequestTemplate> responseTemplates = requestTemplate.getResponseTemplates();
        
        RequestTemplate responseTemplate = responseTemplates.get(statusCode);
        if (responseTemplate == null) {
            responseTemplate = new RequestTemplate(new HashMap<>(), null, new HashMap<>());
            responseTemplates.put(statusCode, responseTemplate);
        }

        try {
            String respPayload = responseParams.getPayload();

            if(respPayload.startsWith("[")) {
                respPayload = "{\"json\": "+respPayload+"}";
            }

            BasicDBObject payload = BasicDBObject.parse(respPayload);
            deletedInfo.addAll(responseTemplate.process2(payload, baseURL, methodStr, statusCode, userId));
            responseTemplate.processHeaders(responseParams.getHeaders(), baseURL, method.name(), statusCode, userId);
        } catch (JsonParseException e) {

        }
    }

    public static String extractUserId(HttpResponseParams responseParams, String userIdentifier) {
        List<String> token = responseParams.getRequestParams().getHeaders().get(userIdentifier);
        if (token == null || token.size() == 0) {
            return "HC_"+HttpCallParser.counter;
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

    public void computeDelta(URLAggregator origAggregator, boolean triggerTemplateGeneration) {
        URLAggregator aggregator = new URLAggregator(origAggregator.urls);

        origAggregator.urls = new ConcurrentHashMap<>();

        processKnownURLs(aggregator, deletedInfo);

        processUnknownURLs(aggregator, triggerTemplateGeneration, deletedInfo);

        for (String url: aggregator.urls.keySet()) {
            origAggregator.addURL(aggregator.urls.get(url), url);
        }
    }


    int tryGenerateURLsTimestamp = 0;

    private void processUnknownURLs(URLAggregator aggregator, boolean triggerTemplateGeneration, List<SingleTypeInfo> deletedInfo) {
        Iterator<Map.Entry<String, Set<HttpResponseParams>>> iterator = aggregator.urls.entrySet().iterator();
        try {
            while (iterator.hasNext()) {
                Map.Entry<String, Set<HttpResponseParams>> entry = iterator.next();
                String url = entry.getKey();
                Set<HttpResponseParams> responseParamsList = entry.getValue();


                URLMethods urlMethods = delta.getStrictURLToMethods().get(url);
                if (urlMethods == null) {
                    urlMethods = new URLMethods(new HashMap<>());
                    delta.getStrictURLToMethods().put(url, urlMethods);
                }

                logger.info("parsing a new url " + url);
                processResponse(urlMethods, responseParamsList, deletedInfo);
                iterator.remove();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        int now = Context.now();
        if (now - tryGenerateURLsTimestamp > 60 * 5 || triggerTemplateGeneration) {

            logger.info("trying to generate url template");
            tryGenerateURLsTimestamp = now;

            tryGenerateURLTemplates();

            iterator = aggregator.urls.entrySet().iterator();
        
            while (iterator.hasNext()) {
                Map.Entry<String, Set<HttpResponseParams>> entry = iterator.next();
                String url = entry.getKey();
                Set<HttpResponseParams> responseParamsList = entry.getValue();

                for(URLTemplate  urlTemplate: delta.getTemplateURLToMethods().keySet()) {
                    if (urlTemplate.match(url)) {
                        URLMethods urlMethods = delta.getTemplateURLToMethods().get(urlTemplate);
                        processResponse(urlMethods, responseParamsList, deletedInfo);
                        iterator.remove();
                        break;
                    }                
                }
            }
        }

    }

    private void processKnownURLs(URLAggregator aggregator, List<SingleTypeInfo> deletedInfo) {
        Iterator<Map.Entry<String, Set<HttpResponseParams>>> iterator = aggregator.urls.entrySet().iterator();
        try {
            while (iterator.hasNext()) {
                Map.Entry<String, Set<HttpResponseParams>> entry = iterator.next();
                String url = entry.getKey();
                Set<HttpResponseParams> responseParamsList = entry.getValue();

                URLMethods strictMatch = dbState.getStrictURLToMethods().get(url);
                if (strictMatch != null) {
                    URLMethods urlMethods = delta.getStrictURLToMethods().get(url);
                    if (urlMethods == null) {
                        urlMethods = strictMatch.copy();
                        delta.getStrictURLToMethods().put(url, urlMethods);
                    }

                    processResponse(urlMethods, entry.getValue(), deletedInfo);
                    iterator.remove();
                } else {
                    for (URLTemplate  urlTemplate: dbState.getTemplateURLToMethods().keySet()) {
                        if (urlTemplate.match(url)) {
                            URLMethods urlMethods = delta.getTemplateURLToMethods().get(urlTemplate);
                            if (urlMethods == null) {
                                urlMethods = dbState.getTemplateURLToMethods().get(urlTemplate).copy();
                                delta.getTemplateURLToMethods().put(urlTemplate, urlMethods);
                            }
            
                            processResponse(urlMethods, responseParamsList, deletedInfo);
                            iterator.remove();
                            break;
                        }
                    }
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

    private Map<Integer, Map<Method, Map<String, RequestTemplate>>> groupByTokenSize() {
        Map<Integer, Map<Method, Map<String, RequestTemplate>>> sizeToURL = new HashMap<>();

        for(String rawURL: delta.getStrictURLToMethods().keySet()) {
            URLMethods urlMethods = delta.getStrictURLToMethods().get(rawURL);

            for (Method method: urlMethods.getMethodToRequestTemplate().keySet()) {
                RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(method);
                if (reqTemplate.getUserIds().size() < 5) {
                    String url = trim(rawURL);
                    String[] tokens = url.split("/");
                    Map<Method, Map<String, RequestTemplate>> urlSet = sizeToURL.get(tokens.length);
                    urlSet = sizeToURL.get(tokens.length);
                    if (urlSet == null) {
                        urlSet = new HashMap<>();
                        sizeToURL.put(tokens.length, urlSet);
                    }
        
                    Map<String, RequestTemplate> urlsForThisMethod = urlSet.get(method);      
                    if (urlsForThisMethod == null) {
                        urlsForThisMethod = new HashMap<>();
                        urlSet.put(method, urlsForThisMethod);
                    }

                    urlsForThisMethod.put(rawURL, urlMethods.getMethodToRequestTemplate().get(method));
                }
            }
        }

        return sizeToURL;
    }


    private void tryGenerateURLTemplates() {

        Map<Integer, Map<Method, Map<String, RequestTemplate>>> sizeToURL = groupByTokenSize();

        Iterator<Map.Entry<Integer, Map<Method, Map<String, RequestTemplate>>>> iter = sizeToURL.entrySet().iterator();
        while(iter.hasNext()) {
            Map.Entry<Integer, Map<Method, Map<String, RequestTemplate> >> entry = iter.next();
            int tLen = entry.getKey();
            Map<Method, Map<String, RequestTemplate>> urlsForEachMethod = entry.getValue();

            if (tLen == 1) continue;

            for(Map.Entry<Method, Map<String, RequestTemplate> > entry2: urlsForEachMethod.entrySet()) {
                Method method = entry2.getKey();
                Map<String, RequestTemplate>  urls = entry2.getValue();
                removeMatchedUrls(method, urls);
            }
        }
    }

    private void removeMatchedUrls(Method method, Map<String, RequestTemplate> origUrls) {

        while (origUrls.size() > 0) {
            Iterator<Map.Entry<String, RequestTemplate>> origUrlsIterator = origUrls.entrySet().iterator();
            Map.Entry<String, RequestTemplate> urlAndTemplate = origUrlsIterator.next();
            String[] sampleUrl = tokenize(urlAndTemplate.getKey());

            URLTemplate sample = new URLTemplate(sampleUrl, new SuperType[sampleUrl.length]);
            int count = tryPatternsHelper(sample, urlAndTemplate.getValue(), 0, origUrls, thresh);
            if (count > thresh) {
                logger.info("Merging in a single URL template" + sample.getTemplateString());
                Map<Method, RequestTemplate> methodToTemplate = new HashMap<>();
                RequestTemplate newTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>());
                methodToTemplate.put(method, newTemplate);
                delta.getTemplateURLToMethods().put(sample, new URLMethods(methodToTemplate));

                Iterator<Map.Entry<String, URLMethods>> strictURLIterator = delta.getStrictURLToMethods().entrySet().iterator();

                while(strictURLIterator.hasNext()) {
                    Map.Entry<String, URLMethods> urlAndMethods = strictURLIterator.next();
                    if (sample.match(urlAndMethods.getKey())) {
                        Map<Method, RequestTemplate> strictMethods = urlAndMethods.getValue().getMethodToRequestTemplate();

                        if (strictMethods != null && strictMethods.containsKey(method)) {
                            newTemplate.mergeFrom(strictMethods.get(method));
                            RequestTemplate removed = strictMethods.remove(method);
                            if (strictMethods.size() == 0) {
                                logger.info("Removing completely" + urlAndMethods.getKey());
                                strictURLIterator.remove();
                            }
                            deletedInfo.addAll(removed.copy().getAllTypeInfo());
                        }
                    }
                }

                Iterator<Map.Entry<String, RequestTemplate>> matchIterator = origUrls.entrySet().iterator();
                while(matchIterator.hasNext()) {
                    Map.Entry<String, RequestTemplate> matchingUrlAndTemplate = matchIterator.next();
                    String url = matchingUrlAndTemplate.getKey();
                    if (sample.match(url)) {
                        matchIterator.remove();
                    }
                }
            } else {
                origUrlsIterator.remove();
            }
        }
    }


    public static String[] tokenize(String url) {
        return trim(url).split("/");
    }

    int tryPatternsHelper(URLTemplate urlTemplate, RequestTemplate requestTemplate, int index, Map<String, RequestTemplate> urls, int thresh) {

        if (index == urlTemplate.getTypes().length) {
            int count = 0;
            for(Map.Entry<String, RequestTemplate> entry: urls.entrySet()) {
                if (urlTemplate.match(tokenize(entry.getKey())) && requestTemplate.compare(entry.getValue())) {
                    count++;

                    if (count > thresh) {
                        break;
                    }
                }
            }
            return count;
        }
    
        String tempToken = urlTemplate.getTokens()[index];
        int countAsItIs = tryPatternsHelper(urlTemplate, requestTemplate, index+1, urls, thresh);

        if (countAsItIs >  thresh) {
            return countAsItIs;
        }

        urlTemplate.getTokens()[index] = null;
        int countWithPattern = 0;
        if (NumberUtils.isParsable(tempToken)) {
            urlTemplate.getTypes()[index] = SuperType.INTEGER;
            countWithPattern = tryPatternsHelper(urlTemplate, requestTemplate, index+1, urls, thresh);
        } else {
            urlTemplate.getTypes()[index] = SuperType.STRING;
            countWithPattern = tryPatternsHelper(urlTemplate, requestTemplate, index+1, urls, thresh);
        }

        if (countWithPattern >  thresh) {
            return countWithPattern;
        }

        urlTemplate.getTokens()[index] = tempToken;
        return -1;
    }

    Map<String, SingleTypeInfo> convertToMap(List<SingleTypeInfo> l) {
        Map<String, SingleTypeInfo> ret = new HashMap<>();
        for(SingleTypeInfo e: l) {
            ret.put(e.composeKey(), e);
        }

        return ret;
    }

    public ArrayList<WriteModel<SingleTypeInfo>> getDBUpdates() {
        APICatalog currentState = dbState;
        APICatalog currentDelta = delta;

        Map<String, SingleTypeInfo> dbInfoMap = convertToMap(currentState.getAllTypeInfo());
        Map<String, SingleTypeInfo> deltaInfoMap = convertToMap(currentDelta.getAllTypeInfo());

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
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
            }

            Bson updateKey = createFilters(deltaInfo);

            bulkUpdates.add(new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true)));
        }

        for(SingleTypeInfo deleted: deletedInfo) {
            bulkUpdates.add(new DeleteOneModel<>(createFilters(deleted), new DeleteOptions()));
        }

        return bulkUpdates;
    }

    public static Bson createFilters(SingleTypeInfo info) {
        return Filters.and(
            Filters.eq("url", info.getUrl()),
            Filters.eq("method", info.getMethod()),
            Filters.eq("responseCode", info.getResponseCode()),
            Filters.eq("isHeader", info.getIsHeader()),
            Filters.eq("param", info.getParam()),
            Filters.eq("subType", info.getSubType())
        );

    }

    private static URLTemplate createUrlTemplate(String url) {
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

        URLTemplate urlTemplate = new URLTemplate(tokens, types);

        return urlTemplate;
    }

    public void buildFromDB(int id) {
        List<SingleTypeInfo> allParams = SingleTypeInfoDao.instance.fetchAll();
        this.dbState = build(id, allParams);
    }

    private static APICatalog build(int id, List<SingleTypeInfo> allParams) {
        APICatalog ret = new APICatalog(id, new HashMap<>(), new HashMap<>());
        

        for (SingleTypeInfo param: allParams) {
            String url = param.getUrl();
            URLMethods urlMethods;
            if (url.contains("STRING") || url.contains("INTEGER")) {
                URLTemplate urlTemplate = createUrlTemplate(url);
                urlMethods = ret.getTemplateURLToMethods().get(urlTemplate);

                if (urlMethods == null) {
                    urlMethods = new URLMethods(new HashMap<>());
                    ret.getTemplateURLToMethods().put(urlTemplate, urlMethods);
                }

            } else {
                urlMethods = ret.getStrictURLToMethods().get(url);

                if (urlMethods == null) {
                    urlMethods = new URLMethods(new HashMap<>());
                    ret.getStrictURLToMethods().put(url, urlMethods);
                }
            }

            Method method = Method.valueOf(param.getMethod());

            RequestTemplate reqTemplate = urlMethods.getMethodToRequestTemplate().get(method);

            if (reqTemplate == null) {
                reqTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>());
                urlMethods.getMethodToRequestTemplate().put(method, reqTemplate);
            }

            if (param.getResponseCode() > 0) {
                RequestTemplate respTemplate = reqTemplate.getResponseTemplates().get(param.getResponseCode());
                if (respTemplate == null) {
                    respTemplate = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>());
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

        for (URLMethods urlMethods: ret.getStrictURLToMethods().values()) {
            for (RequestTemplate requestTemplate: urlMethods.getMethodToRequestTemplate().values()) {
                requestTemplate.buildTrie();

                for (RequestTemplate responseTemplate: requestTemplate.getResponseTemplates().values()) {
                    responseTemplate.buildTrie();
                }    
            }
        }

        return ret;
    }

    public void syncWithDB() {
        List<WriteModel<SingleTypeInfo>> writes = getDBUpdates();

        logger.info("adding " + writes.size() + " updates");

        if (writes.size() > 0) {
            SingleTypeInfoDao.instance.getMCollection().bulkWrite(writes);
        }

        buildFromDB(1);
    }

    public void printNewURLsInDelta() {
        for(String s: delta.getStrictURLToMethods().keySet()) {
            logger.info(s);
        }

        for(URLTemplate s: delta.getTemplateURLToMethods().keySet()) {
            logger.info(s.getTemplateString());
        }
    }

    public APICatalog getDelta() {
        return this.delta;
    }

    public APICatalog getDBState() {
        return this.dbState;
    }


    public void setDBState(APICatalog dbState) {
        this.dbState = dbState;
    }
}
