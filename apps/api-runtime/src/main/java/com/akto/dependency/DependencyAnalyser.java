package com.akto.dependency;

import com.akto.dao.context.Context;
import com.akto.dependency.store.HashSetStore;
import com.akto.dependency.store.Store;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.*;
import com.akto.runtime.URLAggregator;
import com.akto.util.JSONUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;

public class DependencyAnalyser {
    Store valueStore; // this is to store all the values seen in response payload
    Store urlValueStore; // this is to store all the url$value seen in response payload
    Store urlParamValueStore; // this is to store all the url$param$value seen in response payload

    Map<String, Set<String>> urlsToResponseParam = new HashMap<>();

    Map<Integer, DependencyNode> nodes = new HashMap<>();
    public Map<Integer, APICatalog> dbState = new HashMap<>();


    public DependencyAnalyser(Map<Integer, APICatalog> dbState) {
        valueStore = new HashSetStore(10_000);
        urlValueStore= new HashSetStore(10_000);
        urlParamValueStore = new HashSetStore(10_000);
        this.dbState = dbState;
    }

    public void analyse(HttpResponseParams responseParams) {
        if (responseParams.statusCode < 200 || responseParams.statusCode >= 300) return;

        HttpRequestParams requestParams = responseParams.getRequestParams();
        String urlWithParams = requestParams.getURL();

        int apiCollectionId = requestParams.getApiCollectionId();
        String method = requestParams.getMethod();

        // get actual url (without any query params)
        URLStatic urlStatic = URLAggregator.getBaseURL(requestParams.getURL(), method);
        String url = urlStatic.getUrl();

        if (url.endsWith(".js") || url.endsWith(".png") || url.endsWith(".css") || url.endsWith(".jpeg") ||
                url.endsWith(".svg") || url.endsWith(".webp") || url.endsWith(".woff2")) return;

        // find real url. Real url is the one that is present in db. For example /api/books/1 is actually api/books/INTEGER
        url = realUrl(apiCollectionId, urlStatic).getUrl();

        String combinedUrl = apiCollectionId + "#" + url + "#" + method;

        // different URL variables and corresponding examples. Use accordingly
        // urlWithParams : /api/books/2?user=User1
        // url: api/books/INTEGER


        // Store response params in store
        String respPayload = responseParams.getPayload();
        if (respPayload == null || respPayload.isEmpty()) respPayload = "{}";
        if (respPayload.startsWith("[")) respPayload = "{\"json\": "+respPayload+"}";
        BasicDBObject respPayloadObj;
        try {
            respPayloadObj = BasicDBObject.parse(respPayload);
        } catch (Exception e) {
            respPayloadObj = BasicDBObject.parse("{}");
        }

        Map<String, Set<Object>> respFlattened = JSONUtils.flatten(respPayloadObj);

        Set<String> paramSet = urlsToResponseParam.getOrDefault(combinedUrl, new HashSet<>());
        for (String param: respFlattened.keySet()) {
            paramSet.add(param);
            for (Object val: respFlattened.get(param) ) {
                if (!filterValues(val)) continue;
                valueStore.add(val.toString());
                urlValueStore.add(combinedUrl + "$" + val);
                urlParamValueStore.add(combinedUrl + "$" + param + "$" + val);
            }
        }
        urlsToResponseParam.put(combinedUrl, paramSet);

        // Store url in Set

        // Check if request params in store
        //      a. Check if same value seen before
        //      b. Loop over previous urls and find which url had the value
        //      c. Loop over previous urls and params and find which param matches

        // analyse request payload
        BasicDBObject reqPayload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams); // using urlWithParams to extract any query parameters
        Map<String, Set<Object>> reqFlattened = JSONUtils.flatten(reqPayload);

