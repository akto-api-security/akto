package com.akto.analyser;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.*;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.akto.types.CappedSet;
import com.akto.util.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.mongodb.BasicDBObject;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.*;

public class ResourceAnalyser {
    BloomFilter<CharSequence> duplicateCheckerBF;
    BloomFilter<CharSequence> valuesBF;
    Map<String, SingleTypeInfo> countMap = new HashMap<>();

    int last_sync = 0;

    private static final LoggerMaker loggerMaker = new LoggerMaker(ResourceAnalyser.class);

    public ResourceAnalyser(int duplicateCheckerBfSize, double duplicateCheckerBfFpp, int valuesBfSize, double valuesBfFpp) {
        duplicateCheckerBF = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), duplicateCheckerBfSize, duplicateCheckerBfFpp
        );

        valuesBF = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), valuesBfSize, valuesBfFpp
        );

        syncWithDb();
    }

    private static final String X_FORWARDED_FOR = "x-forwarded-for";

    public URLTemplate matchWithUrlTemplate(int apiCollectionId, String url, String method) {
        APICatalog catalog = catalogMap.get(apiCollectionId);
        if (catalog == null) return null;
        URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));
        for (URLTemplate urlTemplate: catalog.getTemplateURLToMethods().keySet()) {
            if (urlTemplate.match(urlStatic)) return urlTemplate;
        }
        return null;
    }

    public URLStatic matchWithUrlStatic(int apiCollectionId, String url, String method) {
        APICatalog catalog = catalogMap.get(apiCollectionId);
        if (catalog == null) return null;
        URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));

        Map<URLStatic, RequestTemplate> strictURLToMethods = catalog.getStrictURLToMethods();
        if (strictURLToMethods.containsKey(urlStatic)) return urlStatic;

        if (url.length() < 2) return null;

        List<String> urlVariations = new ArrayList<>();
        if (url.startsWith("/")) {
            urlVariations.add(url.substring(1));
            urlVariations.add(url.substring(1).toLowerCase());
        }
        urlVariations.add(url.toLowerCase());

        if (url.endsWith("/")) {
            urlVariations.add(url.substring(0, url.length()-1));
            urlVariations.add(url.substring(0, url.length()-1).toLowerCase());
        }

        for (String modifiedUrl: urlVariations) {
            URLStatic urlStaticModified = new URLStatic(modifiedUrl, URLMethods.Method.valueOf(method));
            if (strictURLToMethods.containsKey(urlStaticModified)) {
                return urlStaticModified;
            };
        }

        return null;
    }

    private final Set<String> hostsSeen = new HashSet<>();


    public void analyse(HttpResponseParams responseParams) {
        if (responseParams.statusCode < 200 || responseParams.statusCode >= 300) return;

        if (countMap.keySet().size() > 200_000 || (Context.now() - last_sync) > 120) {
            syncWithDb();
        }

        HttpRequestParams requestParams = responseParams.getRequestParams();
        String urlWithParams = requestParams.getURL();

        // user id
        Map<String,List<String>> headers = requestParams.getHeaders();
        if (headers == null) {
            loggerMaker.infoAndAddToDb("No headers", LoggerMaker.LogDb.ANALYSER);
            return;
        }

        List<String> ipList = headers.get(X_FORWARDED_FOR);
        if (ipList == null || ipList.isEmpty()) {
            loggerMaker.infoAndAddToDb("IP not found: " + headers.keySet(), LoggerMaker.LogDb.ANALYSER);
            return;
        }
        String userId = ipList.get(0);

        // get actual api collection id
        Integer apiCollectionId = requestParams.getApiCollectionId();
        String hostName = HttpCallParser.getHeaderValue(requestParams.getHeaders(), "host");
        apiCollectionId = findTrueApiCollectionId(apiCollectionId, hostName, responseParams.getSource());

        if (hostName != null) hostsSeen.add(hostName);

        if (apiCollectionId == null) {
            loggerMaker.infoAndAddToDb("API collection not found: " + apiCollectionId + " " + hostName + " " + responseParams.getSource(), LoggerMaker.LogDb.ANALYSER);
            return;
        }

        String method = requestParams.getMethod();

        // get actual url (without any query params)
        URLStatic urlStatic = URLAggregator.getBaseURL(requestParams.getURL(), method);
        String baseUrl = urlStatic.getUrl();
        String url = baseUrl;

        // there is a bug in runtime because of which some static URLs don't have leading slash
        // this checks and returns the actual url to be used
        // if we don't find it then check in templates
        // It is possible we don't find it still because of sync of runtime happening after analyser receiving data
        // in that case we still update (assuming leading slash bug doesn't exist)
        // if by the time update is made db has that url then good else update will fail as upsert is false
        URLTemplate urlTemplate = null;
        URLStatic urlStaticFromDb = matchWithUrlStatic(apiCollectionId, url, method);
        if (urlStaticFromDb != null) {
            url = urlStaticFromDb.getUrl();
        } else {
            // URLs received by api analyser are raw urls (i.e. not templatised)
            // So checking if it can be merged with any existing template URLs from db
            urlTemplate = matchWithUrlTemplate(apiCollectionId, url, method);
            if (urlTemplate != null) {
                url = urlTemplate.getTemplateString();
            }
        }

        String combinedUrl = apiCollectionId + "#" + url + "#" + method;

        // different URL variables and corresponding examples. Use accordingly
        // urlWithParams : /api/books/2?user=User1
        // baseUrl: /api/books/2
        // url: api/books/INTEGER

        // analyse url params
        if (urlTemplate != null) {
            String[] tokens = APICatalogSync.tokenize(baseUrl); // tokenize only the base url
            SingleTypeInfo.SuperType[] types = urlTemplate.getTypes();
            int size = tokens.length;
            for (int idx=0; idx < size; idx++) {
                SingleTypeInfo.SuperType type = types[idx];
                String value = tokens[idx];
                if (type != null) { // only analyse the INTEGER/STRING part of the url
                    analysePayload(value, idx+"", combinedUrl, userId, url, method, -1,
                            apiCollectionId, false, true);
                }
            }
        }

        // analyse request payload
        BasicDBObject payload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams); // using urlWithParams to extract any query parameters
        Map<String, Set<Object>> flattened = JSONUtils.flatten(payload);
        for (String param: flattened.keySet()) {
            for (Object val: flattened.get(param) ) {
                analysePayload(val, param, combinedUrl, userId, url,
                        method, -1, apiCollectionId, false, false);
            }
        }

        // analyse request headers
