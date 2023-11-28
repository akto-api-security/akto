package com.akto.dao;

import com.akto.dto.ApiInfo;
import com.akto.dto.DependencyNode;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyNodeDao extends AccountsContextDao<DependencyNode>{

    public static final DependencyNodeDao instance = new DependencyNodeDao();

    @Override
    public String getCollName() {
        return "dependency_nodes";
    }

    @Override
    public Class<DependencyNode> getClassT() {
        return DependencyNode.class;
    }

    public static Bson generateFilter(DependencyNode dependencyNode) {
        return Filters.and(
                Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, dependencyNode.getApiCollectionIdResp()),
                Filters.eq(DependencyNode.URL_RESP, dependencyNode.getUrlResp()),
                Filters.eq(DependencyNode.METHOD_RESP, dependencyNode.getMethodResp()),
                Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, dependencyNode.getApiCollectionIdReq()),
                Filters.eq(DependencyNode.URL_REQ, dependencyNode.getUrlReq()),
                Filters.eq(DependencyNode.METHOD_REQ, dependencyNode.getMethodReq())
        );
    }

    public static Map<ApiInfo.ApiInfoKey, List<DependencyNode>> fetchDependentNodes(List<ApiInfo.ApiInfoKey> apiInfoKeys, boolean isParent) {
        Map<ApiInfo.ApiInfoKey, List<DependencyNode>> result = new HashMap<>();
        if (apiInfoKeys == null || apiInfoKeys.isEmpty()) return result ;

        String apiCollectionIdFilterKey = isParent ? DependencyNode.API_COLLECTION_ID_REQ : DependencyNode.API_COLLECTION_ID_RESP;
        String urlFilterKey = isParent ? DependencyNode.URL_REQ : DependencyNode.URL_RESP;
        String methodFilterKey = isParent ? DependencyNode.METHOD_REQ: DependencyNode.METHOD_RESP;

        List<Bson> filters = new ArrayList<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeys) {
            Bson filter= Filters.and(
                    Filters.eq(apiCollectionIdFilterKey,apiInfoKey.getApiCollectionId()+""),
                    Filters.eq(urlFilterKey, apiInfoKey.getUrl()),
                    Filters.eq(methodFilterKey, apiInfoKey.getMethod().name())
            );
            filters.add(filter);
        }

        List<DependencyNode> nodes = DependencyNodeDao.instance.findAll(Filters.or(filters));

        for (DependencyNode node: nodes) {
            String apiCollectionIdString = isParent ? node.getApiCollectionIdReq() : node.getApiCollectionIdResp();
            int apiCollectionId = Integer.parseInt(apiCollectionIdString);
            String url = isParent ? node.getUrlReq() : node.getUrlResp();
            String methodString = isParent ? node.getMethodReq() : node.getMethodResp();
            URLMethods.Method method = URLMethods.Method.valueOf(methodString);
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId,url,method);

            List<DependencyNode> dependencyNodes = result.getOrDefault(apiInfoKey, new ArrayList<>());
            dependencyNodes.add(node);
            result.put(apiInfoKey, dependencyNodes);
        }

        return result;
    }

    public static List<DependencyNode> fetchParentAndChildrenNodes(int apiCollectionId, String url, URLMethods.Method method) {
        Bson childrenFilter = Filters.and(
                Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionId+""),
                Filters.eq(DependencyNode.URL_RESP, url),
                Filters.eq(DependencyNode.METHOD_RESP, method.name())
        );

        Bson parentFilter  = Filters.and(
                Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionId+""),
                Filters.eq(DependencyNode.URL_REQ, url),
                Filters.eq(DependencyNode.METHOD_REQ, method.name())
        );

        return DependencyNodeDao.instance.findAll(
                Filters.or(parentFilter, childrenFilter)
        );

    }
}
