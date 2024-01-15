package com.akto.dao;

import com.akto.dto.dependency_flow.Node;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;


public class DependencyFlowNodesDao extends AccountsContextDao<Node>{

    public static final DependencyFlowNodesDao instance = new DependencyFlowNodesDao();

    public List<Node> findNodesForCollectionIds(List<Integer> apiCollectionIds, boolean removeZeroLevel, int skip, int limit) {
        List<String> apiCollectionIdStrings = new ArrayList<>();
        for (Integer apiCollectionId: apiCollectionIds) {
            if (apiCollectionId == null) continue;
            apiCollectionIdStrings.add(apiCollectionId+"");
        }
        return instance.findNodesForCollectionIdString(apiCollectionIdStrings, removeZeroLevel, skip, limit);
    }

    public List<Node> findNodesForCollectionIdString(List<String> apiCollectionIdStrings, boolean removeZeroLevel, int skip, int limit) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.in("apiCollectionId", apiCollectionIdStrings));
        if (removeZeroLevel) filters.add(Filters.gt("maxDepth", 0));
        return instance.findAll(Filters.and(filters), skip, limit, Sorts.ascending("_id"));
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
