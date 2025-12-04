package com.akto.runtime;

import com.akto.mcp.McpSchema;
import com.akto.util.Pair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.filter.MergedUrlsDao;
import com.akto.dto.*;
import com.akto.dto.billing.SyncLimit;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.filter.MergedUrls;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dto.monitoring.FilterConfig;
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
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.filter.DictionaryFilter;
import com.akto.runtime.merge.MergeOnHostOnly;
import com.akto.runtime.policies.AktoPolicyNew;
import com.akto.task.Cluster;
import com.akto.types.CappedSet;
import com.akto.usage.UsageMetricCalculator;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.JSONUtils;
import com.akto.utils.RedactSampleData;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.api.client.util.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import org.apache.commons.lang3.math.NumberUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Map.Entry;

import static com.akto.dto.type.KeyTypes.patternToSubType;

public class APICatalogSync {

    public static final int VULNERABLE_API_COLLECTION_ID = 1111111111;
    public static final int LLM_API_COLLECTION_ID = 1222222222;

    public int thresh;
    public String userIdentifier;
    private static final Logger logger = LoggerFactory.getLogger(APICatalogSync.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(APICatalogSync.class);
    public Map<Integer, APICatalog> dbState;
    public Map<Integer, APICatalog> delta;
    public AktoPolicyNew aktoPolicyNew;
    public Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap;
    public static boolean mergeAsyncOutside = true;
    public BloomFilter<CharSequence> existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );
    private boolean skipMergingOnKnownStaticURLsForVersionedApis = false;

    public static final Pattern VERSION_PATTERN = Pattern.compile("\\bv([1-9][0-9]?|100)\\b");

    public static Set<MergedUrls> mergedUrls;

    public Map<String, FilterConfig> advancedFilterMap =  new HashMap<>();

    /* Note: We have hardcoded the logic of not merging URLs for MCP Server.
        The apiCollectionId - -1 has nothing to do with this.
        Since we do not know the collectionId for MCP Server, we have set it to -1.
    */

