package com.akto.action;

import com.akto.dto.DependencyNode;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.dependency_flow.TreeHelper;
import com.akto.dto.type.URLMethods;

import java.util.Collection;

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


    public static boolean isParentNode(DependencyNode node, int apiCollectionId, String url, String method) {
        return (apiCollectionId+"#"+url+"#"+method).equals(node.getApiCollectionIdReq()+"#"+ node.getUrlReq()+"#"+node.getMethodReq());
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
}