        for (String requestParam: reqFlattened.keySet()) {
            processRequestParam(requestParam, reqFlattened.get(requestParam), combinedUrl);
        }
    }

    private void processRequestParam(String requestParam, Set<Object> reqFlattenedValuesSet, String originalCombinedUrl) {
        for (Object val : reqFlattenedValuesSet) {
            if (filterValues(val) && valueSeen(val)) {
                processValueForUrls(requestParam, val, originalCombinedUrl);
            }
        }
    }

    private void processValueForUrls(String requestParam, Object val, String originalCombinedUrl) {
        for (String url : urlsToResponseParam.keySet()) {
            if (!url.equals(originalCombinedUrl) && urlValSeen(url, val)) {
                processUrlForParam(url, requestParam, val, originalCombinedUrl);
            }
        }
    }

    private void processUrlForParam(String url, String requestParam, Object val, String originalCombinedUrl) {
        for (String responseParam : urlsToResponseParam.get(url)) {
            if (urlParamValueSeen(url, responseParam, val)) {
                updateNodesMap(url, responseParam, originalCombinedUrl, requestParam);
            }
        }
    }

    public URLStatic realUrl(int apiCollectionId, URLStatic urlStatic) {
        APICatalog apiCatalog = this.dbState.get(apiCollectionId);
        if (apiCatalog == null) return urlStatic;

        Map<URLStatic, RequestTemplate> strictURLToMethods = apiCatalog.getStrictURLToMethods();
        boolean strictUrlFound = strictURLToMethods != null && strictURLToMethods.containsKey(urlStatic);
        if (strictUrlFound) return urlStatic;

        Map<URLTemplate, RequestTemplate> templateURLToMethods = apiCatalog.getTemplateURLToMethods();
        if (templateURLToMethods == null) return urlStatic;
        for (URLTemplate urlTemplate: templateURLToMethods.keySet()) {
            boolean match = urlTemplate.match(urlStatic);
            if (match) {
                return new URLStatic(urlTemplate.getTemplateString(), urlStatic.getMethod());
            }
        }

        return urlStatic;
    }


    public boolean filterValues(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean) return false;
        if (val instanceof String) return val.toString().length() > 4 && val.toString().length() < 64;
        if (val instanceof Integer) return ((int) val) > 50;
        return true;
    }

    public boolean valueSeen(Object val) {
        return valueStore.contains(val.toString());
    }

    public boolean urlValSeen(String url, Object val) {
        return urlValueStore.contains(url + "$" + val.toString());
    }

    public boolean urlParamValueSeen(String url, String param, Object val) {
        return urlParamValueStore.contains(url + "$" + param + "$" + val.toString());
    }

    public void updateNodesMap(String combinedUrlResp, String paramResp, String combinedUrlReq, String paramReq) {
        String[] combinedUrlRespSplit = combinedUrlResp.split("#");
        String apiCollectionIdResp = combinedUrlRespSplit[0];
        String urlResp = combinedUrlRespSplit[1];
        String methodResp = combinedUrlRespSplit[2];

        String[] combinedUrlReqSplit = combinedUrlReq.split("#");
        String apiCollectionIdReq = combinedUrlReqSplit[0];
        String urlReq = combinedUrlReqSplit[1];
        String methodReq = combinedUrlReqSplit[2];

        DependencyNode.ParamInfo paramInfo = new DependencyNode.ParamInfo(paramReq, paramResp, 1);

        List<DependencyNode.ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        DependencyNode dependencyNode = new DependencyNode(
                apiCollectionIdResp, urlResp, methodResp, apiCollectionIdReq, urlReq, methodReq, paramInfos, Context.now()
        );

        DependencyNode n1 = nodes.get(dependencyNode.hashCode());
        if (n1 != null) {
            n1.updateOrCreateParamInfo(paramInfo);
        } else {
            n1 = dependencyNode;
        }

        nodes.put(dependencyNode.hashCode(), n1);
    }

    public void mergeNodes() {
        List<DependencyNode> toBeDeleted = new ArrayList<>();
        Map<Integer, DependencyNode> toBeAdded = new HashMap<>();

        for (DependencyNode dependencyNode: nodes.values()) {
            String urlResp = dependencyNode.getUrlResp();
            String apiCollectionIdResp = dependencyNode.getApiCollectionIdResp();
            String methodResp = dependencyNode.getMethodResp();
            String newUrlResp = urlResp;
            if (!APICatalog.isTemplateUrl(urlResp)) {
                 newUrlResp = realUrl(Integer.parseInt(apiCollectionIdResp), new URLStatic(urlResp, URLMethods.Method.valueOf(methodResp))).getUrl();
            }

            String urlReq = dependencyNode.getUrlReq();
            String apiCollectionIdReq = dependencyNode.getApiCollectionIdReq();
            String methodReq = dependencyNode.getMethodReq();
            String newUrlReq = urlReq;
            if (!APICatalog.isTemplateUrl(urlReq)) {
                newUrlReq = realUrl(Integer.parseInt(apiCollectionIdReq), new URLStatic(urlReq, URLMethods.Method.valueOf(methodReq))).getUrl();
            }

            // we try to check if any kind of merging happened or not
            // if yes fill the respective update lists
            if (!newUrlReq.equals(urlReq) || !newUrlResp.equals(urlResp)) {
                DependencyNode copy = dependencyNode.copy();
                copy.setUrlReq(newUrlReq);
                copy.setUrlResp(newUrlResp);

                toBeDeleted.add(dependencyNode);

                DependencyNode toBeAddedNode = toBeAdded.get(copy.hashCode());
                if (toBeAddedNode == null) {
                    toBeAddedNode = copy;
                } else {
                    toBeAddedNode.merge(copy);
                }

                toBeAdded.put(copy.hashCode(), toBeAddedNode);
            }
        }

        for (DependencyNode toBeDeletedNode: toBeDeleted ) {
            nodes.remove(toBeDeletedNode.hashCode());
        }

        for (DependencyNode toBeAddedNode: toBeAdded.values())  {
            int hashCode = toBeAddedNode.hashCode();
            DependencyNode node = nodes.get(hashCode);
            if (node == null) {
                nodes.put(hashCode,toBeAddedNode );
            } else {
                node.merge(toBeAddedNode);
            }
        }

    }

    public void syncWithDb() {
        ArrayList<WriteModel<DependencyNode>> bulkUpdates1 = new ArrayList<>();
        ArrayList<WriteModel<DependencyNode>> bulkUpdates2 = new ArrayList<>();
        ArrayList<WriteModel<DependencyNode>> bulkUpdates3 = new ArrayList<>();

        mergeNodes();

        for (DependencyNode dependencyNode: nodes.values()) {

            String urlResp = dependencyNode.getUrlResp();
            String apiCollectionIdResp = dependencyNode.getApiCollectionIdResp();
            String methodResp = dependencyNode.getMethodResp();

            String urlReq = dependencyNode.getUrlReq();
            String apiCollectionIdReq = dependencyNode.getApiCollectionIdReq();
            String methodReq = dependencyNode.getMethodReq();

            for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
                Bson filter1 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionIdReq),
                        Filters.eq(DependencyNode.URL_REQ, urlReq),
                        Filters.eq(DependencyNode.METHOD_REQ, methodReq),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionIdResp),
                        Filters.eq(DependencyNode.URL_RESP, urlResp),
                        Filters.eq(DependencyNode.METHOD_RESP, methodResp)
                );

                Bson update1 = Updates.push(DependencyNode.PARAM_INFOS, new Document("$each", Collections.emptyList()));

                // this update is to make sure the document exist else create new one
                UpdateOneModel<DependencyNode> updateOneModel1 = new UpdateOneModel<>(
                        filter1, update1, new UpdateOptions().upsert(true)
                );


                Bson filter2 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionIdReq),
                        Filters.eq(DependencyNode.URL_REQ, urlReq),
                        Filters.eq(DependencyNode.METHOD_REQ, methodReq),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionIdResp),
                        Filters.eq(DependencyNode.URL_RESP, urlResp),
                        Filters.eq(DependencyNode.METHOD_RESP, methodResp),
                        Filters.not(Filters.elemMatch(DependencyNode.PARAM_INFOS,
                                Filters.and(
                                        Filters.eq(DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam()),
                                        Filters.eq(DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam())
                                )
                        ))
                );

                Bson update2 = Updates.push(DependencyNode.PARAM_INFOS,
                        new BasicDBObject(DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam())
                                .append(DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam())
                                .append(DependencyNode.ParamInfo.COUNT, 0)
                );

                // this update is to add paramInfo if it doesn't exist. If exists nothing happens
                UpdateOneModel<DependencyNode> updateOneModel2 = new UpdateOneModel<>(
                        filter2, update2, new UpdateOptions().upsert(false)
                );

                Bson filter3 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionIdReq),
                        Filters.eq(DependencyNode.URL_REQ, urlReq),
                        Filters.eq(DependencyNode.METHOD_REQ, methodReq),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionIdResp),
                        Filters.eq(DependencyNode.URL_RESP, urlResp),
                        Filters.eq(DependencyNode.METHOD_RESP, methodResp),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam()),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam())
                );

                Bson update3 = Updates.combine(
                        Updates.inc(DependencyNode.PARAM_INFOS + ".$." + DependencyNode.ParamInfo.COUNT, paramInfo.getCount()),
                        Updates.set(DependencyNode.LAST_UPDATED, dependencyNode.getLastUpdated())
                );

                // this update runs everytime to update the count
                UpdateOneModel<DependencyNode> updateOneModel3 = new UpdateOneModel<>(
                        filter3, update3,  new UpdateOptions().upsert(false)
                );

                bulkUpdates1.add(updateOneModel1);
                bulkUpdates2.add(updateOneModel2);
                bulkUpdates3.add(updateOneModel3);
            }
        }

        // ordered has to be true or else won't work
        if (bulkUpdates1.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates1, new BulkWriteOptions().ordered(false));
        if (bulkUpdates2.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates2, new BulkWriteOptions().ordered(false));
        if (bulkUpdates3.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates3, new BulkWriteOptions().ordered(false));

        nodes = new HashMap<>();
    }

}
