package com.akto.action;

import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.ReplaceDetailsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.dependency_flow.KVPair;
import com.akto.dto.dependency_flow.Node;
import com.akto.dto.dependency_flow.ReplaceDetail;
import com.akto.dto.dependency_flow.TreeHelper;
import com.akto.dto.type.URLMethods;
import com.akto.utils.Build;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.*;

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
    private List<ReplaceDetail> replaceDetails;
    private int total;
    private int skip;


    public String buildDependencyTable() {
        List<Node> nodes = DependencyFlowNodesDao.instance.findNodesForCollectionIds(apiCollectionIds, true, skip, 50);
        dependencyTableList = new ArrayList<>();

        List<ApiInfo.ApiInfoKey> apiInfoKeys = new ArrayList<>();
        for (Node node: nodes) {
            apiInfoKeys.add(new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod())));
        }
        Map<ApiInfo.ApiInfoKey, List<String>> parametersMap = SingleTypeInfoDao.instance.fetchRequestParameters(apiInfoKeys);

        replaceDetails = ReplaceDetailsDao.instance.findAll(Filters.in(ReplaceDetail._API_COLLECTION_ID, apiCollectionIds));

        for (Node node: nodes) {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(Integer.parseInt(node.getApiCollectionId()), node.getUrl(), URLMethods.Method.fromString(node.getMethod()));
            List<String> params = parametersMap.get(apiInfoKey);
            BasicDBObject res = new BasicDBObject("node", node);
            res.put("params", params);
            dependencyTableList.add(res);
        }

        this.total = DependencyFlowNodesDao.instance.findTotalNodesCount(apiCollectionIds, true);
        return SUCCESS.toUpperCase();
    }

    private List<Build.RunResult> runResults;
    public String invokeDependencyTable() {
        Build build = new Build();
        runResults = build.run(Collections.singletonList(1705668952), new HashMap<>(), new HashMap<>());
        for (Build.RunResult runResult: runResults) {
            runResult.setCurrentMessage("");;
            runResult.setOriginalMessage("");
        }
        return SUCCESS.toUpperCase();
    }

    List<KVPair> kvPairs = new ArrayList<>();

    public String saveReplaceDetails() {
        if (url == null || url.isEmpty() || method == null ) {
            addActionError("Invalid url or method");
            return ERROR.toUpperCase();
        }

        ReplaceDetailsDao.instance.updateOne(
                Filters.and(
                        Filters.eq(ReplaceDetail._API_COLLECTION_ID, apiCollectionId),
                        Filters.eq(ReplaceDetail._URL, url),
                        Filters.eq(ReplaceDetail._METHOD, method.name())
                ),
                Updates.set(ReplaceDetail._KV_PAIRS, kvPairs)
        );

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

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public int getTotal() {
        return total;
    }


    public List<Build.RunResult> getRunResults() {
        return runResults;
    }

    public void setKvPairs(List<KVPair> kvPairs) {
        this.kvPairs = kvPairs;
    }

    public List<ReplaceDetail> getReplaceDetails() {
        return replaceDetails;
    }

    
}
