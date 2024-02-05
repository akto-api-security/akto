package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.DependencyNode;
import com.akto.dto.type.URLMethods;
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


    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {
                DependencyNode.API_COLLECTION_ID_RESP, DependencyNode.URL_RESP, DependencyNode.METHOD_RESP,
                DependencyNode.API_COLLECTION_ID_REQ, DependencyNode.URL_REQ, DependencyNode.METHOD_REQ,
        };
        MCollection.createUniqueIndex(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{
                DependencyNode.API_COLLECTION_ID_RESP, DependencyNode.URL_RESP, DependencyNode.METHOD_RESP,
                DependencyNode.API_COLLECTION_ID_REQ, DependencyNode.URL_REQ, DependencyNode.METHOD_REQ,
                DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.REQUEST_PARAM,
                DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.RESPONSE_PARAM,
                DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.IS_URL_PARAM,
                DependencyNode.PARAM_INFOS + "." + DependencyNode.ParamInfo.IS_HEADER,
        };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{DependencyNode.LAST_UPDATED};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
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

    public static Bson generateParentsFilter(int apiCollectionId, String url, URLMethods.Method method) {
        return Filters.and(
                Filters.eq(DependencyNode.API_COLLECTION_ID_REQ, apiCollectionId+""),
                Filters.eq(DependencyNode.URL_REQ, url),
                Filters.eq(DependencyNode.METHOD_REQ, method.name())
        );
    }

    public static Bson generateChildrenFilter(int apiCollectionId, String url, URLMethods.Method method) {
        return Filters.and(
                Filters.eq(DependencyNode.API_COLLECTION_ID_RESP, apiCollectionId+""),
                Filters.eq(DependencyNode.URL_RESP, url),
                Filters.eq(DependencyNode.METHOD_RESP, method.name())
        );
    }

    public static List<DependencyNode> fetchParentAndChildrenNodes(int apiCollectionId, String url, URLMethods.Method method) {
        Bson childrenFilter = generateChildrenFilter(apiCollectionId, url, method);
        Bson parentFilter  = generateParentsFilter(apiCollectionId, url, method);

        return DependencyNodeDao.instance.findAll(
                Filters.or(parentFilter, childrenFilter)
        );

    }

    public List<DependencyNode> findNodesForCollectionIds(List<Integer> apiCollectionIds) {
        List<String> stringApiCollectionIds = new ArrayList<>();
        for (Integer apiCollectionId: apiCollectionIds) {
            if (apiCollectionId != null) stringApiCollectionIds.add(apiCollectionId+"");
        }
        return instance.findAll(
                Filters.or(
                        Filters.in(DependencyNode.API_COLLECTION_ID_REQ, stringApiCollectionIds),
                        Filters.in(DependencyNode.API_COLLECTION_ID_RESP, stringApiCollectionIds)
                )
        );
    }
}
