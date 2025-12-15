package com.akto.hybrid_runtime;

import com.akto.mcp.McpSchema;
import com.akto.util.Pair;
import java.security.interfaces.RSAPublicKey;
import java.util.regex.Pattern;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.akto.PayloadEncodeUtil;
import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dto.*;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.akto.dto.filter.MergedUrls;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.*;
import com.akto.dto.type.SingleTypeInfo.Domain;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.dto.type.SingleTypeInfo.SuperType;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.usage.MetricTypes;
import com.akto.hybrid_runtime.filter_updates.FilterUpdates;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.utils.Utils;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.hybrid_runtime.policies.AktoPolicyNew;
import com.akto.testing_db_layer_client.ClientLayer;
import com.akto.types.CappedSet;
import com.akto.util.filter.DictionaryFilter;
import com.akto.utils.RedactSampleData;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

import static com.akto.dto.type.KeyTypes.patternToSubType;

public class APICatalogSync {

    public static final int VULNERABLE_API_COLLECTION_ID = 1111111111;
    public static final int LLM_API_COLLECTION_ID = 1222222222;

    public int thresh;
    public String userIdentifier;
    private static final LoggerMaker loggerMaker = new LoggerMaker(APICatalogSync.class, LogDb.RUNTIME);
    public Map<Integer, APICatalog> dbState;
    public Map<Integer, APICatalog> delta;
    public AktoPolicyNew aktoPolicyNew;
    public Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap;
    public static boolean mergeAsyncOutside = true;
    public int lastStiFetchTs = 0;
    public BloomFilter<CharSequence> existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );
    public Map<String, FilterConfig> advancedFilterMap =  new HashMap<>();

    private static DataActor dataActor = DataActorFactory.fetchInstance();
    public static Set<MergedUrls> mergedUrls;
    private static final ClientLayer clientLayer = new ClientLayer();
    private static RSAPublicKey publicKey = PayloadEncodeUtil.getPublicKey();
    public APICatalogSync(String userIdentifier,int thresh, boolean fetchAllSTI) {
        this(userIdentifier, thresh, fetchAllSTI, true);
    }

    /* Note: We have hardcoded the logic of not merging URLs for MCP Server.
        The apiCollectionId - -1 has nothing to do with this.
        Since we do not know the collectionId for MCP Server, we have set it to -1.
     */
    private static final Set<MergedUrls> MERGED_URLS_FOR_MCP = new HashSet<>(Arrays.asList(
        new MergedUrls("tools/call/STRING", "POST", -1),
        new MergedUrls("tools/call/INTEGER", "POST", -1),
        new MergedUrls("tools/call/FLOAT", "POST", -1),
        new MergedUrls("tools/call/OBJECT_ID", "POST", -1),
        new MergedUrls("tools/call/VERSIONED", "POST", -1)
    ));

    private boolean mergeUrlsOnVersions;

    // New overloaded constructor
    public APICatalogSync(String userIdentifier, int thresh, boolean fetchAllSTI, boolean buildFromDb) {
        this.thresh = thresh;
        this.userIdentifier = userIdentifier;
        this.dbState = new HashMap<>();
        this.delta = new HashMap<>();
        this.sensitiveParamInfoBooleanMap = new HashMap<>();
        this.aktoPolicyNew = new AktoPolicyNew();
        this.mergedUrls = new HashSet<>();
        if (buildFromDb) {
            buildFromDB(false, fetchAllSTI);
        }
    }

    public static final int STRING_MERGING_THRESHOLD = 10;

    public void processResponse(RequestTemplate requestTemplate, Collection<HttpResponseParams> responses, List<SingleTypeInfo> deletedInfo) {
        Iterator<HttpResponseParams> iter = responses.iterator();
        while(iter.hasNext()) {
            try {
                processResponse(requestTemplate, iter.next(), deletedInfo);
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "processResponse: " + e.getMessage());
            }
        }
    }

    public void processResponse(RequestTemplate requestTemplate, HttpResponseParams responseParams, List<SingleTypeInfo> deletedInfo) {
        int timestamp = responseParams.getTimeOrNow();
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

        String reqPayload = requestParams.getPayload();

        if (reqPayload == null || reqPayload.isEmpty()) {
            reqPayload = "{}";
        }

        requestTemplate.processHeaders(requestParams.getHeaders(), baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp);
        BasicDBObject requestPayload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams);
        if (requestPayload != null) {
            deletedInfo.addAll(requestTemplate.process2(requestPayload, baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp));
        }
        requestTemplate.recordMessage(responseParams.getOrig());

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

            deletedInfo.addAll(responseTemplate.process2(payload, baseURL.getUrl(), methodStr, statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp));
            responseTemplate.processHeaders(responseParams.getHeaders(), baseURL.getUrl(), method.name(), statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp);
            if (!responseParams.getIsPending()) {
                responseTemplate.processTraffic(responseParams.getTime());
            }

        } catch (JsonParseException e) {
            loggerMaker.errorAndAddToDb(e, "Failed to parse json payload " + e.getMessage());
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
        loggerMaker.info("aggregator: " + (System.currentTimeMillis() - start));
        origAggregator.urls = new ConcurrentHashMap<>();

        Set<Map.Entry<URLStatic, Set<HttpResponseParams>>> entries = aggregator.urls.entrySet();
        for (Map.Entry<URLStatic, Set<HttpResponseParams>> entry : entries) {
            Set<HttpResponseParams> value = entry.getValue();
            for (HttpResponseParams responseParams: value) {
                try {
                    aktoPolicyNew.process(responseParams);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

        start = System.currentTimeMillis();
        processKnownStaticURLs(aggregator, deltaCatalog, dbCatalog);
        loggerMaker.info("processKnownStaticURLs: " +  (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        Map<URLStatic, RequestTemplate> pendingRequests = createRequestTemplates(aggregator);
        loggerMaker.info("pendingRequests: " + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        tryWithKnownURLTemplates(pendingRequests, deltaCatalog, dbCatalog, apiCollectionId );
        loggerMaker.info("tryWithKnownURLTemplates: " + (System.currentTimeMillis() - start));

        if (!mergeAsyncOutside) {
            start = System.currentTimeMillis();
            tryMergingWithKnownStrictURLs(pendingRequests, dbCatalog, deltaCatalog);
            loggerMaker.info("tryMergingWithKnownStrictURLs: " + (System.currentTimeMillis() - start));
        } else {
            if (DataControlFetcher.discardNewApi()) {
                pendingRequests.clear();
            }
            AllMetrics.instance.setDeltaCatalogNewCount(pendingRequests.size());
            AllMetrics.instance.setDeltaCatalogTotalCount(pendingRequests.size());
            for (URLStatic pending: pendingRequests.keySet()) {
                RequestTemplate pendingTemplate = pendingRequests.get(pending);

                URLTemplate parameterisedTemplate = null;
                if((apiCollectionId != VULNERABLE_API_COLLECTION_ID) && (apiCollectionId != LLM_API_COLLECTION_ID)){
                    parameterisedTemplate = tryParamteresingUrl(pending, this.mergeUrlsOnVersions);
                }

                if(parameterisedTemplate != null){
                    if (Utils.printDebugUrlLog(parameterisedTemplate.getTemplateString())) {
                        loggerMaker.infoAndAddToDb("Found debug url in parameterisedTemplate " + parameterisedTemplate.getTemplateString());
                    }
                    RequestTemplate rt = deltaCatalog.getTemplateURLToMethods().get(parameterisedTemplate);
                    if (rt != null) {
                        rt.mergeFrom(pendingTemplate);
                    } else {
                        deltaCatalog.getTemplateURLToMethods().put(parameterisedTemplate, pendingTemplate);
                    }

                    rt = deltaCatalog.getTemplateURLToMethods().get(parameterisedTemplate);
                    rt.fillUrlParams(tokenize(pending.getUrl()), parameterisedTemplate, apiCollectionId);

                }else{
                    RequestTemplate rt = deltaCatalog.getStrictURLToMethods().get(pending);
                    if (rt != null) {
                        rt.mergeFrom(pendingTemplate);
                    } else {
                        deltaCatalog.getStrictURLToMethods().put(pending, pendingTemplate);
                    }
                }


            }
        }

        loggerMaker.info("processTime: " + RequestTemplate.insertTime + " " + RequestTemplate.processTime + " " + RequestTemplate.deleteTime);

    }

    private static Map<Integer, Map<String, Set<String>>> groupByTokenSize(Map<String, Set<String>> catalog) {
        Map<Integer, Map<String, Set<String>>> sizeToURL = new HashMap<>();
        for(String rawURLPlusMethod: catalog.keySet()) {
            String[] rawUrlPlusMethodSplit = rawURLPlusMethod.split(" ");
            String rawURL = rawUrlPlusMethodSplit.length > 1 ? rawUrlPlusMethodSplit[1] : rawUrlPlusMethodSplit[0];
            Set<String> reqTemplate = catalog.get(rawURLPlusMethod);
            String url = APICatalogSync.trim(rawURL);
            String[] tokens = url.split("/");
            Map<String, Set<String>> urlSet = sizeToURL.get(tokens.length);
            urlSet = sizeToURL.get(tokens.length);
            if (urlSet == null) {
                urlSet = new HashMap<>();
                sizeToURL.put(tokens.length, urlSet);
            }

            urlSet.put(rawURLPlusMethod, reqTemplate);
        }

        return sizeToURL;
    }

    static class ApiMergerResult {
        public Set<String> deleteStaticUrls = new HashSet<>();
        Map<URLTemplate, Set<String>> templateToStaticURLs = new HashMap<>();

        public ApiMergerResult(Map<URLTemplate, Set<String>> templateToSti) {
            this.templateToStaticURLs = templateToSti;
        }

        public String toString() {
            String ret = ("templateToSti======================================================: \n");
            for(URLTemplate urlTemplate: templateToStaticURLs.keySet()) {
                ret += (urlTemplate.getTemplateString()) + "\n";
                for(String str: templateToStaticURLs.get(urlTemplate)) {
                    ret += ("\t " + str + "\n");
                }
            }

            return ret;
        }
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
                    URLTemplate mergedTemplate = tryMergeUrls(dbUrl, newUrl, this.mergeUrlsOnVersions);
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
                URLTemplate mergedTemplate = tryMergeUrls(deltaUrl, newUrl, this.mergeUrlsOnVersions);
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

    public static boolean areBothMatchingUrls(URLStatic newUrl, URLStatic deltaUrl, URLTemplate mergedTemplate, boolean urlRegexMatchingEnabled) {

        String[] n = tokenize(newUrl.getUrl());
        String[] o = tokenize(deltaUrl.getUrl());
        SuperType[] b = mergedTemplate.getTypes();
        for (int idx =0 ; idx < b.length; idx++) {
            SuperType c = b[idx];
            if (Objects.equals(c, SuperType.STRING) && o.length > idx) {
                String val = n[idx];
                boolean isAlphaNumeric = isAlphanumericString(val) && isAlphanumericString(o[idx]);
    
                if(!isAlphaNumeric) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isAlphanumericString(String s) {

        int intCount = 0;
        int charCount = 0;
        if (s.length() < 6) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {

            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                intCount++;
            } else if (Character.isLetter(c)) {
                charCount++;
            }
        }
        return (intCount >= 3 && charCount >= 1);
    }


    private static boolean isValidSubtype(SubType subType){
        return !(subType.getName().equals(SingleTypeInfo.GENERIC.getName()) || subType.getName().equals(SingleTypeInfo.OTHER.getName()));
    }

    public static boolean isNumber(String val) {
        try {
            Integer.parseInt(val);
            return true;
        } catch (Exception ignored1) {
            try {
                Long.parseLong(val);
                return true;
            } catch (Exception ignored2) {
                return false;
            }
        }
    }

    private static boolean isValidVersionToken(String token){
        if(token == null || token.isEmpty()) return false;
        token = token.trim().toLowerCase();
        if(token.startsWith("v") && token.length() > 1 && token.length() < 4) {
            String versionString = token.substring(1, token.length());
            try {
                int version = Integer.parseInt(versionString);
                if (version > 0) {
                    return true;
                } 
            } catch (Exception e) {
                // TODO: handle exception
                return false;
            }
            return false;
        }
        return false;
    }

    public static URLTemplate tryParamteresingUrl(URLStatic newUrl, boolean mergeUrlsOnVersions){
        String[] tokens = tokenize(newUrl.getUrl());
        if(tokens.length < 2){
            return null;
        }
        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);
        boolean allNull = true;
        SuperType[] newTypes = new SuperType[tokens.length];

        int start = newUrl.getUrl().startsWith("http") ? 3 : 0;
        for(int i = start; i < tokens.length; i ++) {
            String tempToken = tokens[i];
            if(DictionaryFilter.isEnglishWord(tempToken)) continue;

            if (NumberUtils.isParsable(tempToken)) {
                newTypes[i] = isNumber(tempToken) ? SuperType.INTEGER : SuperType.FLOAT;
                tokens[i] = null;
            } else if(ObjectId.isValid(tempToken)){
                newTypes[i] = SuperType.OBJECT_ID;
                tokens[i] = null;
            }else if(pattern.matcher(tempToken).matches()){
                newTypes[i] = SuperType.STRING;
                tokens[i] = null;
            }else if(mergeUrlsOnVersions && isValidVersionToken(tempToken)){
                newTypes[i] = SuperType.VERSIONED;
                tokens[i] = null;
            }

            if(tokens[i] != null){
                SubType tempSubType = KeyTypes.findSubType(tokens[i], ""+i, null,true);
                if(isValidSubtype(tempSubType)){
                    newTypes[i] = SuperType.STRING;
                    tokens[i] = null;
                }else if(isAlphanumericString(tempToken)){
                    newTypes[i] = SuperType.STRING;
                    tokens[i] = null;
                }
            }
            
            if(newTypes[i] != null){
                allNull = false;
            }
        }

        if (allNull) return null;

        URLTemplate urlTemplate = new URLTemplate(tokens, newTypes, newUrl.getMethod());

        return getMergedUrlTemplate(urlTemplate);
    }


    public static URLTemplate tryMergeUrls(URLStatic dbUrl, URLStatic newUrl, boolean mergeUrlsOnVersions) {
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
            if (DictionaryFilter.isEnglishWord(tempToken) || DictionaryFilter.isEnglishWord(dbToken)) continue;

            int minCount = dbUrl.getUrl().startsWith("http") && newUrl.getUrl().startsWith("http") ? 3 : 0;
            if (tempToken.equalsIgnoreCase(dbToken) || i < minCount) {
                continue;
            }
            
            if (NumberUtils.isParsable(tempToken) && NumberUtils.isParsable(dbToken)) {
                newTypes[i] = SuperType.INTEGER;
                newTokens[i] = null;
            } else if(ObjectId.isValid(tempToken) && ObjectId.isValid(dbToken)){
                newTypes[i] = SuperType.OBJECT_ID;
                newTokens[i] = null;
            }else if(isValidVersionToken(tempToken) && isValidVersionToken(dbToken)){
                newTypes[i] = SuperType.VERSIONED;
                newTokens[i] = null;
            } 
            else if(pattern.matcher(tempToken).matches() && pattern.matcher(dbToken).matches()){
                newTypes[i] = SuperType.STRING;
                newTokens[i] = null;
            }else if(isAlphanumericString(tempToken) && isAlphanumericString(dbToken)){
                newTypes[i] = SuperType.STRING;
                newTokens[i] = null;
            } else {
                newTypes[i] = SuperType.STRING;
                newTokens[i] = null;
                templatizedStrTokens++;
            }
        }

        boolean allNull = true;
        for (SingleTypeInfo.SuperType superType: newTypes) {
            allNull = allNull && (superType == null);
        }

        if (allNull) return null;

        if (templatizedStrTokens <= 1) {
            URLTemplate urlTemplate = new URLTemplate(newTokens, newTypes, newUrl.getMethod());

            return getMergedUrlTemplate(urlTemplate);
        }
        return null;
    }

    public static URLTemplate getMergedUrlTemplate(URLTemplate urlTemplate) {
        try {
            for(MergedUrls mergedUrl : mergedUrls) {
                if(mergedUrl.getUrl().equals(urlTemplate.getTemplateString()) &&
                        mergedUrl.getMethod().equals(urlTemplate.getMethod().name())) {
                    return null;
                }
            }

            String mergedUrlString = urlTemplate.getTemplateString();
            if (McpSchema.MCP_METHOD_SET.stream().anyMatch(mergedUrlString::contains)) {
                return null;
            }
        } catch(Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while creating a new URL object: " + e.getMessage());
        }

        return urlTemplate;
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
                        if (DataControlFetcher.discardOldApi()) {
                            iterator.remove();
                            break;
                        } else {
                            AllMetrics.instance.setDeltaCatalogTotalCount(1);
                            RequestTemplate alreadyInDelta = deltaCatalog.getTemplateURLToMethods().get(urlTemplate);
                            if (Utils.printDebugUrlLog(newUrl.getUrl())) {
                                loggerMaker.infoAndAddToDb("Found debug url in tryWithKnownURLTemplates match " + newUrl.getUrl());
                            }

                            if (alreadyInDelta != null) {
                                alreadyInDelta.fillUrlParams(tokenize(newUrl.getUrl()), urlTemplate, apiCollectionId);
                                alreadyInDelta.mergeFrom(newRequestTemplate);
                                if (Utils.printDebugUrlLog(newUrl.getUrl())) {
                                    loggerMaker.infoAndAddToDb("Found debug url in tryWithKnownURLTemplates alreadyInDelta non empty " + newUrl.getUrl());
                                }
                            } else {
                                RequestTemplate dbTemplate = dbCatalog.getTemplateURLToMethods().get(urlTemplate);
                                RequestTemplate dbCopy = dbTemplate.copy();
                                dbCopy.mergeFrom(newRequestTemplate);
                                dbCopy.fillUrlParams(tokenize(newUrl.getUrl()), urlTemplate, apiCollectionId);
                                deltaCatalog.getTemplateURLToMethods().put(urlTemplate, dbCopy);
                                if (Utils.printDebugUrlLog(newUrl.getUrl())) {
                                    loggerMaker.infoAndAddToDb("Found debug url in tryWithKnownURLTemplates alreadyInDelta empty " + newUrl.getUrl());
                                }
                            }
                            iterator.remove();
                            break;
                        }
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
                    if (Utils.printDebugUrlLog(url.getUrl())) {
                        loggerMaker.infoAndAddToDb("Found debug url in createRequestTemplates " + url.getUrl());
                    }
                    ret.put(url, requestTemplate);
                }
                processResponse(requestTemplate, responseParamsList, deletedInfo);
                iterator.remove();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in createRequestTemplates: " + e.getMessage());
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
                    if (DataControlFetcher.discardOldApi()) {
                        iterator.remove();
                    } else {
                        AllMetrics.instance.setDeltaCatalogTotalCount(1);
                        if (requestTemplate == null) {
                            requestTemplate = strictMatch.copy(); // to further process the requestTemplate
                            deltaCatalogStrictURLToMethods.put(url, requestTemplate);
                            if (Utils.printDebugUrlLog(url.getUrl())) {
                                loggerMaker.infoAndAddToDb("Found debug url in processKnownStaticURLs " + url.getUrl());
                            }
                            strictMatch.mergeFrom(requestTemplate); // to update the existing requestTemplate in db with new data
                        }

                        processResponse(requestTemplate, responseParamsList, deletedInfo);
                        iterator.remove();
                    }
                }

            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in processKnownStaticURLs: " + e.getMessage());
        }
    }

    public static String trim(String url) {
        // if (mergeAsyncOutside) {
        //     if ( !(url.startsWith("/") ) && !( url.startsWith("http") || url.startsWith("ftp")) ){
        //         url = "/" + url;
        //     }
        // } else {
            if (url.startsWith("/")) url = url.substring(1, url.length());
        // }
        
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

    public ArrayList<WriteModel<SampleData>> getDBUpdatesForSampleData(int apiCollectionId, APICatalog currentDelta, APICatalog dbCatalog, boolean forceUpdate, boolean accountLevelRedact, boolean apiCollectionLevelRedact) {
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
                try {
                    String redactedSample = RedactSampleData.redactIfRequired(s, accountLevelRedact, apiCollectionLevelRedact);
                    finalSamples.add(redactedSample);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"Error while redacting data 1" )
                    ;
                }
            }
            Bson bson = Updates.combine(
                Updates.pushEach("samples", finalSamples, new PushOptions().slice(-10)),
                Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(sample.getId().getApiCollectionId()))
            );

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
            updates.add(Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(trafficInfo.getId().getApiCollectionId())));

            bulkUpdates.add(
                new UpdateOneModel<>(Filters.eq("_id", trafficInfo.getId()), Updates.combine(updates), new UpdateOptions().upsert(true))
            );
        }

        return bulkUpdates;
    }

    public DbUpdateReturn getDBUpdatesForParams(APICatalog currentDelta, APICatalog currentState, boolean redactSampleData, boolean collectionLevelRedact) {
        Map<String, SingleTypeInfo> dbInfoMap = convertToMap(currentState.getAllTypeInfo());
        Map<String, SingleTypeInfo> deltaInfoMap = convertToMap(currentDelta.getAllTypeInfo());

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        ArrayList<WriteModel<SensitiveSampleData>> bulkUpdatesForSampleData = new ArrayList<>();
        int now = Context.now();
        for(String key: deltaInfoMap.keySet()) {
            SingleTypeInfo dbInfo = dbInfoMap.get(key);
            SingleTypeInfo deltaInfo = deltaInfoMap.get(key);
            boolean isQueryParam = false;
            if(deltaInfo.getParam().contains("_queryParam")) {
                String originalParam = deltaInfo.getParam().split("_queryParam")[0];
                isQueryParam = true;
                deltaInfo.setParam(originalParam);
            }
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

            int timestamp = deltaInfo.getTimestamp() > 0 ? deltaInfo.getTimestamp() : now;
            update = Updates.combine(update, Updates.setOnInsert("timestamp", timestamp));

            update = Updates.combine(update, Updates.max(SingleTypeInfo.LAST_SEEN, deltaInfo.getLastSeen()));
            update = Updates.combine(update, Updates.max(SingleTypeInfo.MAX_VALUE, deltaInfo.getMaxValue()));
            update = Updates.combine(update, Updates.min(SingleTypeInfo.MIN_VALUE, deltaInfo.getMinValue()));
            update = Updates.combine(update, Updates.set("isQueryParam", isQueryParam));

            if (!Main.isOnprem) {
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
            }

            if (!(redactSampleData || collectionLevelRedact) && deltaInfo.getExamples() != null && !deltaInfo.getExamples().isEmpty()) {
                Set<Object> updatedSampleData = new HashSet<>();
                for (Object example : deltaInfo.getExamples()) {
                    try{
                        String exampleStr = (String) example;
                        String s = RedactSampleData.redactDataTypes(exampleStr);
                        updatedSampleData.add(s);
                    } catch (Exception e) {
                        ;
                    }
                }
                deltaInfo.setExamples(updatedSampleData);
                Bson bson = Updates.combine(
                    Updates.pushEach(SensitiveSampleData.SAMPLE_DATA, Arrays.asList(deltaInfo.getExamples().toArray()), new PushOptions().slice(-1 *SensitiveSampleData.cap)),
                    Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(deltaInfo.getApiCollectionId()))
                );
                bulkUpdatesForSampleData.add(
                        new UpdateOneModel<>(
                                SensitiveSampleDataDao.getFilters(deltaInfo),
                                bson,
                                new UpdateOptions().upsert(true)
                        )
                );
            }

            Bson updateKey = Filters.and(SingleTypeInfoDao.createFilters(deltaInfo));
            update = Updates.combine(update,
            Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(deltaInfo.getApiCollectionId())));

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


    public static String[] trimAndSplit(String url) {
        return trim(url).split("/");
    }

    public static URLTemplate createUrlTemplate(String url, Method method) {
        String[] tokens = trimAndSplit(url);
        SuperType[] types = new SuperType[tokens.length];
        for(int i = 0; i < tokens.length; i ++ ) {
            String token = tokens[i];

            if (token.equals(SuperType.STRING.name())) {
                tokens[i] = null;
                types[i] = SuperType.STRING;
            } else if (token.equals(SuperType.INTEGER.name())) {
                tokens[i] = null;
                types[i] = SuperType.INTEGER;
            } else if (token.equals(SuperType.OBJECT_ID.name())) {
                tokens[i] = null;
                types[i] = SuperType.OBJECT_ID;
            } else if (token.equals(SuperType.FLOAT.name())) {
                tokens[i] = null;
                types[i] = SuperType.FLOAT;
            } else {
                types[i] = null;
            }

        }

        URLTemplate urlTemplate = new URLTemplate(tokens, types, method);

        return urlTemplate;
    }

    public static void clearValuesInDB() {
        List<String> rangeSubTypes = new ArrayList<>();
        rangeSubTypes.add(SingleTypeInfo.INTEGER_32.getName());
        rangeSubTypes.add(SingleTypeInfo.INTEGER_64.getName());
        rangeSubTypes.add(SingleTypeInfo.FLOAT.getName());

        String fieldName = "values.elements." + (SingleTypeInfo.VALUES_LIMIT + 1);
        int sliceLimit = -1 * SingleTypeInfo.VALUES_LIMIT;

        // range update
        UpdateResult rangeUpdateResult = SingleTypeInfoDao.instance.updateMany(
                Filters.and(
                        Filters.exists(fieldName, true),
                        Filters.in(SingleTypeInfo.SUB_TYPE, rangeSubTypes)
                ),
                Updates.combine(
                        Updates.pushEach("values.elements", new ArrayList<>(), new PushOptions().slice(sliceLimit)),
                        Updates.set(SingleTypeInfo._DOMAIN, Domain.RANGE.name())
                )
        );
        loggerMaker.infoAndAddToDb("RangeUpdateResult for clearValuesInDb function = " + "match count: " + rangeUpdateResult.getMatchedCount() + ", modify count: " + rangeUpdateResult.getModifiedCount());

        // any update
        UpdateResult anyUpdateResult = SingleTypeInfoDao.instance.updateMany(
                Filters.and(
                        Filters.exists(fieldName, true),
                        Filters.nin(SingleTypeInfo.SUB_TYPE, rangeSubTypes)
                ),
                Updates.combine(
                        Updates.pushEach("values.elements", new ArrayList<>(), new PushOptions().slice(sliceLimit)),
                        Updates.set(SingleTypeInfo._DOMAIN, Domain.ANY.name())
                )
        );

        loggerMaker.infoAndAddToDb("AnyUpdateResult for clearValuesInDb function = " + "match count: " + anyUpdateResult.getMatchedCount() + ", modify count: " + anyUpdateResult.getModifiedCount());
    }

    public void buildFromDB(boolean calcDiff, boolean fetchAllSTI) {

        loggerMaker.infoAndAddToDb("Started building from dB with calcDiff " + calcDiff + " fetchAllSTI: " + fetchAllSTI);
        loggerMaker.infoAndAddToDb("Fetching STIs: " + fetchAllSTI);
        List<SingleTypeInfo> allParams;
        /*
         * This fetches all STIs in batches of 100, filtered by host headers.
         * Since they are filtered by host header, APIs with no hosts will not be present here.
         * Impact: these APIs will be calculated as new APIs every time,
         * but since we have a grace period of 1 day in mini-runtime
         * and we recalculate the usage every four hours,
         * the end user will not face a wrongful overage.
         */
        allParams = dataActor.fetchAllStis();
        if (allParams.size() > 0) {
            lastStiFetchTs = allParams.get(allParams.size() - 1).getTimestamp();
        }
        loggerMaker.infoAndAddToDb("Fetched STIs count: " + allParams.size());
        loggerMaker.infoAndAddToDb("Starting building dbState");
        this.dbState = build(allParams, existingAPIsInDb);
        loggerMaker.infoAndAddToDb("Done building dbState");

        // todo: discuss
        //this.sensitiveParamInfoBooleanMap = new HashMap<>();
        List<SensitiveParamInfo> sensitiveParamInfos = dataActor.getUnsavedSensitiveParamInfos();
        loggerMaker.infoAndAddToDb("Done fetching sensitiveParamInfos");
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfos) {
            this.sensitiveParamInfoBooleanMap.put(sensitiveParamInfo, false);
        }

        List<YamlTemplate> advancedFilterTemplates = dataActor.fetchActiveAdvancedFilters();
        advancedFilterMap = FilterYamlTemplateDao.instance.fetchFilterConfig(false, advancedFilterTemplates, true);

        try {
            loggerMaker.infoAndAddToDb("Started clearing values in db ");
            clearValuesInDB();
            loggerMaker.infoAndAddToDb("Finished clearing values in db ");
        } catch (Exception e) {
            loggerMaker.infoAndAddToDb("Error while clearing values in db: " + e.getMessage());
        }

        mergedUrls = dataActor.fetchMergedUrls();

        aktoPolicyNew.buildFromDb(fetchAllSTI);
    }

    public static void updateApiCollectionCount(APICatalog apiCatalog, int apiCollectionId) {
        Set<String> newURLs = new HashSet<>();
        for(URLTemplate url: apiCatalog.getTemplateURLToMethods().keySet()) {
            newURLs.add(url.getTemplateString()+ " "+ url.getMethod().name());
        }
        for(URLStatic url: apiCatalog.getStrictURLToMethods().keySet()) {
            newURLs.add(url.getUrl()+ " "+ url.getMethod().name());
        }

        Bson findQ = Filters.eq("_id", apiCollectionId);
        int batchSize = 50;
        int count = 0;
        Set<String> batchedUrls = new HashSet<>();

        ApiCollectionsDao.instance.getMCollection().updateOne(findQ, Updates.unset(ApiCollection.URLS_STRING));

        for (String url : newURLs) {
            batchedUrls.add(url);
            count++;

            if (count == batchSize) {
                ApiCollectionsDao.instance.getMCollection().bulkWrite(
                        Collections.singletonList(new UpdateManyModel<>(findQ,
                                Updates.addEachToSet(ApiCollection.URLS_STRING, new ArrayList<>(batchedUrls)), new UpdateOptions())),
                        new BulkWriteOptions().ordered(false));
                count = 0;
                batchedUrls.clear();
            }
        }

        if (!batchedUrls.isEmpty()) {
            ApiCollectionsDao.instance.getMCollection().bulkWrite(
                    Collections.singletonList(new UpdateManyModel<>(findQ,
                            Updates.addEachToSet(ApiCollection.URLS_STRING, new ArrayList<>(batchedUrls)), new UpdateOptions())),
                    new BulkWriteOptions().ordered(false));
        }
    }

    private static void buildHelper(SingleTypeInfo param, Map<Integer, APICatalog> ret) {
        String url = param.getUrl();
        int collId = param.getApiCollectionId();
        APICatalog catalog = ret.get(collId);

        if (catalog == null) {
            catalog = new APICatalog(collId, new HashMap<>(), new HashMap<>());
            ret.put(collId, catalog);
        }
        RequestTemplate reqTemplate;
        if (APICatalog.isTemplateUrl(url)) {
            URLTemplate urlTemplate = createUrlTemplate(url, Method.fromString(param.getMethod()));
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
                loggerMaker.errorAndAddToDb(e, "ERROR while parsing url param position: " + p);
            }
            return;
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
                loggerMaker.info("null value - " + param.composeKey());
            }

            keyTypesMap.put(param.getParam(), keyTypes);
        }

        SingleTypeInfo info = keyTypes.getOccurrences().get(param.getSubType());
        if (info != null && info.getTimestamp() > param.getTimestamp()) {
            param = info;
        }

        keyTypes.getOccurrences().put(param.getSubType(), param);
    }


    private static Map<Integer, APICatalog> build(List<SingleTypeInfo> allParams, BloomFilter<CharSequence> existingAPIsInDb) {
        Map<Integer, APICatalog> ret = new HashMap<>();
        demosAndDeactivatedCollections = getDemosAndDeactivated();
        for (SingleTypeInfo param: allParams) {
            try {
                buildHelper(param, ret);
                fillExistingAPIsInDb(param, existingAPIsInDb);
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb(e, "Error while building from db: " + e.getMessage());
            }
        }

        return ret;
    }

    private static ApiCollection juiceShop = null;
    private static int lastDeactivatedFetched = 0;
    private static final int REFRESH_INTERVAL = 60 * 10; // 10 minutes.
    private static List<Integer> deactivatedCollections = new ArrayList<>();

    public static List<Integer> getDeactivated() {
        if ((lastDeactivatedFetched + REFRESH_INTERVAL) >= Context.now()) {
            return deactivatedCollections;
        }

        deactivatedCollections = dataActor.fetchDeactivatedCollections();
        lastDeactivatedFetched = Context.now();
        return deactivatedCollections;
    }

    public static Set<Integer> getDemosAndDeactivated() {
        Set<Integer> ret = new HashSet<>();

        List<Integer> deactivatedIds = getDeactivated();
        if (juiceShop == null) {
            juiceShop = dataActor.findApiCollectionByName("juice_shop_demo");
            /*
             * In case the user has deleted juice shop demo collection,
             * we insert a sample collection
             * to avoid making the API call every time.
             */
            if (juiceShop == null) {
                juiceShop = new ApiCollection();
            }
        }
        Set<Integer> demos = new HashSet<>();
        demos.add(1111111111);

        /*
         * In case of no juice_shop_demo collection,
         * the id will be 0, as set above.
         */
        if (juiceShop != null && juiceShop.getId() != 0) {
            demos.add(juiceShop.getId());
        }
        ret.addAll(deactivatedIds);
        ret.addAll(demos);
        return ret;
    }

    private static Set<Integer> demosAndDeactivatedCollections = getDemosAndDeactivated();

    private static void fillExistingAPIsInDb(SingleTypeInfo sti, BloomFilter<CharSequence> existingAPIsInDb) {

        if (demosAndDeactivatedCollections.contains(sti.getApiCollectionId())) {
            return;
        }
        String key = sti.composeApiInfoKey();
        if (key != null) {
            existingAPIsInDb.put(key);
        }
    }

    int counter = 0;
    
    static int lastBuildFromDb = 0;
    final static int DB_REFRESH_CYCLE = 15 * 60; // 15 minutes

    public void syncWithDB(boolean syncImmediately, boolean fetchAllSTI, SyncLimit apiSyncLimit,
        SyncLimit mcpAssetsSyncLimit, SyncLimit aiAssetsSyncLimit) {
        loggerMaker.infoAndAddToDb("Started sync with db! syncImmediately="+syncImmediately + " fetchAllSTI="+fetchAllSTI);
        List<Object> writesForParams = new ArrayList<>();
        List<Object> writesForSensitiveSampleData = new ArrayList<>();
        List<Object> writesForTraffic = new ArrayList<>();
        List<Object> writesForSampleData = new ArrayList<>();
        List<Object> writesForSensitiveParamInfo = new ArrayList<>();
        Map<Integer, Boolean> apiCollectionToRedactPayload = new HashMap<>();
        loggerMaker.debug("fetch all collections meta");
        List<ApiCollection> all = dataActor.fetchAllApiCollectionsMeta();
        Map<Integer, ApiCollection> apiCollectionMap = new HashMap<>();
        for(ApiCollection apiCollection: all) {
            apiCollectionToRedactPayload.put(apiCollection.getId(), apiCollection.getRedact());
            apiCollectionMap.put(apiCollection.getId(), apiCollection);
        }

        AccountSettings accountSettings = dataActor.fetchAccountSettings();
        if (accountSettings != null) {
            int acc = accountSettings.getId();
            Context.accountId.set(acc);
        }
        
        int accountId = Context.accountId.get();
        boolean redact = accountId == 1718042191;
        if (accountSettings != null) {
            redact =  accountSettings.isRedactPayload();
        }

        counter++;
        
        for(int apiCollectionId: this.delta.keySet()) {
            loggerMaker.debug("Syncing apiCollectionId: " + apiCollectionId + " counter: " + counter);
            APICatalog deltaCatalog = this.delta.get(apiCollectionId);

            /*
             * Caching for the actual API call is done in getDeactivated() function.
             */
            demosAndDeactivatedCollections = getDemosAndDeactivated();
            Pair<SyncLimit, MetricTypes> syncLimitPair = getSyncLimitForApiCollection(
                apiCollectionMap.get(apiCollectionId), apiSyncLimit,
                mcpAssetsSyncLimit, aiAssetsSyncLimit);

            SyncLimit syncLimit = syncLimitPair.getFirst();

            if (syncLimit.checkLimit && !demosAndDeactivatedCollections.contains(apiCollectionId)) {
                
                int deltaUsage = 0;
                Iterator<Entry<URLStatic, RequestTemplate>> staticUrlIterator = deltaCatalog.getStrictURLToMethods().entrySet().iterator();
                while (staticUrlIterator.hasNext()) {
                    Entry<URLStatic, RequestTemplate> entry = staticUrlIterator.next();
                    URLStatic urlStatic = entry.getKey();
                    String checkString = apiCollectionId + " " + urlStatic.getFullString();
                    if (!existingAPIsInDb.mightContain(checkString)) {
                        if (syncLimit.updateUsageLeftAndCheckSkip()) {
                            // Record overage information
                            UningestedApiTracker.recordOverage(
                                apiCollectionId,
                                "STATIC",
                                urlStatic.getMethod(),
                                urlStatic.getUrl()
                            );
                            staticUrlIterator.remove();
                            if (Utils.printDebugUrlLog(checkString)) {
                                loggerMaker.infoAndAddToDb("Found debug url in updateUsageLeftAndCheckSkip skip " + checkString);
                            }
                        } else {
                            existingAPIsInDb.put(checkString);
                            deltaUsage++;
                        }
                    }
                }

                Iterator<Entry<URLTemplate, RequestTemplate>> templateUrlIterator = deltaCatalog.getTemplateURLToMethods().entrySet().iterator();
                while(templateUrlIterator.hasNext()) {
                    Entry<URLTemplate, RequestTemplate> entry = templateUrlIterator.next();
                    URLTemplate urlTemplate = entry.getKey();
                    String checkString = apiCollectionId + " " + urlTemplate.getTemplateString() + " "
                            + urlTemplate.getMethod().name();
                    if (!existingAPIsInDb.mightContain(checkString)) {
                        if (syncLimit.updateUsageLeftAndCheckSkip()) {
                            // Record overage information
                            UningestedApiTracker.recordOverage(
                                apiCollectionId,
                                "TEMPLATE",
                                urlTemplate.getMethod(),
                                urlTemplate.getTemplateString()
                            );
                            templateUrlIterator.remove();
                            if (Utils.printDebugUrlLog(checkString)) {
                                loggerMaker.infoAndAddToDb("Found debug url in updateUsageLeftAndCheckSkip skip " + checkString);
                            }
                        } else {
                            existingAPIsInDb.put(checkString);
                            deltaUsage++;
                        }
                    }
                }

                dataActor.updateUsage(syncLimitPair.getSecond(), deltaUsage);
                loggerMaker.infoAndAddToDb("Syncing done after passing checking limit: " + deltaUsage);
            }

            /*
             * Note: Since multiple runtime-instances are running simultaneously, all of
             * them would get the same limit (say 5) and insert up to 5 new APIs each. This
             * would cause an overage of roughly (n-1)*(5) endpoints, where n is no. of
             * runtime instances, after which no new endpoints would be inserted.
             * We can mitigate this minimal overage using DB lock, at organization level (we
             * currently have db lock at account level), but are holding as it may
             * slow the runtime instance.
             */

            APICatalog dbCatalog = this.dbState.getOrDefault(apiCollectionId, new APICatalog(apiCollectionId, new HashMap<>(), new HashMap<>()));
            boolean redactCollectionLevel = apiCollectionToRedactPayload.getOrDefault(apiCollectionId, false);

            //DbUpdateReturn dbUpdateReturn = getDBUpdatesForParams(deltaCatalog, dbCatalog, redact, redactCollectionLevel);

            // todo: redactCollectionLevel
            loggerMaker.debug("Building params writes for apiCollectionId: " + apiCollectionId);
            DbUpdateReturnHybrid dbUpdateReturn = getDBUpdatesForParamsHybrid(deltaCatalog, dbCatalog, redact, redactCollectionLevel);
            writesForParams.addAll(dbUpdateReturn.bulkUpdatesForSingleTypeInfo);
            writesForSensitiveSampleData.addAll(dbUpdateReturn.bulkUpdatesForSampleData);
            writesForSensitiveParamInfo.addAll(dbUpdateReturn.bulkUpdatesForSensitiveParamInfo);
            writesForTraffic.addAll(getDBUpdatesForTrafficHybrid(apiCollectionId, deltaCatalog));

            deltaCatalog.setDeletedInfo(new ArrayList<>());

            boolean forceUpdate = syncImmediately || counter % 10 == 0;
            loggerMaker.debug("Building DB updates writes for sample data for apiCollectionId: " + apiCollectionId + " forceUpdate: " + forceUpdate);
            writesForSampleData.addAll(getDBUpdatesForSampleDataHybrid(apiCollectionId, deltaCatalog,dbCatalog, forceUpdate, redact, redactCollectionLevel));
            loggerMaker.debug("done with sample data updates for apiCollectionId: " + apiCollectionId + " sampleData size: " + writesForSampleData.size());

        }

        loggerMaker.infoAndAddToDb("adding " + writesForParams.size() + " updates for params");
        int from = 0;
        int batch = 10000;

        long start = System.currentTimeMillis();
        if (writesForParams.size() >0) {

            do {

                List<Object> slicedWrites = writesForParams.subList(from, Math.min(from + batch, writesForParams.size()));
                from += batch;
                if (slicedWrites.size() > 0) {
                    dataActor.bulkWriteSingleTypeInfo(slicedWrites);
                }
            } while (from < writesForParams.size());
        }

        aktoPolicyNew.syncWithDb();

        loggerMaker.infoAndAddToDb("adding " + writesForTraffic.size() + " updates for traffic");
        if(writesForTraffic.size() > 0) {
            dataActor.bulkWriteTrafficInfo(writesForTraffic);
        }


        loggerMaker.infoAndAddToDb("adding " + writesForSampleData.size() + " updates for samples");
        if(writesForSampleData.size() > 0) {
            dataActor.bulkWriteSampleData(writesForSampleData);
        }

        if (writesForSensitiveSampleData.size() > 0) {
            dataActor.bulkWriteSensitiveSampleData(writesForSensitiveSampleData);
        }

        if (writesForSensitiveParamInfo.size() > 0) {
            dataActor.bulkWriteSensitiveParamInfo(writesForSensitiveParamInfo);
        }

        refreshDbState(fetchAllSTI);
    }

    public void refreshDbState(boolean fetchAllSTI) {
        int now = Context.now();
        if (lastBuildFromDb + DB_REFRESH_CYCLE < now) {
            loggerMaker.infoAndAddToDb("starting build from db inside syncWithDb at : " + now);
            buildFromDB(true, fetchAllSTI);
            now = Context.now();
            loggerMaker.infoAndAddToDb("Finished syncing with db at : " + now);
            lastBuildFromDb = now;
        }
    }

    private static final Gson gson = new Gson();
    private static final String AKTO_UUID = "akto_uuid";

    public List<BulkUpdates> getDBUpdatesForSampleDataHybrid(int apiCollectionId, APICatalog currentDelta, APICatalog dbCatalog, boolean forceUpdate, boolean accountLevelRedact, boolean apiCollectionLevelRedact) {
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

        List<BulkUpdates> bulkUpdates = new ArrayList<>();
        List<SampleDataAlt> unfilteredSamples = new ArrayList<>();
        loggerMaker.debug("Redacting sample data for apiCollectionId: " + apiCollectionId + " sampleData size: " + sampleData.size());
        handleSampleDataRedaction(apiCollectionId, accountLevelRedact, apiCollectionLevelRedact, sampleData, bulkUpdates, unfilteredSamples);

        loggerMaker.debug("Inserting bulk sample data for apiCollectionId: " + apiCollectionId + " sampleData size: " + sampleData.size());

        if (accountLevelRedact || apiCollectionLevelRedact) {
            try {
                long start = System.currentTimeMillis();
                List<SampleDataAlt> samplesBatch = new ArrayList<>();
                for (int i = 0; i < unfilteredSamples.size(); i++) {
                    samplesBatch.add(unfilteredSamples.get(i));
                    if ((i % 100) == 0) {
                        clientLayer.bulkInsertSamples(samplesBatch);
                        samplesBatch = new ArrayList<>();
                    }
                }
                if (!samplesBatch.isEmpty()) {
                    clientLayer.bulkInsertSamples(samplesBatch);
                }
                AllMetrics.instance.setPostgreSampleDataInsertedCount(unfilteredSamples.size());
                AllMetrics.instance.setPostgreSampleDataInsertLatency(System.currentTimeMillis() - start);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "unable to insert sample data in postgres");
            }
        }
        
        loggerMaker.debug("Inserted bulk sample data for apiCollectionId: " + apiCollectionId + " sampleData size: " + sampleData.size());


        return bulkUpdates;
    }

    private final ExecutorService batchExecutor = Executors.newSingleThreadExecutor();

    public <T> T runWithTimeout(Callable<T> task, int timeoutSeconds) throws Exception {
        Future<T> future = batchExecutor.submit(task);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); 
            throw new RuntimeException("Batch timed out and was killed", e);
        }
    }

    private void handleSampleDataRedaction(int apiCollectionId, boolean accountLevelRedact, boolean apiCollectionLevelRedact, List<SampleData> sampleData,
            List<BulkUpdates> bulkUpdates, List<SampleDataAlt> unfilteredSamples) {
        int batchSize = 100;
        for (int i = 0; i < sampleData.size(); i += batchSize) {
            int end = Math.min(i + batchSize, sampleData.size());
            List<SampleData> batch = sampleData.subList(i, end);
            int accId = Context.accountId.get();
            List<SampleData> lastExecutedBatch = new ArrayList<>();
            try {
                runWithTimeout(() -> {
                    Context.accountId.set(accId);
                    processSampleBatch(batch, accountLevelRedact, apiCollectionLevelRedact, bulkUpdates, unfilteredSamples, lastExecutedBatch); // explicitly pass accountId, threads don't share Context.
                    return null;
                }, 10);
            } catch (Exception e) {
                // print lastExecutedBatch for debugging
                loggerMaker.errorAndAddToDb(e, "Batch processing timed out or failed apiCollectionId: " + apiCollectionId + " batch size: " + batch.size());
            }
        }
    }

    private void processSampleBatch(List<SampleData> batch, boolean accountLevelRedact, boolean apiCollectionLevelRedact,
            List<BulkUpdates> bulkUpdates, List<SampleDataAlt> unfilteredSamples, List<SampleData> lastExecutedBatch) {
        for (SampleData sample: batch) {
            if (sample.getSamples().size() == 0) {
                continue;
            }
            List<String> finalSamples = new ArrayList<>();
            List<String> sampleIds = new ArrayList<>();
            ArrayList<String> updates = new ArrayList<>();
            for (String s: sample.getSamples()) {
                try {
                    lastExecutedBatch.clear();
                    lastExecutedBatch.add(sample);
                    String redactedSample = RedactSampleData.redactIfRequired(s, accountLevelRedact, apiCollectionLevelRedact);
                    if (accountLevelRedact || apiCollectionLevelRedact) {
                        Map<String, Object> json = gson.fromJson(redactedSample, Map.class);
                        UUID uuid = UUID.randomUUID();
                        json.put(AKTO_UUID, uuid);
                        redactedSample = gson.toJson(json);
                        int now = Context.now();
                        Key id = sample.getId();
                        int accountId = Context.accountId.get();
                        String piiRedactedSample = RedactSampleData.redactIfRequired(s, false, false);
                        if (publicKey != null) {
                            try {
                                piiRedactedSample = PayloadEncodeUtil.encryptAndPack(piiRedactedSample, publicKey);                                
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb("error encoding payload string " + e.getMessage());
                            }
                        }
                        SampleDataAlt sampleDataAlt = new SampleDataAlt(uuid, piiRedactedSample, id.getApiCollectionId(),
                                id.getMethod().name(), id.getUrl(), id.getResponseCode(), now, accountId);
                        if (sample.getId().getApiCollectionId() != 0) {
                            unfilteredSamples.add(sampleDataAlt);
                        }
                        sampleIds.add(uuid.toString());

                    }
                    finalSamples.add(redactedSample);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"Error while redacting data" );
                }
            }
            UpdatePayload updatePayload = new UpdatePayload("samples", finalSamples, "pushEach");
            updates.add(updatePayload.toString());

            updatePayload = new UpdatePayload(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(sample.getId().getApiCollectionId()), "setOnInsert");
            updates.add(updatePayload.toString());

            if (accountLevelRedact || apiCollectionLevelRedact) {
                updatePayload = new UpdatePayload(SampleData.SAMPLE_IDS, sampleIds, "pushEach");
                updates.add(updatePayload.toString());
            }

            Map<String, Object> filterMap = new HashMap<>();
            filterMap.put("_id", sample.getId());
            bulkUpdates.add(new BulkUpdates(filterMap, updates));
        }
    }

    public DbUpdateReturnHybrid getDBUpdatesForParamsHybrid(APICatalog currentDelta, APICatalog currentState, boolean redactSampleData, boolean collectionLevelRedact) {
        Map<String, SingleTypeInfo> dbInfoMap = convertToMap(currentState.getAllTypeInfo());
        Map<String, SingleTypeInfo> deltaInfoMap = convertToMap(currentDelta.getAllTypeInfo());

        ArrayList<BulkUpdates> bulkUpdates = new ArrayList<>();
        ArrayList<BulkUpdates> bulkUpdatesForSampleData = new ArrayList<>();
        int now = Context.now();
        for(String key: deltaInfoMap.keySet()) {
            ArrayList<String> updates = new ArrayList<>();

            SingleTypeInfo dbInfo = dbInfoMap.get(key);
            SingleTypeInfo deltaInfo = deltaInfoMap.get(key);

            if (deltaInfo != null && Utils.printDebugUrlLog(deltaInfo.getUrl())) {
                loggerMaker.infoAndAddToDb("Found debug url in getDBUpdatesForParamsHybrid in deltaInfo " + deltaInfo.getUrl());
            }

            if (dbInfo != null && Utils.printDebugUrlLog(dbInfo.getUrl())) {
                loggerMaker.infoAndAddToDb("Found debug url in getDBUpdatesForParamsHybrid in dbInfo " + dbInfo.getUrl());
            }

            if (deltaInfo.getParam().equalsIgnoreCase("host")) {
                if (dbInfo == null) {
                    AllMetrics.instance.setCyborgNewApiCount(1);
                }
                AllMetrics.instance.setCyborgTotalApiCount(1);
            }

            int inc = deltaInfo.getCount() - (dbInfo == null ? 0 : dbInfo.getCount());
            long lastSeenDiff = deltaInfo.getLastSeen() - (dbInfo == null ? 0 : dbInfo.getLastSeen());
            boolean minMaxChanged = (dbInfo == null) || (dbInfo.getMinValue() != deltaInfo.getMinValue()) || (dbInfo.getMaxValue() != deltaInfo.getMaxValue());
            boolean valuesChanged = (dbInfo == null) || (dbInfo.getValues().count() != deltaInfo.getValues().count());

            if (inc == 0 && lastSeenDiff < (60*30) && !minMaxChanged && !valuesChanged) {
                continue;
            } else {
                inc = 1;
            }

            UpdatePayload updatePayload = new UpdatePayload("count", inc, "inc");

            updates.add(updatePayload.toString());

            int timestamp = deltaInfo.getTimestamp() > 0 ? deltaInfo.getTimestamp() : now;

            updatePayload = new UpdatePayload("timestamp", timestamp, "setOnInsert");
            updates.add(updatePayload.toString());

            updatePayload = new UpdatePayload(SingleTypeInfo.LAST_SEEN, deltaInfo.getLastSeen(), "max");
            updates.add(updatePayload.toString());

            updatePayload = new UpdatePayload(SingleTypeInfo.MAX_VALUE, deltaInfo.getMaxValue(), "max");
            updates.add(updatePayload.toString());

            updatePayload = new UpdatePayload(SingleTypeInfo._COLLECTION_IDS, deltaInfo.getApiCollectionId(), "setOnInsert");
            updates.add(updatePayload.toString());

            updatePayload = new UpdatePayload(SingleTypeInfo.MIN_VALUE, deltaInfo.getMinValue(), "min");
            updates.add(updatePayload.toString());

            if (!(redactSampleData || collectionLevelRedact) && deltaInfo.getExamples() != null && !deltaInfo.getExamples().isEmpty()) {
                Set<Object> updatedSampleData = new HashSet<>();
                for (Object example : deltaInfo.getExamples()) {
                    try{
                        String exampleStr = (String) example;
                        String s = RedactSampleData.redactDataTypes(exampleStr);
                        updatedSampleData.add(s);
                    } catch (Exception e) {
                        ;
                    }
                }
                deltaInfo.setExamples(updatedSampleData);
                ArrayList<String> sampleDataUpdates = new ArrayList<>();
                updatePayload = new UpdatePayload(SensitiveSampleData.SAMPLE_DATA, Arrays.asList(deltaInfo.getExamples().toArray()), "pushEach");
                AllMetrics.instance.setCyborgApiPayloadSize(updatePayload.getSize());
                sampleDataUpdates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(deltaInfo.getApiCollectionId()), "setOnInsert");
                sampleDataUpdates.add(updatePayload.toString());
                bulkUpdatesForSampleData.add(new BulkUpdates(SensitiveSampleDataDao.getFiltersMap(deltaInfo), sampleDataUpdates));
            }

            boolean isEligible = true;
            try {
                isEligible = FilterUpdates.isEligibleForUpdate(deltaInfo.getApiCollectionId(), deltaInfo.getUrl(), deltaInfo.getMethod(), deltaInfo.getParam(), deltaInfo.getResponseCode(), "update");
                // if (!isEligible) {
                //     loggerMaker.infoAndAddToDb("param already found " + deltaInfo.getParam() + " " + deltaInfo.getUrl());
                // }
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("error evaluating if sti param is eligible for update " + e.getMessage());
            }

            if (isEligible) {
                bulkUpdates.add(new BulkUpdates(SingleTypeInfoDao.createFiltersMap(deltaInfo), updates));
            }

        }

        for(SingleTypeInfo deleted: currentDelta.getDeletedInfo()) {
            ArrayList<String> updates = new ArrayList<>();
            UpdatePayload updatePayload = new UpdatePayload(null, null, "delete");
            updates.add(updatePayload.toString());
            currentDelta.getStrictURLToMethods().remove(new URLStatic(deleted.getUrl(), Method.fromString(deleted.getMethod())));

            boolean isEligible = true;
            try {
                isEligible = FilterUpdates.isEligibleForUpdate(deleted.getApiCollectionId(), deleted.getUrl(), deleted.getMethod(), deleted.getParam(), deleted.getResponseCode(), "delete");
                // if (!isEligible) {
                //     loggerMaker.infoAndAddToDb("param already found for delete update " + deleted.getParam() + " " + deleted.getUrl());
                // }
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("error evaluating if sti param is eligible for delete update " + e.getMessage());
            }

            if (isEligible) {
                bulkUpdates.add(new BulkUpdates(SingleTypeInfoDao.createFiltersMap(deleted), updates));
            }

            ArrayList<String> sampleDataUpdates = new ArrayList<>();
            updatePayload = new UpdatePayload(null, null, "delete");
            sampleDataUpdates.add(updatePayload.toString());

            bulkUpdatesForSampleData.add(new BulkUpdates(SensitiveSampleDataDao.getFiltersMap(deleted), sampleDataUpdates));
        }


        ArrayList<BulkUpdates> bulkUpdatesForSensitiveParamInfo = new ArrayList<>();
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfoBooleanMap.keySet()) {
            if (!sensitiveParamInfoBooleanMap.get(sensitiveParamInfo)) continue;
            ArrayList<String> updates = new ArrayList<>();
            UpdatePayload updatePayload = new UpdatePayload(SensitiveParamInfo.SAMPLE_DATA_SAVED, true, "set");
            updates.add(updatePayload.toString());
            bulkUpdatesForSensitiveParamInfo.add(new BulkUpdates(SensitiveParamInfoDao.getFiltersMap(sensitiveParamInfo), updates));

        }

        return new DbUpdateReturnHybrid(bulkUpdates, bulkUpdatesForSampleData, bulkUpdatesForSensitiveParamInfo);
    }

    public ArrayList<BulkUpdates> getDBUpdatesForTrafficHybrid(int apiCollectionId, APICatalog currentDelta) {

        List<TrafficInfo> trafficInfos = new ArrayList<>();
        for(Map.Entry<URLStatic, RequestTemplate> entry: currentDelta.getStrictURLToMethods().entrySet()) {
            trafficInfos.addAll(entry.getValue().removeAllTrafficInfo(apiCollectionId, entry.getKey().getUrl(), entry.getKey().getMethod(), -1));
        }

        for(Map.Entry<URLTemplate, RequestTemplate> entry: currentDelta.getTemplateURLToMethods().entrySet()) {
            trafficInfos.addAll(entry.getValue().removeAllTrafficInfo(apiCollectionId, entry.getKey().getTemplateString(), entry.getKey().getMethod(), -1));
        }

        ArrayList<BulkUpdates> bulkUpdates = new ArrayList<>();
        for (TrafficInfo trafficInfo: trafficInfos) {
            ArrayList<String> updates = new ArrayList<>();
            for (Map.Entry<String, Integer> entry: trafficInfo.mapHoursToCount.entrySet()) {
                UpdatePayload updatePayload = new UpdatePayload("mapHoursToCount."+entry.getKey(), entry.getValue(), "inc");
                updates.add(updatePayload.toString());
                updatePayload = new UpdatePayload(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(trafficInfo.getId().getApiCollectionId()), "setOnInsert");
                updates.add(updatePayload.toString());
            }
            Map<String, Object> filterMap = new HashMap<>();
            filterMap.put("_id", trafficInfo.getId());
            bulkUpdates.add(new BulkUpdates(filterMap, updates));
        }

        return bulkUpdates;
    }

    public static class DbUpdateReturnHybrid {
        public ArrayList<BulkUpdates> bulkUpdatesForSingleTypeInfo;
        public ArrayList<BulkUpdates> bulkUpdatesForSampleData;
        public ArrayList<BulkUpdates> bulkUpdatesForSensitiveParamInfo = new ArrayList<>();

        public DbUpdateReturnHybrid(ArrayList<BulkUpdates> bulkUpdatesForSingleTypeInfo,
                              ArrayList<BulkUpdates> bulkUpdatesForSampleData,
                              ArrayList<BulkUpdates> bulkUpdatesForSensitiveParamInfo
        ) {
            this.bulkUpdatesForSingleTypeInfo = bulkUpdatesForSingleTypeInfo;
            this.bulkUpdatesForSampleData = bulkUpdatesForSampleData;
            this.bulkUpdatesForSensitiveParamInfo = bulkUpdatesForSensitiveParamInfo;
        }
    }

    public void printNewURLsInDelta(APICatalog deltaCatalog) {
        for(URLStatic s: deltaCatalog.getStrictURLToMethods().keySet()) {
            loggerMaker.info(s.getUrl());
        }

        for(URLTemplate s: deltaCatalog.getTemplateURLToMethods().keySet()) {
            loggerMaker.info(s.getTemplateString());
        }
    }


    public APICatalog getDelta(int apiCollectionId) {
        return this.delta.get(apiCollectionId);
    }


    public APICatalog getDbState(int apiCollectionId) {
        return this.dbState.get(apiCollectionId);
    }

    public boolean isMergeUrlsOnVersions() {
        return mergeUrlsOnVersions;
    }

    public void setMergeUrlsOnVersions(boolean mergeUrlsOnVersions) {
        this.mergeUrlsOnVersions = mergeUrlsOnVersions;
    }

    private Pair<SyncLimit, MetricTypes> getSyncLimitForApiCollection(ApiCollection apiCollection, SyncLimit apiSyncLimit,
        SyncLimit mcpAssetsSyncLimit, SyncLimit aiAssetsSyncLimit) {

        if (apiCollection == null) {
            return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
        }

        if (apiCollection.isMcpCollection()) {
            return new Pair<>(mcpAssetsSyncLimit, MetricTypes.MCP_ASSET_COUNT);
        }

        if (apiCollection.isGenAICollection()) {
            return new Pair<>(aiAssetsSyncLimit, MetricTypes.AI_ASSET_COUNT);
        }

        return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
    }
}
