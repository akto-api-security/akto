package com.akto.action;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.dependency_flow.TreeHelper;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DependencyAction extends UserAction {

    private int apiCollectionId;
    private String url;
    private URLMethods.Method method;

    private Collection<Node> result;

    @Override
    public String execute()  {
        TreeHelper treeHelper = new TreeHelper();
        treeHelper.buildTree(apiCollectionId+"", url, method.name());
        this.result = treeHelper.result.values();
        return SUCCESS.toUpperCase();
    }

    private List<Integer> apiCollectionIds;
    private List<BasicDBObject> dependencyTableList;
    private int skip;


    public String buildDependencyTable() {
        List<Node> nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionIds, true, skip, 50);
        dependencyTableList = new ArrayList<>();

        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        for (Node node: nodes) {
            apiInfoKeys.add(new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod())));
        }
        Map<ApiInfo.ApiInfoKey, List<String>> parametersMap = SingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);

        for (Node node: nodes) {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod()));
            List<String> params = parametersMap.get(apiInfoKey);
            BasicDBObject res = new BasicDBObject("node", node);
            res.put("params", params);
            dependencyTableList.add(res);
        }

        return SUCCESS.toUpperCase();
    }

    public Collection<Node> getResult() {
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

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public List<BasicDBObject> getDependencyTableList() {
        return dependencyTableList;
    }
}
