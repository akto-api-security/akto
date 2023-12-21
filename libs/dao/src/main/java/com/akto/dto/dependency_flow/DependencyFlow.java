package com.akto.dto.dependency_flow;

import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.mongodb.BasicDBObject;

import java.util.*;

public class DependencyFlow {

    public Map<Integer, Node> initialNodes = new HashMap<>();
    public Map<Integer, Node> resultNodes = new HashMap<>();

    public void syncWithDb() {
        List<Node> nodes = new ArrayList<>(resultNodes.values());
        if (nodes.size() > 0) DependencyFlowNodesDao.instance.insertMany(nodes);
    }

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
            if (node == null || node.getConnections() == null) {
                System.out.println("Dead end");
                continue;
            }
            Map<String, Connection> connections = node.getConnections();
            for (Connection connection: connections.values()) {
                for (Edge edge: connection.getEdges()) {
                    Node resultNode = resultNodes.get(Objects.hash(edge.getApiCollectionId(), edge.getUrl(), edge.getMethod()));
                    Edge reverseEdge = new Edge(node.getApiCollectionId(), node.getUrl(), node.getMethod(), connection.getParam(), edge.getCount(), depth + 1);
                    Connection reverseConnection = resultNode.getConnections().get(edge.getParam());
                    if (reverseConnection == null) {
                        reverseConnection = new Connection(edge.getParam(), new ArrayList<>());
                        resultNode.getConnections().put(edge.getParam(), reverseConnection);
                    }
                    reverseConnection.getEdges().add(reverseEdge);

                    boolean flag = isDone(resultNode);
                    int key = resultNode.hashCode();
                    if (flag && !done.contains(key)) {
                        // depth+1 will be the max depth because it will contain
                        queue.add(depth+1 + "#" + key);
                        done.add(key);
                    }
                }
            }
        }
    }

    public static boolean isDone(Node node) {
        boolean flag = true;
        for (Connection c: node.getConnections().values()) {
            flag = flag && c.getEdges().size() > 0;
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
            Connection connection = node.getConnections().getOrDefault(paramResp, new Connection(paramResp, new ArrayList<>()));

            String paramReq = paramInfo.getRequestParam();
            connection.getEdges().add(new Edge(
                    dependencyNode.getApiCollectionIdReq(), dependencyNode.getUrlReq(), dependencyNode.getMethodReq(),
                    paramReq, paramInfo.getCount(), -1
            ));

            node.getConnections().put(paramResp, connection);
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
            node.getConnections().put(paramReq, connection);
        }

        resultNodes.put(key, node);
    }

}
