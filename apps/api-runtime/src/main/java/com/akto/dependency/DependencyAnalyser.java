package com.akto.dependency;

import com.akto.dependency.store.HashSetStore;
import com.akto.dependency.store.Store;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.URLStatic;
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


    public DependencyAnalyser() {
        valueStore = new HashSetStore(10_000);
        urlValueStore= new HashSetStore(10_000);
        urlParamValueStore = new HashSetStore(10_000);
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

        String combinedUrl = apiCollectionId + "#" + url + "#" + method;

        // different URL variables and corresponding examples. Use accordingly
        // urlWithParams : /api/books/2?user=User1
        // url: api/books/INTEGER


        // Store response params in store
        //    1. filter values
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
        for (String param: reqFlattened.keySet()) {
            for (Object val: reqFlattened.get(param) ) {
                if (!filterValues(val)) continue;
                if (!valueSeen(val)) continue;
                for (String x: urlsToResponseParam.keySet())  {
                    if (x.equals(combinedUrl)) continue; // don't match with same API
                    if (!urlValSeen(x, val)) continue;
                    for (String y: urlsToResponseParam.get(x)) {
                        if (!urlParamValueSeen(x, y, val)) continue;
                        add(x, y, combinedUrl, param);
                    }
                }
            }
        }

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

    public void add(String combinedUrlResp, String paramResp, String combinedUrlReq, String paramReq) {
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
                apiCollectionIdResp, urlResp, methodResp, apiCollectionIdReq, urlReq, methodReq, paramInfos
        );

        DependencyNode n1 = nodes.get(dependencyNode.hashCode());
        if (n1 != null) {
            n1.updateOrCreateParamInfo(paramInfo);
        } else {
            n1 = dependencyNode;
        }

        nodes.put(dependencyNode.hashCode(), n1);
    }

    public void syncWithDb() {
        ArrayList<WriteModel<DependencyNode>> bulkUpdates = new ArrayList<>();
        for (DependencyNode dependencyNode: nodes.values()) {
            for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
                Bson filter1 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, dependencyNode.getApiCollectionIdReq()),
                        Filters.eq(DependencyNode.URL_REQ, dependencyNode.getUrlReq()),
                        Filters.eq(DependencyNode.METHOD_REQ, dependencyNode.getMethodReq()),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, dependencyNode.getApiCollectionIdResp()),
                        Filters.eq(DependencyNode.URL_RESP, dependencyNode.getUrlResp()),
                        Filters.eq(DependencyNode.METHOD_RESP, dependencyNode.getMethodResp())
                );

                Bson update1 = Updates.push(DependencyNode.PARAM_INFOS, new Document("$each", Collections.emptyList()));

                // this update is to make sure the document exist else create new one
                UpdateOneModel<DependencyNode> updateOneModel1 = new UpdateOneModel<>(
                        filter1, update1, new UpdateOptions().upsert(true)
                );


                Bson filter2 = Filters.and(
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, dependencyNode.getApiCollectionIdReq()),
                        Filters.eq(DependencyNode.URL_REQ, dependencyNode.getUrlReq()),
                        Filters.eq(DependencyNode.METHOD_REQ, dependencyNode.getMethodReq()),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, dependencyNode.getApiCollectionIdResp()),
                        Filters.eq(DependencyNode.URL_RESP, dependencyNode.getUrlResp()),
                        Filters.eq(DependencyNode.METHOD_RESP, dependencyNode.getMethodResp()),
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
                        Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, dependencyNode.getApiCollectionIdReq()),
                        Filters.eq(DependencyNode.URL_REQ, dependencyNode.getUrlReq()),
                        Filters.eq(DependencyNode.METHOD_REQ, dependencyNode.getMethodReq()),
                        Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, dependencyNode.getApiCollectionIdResp()),
                        Filters.eq(DependencyNode.URL_RESP, dependencyNode.getUrlResp()),
                        Filters.eq(DependencyNode.METHOD_RESP, dependencyNode.getMethodResp()),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.REQUEST_PARAM, paramInfo.getRequestParam()),
                        Filters.eq(DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.RESPONSE_PARAM, paramInfo.getResponseParam())
                );

                Bson update3 = Updates.inc(DependencyNode.PARAM_INFOS + ".$." + DependencyNode.ParamInfo.COUNT, paramInfo.getCount());

                // this update runs everytime to update the count
                UpdateOneModel<DependencyNode> updateOneModel3 = new UpdateOneModel<>(
                        filter3, update3,  new UpdateOptions().upsert(false)
                );

                bulkUpdates.add(updateOneModel1);
                bulkUpdates.add(updateOneModel2);
                bulkUpdates.add(updateOneModel3);

            }
        }

        // ordered has to be true or else won't work
        if (bulkUpdates.size() > 0) DependencyNodeDao.instance.getMCollection().bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(true));

        nodes = new HashMap<>();
    }

}
