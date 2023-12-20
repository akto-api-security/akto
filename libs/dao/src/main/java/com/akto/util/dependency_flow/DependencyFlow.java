package com.akto.util.dependency_flow;

import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.mongodb.BasicDBObject;

import java.util.*;

public class DependencyFlow {

    public Map<Integer, Node> initialNodes = new HashMap<>();
    public Map<Integer, Node> resultNodes = new HashMap<>();

    public void run() {
        List<DependencyNode> dependencyNodeList = DependencyNodeDao.instance.findAll(new BasicDBObject());

        for (DependencyNode dependencyNode: dependencyNodeList) {
            fillNodes(dependencyNode);
            fillResultNodes(dependencyNode);
        }

        // build initial queue
        // depth 0 for initial queue
        Queue<String> queue = new LinkedList<>();
        Set<Integer> done = new HashSet<>();

        for (Node node: initialNodes.values()) {
            int id = node.hashCode();
            if (resultNodes.containsKey(id)) continue;
            queue.add(0 + "#" + id);
        }

        // pop queue until empty
        while (!queue.isEmpty()) {
            String combined = queue.remove();
            int depth = Integer.parseInt(combined.split("#")[0]);
            int nodeId = Integer.parseInt(combined.split("#")[1]);
            Node node = initialNodes.get(nodeId);
            if (node == null || node.connections == null) {
                System.out.println("Dead end");
                continue;
            }
            Map<String, Connection> connections = node.connections;
            for (Connection connection: connections.values()) {
                for (Edge edge: connection.edges) {
                    Node resultNode = resultNodes.get(Objects.hash(edge.apiCollectionId, edge.url, edge.method));
                    Edge reverseEdge = new Edge(node.apiCollectionId, node.url, node.method, connection.param, edge.count, depth + 1);
                    Connection reverseConnection = resultNode.connections.get(edge.param);
                    if (reverseConnection == null) {
                        reverseConnection = new Connection(edge.param, new ArrayList<>());
                        resultNode.connections.put(edge.param, reverseConnection);
                    }
                    reverseConnection.edges.add(reverseEdge);

                    boolean flag = isDone(resultNode);
                    int key = resultNode.hashCode();
                    if (flag && !done.contains(key)) {
                        queue.add(depth+1 + "#" + key);
                        done.add(key);
                    }
                }
            }
        }
    }

    public static boolean isDone(Node node) {
        boolean flag = true;
        for (Connection c: node.connections.values()) {
            flag = flag && c.edges.size() > 0;
        }

        return flag;
    }

    public void fillNodes(DependencyNode dependencyNode) {
        Node node = new Node(
                dependencyNode.getApiCollectionIdResp(),
                dependencyNode.getUrlResp(),
                dependencyNode.getMethodResp(),
                new HashMap<>()
        );

        Integer key = node.hashCode();
        node = initialNodes.getOrDefault(key, node);

        for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
            String paramResp = paramInfo.getResponseParam();
            Connection connection = node.connections.getOrDefault(paramResp, new Connection(paramResp, new ArrayList<>()));

            String paramReq = paramInfo.getRequestParam();
            connection.edges.add(new Edge(
                    dependencyNode.getApiCollectionIdReq(), dependencyNode.getUrlReq(), dependencyNode.getMethodReq(),
                    paramReq, paramInfo.getCount(), -1
            ));

            node.connections.put(paramResp, connection);
        }

        initialNodes.put(key, node);
    }

    public void fillResultNodes(DependencyNode dependencyNode) {
        Node node = new Node(
                dependencyNode.getApiCollectionIdReq(),
                dependencyNode.getUrlReq(),
                dependencyNode.getMethodReq(),
                new HashMap<>()
        );

        Integer key = node.hashCode();
        node = resultNodes.getOrDefault(key, node);

        for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
            String paramReq = paramInfo.getRequestParam();
            Connection connection = new Connection(paramReq, new ArrayList<>());
            node.connections.put(paramReq, connection);
        }

        resultNodes.put(key, node);
    }

}
