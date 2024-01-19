package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.dependency_flow.Node;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;


public class DependencyFlowNodesDao extends AccountsContextDao<Node>{

    public static final DependencyFlowNodesDao instance = new DependencyFlowNodesDao();

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

        String[] fieldNames = {Node._API_COLLECTION_ID, Node._URL, Node._METHOD};
        MCollection.createUniqueIndex(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{Node._API_COLLECTION_ID, Node._MAX_DEPTH};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

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
        filters.add(Filters.in(Node._API_COLLECTION_ID, apiCollectionIdStrings));
        if (removeZeroLevel) filters.add(Filters.gt(Node._MAX_DEPTH, 0));
        return instance.findAll(Filters.and(filters), skip, limit, Sorts.ascending("_id"));
    }

    public int findTotalNodesCount(List<Integer> apiCollectionIds, boolean removeZeroLevel) {
        List<String> apiCollectionIdStrings = new ArrayList<>();
        for (Integer apiCollectionId: apiCollectionIds) {
            if (apiCollectionId == null) continue;
            apiCollectionIdStrings.add(apiCollectionId+"");
        }

        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.in(Node._API_COLLECTION_ID, apiCollectionIdStrings));
        if (removeZeroLevel) filters.add(Filters.gt(Node._MAX_DEPTH, 0));
        return (int) instance.count(Filters.and(filters));
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
