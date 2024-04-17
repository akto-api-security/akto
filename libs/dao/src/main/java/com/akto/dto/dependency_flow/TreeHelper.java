package com.akto.dto.dependency_flow;

import com.akto.dao.DependencyFlowNodesDao;
import com.mongodb.client.model.Filters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TreeHelper {
    public Map<Integer, Node> result = new HashMap<>();
    public int nodeCount = 0;
    private final int MAX_NODE_COUNT = 10;

    public TreeHelper() {
        this.result = new HashMap<>();
        this.nodeCount = 0;
    }


    public void buildTree(String apiCollectionId, String url, String method) {

        if (result.containsKey(Objects.hash(apiCollectionId, url, method))) return;

        nodeCount += 1;
        if (nodeCount > MAX_NODE_COUNT) return;

        Node node = DependencyFlowNodesDao.instance.findOne(
                Filters.and(
                        Filters.eq("apiCollectionId", apiCollectionId),
                        Filters.eq("url", url),
                        Filters.eq("method", method)
                )
        );

        if (node == null) return;

        result.put(node.hashCode(), node);

        if (node.getMaxDepth() == 0) return;


        Map<String, Connection> connections = node.getConnections();
        for (Connection connection: connections.values()) {
            List<Edge> edges = connection.getEdges();
            if (edges.isEmpty()) continue;
            Edge edge = edges.get(0); // get the first edge because it is guaranteed to have the least depth
            int depth = edge.getDepth();
            if (depth > 10) continue; // todo: handle this

            buildTree(edge.getApiCollectionId(), edge.getUrl(), edge.getMethod());
        }
    }
}
