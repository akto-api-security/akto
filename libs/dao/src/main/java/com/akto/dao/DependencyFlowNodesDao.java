package com.akto.dao;

import com.akto.dto.dependency_flow.Node;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;


public class DependencyFlowNodesDao extends AccountsContextDao<Node>{

    public static final DependencyFlowNodesDao instance = new DependencyFlowNodesDao();

    public List<Node> findNodesForCollectionIds(List<Integer> apiCollectionIds) {
        List<String> apiCollectionIdStrings = new ArrayList<>();
        for (Integer apiCollectionId: apiCollectionIds) {
            if (apiCollectionId == null) continue;
            apiCollectionIdStrings.add(apiCollectionId+"");
        }
        return instance.findNodesForCollectionIdString(apiCollectionIdStrings);
    }

    public List<Node> findNodesForCollectionIdString(List<String> apiCollectionIdStrings) {
        return instance.findAll(Filters.in("apiCollectionId", apiCollectionIdStrings));
    }

    @Override
    public String getCollName() {
        return "dependency_flow_nodes";
    }

    @Override
    public Class<Node> getClassT() {
        return Node.class;
    }

}