    public APICatalogSync(String userIdentifier,int thresh, boolean fetchAllSTI) {
        this(userIdentifier, thresh, fetchAllSTI, true);
    }
    private boolean mergeUrlsOnVersions = false;
    // New overloaded constructor
    public APICatalogSync(String userIdentifier, int thresh, boolean fetchAllSTI, boolean buildFromDb) {
        this.thresh = thresh;
        this.userIdentifier = userIdentifier;
        this.dbState = new HashMap<>();
        this.delta = new HashMap<>();
        this.sensitiveParamInfoBooleanMap = new HashMap<>();
        this.aktoPolicyNew = new AktoPolicyNew();
        mergedUrls = new HashSet<>();
        if (buildFromDb) {
            buildFromDB(false, fetchAllSTI);
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            if (accountSettings != null && accountSettings.getPartnerIpList() != null) {
                partnerIpsList = accountSettings.getPartnerIpList();
                setMergeUrlsOnVersions(accountSettings.isAllowMergingOnVersions());
            }
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
                loggerMaker.errorAndAddToDb("processResponse: " + e.getMessage(), LogDb.RUNTIME);
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
        JSONObject jsonObject = RequestTemplate.parseRequestPayloadToJsonObject(requestParams.getPayload(), urlWithParams);
        Map<String, Set<Object>> flattened = JSONUtils.flattenJSONObject(jsonObject);
        deletedInfo.addAll(requestTemplate.process2(flattened, baseURL.getUrl(), methodStr, -1, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp));
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

            JSONObject payload;
            try {
                payload = JSON.parseObject(respPayload);
            } catch (Exception e) {
                payload = JSON.parseObject("{}");
            }

            flattened = JSONUtils.flattenJSONObject(payload);
            deletedInfo.addAll(responseTemplate.process2(flattened, baseURL.getUrl(), methodStr, statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp));
            responseTemplate.processHeaders(responseParams.getHeaders(), baseURL.getUrl(), method.name(), statusCode, userId, requestParams.getApiCollectionId(), responseParams.getOrig(), sensitiveParamInfoBooleanMap, timestamp);
            if (!responseParams.getIsPending()) {
                responseTemplate.processTraffic(responseParams.getTime());
            }

        } catch (JsonParseException e) {
            loggerMaker.errorAndAddToDb("Failed to parse json payload " + e.getMessage(), LogDb.RUNTIME);
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

    public void computeDelta(URLAggregator origAggregator, boolean triggerTemplateGeneration, int apiCollectionId, boolean makeApisCaseInsensitive) {
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
        logger.info("aggregator: " + (System.currentTimeMillis() - start));
        origAggregator.urls = new ConcurrentHashMap<>();

        Set<Map.Entry<URLStatic, Set<HttpResponseParams>>> entries = aggregator.urls.entrySet();
        for (Map.Entry<URLStatic, Set<HttpResponseParams>> entry : entries) {
            Set<HttpResponseParams> value = entry.getValue();
            for (HttpResponseParams responseParams: value) {
                try {
                    aktoPolicyNew.process(responseParams, partnerIpsList);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

        start = System.currentTimeMillis();
        processKnownStaticURLs(aggregator, deltaCatalog, dbCatalog, makeApisCaseInsensitive);
        logger.info("processKnownStaticURLs: " +  (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        Map<URLStatic, RequestTemplate> pendingRequests = createRequestTemplates(aggregator);
        logger.info("pendingRequests: " + (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        tryWithKnownURLTemplates(pendingRequests, deltaCatalog, dbCatalog, apiCollectionId );
        logger.info("tryWithKnownURLTemplates: " + (System.currentTimeMillis() - start));

        if (!mergeAsyncOutside) {
            start = System.currentTimeMillis();
            tryMergingWithKnownStrictURLs(pendingRequests, dbCatalog, deltaCatalog);
            logger.info("tryMergingWithKnownStrictURLs: " + (System.currentTimeMillis() - start));
        } else {
            for (URLStatic pending: pendingRequests.keySet()) {
                RequestTemplate pendingTemplate = pendingRequests.get(pending);

                URLTemplate parameterisedTemplate = null;
                if((apiCollectionId != VULNERABLE_API_COLLECTION_ID) && (apiCollectionId != LLM_API_COLLECTION_ID)){
                    parameterisedTemplate = tryParamteresingUrl(pending, mergeUrlsOnVersions);
                }

                if(parameterisedTemplate != null){
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

        logger.info("processTime: " + RequestTemplate.insertTime + " " + RequestTemplate.processTime + " " + RequestTemplate.deleteTime);

    }

    public static ApiMergerResult tryMergeURLsInCollection(int apiCollectionId, Boolean urlRegexMatchingEnabled, boolean mergeUrlsBasic, BloomFilter<CharSequence> existingAPIsInDb, boolean ignoreCaseInsensitiveApis, boolean mergeUrlsOnVersions) {
        ApiCollection apiCollection = ApiCollectionsDao.instance.getMeta(apiCollectionId);

        if (apiCollection != null && apiCollection.isMcpCollection()) {
            return new ApiMergerResult(new HashMap<>());
        }

        Bson filterQ = null;
        if (apiCollection != null && apiCollection.getHostName() == null) {
            filterQ = Filters.eq("apiCollectionId", apiCollectionId);
        } else {
            Bson hostFilter = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
            Bson normalFilter = Filters.and(
                    Filters.eq("apiCollectionId", apiCollectionId),
                    Filters.or(Filters.eq("isHeader", false), Filters.eq("param", "host"))
            );
            filterQ = mergeUrlsBasic ? hostFilter : normalFilter;
        }

        int offset = 0;
        int limit = mergeUrlsBasic ? 10_000 : 1_000_000;

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        ApiMergerResult finalResult = new ApiMergerResult(new HashMap<>());
        do {
            singleTypeInfos = SingleTypeInfoDao.instance.findAll(filterQ, offset, limit, null, Projections.exclude("values"));
            logger.info("Singetypeinfo size: " + singleTypeInfos.size());

            Map<String, Set<String>> staticUrlToSti = new HashMap<>();
            Set<String> templateUrlSet = new HashSet<>();
            List<String> templateUrls = new ArrayList<>();
            for(SingleTypeInfo sti: singleTypeInfos) {
                String key = sti.getMethod() + " " + sti.getUrl();
                fillExistingAPIsInDb(sti, existingAPIsInDb);

                if (APICatalog.isTemplateUrl(sti.getUrl())) {
                    templateUrlSet.add(key);
                    continue;
                };

                if (sti.getIsUrlParam()) continue;
                if (sti.getIsHeader()) {
                    staticUrlToSti.putIfAbsent(key, new HashSet<>());
                    continue;
                }


                Set<String> set = staticUrlToSti.get(key);
                if (set == null) {
                    set = new HashSet<>();
                    staticUrlToSti.put(key, set);
                }

                set.add(sti.getResponseCode() + " " + sti.getParam());
            }

            for (String s: templateUrlSet) {
                templateUrls.add(s);
            }

            // handle case sensitive apis in here only
            Set<String> seenStaticUrls = new HashSet<>();

            Iterator<String> iterator = staticUrlToSti.keySet().iterator();
            while (iterator.hasNext()) {
                String staticURL = iterator.next();
                Method staticMethod = Method.fromString(staticURL.split(" ")[0]);
                String staticEndpoint = staticURL.split(" ")[1];
                String tempEndpoint = staticEndpoint.toLowerCase();

                if(ignoreCaseInsensitiveApis){
                    if(!seenStaticUrls.isEmpty() && seenStaticUrls.contains(tempEndpoint)){
                        finalResult.deleteStaticUrls.add(staticURL);
                        iterator.remove();
                        continue;
                    }
                }

                for (String templateURL: templateUrls) {
                    Method templateMethod = Method.fromString(templateURL.split(" ")[0]);
                    String templateEndpoint = templateURL.split(" ")[1];

                    URLTemplate urlTemplate = createUrlTemplate(templateEndpoint, templateMethod);
                    if (urlTemplate.match(staticEndpoint, staticMethod)) {
                        finalResult.deleteStaticUrls.add(staticURL);
                        iterator.remove();
                        break;
                    }
                }
                if(ignoreCaseInsensitiveApis){
                    seenStaticUrls.add(tempEndpoint);
                }
            }

            Map<Integer, Map<String, Set<String>>> sizeToUrlToSti = groupByTokenSize(staticUrlToSti);

            sizeToUrlToSti.remove(1);
            sizeToUrlToSti.remove(0);


            for(int size: sizeToUrlToSti.keySet()) {
                ApiMergerResult result = tryMergingWithKnownStrictURLs(sizeToUrlToSti.get(size), urlRegexMatchingEnabled, !mergeUrlsBasic, mergeUrlsOnVersions);
                finalResult.templateToStaticURLs.putAll(result.templateToStaticURLs);
            }

            offset += limit;
        } while (!singleTypeInfos.isEmpty());

        return finalResult;
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

    private static ApiMergerResult tryMergingWithKnownStrictURLs(
        Map<String, Set<String>> pendingRequests, Boolean urlRegexMatchingEnabled, boolean doBodyMatch, boolean mergeUrlsOnVersions
    ) {
        Map<URLTemplate, Set<String>> templateToStaticURLs = new HashMap<>();

        Iterator<Map.Entry<String, Set<String>>> iterator = pendingRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Set<String>> entry = iterator.next();
            iterator.remove();

            String newUrl = entry.getKey();
            Set<String> newTemplate = entry.getValue();
            Method newMethod = Method.fromString(newUrl.split(" ")[0]);
            String newEndpoint = newUrl.split(" ")[1];

            boolean matchedInDeltaTemplate = false;
            for(URLTemplate urlTemplate: templateToStaticURLs.keySet()){
                if (urlTemplate.match(newEndpoint, newMethod)) {
                    matchedInDeltaTemplate = true;
                    templateToStaticURLs.get(urlTemplate).add(newUrl);
                    break;
                }
            }

            if (matchedInDeltaTemplate) {
                continue;
            }

            URLStatic newStaticUrl = new URLStatic(newEndpoint, newMethod);

            URLTemplate tempUrlTemplate = tryParamteresingUrl(newStaticUrl, mergeUrlsOnVersions);
            if(tempUrlTemplate != null){
                Set<String> matchedStaticURLs = templateToStaticURLs.getOrDefault(tempUrlTemplate, new HashSet<>());
                matchedStaticURLs.add(newUrl);
                templateToStaticURLs.put(tempUrlTemplate, matchedStaticURLs);
            }


            int countSimilarURLs = 0;
            Map<URLTemplate, Map<String, Set<String>>> potentialMerges = new HashMap<>();
            for(String aUrl: pendingRequests.keySet()) {
                Set<String> aTemplate = pendingRequests.get(aUrl);
                Method aMethod = Method.fromString(aUrl.split(" ")[0]);
                String aEndpoint = aUrl.split(" ")[1];
                URLStatic aStatic = new URLStatic(aEndpoint, aMethod);
                URLStatic newStatic = new URLStatic(newEndpoint, newMethod);
                URLTemplate mergedTemplate = APICatalogSync.tryMergeUrls(aStatic, newStatic, mergeUrlsOnVersions);
                if (mergedTemplate == null) {
                    continue;
                }

                boolean compareKeys = doBodyMatch && RequestTemplate.compareKeys(aTemplate, newTemplate, mergedTemplate);
                if (APICatalogSync.areBothMatchingUrls(newStatic,aStatic,mergedTemplate, urlRegexMatchingEnabled) || APICatalogSync.areBothUuidUrls(newStatic,aStatic,mergedTemplate) || compareKeys) {
                    Map<String, Set<String>> similarTemplates = potentialMerges.get(mergedTemplate);
                    if (similarTemplates == null) {
                        similarTemplates = new HashMap<>();
                        potentialMerges.put(mergedTemplate, similarTemplates);
                    } 
                    similarTemplates.put(aUrl, aTemplate);

                    if (!RequestTemplate.isMergedOnStr(mergedTemplate) || APICatalogSync.areBothUuidUrls(newStatic,aStatic,mergedTemplate) || APICatalogSync.areBothMatchingUrls(newStatic,aStatic,mergedTemplate, urlRegexMatchingEnabled)) {
                        countSimilarURLs = APICatalogSync.STRING_MERGING_THRESHOLD;
                    }

                    countSimilarURLs++;
                }
            }

            if (countSimilarURLs >= APICatalogSync.STRING_MERGING_THRESHOLD) {
                URLTemplate mergedTemplate = potentialMerges.keySet().iterator().next();
                Set<String> matchedStaticURLs = templateToStaticURLs.get(mergedTemplate);

                if (matchedStaticURLs == null) {
                    matchedStaticURLs = new HashSet<>();
                    templateToStaticURLs.put(mergedTemplate, matchedStaticURLs);
                }

                matchedStaticURLs.add(newUrl);

                for (Map.Entry<String, Set<String>> rt: potentialMerges.getOrDefault(mergedTemplate, new HashMap<>()).entrySet()) {
                    matchedStaticURLs.add(rt.getKey());
                }
            }
        }

        return new ApiMergerResult(templateToStaticURLs);
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

    public static URLTemplate tryParamteresingUrl(URLStatic newUrl, boolean mergeUrlsOnVersions) {
        String[] tokens = tokenize(newUrl.getUrl());
        boolean tokensBelowThreshold = tokens.length < 2;
        Pattern pattern = patternToSubType.get(SingleTypeInfo.UUID);
        boolean allNull = true;
        SuperType[] newTypes = new SuperType[tokens.length];

        int start = newUrl.getUrl().startsWith("http") ? 3 : 0;

        if(HttpResponseParams.isGraphQLEndpoint(newUrl.getUrl())) {
            return null; // Don't merge GraphQL endpoints
        }

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
                if(!tokensBelowThreshold && isValidSubtype(tempSubType)){
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

        if(HttpResponseParams.isGraphQLEndpoint(dbUrl.getUrl()) || HttpResponseParams.isGraphQLEndpoint(newUrl.getUrl())) {
            return null; // Don't merge GraphQL endpoints
        }

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
            } else if(pattern.matcher(tempToken).matches() && pattern.matcher(dbToken).matches()){
                newTypes[i] = SuperType.STRING;
                newTokens[i] = null;
            }else if(isAlphanumericString(tempToken) && isAlphanumericString(dbToken)){
                newTypes[i] = SuperType.STRING;
                newTokens[i] = null;
            }else if(mergeUrlsOnVersions && isValidVersionToken(tempToken) && isValidVersionToken(dbToken)){
                newTypes[i] = SuperType.VERSIONED;
                newTokens[i] = null;
            }else {
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
            loggerMaker.errorAndAddToDb("Error while creating a new URL object: " + e.getMessage(), LogDb.RUNTIME);
        }

        return urlTemplate;
    }

    public static void mergeUrlsAndSave(int apiCollectionId, Boolean urlRegexMatchingEnabled, boolean mergeUrlsBasic, BloomFilter<CharSequence> existingAPIsInDb,boolean ignoreCaseInsensitiveApis, boolean mergeUrlsOnVersions) {
        if (apiCollectionId == LLM_API_COLLECTION_ID || apiCollectionId == VULNERABLE_API_COLLECTION_ID) return;

        ApiMergerResult result = tryMergeURLsInCollection(apiCollectionId, urlRegexMatchingEnabled, mergeUrlsBasic, existingAPIsInDb, ignoreCaseInsensitiveApis, mergeUrlsOnVersions);

        String deletedStaticUrlsString = "";
        int counter = 0;
        if (result.deleteStaticUrls != null) {
            for (String dUrl: result.deleteStaticUrls) {
                if (counter >= 50) break;
                if (dUrl == null) continue;
                deletedStaticUrlsString += dUrl + ", ";
                counter++;
            }
        }
        loggerMaker.debugInfoAddToDb("deleteStaticUrls: " + deletedStaticUrlsString, LogDb.RUNTIME);

        loggerMaker.debugInfoAddToDb("merged URLs: ", LogDb.RUNTIME);
        if (result.templateToStaticURLs != null) {
            for (URLTemplate urlTemplate: result.templateToStaticURLs.keySet()) {
                String tempUrl = urlTemplate.getTemplateString() + " : ";
                counter = 0;
                if (result.templateToStaticURLs == null) continue;
                for (String url: result.templateToStaticURLs.get(urlTemplate)) {
                    if (counter >= 5) break;
                    if (url == null) continue;
                    tempUrl += url + ", ";
                    counter++;
                }

                loggerMaker.debugInfoAddToDb( tempUrl, LogDb.RUNTIME);
            }
        }

        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSti = new ArrayList<>();
        ArrayList<WriteModel<SampleData>> bulkUpdatesForSampleData = new ArrayList<>();
        ArrayList<WriteModel<ApiInfo>> bulkUpdatesForApiInfo = new ArrayList<>();
        ArrayList<WriteModel<DependencyNode>> bulkUpdatesForDependencyNode = new ArrayList<>();

        for (URLTemplate urlTemplate: result.templateToStaticURLs.keySet()) {
            Set<String> matchStaticURLs = result.templateToStaticURLs.get(urlTemplate);
            String newTemplateUrl = urlTemplate.getTemplateString();
            if (!APICatalog.isTemplateUrl(newTemplateUrl)) continue;

            boolean isFirst = true;
            for (String matchedURL: matchStaticURLs) {
                Method delMethod = Method.fromString(matchedURL.split(" ")[0]);
                String delEndpoint = matchedURL.split(" ")[1];
                Bson filterQ = SingleTypeInfoDao.filterForSTIUsingURL(apiCollectionId, delEndpoint, delMethod);
                Bson filterQSampleData = SampleDataDao.filterForSampleData(apiCollectionId, delEndpoint, delMethod);

                if (isFirst) {

                    for (int i = 0; i < urlTemplate.getTypes().length; i++) {
                        SuperType superType = urlTemplate.getTypes()[i];
                        if (superType == null) continue;

                        int idx = delEndpoint.startsWith("http") ? i:i+1;
                        String word = delEndpoint.split("/")[idx];
                        SingleTypeInfo.ParamId stiId = new SingleTypeInfo.ParamId(newTemplateUrl, delMethod.name(), -1, false, i+"", SingleTypeInfo.GENERIC, apiCollectionId, true);
                        SubType tokenSubType = KeyTypes.findSubType(word, "", null,true);
                        stiId.setSubType(tokenSubType);
                        SingleTypeInfo sti = new SingleTypeInfo(
                            stiId, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, CappedSet.create(i+""),
                            SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MIN_VALUE, SingleTypeInfo.ACCEPTED_MAX_VALUE);


                        // SingleTypeInfoDao.instance.insertOne(sti);
                        bulkUpdatesForSti.add(new InsertOneModel<>(sti));
                    }

                    SingleTypeInfoDao.instance.getMCollection().updateMany(filterQ, Updates.set("url", newTemplateUrl));

                    bulkUpdatesForSti.add(new UpdateManyModel<>(filterQ, Updates.set("url", newTemplateUrl), new UpdateOptions()));

                    SampleData sd = SampleDataDao.instance.findOne(filterQSampleData);
                    if (sd != null) {
                        sd.getId().url = newTemplateUrl;
                        // SampleDataDao.instance.insertOne(sd);
                        bulkUpdatesForSampleData.add(new InsertOneModel<>(sd));
                    }


                    ApiInfo apiInfo = ApiInfoDao.instance.findOne(filterQSampleData);
                    if (apiInfo != null) {
                        apiInfo.getId().url = newTemplateUrl;
                        // ApiInfoDao.instance.insertOne(apiInfo);
                        bulkUpdatesForApiInfo.add(new InsertOneModel<>(apiInfo));
                    }

                    isFirst = false;
                } else {
                    bulkUpdatesForSti.add(new DeleteManyModel<>(filterQ));
                    // SingleTypeInfoDao.instance.deleteAll(filterQ);

                }

                bulkUpdatesForSampleData.add(new DeleteManyModel<>(filterQSampleData));
                bulkUpdatesForApiInfo.add(new DeleteManyModel<>(filterQSampleData));

                Bson filterForDependencyNode = Filters.or(
                        Filters.and(
                                Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionId+""),
                                Filters.eq(DependencyNode.URL_REQ, delEndpoint),
                                Filters.eq(DependencyNode.METHOD_REQ, delMethod.name())
                        ),
                        Filters.and(
                                Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionId+""),
                                Filters.eq(DependencyNode.URL_RESP, delEndpoint),
                                Filters.eq(DependencyNode.METHOD_RESP,delMethod.name())
                        )
                );
                bulkUpdatesForDependencyNode.add(new DeleteManyModel<>(filterForDependencyNode));

                // SampleDataDao.instance.deleteAll(filterQSampleData);
                // ApiInfoDao.instance.deleteAll(filterQSampleData);
            }
        }

        for (String deleteStaticUrl: result.deleteStaticUrls) {
            Method delMethod = Method.fromString(deleteStaticUrl.split(" ")[0]);
            String delEndpoint = deleteStaticUrl.split(" ")[1];  
            Bson filterQ = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("method", delMethod.name()),
                Filters.eq("url", delEndpoint)
            );

            Bson filterQSampleData = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.eq("_id.method", delMethod.name()),
                Filters.eq("_id.url", delEndpoint)
            );

            Bson filterForDependencyNode = Filters.or(
                    Filters.and(
                            Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionId+""),
                            Filters.eq(DependencyNode.URL_REQ, delEndpoint),
                            Filters.eq(DependencyNode.METHOD_REQ, delMethod.name())
                    ),
                    Filters.and(
                            Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionId+""),
                            Filters.eq(DependencyNode.URL_RESP, delEndpoint),
                            Filters.eq(DependencyNode.METHOD_RESP,delMethod.name())
                    )
            );
            bulkUpdatesForDependencyNode.add(new DeleteManyModel<>(filterForDependencyNode));

            bulkUpdatesForSti.add(new DeleteManyModel<>(filterQ));
            bulkUpdatesForSampleData.add(new DeleteManyModel<>(filterQSampleData));
            // SingleTypeInfoDao.instance.deleteAll(filterQ);
            // SampleDataDao.instance.deleteAll(filterQSampleData);
        }

        if (bulkUpdatesForSti.size() > 0) {
            try {
                SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForSti, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("STI bulkWrite error: " + e.getMessage(),LogDb.RUNTIME);
            }
        }

        if (bulkUpdatesForSampleData.size() > 0) {
            try {
                SampleDataDao.instance.getMCollection().bulkWrite(bulkUpdatesForSampleData, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("SampleData bulkWrite error: " + e.getMessage(),LogDb.RUNTIME);
            }
        }

        if (bulkUpdatesForApiInfo.size() > 0) {
            try {
                ApiInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForApiInfo, new BulkWriteOptions().ordered(false));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("ApiInfo bulkWrite error: " + e.getMessage(),LogDb.RUNTIME);
            }
        }

        if (bulkUpdatesForDependencyNode.size() > 0) {
            BulkWriteResult bulkWriteResult = DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdatesForDependencyNode, new BulkWriteOptions().ordered(false));
        }
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
            loggerMaker.errorAndAddToDb(e.toString(), LogDb.RUNTIME);
        }

        return ret;
    }

    private void processKnownStaticURLs(URLAggregator aggregator, APICatalog deltaCatalog, APICatalog dbCatalog, boolean makeApisCaseInsensitive) {
        Iterator<Map.Entry<URLStatic, Set<HttpResponseParams>>> iterator = aggregator.urls.entrySet().iterator();
        List<SingleTypeInfo> deletedInfo = deltaCatalog.getDeletedInfo();

        // handle case insensitive apis here in the same aggregator
        Set<String> lowerCaseApisSet = new HashSet<>();

        try {
            while (iterator.hasNext()) {
                Map.Entry<URLStatic, Set<HttpResponseParams>> entry = iterator.next();
                URLStatic url = entry.getKey();
                Set<HttpResponseParams> responseParamsList = entry.getValue();

                String endpoint = url.getUrl();
                if(this.skipMergingOnKnownStaticURLsForVersionedApis && VERSION_PATTERN.matcher(endpoint).find()){
                    continue;
                }

                if(makeApisCaseInsensitive){
                    if(lowerCaseApisSet.contains(endpoint.toLowerCase())){
                        iterator.remove();
                        continue;
                    }
                    lowerCaseApisSet.add(endpoint.toLowerCase());
                }

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
            loggerMaker.errorAndAddToDb(e.toString(),LogDb.RUNTIME);
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
        Set<MergedUrls> mergedUrls = MergedUrlsDao.instance.getMergedUrls();
        Map<String, SingleTypeInfo> ret = new HashMap<>();
        for(SingleTypeInfo e: l) {
            if(!mergedUrls.contains(new MergedUrls(e.getUrl(), e.getMethod(), e.getApiCollectionId()))) {
                ret.put(e.composeKey(), e);
            }
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
                    loggerMaker.errorAndAddToDb(e,"Error while redacting data" , LogDb.RUNTIME)
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

    public DbUpdateReturn getDBUpdatesForParams(APICatalog currentDelta, APICatalog currentState, boolean redactSampleData, boolean collectionLevelRedact, HttpResponseParams.Source source) {
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
                isQueryParam = true;
                String originalParam = deltaInfo.getParam().split("_queryParam")[0];
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
            if (source != null) {
                Bson updateSourceMap = Updates.set(SingleTypeInfo.SOURCES + "." + source.name(), new Document("timestamp", timestamp) );
                update = Updates.combine(update, updateSourceMap);
            }

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

            Bson updateKey = SingleTypeInfoDao.createFilters(deltaInfo);
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
        loggerMaker.infoAndAddToDb("RangeUpdateResult for clearValuesInDb function = " + "match count: " + rangeUpdateResult.getMatchedCount() + ", modify count: " + rangeUpdateResult.getModifiedCount(), LogDb.RUNTIME);

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

        loggerMaker.infoAndAddToDb("AnyUpdateResult for clearValuesInDb function = " + "match count: " + anyUpdateResult.getMatchedCount() + ", modify count: " + anyUpdateResult.getModifiedCount(), LogDb.RUNTIME);
    }

    private int lastMergeAsyncOutsideTs = 0;
    public void buildFromDB(boolean calcDiff, boolean fetchAllSTI) {

        loggerMaker.infoAndAddToDb("Started building from dB", LogDb.RUNTIME);
        boolean mergingCalled = false;

        demosAndDeactivatedCollections = UsageMetricCalculator.getDemosAndDeactivated();
        existingAPIsInDb = BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 1_000_000, 0.001 );

        if (mergeAsyncOutside && fetchAllSTI ) {
            if (Context.now() - lastMergeAsyncOutsideTs > 600) {
                loggerMaker.infoAndAddToDb("Started mergeAsyncOutside", LogDb.RUNTIME);
                this.lastMergeAsyncOutsideTs = Context.now();

                boolean gotDibs = Cluster.callDibs(Cluster.RUNTIME_MERGER, 1800, 60);
                if (gotDibs) {
                    loggerMaker.infoAndAddToDb("Got dibs", LogDb.RUNTIME);
                    mergingCalled = true;
                    BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
                    if (backwardCompatibility == null || backwardCompatibility.getMergeOnHostInit() == 0) {
                        loggerMaker.infoAndAddToDb("Merging hosts...", LogDb.RUNTIME);
                        new MergeOnHostOnly().mergeHosts();
                        Bson update = Updates.set(BackwardCompatibility.MERGE_ON_HOST_INIT, Context.now());
                        BackwardCompatibilityDao.instance.getMCollection().updateMany(new BasicDBObject(), update);
                        loggerMaker.infoAndAddToDb("Merging hosts completed", LogDb.RUNTIME);
                    }

                    try {
                        List<ApiCollection> allCollections = ApiCollectionsDao.instance.getMetaAll();
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        boolean makeApisCaseInsensitive = false;
                        boolean mergeUrlsOnVersions = false;
                        if(accountSettings != null){
                            makeApisCaseInsensitive = accountSettings.getHandleApisCaseInsensitive();
                            mergeUrlsOnVersions = accountSettings.isAllowMergingOnVersions();
                        }
                        
                        Boolean urlRegexMatchingEnabled = accountSettings == null || accountSettings.getUrlRegexMatchingEnabled();
                        loggerMaker.infoAndAddToDb("url regex matching enabled status is " + urlRegexMatchingEnabled, LogDb.RUNTIME);
                        for(ApiCollection apiCollection: allCollections) {
                            int start = Context.now();
                            loggerMaker.infoAndAddToDb("Started merging API collection " + apiCollection.getId(), LogDb.RUNTIME);
                            try {
                                mergeUrlsAndSave(apiCollection.getId(), true, true, existingAPIsInDb, makeApisCaseInsensitive, mergeUrlsOnVersions);
                                loggerMaker.infoAndAddToDb("Finished merging API collection basic " + apiCollection.getId() + " in " + (Context.now() - start) + " seconds", LogDb.RUNTIME);
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb(e.getMessage(),LogDb.RUNTIME);
                            }

                            try {
                                mergeUrlsAndSave(apiCollection.getId(), true, false, existingAPIsInDb, makeApisCaseInsensitive, mergeUrlsOnVersions);
                                loggerMaker.infoAndAddToDb("Finished merging API collection all" + apiCollection.getId() + " in " + (Context.now() - start) + " seconds", LogDb.RUNTIME);
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb(e.getMessage(),LogDb.RUNTIME);
                            }

                        }
                    } catch (Exception e) {
                        String err = e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : e.getMessage() ;
                        loggerMaker.errorAndAddToDb("error in mergeUrlsAndSave: " + err, LogDb.RUNTIME);
                        e.printStackTrace();
                    }

                    try {
                        loggerMaker.infoAndAddToDb("Started clearing values in db ", LogDb.RUNTIME);
                        clearValuesInDB();
                        loggerMaker.infoAndAddToDb("Finished clearing values in db ", LogDb.RUNTIME);
                    } catch (Exception e) {
                        loggerMaker.infoAndAddToDb("Error while clearing values in db: " + e.getMessage(), LogDb.RUNTIME);
                    }
                }
                loggerMaker.infoAndAddToDb("Finished mergeAsyncOutside", LogDb.RUNTIME);
            }
        }

        loggerMaker.infoAndAddToDb("Fetching STIs: " + fetchAllSTI, LogDb.RUNTIME);
        List<SingleTypeInfo> allParams;
        if (fetchAllSTI) {
            Bson filterForHostHeader = SingleTypeInfoDao.filterForHostHeader(-1,false);
            Bson filterQ = Filters.and(filterForHostHeader, Filters.regex(SingleTypeInfo._URL, "STRING|INTEGER"));
            allParams = SingleTypeInfoDao.instance.findAll(filterQ, Projections.exclude(SingleTypeInfo._VALUES));
            allParams.addAll(SingleTypeInfoDao.instance.findAll(new BasicDBObject(), Projections.exclude(SingleTypeInfo._VALUES)));

            int dependencyFlowLimit = 1_000;
            if (mergingCalled && allParams.size() < dependencyFlowLimit) {
                loggerMaker.infoAndAddToDb("ALl params less than " + dependencyFlowLimit +", running dependency flow", LogDb.RUNTIME);
                try {
                    DependencyFlow dependencyFlow = new DependencyFlow();
                    dependencyFlow.run(null);
                    dependencyFlow.syncWithDb();
                    loggerMaker.infoAndAddToDb("Finished running dependency flow", LogDb.RUNTIME);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while running dependency flow in runtime: " + e.getMessage(), LogDb.RUNTIME);
                }
            }

        } else {
            List<Integer> apiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();
            allParams = SingleTypeInfoDao.instance.fetchStiOfCollections(apiCollectionIds);
        }
        loggerMaker.infoAndAddToDb("Fetched STIs count: " + allParams.size(), LogDb.RUNTIME);
        this.dbState.clear();
        loggerMaker.infoAndAddToDb("Starting building dbState", LogDb.RUNTIME);
        this.dbState = build(allParams, existingAPIsInDb);
        loggerMaker.infoAndAddToDb("Done building dbState", LogDb.RUNTIME);
        this.sensitiveParamInfoBooleanMap = new HashMap<>();
        List<SensitiveParamInfo> sensitiveParamInfos = SensitiveParamInfoDao.instance.getUnsavedSensitiveParamInfos();
        loggerMaker.infoAndAddToDb("Done fetching sensitiveParamInfos", LogDb.RUNTIME);
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfos) {
            this.sensitiveParamInfoBooleanMap.put(sensitiveParamInfo, false);
        }

        if (mergeAsyncOutside) {
            this.delta = new HashMap<>();
        }

        List<YamlTemplate> advancedFilterTemplates = AdvancedTrafficFiltersDao.instance.findAll(Filters.ne(YamlTemplate.INACTIVE, true));
        advancedFilterMap = FilterYamlTemplateDao.instance.fetchFilterConfig(false, advancedFilterTemplates, true);
        try {
            // fetchAllSTI check added to make sure only runs in dashboard
            if (!fetchAllSTI) {
                loggerMaker.infoAndAddToDb("Started running update API collection count function", LogDb.RUNTIME);
                for(int collectionId: this.dbState.keySet()) {
                    APICatalog newCatalog = this.dbState.get(collectionId);
                    updateApiCollectionCount(newCatalog, collectionId);
                }
                loggerMaker.infoAndAddToDb("Finished running update API collection count function", LogDb.RUNTIME);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while filling urls in apiCollection: " + e.getMessage(), LogDb.RUNTIME);
        }

        mergedUrls = MergedUrlsDao.instance.getMergedUrls();

        loggerMaker.infoAndAddToDb("Building from db completed", LogDb.RUNTIME);
        aktoPolicyNew.buildFromDb(fetchAllSTI);
    }

    public static void updateApiCollectionCount(APICatalog apiCatalog, int apiCollectionId) {
        Set<String> newURLs = new HashSet<>();
        
        if (apiCatalog == null) {
            return;
        }

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
                loggerMaker.errorAndAddToDb("ERROR while parsing url param position: " + p, LogDb.RUNTIME);
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

    private static Set<Integer> demosAndDeactivatedCollections = UsageMetricCalculator.getDemosAndDeactivated();

    private static void fillExistingAPIsInDb(SingleTypeInfo sti, BloomFilter<CharSequence> existingAPIsInDb) {

        if(existingAPIsInDb==null){
            return;
        }

        if (demosAndDeactivatedCollections.contains(sti.getApiCollectionId())) {
            return;
        }
        String key = sti.composeApiInfoKey();
        if (key != null) {
            existingAPIsInDb.put(key);
        }
    }

    public static Map<Integer, APICatalog> build(List<SingleTypeInfo> allParams, BloomFilter<CharSequence> existingAPIsInDb) {
        Map<Integer, APICatalog> ret = new HashMap<>();
        
        for (SingleTypeInfo param: allParams) {
            try {
                buildHelper(param, ret);
                fillExistingAPIsInDb(param, existingAPIsInDb);
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("Error while building from db: " + e.getMessage(), LogDb.RUNTIME);
            }
        }

        return ret;
    }

    int counter = 0;
    List<String> partnerIpsList = new ArrayList<>();
    
    public void syncWithDB(boolean syncImmediately, boolean fetchAllSTI, SyncLimit apiSyncLimit, SyncLimit mcpAssetsSyncLimit, SyncLimit aiAssetsSyncLimit, HttpResponseParams.Source source) {
        loggerMaker.infoAndAddToDb("Started sync with db! syncImmediately="+syncImmediately + " fetchAllSTI="+fetchAllSTI, LogDb.RUNTIME);
        List<WriteModel<SingleTypeInfo>> writesForParams = new ArrayList<>();
        List<WriteModel<SensitiveSampleData>> writesForSensitiveSampleData = new ArrayList<>();
        List<WriteModel<TrafficInfo>> writesForTraffic = new ArrayList<>();
        List<WriteModel<SampleData>> writesForSampleData = new ArrayList<>();
        List<WriteModel<SensitiveParamInfo>> writesForSensitiveParamInfo = new ArrayList<>();
        Map<Integer, Boolean> apiCollectionToRedactPayload = new HashMap<>();
        Map<Integer, ApiCollection> apiCollectionMap = fetchAllApiCollection();
        for(ApiCollection apiCollection: apiCollectionMap.values()) {
            apiCollectionToRedactPayload.put(apiCollection.getId(), apiCollection.getRedact());
        }

        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());

        boolean redact = false;
        if (accountSettings != null) {
            redact = accountSettings.isRedactPayload() && source.equals(HttpResponseParams.Source.MIRRORING);
            if (accountSettings.getPartnerIpList() != null) {
                partnerIpsList = accountSettings.getPartnerIpList();
            }
        }

        counter++;
        for(int apiCollectionId: this.delta.keySet()) {
            APICatalog deltaCatalog = this.delta.get(apiCollectionId);

            Set<Integer> demosAndDeactivatedCollections = UsageMetricCalculator.getDemosAndDeactivated();

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
                            staticUrlIterator.remove();
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
                            templateUrlIterator.remove();
                        } else {
                            existingAPIsInDb.put(checkString);
                            deltaUsage++;
                        }
                    }
                }

                UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(syncLimitPair.getSecond(),
                    Context.accountId.get(), deltaUsage);
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
            DbUpdateReturn dbUpdateReturn = getDBUpdatesForParams(deltaCatalog, dbCatalog, redact, redactCollectionLevel, source);
            writesForParams.addAll(dbUpdateReturn.bulkUpdatesForSingleTypeInfo);
            writesForSensitiveSampleData.addAll(dbUpdateReturn.bulkUpdatesForSampleData);
            writesForSensitiveParamInfo.addAll(dbUpdateReturn.bulkUpdatesForSensitiveParamInfo);
            writesForTraffic.addAll(getDBUpdatesForTraffic(apiCollectionId, deltaCatalog));
            deltaCatalog.setDeletedInfo(new ArrayList<>());

            boolean forceUpdate = syncImmediately || counter % 10 == 0;
            writesForSampleData.addAll(getDBUpdatesForSampleData(apiCollectionId, deltaCatalog,dbCatalog, forceUpdate, redact, redactCollectionLevel));
        }

        loggerMaker.infoAndAddToDb("adding " + writesForParams.size() + " updates for params", LogDb.RUNTIME);
        int from = 0;
        int batch = 10000;

        long start = System.currentTimeMillis();
        if (writesForParams.size() >0) {
            do {

                List<WriteModel<SingleTypeInfo>> slicedWrites = writesForParams.subList(from, Math.min(from + batch, writesForParams.size()));
                from += batch;
                BulkWriteResult res =
                        SingleTypeInfoDao.instance.getMCollection().bulkWrite(
                                slicedWrites,
                                new BulkWriteOptions().ordered(true).bypassDocumentValidation(false)
                        );

                loggerMaker.infoAndAddToDb((System.currentTimeMillis() - start) + ": " + res.getInserts().size() + " " + res.getUpserts().size(), LogDb.RUNTIME);
            } while (from < writesForParams.size());
        }

        aktoPolicyNew.syncWithDb(source);

        loggerMaker.infoAndAddToDb("adding " + writesForTraffic.size() + " updates for traffic", LogDb.RUNTIME);
        if(writesForTraffic.size() > 0) {
            BulkWriteResult res = TrafficInfoDao.instance.getMCollection().bulkWrite(writesForTraffic);

            loggerMaker.infoAndAddToDb(res.getInserts().size() + " " +res.getUpserts().size(), LogDb.RUNTIME);

        }


        loggerMaker.infoAndAddToDb("adding " + writesForSampleData.size() + " updates for samples", LogDb.RUNTIME);
        if(writesForSampleData.size() > 0) {
            BulkWriteResult res = SampleDataDao.instance.getMCollection().bulkWrite(writesForSampleData);

            loggerMaker.infoAndAddToDb(res.getInserts().size() + " " +res.getUpserts().size(), LogDb.RUNTIME);

        }

        if (writesForSensitiveSampleData.size() > 0) {
            try {
                SensitiveSampleDataDao.instance.getMCollection().bulkWrite(writesForSensitiveSampleData);
                loggerMaker.infoAndAddToDb("successfully added " + writesForSensitiveSampleData.size() + " updates for sensitive sample data" , LogDb.RUNTIME);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "error while adding SensitiveSampleData",LogDb.RUNTIME);
            }
        }

        if (writesForSensitiveParamInfo.size() > 0) {
            try {
                SensitiveParamInfoDao.instance.getMCollection().bulkWrite(writesForSensitiveParamInfo);
                loggerMaker.infoAndAddToDb("successfully added " + writesForSensitiveParamInfo.size() + " updates for sensitive sample param info" , LogDb.RUNTIME);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "error while adding SensitiveParamInfo",LogDb.RUNTIME);
            }
        }

        loggerMaker.infoAndAddToDb("starting build from db inside syncWithDb", LogDb.RUNTIME);
        buildFromDB(true, fetchAllSTI);
        loggerMaker.infoAndAddToDb("Finished syncing with db", LogDb.RUNTIME);
    }

    public void printNewURLsInDelta(APICatalog deltaCatalog) {
        for(URLStatic s: deltaCatalog.getStrictURLToMethods().keySet()) {
            logger.info(s.getUrl());
        }

        for(URLTemplate s: deltaCatalog.getTemplateURLToMethods().keySet()) {
            logger.info(s.getTemplateString());
        }
    }

    private Map<Integer, ApiCollection> fetchAllApiCollection() {
        Map<Integer, ApiCollection> allCollections = new HashMap<>();
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        for(ApiCollection apiCollection: apiCollections) {
            allCollections.put(apiCollection.getId(), apiCollection);
        }
        return allCollections;
    }

    private Pair<SyncLimit, MetricTypes> getSyncLimitForApiCollection(ApiCollection apiCollection, SyncLimit apiSyncLimit,
        SyncLimit mcpAssetsSyncLimit, SyncLimit aiAssetsSyncLimit) {

        if (apiCollection == null) {
            return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
        }

        // For agentic billing: Use combined limit that respects both MCP and GenAI limits
        if (apiCollection.isMcpCollection()) {
            // Get combined limit that ensures both MCP and GenAI limits are respected
            SyncLimit combinedLimit = com.akto.billing.UsageMetricUtils.getCombinedAgenticSyncLimit(Context.accountId.get());
            if (combinedLimit.checkLimit) {
                return new Pair<>(combinedLimit, MetricTypes.MCP_ASSET_COUNT);
            }
            return new Pair<>(mcpAssetsSyncLimit, MetricTypes.MCP_ASSET_COUNT);
        }

        if (apiCollection.isGenAICollection()) {
            // Get combined limit that ensures both MCP and GenAI limits are respected
            SyncLimit combinedLimit = com.akto.billing.UsageMetricUtils.getCombinedAgenticSyncLimit(Context.accountId.get());
            if (combinedLimit.checkLimit) {
                return new Pair<>(combinedLimit, MetricTypes.AI_ASSET_COUNT);
            }
            return new Pair<>(aiAssetsSyncLimit, MetricTypes.AI_ASSET_COUNT);
        }

        return new Pair<>(apiSyncLimit, MetricTypes.ACTIVE_ENDPOINTS);
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

    public boolean isSkipMergingOnKnownStaticURLsForVersionedApis() {
        return skipMergingOnKnownStaticURLsForVersionedApis;
    }

    public void setSkipMergingOnKnownStaticURLsForVersionedApis(boolean skipMergingOnKnownStaticURLs) {
        this.skipMergingOnKnownStaticURLsForVersionedApis = skipMergingOnKnownStaticURLs;
    }

}
