package com.akto.action;

import com.akto.dao.DependencyNodeDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.DependencyNode;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DependencyAction extends UserAction {

    private int apiCollectionId;
    private String url;
    private URLMethods.Method method;

    private List<BasicDBObject> result;

    @Override
    public String execute()  {
        result = new ArrayList<>();
        List<DependencyNode> nodes = DependencyNodeDao.fetchParentAndChildrenNodes(apiCollectionId, url, method);

        List<ApiInfo.ApiInfoKey> apiInfoKeysParent = new ArrayList<>();
        List<ApiInfo.ApiInfoKey> apiInfoKeysChildren = new ArrayList<>();
        for (DependencyNode node : nodes) {
            if (isParentNode(node, apiCollectionId, url, method.name())) {
                apiInfoKeysParent.add(new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionIdResp()), node.getUrlResp(), URLMethods.Method.valueOf(node.getMethodResp())));
            } else {
                apiInfoKeysChildren.add(new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionIdReq()), node.getUrlReq(), URLMethods.Method.valueOf(node.getMethodReq())));
            }
        }

        Map<ApiInfo.ApiInfoKey, List<DependencyNode>> parentNodes = DependencyNodeDao.fetchDependentNodes(apiInfoKeysParent, true);
        Map<ApiInfo.ApiInfoKey, List<DependencyNode>> childrenNodes = DependencyNodeDao.fetchDependentNodes(apiInfoKeysChildren, false);

        for (DependencyNode node: nodes) {
            BasicDBObject basicDBObject = new BasicDBObject();
            basicDBObject.put("node", node);
            int count = 0;
            if (isParentNode(node, apiCollectionId, url, method.name())) {
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionIdResp()), node.getUrlResp(), URLMethods.Method.valueOf(node.getMethodResp()));
                count = parentNodes.getOrDefault(apiInfoKey, new ArrayList<>()).size();
            } else {
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionIdReq()), node.getUrlReq(), URLMethods.Method.valueOf(node.getMethodReq()));
                count = childrenNodes.getOrDefault(apiInfoKey, new ArrayList<>()).size();
            }
            basicDBObject.put("nodeInfo", new BasicDBObject("dependents", count));
            result.add(basicDBObject);
        }

        return SUCCESS.toUpperCase();
    }



    public static boolean isParentNode(DependencyNode node, int apiCollectionId, String url, String method) {
        return (apiCollectionId+"#"+url+"#"+method).equals(node.getApiCollectionIdReq()+"#"+ node.getUrlReq()+"#"+node.getMethodReq());
    }

    public List<BasicDBObject> getResult() {
        return result;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(URLMethods.Method method) {
        this.method = method;
    }
}
