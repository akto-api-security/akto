package com.akto.action;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.dependency_flow.TreeHelper;
import com.akto.dto.type.URLMethods;
import com.mongodb.client.model.Filters;

import java.util.Collection;
import java.util.List;

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

    List<Integer> apiCollectionIds;
    List<ApiInfo> apiInfoList;
    List<Node> nodes;
    public String buildDependencyTable() {
        this.apiInfoList = ApiInfoDao.instance.findApiInfos(apiCollectionIds);
        this.nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionIds);
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

    public List<ApiInfo> getApiInfoList() {
        return apiInfoList;
    }

    public List<Node> getNodes() {
        return nodes;
    }
}