//        Map<String, List<String>> requestHeaders = requestParams.getHeaders();
//        for (String headerName: requestHeaders.keySet()) {
//            if (StandardHeaders.isStandardHeader(headerName)) continue;
//            List<String> headerValues = requestHeaders.get(headerName);
//            if (headerValues == null) {
//                headerValues = Collections.singletonList("null");
//            }
//            for (String headerValue: headerValues) {
//                analysePayload(headerValue, headerName, combinedUrl, userId, url,
//                        method, -1, apiCollectionId, true, false);
//            }
//        }
    }


    public void analysePayload(Object paramObject, String param, String combinedUrl, String userId,
                               String url, String method, int statusCode, int apiCollectionId, boolean isHeader,
                               boolean isUrlParam) {
        String paramValue = convertToParamValue(paramObject);
        if (paramValue == null) return ;

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method, statusCode, isHeader, param, SingleTypeInfo.GENERIC, apiCollectionId, isUrlParam
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0 , 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        // check if moved
        boolean moved = checkIfMoved(combinedUrl, param, paramValue);
        if (moved) return;

        // check if duplicate
        boolean isNew = checkDuplicate(userId, combinedUrl,param, paramValue);
        if (!isNew) return;

        // check if present
        boolean present = checkIfPresent(combinedUrl, param, paramValue);
        SingleTypeInfo singleTypeInfo1 = countMap.computeIfAbsent(singleTypeInfo.composeKey(), k -> singleTypeInfo);
        if (present) {
            markMoved(combinedUrl, param, paramValue);
            singleTypeInfo1.incPublicCount(1);
        } else {
            addToValueBF(combinedUrl, param, paramValue);
            singleTypeInfo1.incUniqueCount(1);
        }
    }



    public Map<Integer, APICatalog> catalogMap = new HashMap<>();

    public void buildCatalog() {
        List<ApiInfo.ApiInfoKey> apis = SingleTypeInfoDao.instance.fetchEndpointsInCollection(-1);
        loggerMaker.infoAndAddToDb("APIs fetched from db: " + apis.size(), LoggerMaker.LogDb.ANALYSER);

        for (ApiInfo.ApiInfoKey apiInfoKey: apis) {

            int apiCollectionId = apiInfoKey.getApiCollectionId();
            String url = apiInfoKey.getUrl();
            String method = apiInfoKey.getMethod().name();

            APICatalog catalog = catalogMap.get(apiCollectionId);
            if (catalog == null) {
                catalog = new APICatalog(0, new HashMap<>(), new HashMap<>());
                catalogMap.put(apiCollectionId, catalog);
            }

            Map<URLTemplate,RequestTemplate> urlTemplates = catalog.getTemplateURLToMethods();
            Map<URLStatic, RequestTemplate> strictUrls = catalog.getStrictURLToMethods();

            if (APICatalog.isTemplateUrl(url)) {
                URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url, URLMethods.Method.valueOf(method));
                urlTemplates.put(urlTemplate, null);
            } else {
                URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));
                strictUrls.put(urlStatic, null);
            }
        }

    }


    public void syncWithDb() {
        loggerMaker.infoAndAddToDb("Hosts seen till now: " + hostsSeen, LoggerMaker.LogDb.ANALYSER);

        buildCatalog();
        populateHostNameToIdMap();

        List<WriteModel<SingleTypeInfo>> dbUpdates = getDbUpdatesForSingleTypeInfo();
        loggerMaker.infoAndAddToDb("total db updates count: " + dbUpdates.size(), LoggerMaker.LogDb.ANALYSER);
        countMap = new HashMap<>();
        last_sync = Context.now();
        if (dbUpdates.size() > 0) {
            BulkWriteResult bulkWriteResult = SingleTypeInfoDao.instance.getMCollection().bulkWrite(dbUpdates);
            loggerMaker.infoAndAddToDb("bulkWriteResult: " + bulkWriteResult, LoggerMaker.LogDb.ANALYSER);
        }
    }

    public List<WriteModel<SingleTypeInfo>> getDbUpdatesForSingleTypeInfo() {
        List<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        loggerMaker.infoAndAddToDb("countMap keySet size: " + countMap.size(), LoggerMaker.LogDb.ANALYSER);

        for (SingleTypeInfo singleTypeInfo: countMap.values()) {
            if (singleTypeInfo.getUniqueCount() == 0 && singleTypeInfo.getPublicCount() == 0) continue;
            Bson filter = SingleTypeInfoDao.createFiltersWithoutSubType(singleTypeInfo);
            Bson update = Updates.combine(
                    Updates.inc(SingleTypeInfo._UNIQUE_COUNT, singleTypeInfo.getUniqueCount()),
                    Updates.inc(SingleTypeInfo._PUBLIC_COUNT, singleTypeInfo.getPublicCount())
            );
            bulkUpdates.add(new UpdateManyModel<>(filter, update, new UpdateOptions().upsert(false)));
        }

        int i = bulkUpdates.size();
        int total = countMap.values().size();

        loggerMaker.infoAndAddToDb("bulkUpdates: " + i + " total countMap size: " + total, LoggerMaker.LogDb.ANALYSER);

        return bulkUpdates;
    }

    public boolean checkDuplicate(String userId, String combinedUrl, String paramName, String paramValue) {
        String a = userId + "$" + combinedUrl + "$" + paramName + "$" + paramValue;
        return duplicateCheckerBF.put(a);
    }

    public boolean checkIfMoved(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue + "$moved";
        return valuesBF.mightContain(a);
    }

    public void markMoved(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue + "$moved";
        valuesBF.put(a);
    }

    public boolean checkIfPresent(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue;
        return valuesBF.mightContain(a);
    }

    public void addToValueBF(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue;
        valuesBF.put(a);
    }

    public String convertToParamValue(Object value) {
        if (value == null) return "null";
        return value.toString();
    }

    private Map<String, Integer> hostNameToIdMap = new HashMap<>();

    public Integer findTrueApiCollectionId(int originalApiCollectionId, String hostName, HttpResponseParams.Source source) {

        if (!HttpCallParser.useHostCondition(hostName, source)) {
            return originalApiCollectionId;
        }

        String key = hostName + "$" + originalApiCollectionId;
        Integer trueApiCollectionId = null;

        if (hostNameToIdMap.containsKey(key)) {
            trueApiCollectionId = hostNameToIdMap.get(key);

        } else if (hostNameToIdMap.containsKey(hostName + "$0")) {
            trueApiCollectionId = hostNameToIdMap.get(hostName + "$0");

        }

        // todo: what if we don't find because of cycles

        return trueApiCollectionId;
    }

    public void populateHostNameToIdMap() {
        hostNameToIdMap = new HashMap<>();
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        for (ApiCollection apiCollection: apiCollectionList) {
            String key = apiCollection.getHostName() + "$" + apiCollection.getVxlanId();
            hostNameToIdMap.put(key, apiCollection.getId());
        }
        loggerMaker.infoAndAddToDb("hostNameToIdMap: " + hostNameToIdMap, LoggerMaker.LogDb.ANALYSER);
    }


}



