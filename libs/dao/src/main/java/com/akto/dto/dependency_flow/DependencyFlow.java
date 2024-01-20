package com.akto.dto.dependency_flow;

import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.mongodb.BasicDBObject;

import java.util.*;

public class DependencyFlow {

    public Map<Integer, ReverseNode> initialNodes = new HashMap<>();
    public Map<Integer, Node> resultNodes = new HashMap<>();

    public void syncWithDb() {
        List<Node> nodes = new ArrayList<>(resultNodes.values());
        if (nodes.size() > 0) {
            for (Node node: nodes) {
                node.fillMaxDepth();
            }
            DependencyFlowNodesDao.instance.getMCollection().drop();
            DependencyFlowNodesDao.instance.insertMany(nodes);
        }
    }

    public void run() {
        initialNodes = new HashMap<>();
        resultNodes = new HashMap<>();
        List<DependencyNode> dependencyNodeList = DependencyNodeDao.instance.findAll(new BasicDBObject());

        for (DependencyNode dependencyNode: dependencyNodeList) {
            fillNodes(dependencyNode);
            fillResultNodes(dependencyNode);
        }

        // build initial queue
        // depth 0 for initial queue
        Queue<String> queue = new LinkedList<>();
        Set<Integer> done = new HashSet<>();

        for (ReverseNode reverseNode: initialNodes.values()) {
            int id = reverseNode.hashCode();
            if (resultNodes.containsKey(id)) continue;
            queue.add(0 + "#" + id);
            Node resultNodeForZeroLevel = new Node(reverseNode.getApiCollectionId(), reverseNode.getUrl(), reverseNode.getMethod(), new HashMap<>());
            resultNodes.put(id, resultNodeForZeroLevel);
        }

        // pop queue until empty
        while (!queue.isEmpty()) {
            String combined = queue.remove();
            int depth = Integer.parseInt(combined.split("#")[0]);
            int nodeId = Integer.parseInt(combined.split("#")[1]);
            ReverseNode reverseNode = initialNodes.get(nodeId);
            if (reverseNode == null || reverseNode.getReverseConnections() == null) {
                System.out.println("Dead end");
                continue;
            }
            Map<String,ReverseConnection>  reverseConnections = reverseNode.getReverseConnections();
            for (ReverseConnection reverseConnection: reverseConnections.values()) {
                for (ReverseEdge reverseEdge: reverseConnection.getReverseEdges()) {

                    Node resultNode = resultNodes.get(Objects.hash(reverseEdge.getApiCollectionId(), reverseEdge.getUrl(), reverseEdge.getMethod()));
                    Edge edge = new Edge(reverseNode.getApiCollectionId(), reverseNode.getUrl(), reverseNode.getMethod(), reverseConnection.getParam(), reverseEdge.getCount(), depth + 1);
                    Connection connection = resultNode.getConnections().get(reverseEdge.getParam());
                    if (connection == null) {
                        connection = new Connection(edge.getParam(), new ArrayList<>(), reverseEdge.isUrlParam());
                        resultNode.getConnections().put(reverseEdge.getParam(), connection);
                    }
                    connection.getEdges().add(edge);

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
        ReverseNode reverseNode = new ReverseNode(
                dependencyNode.getApiCollectionIdResp(),
                dependencyNode.getUrlResp(),
                dependencyNode.getMethodResp(),
                new HashMap<>()
        );

        Integer key = reverseNode.hashCode();
        reverseNode = initialNodes.getOrDefault(key, reverseNode);

        for (DependencyNode.ParamInfo paramInfo: dependencyNode.getParamInfos()) {
            String paramResp = paramInfo.getResponseParam();
            ReverseConnection reverseConnection = reverseNode.getReverseConnections().getOrDefault(paramResp, new ReverseConnection(paramResp, new ArrayList<>()));

            String paramReq = paramInfo.getRequestParam();
            reverseConnection.getReverseEdges().add(
                    new ReverseEdge(
                            dependencyNode.getApiCollectionIdReq(), dependencyNode.getUrlReq(), dependencyNode.getMethodReq(), paramReq, paramInfo.getCount(), paramInfo.getIsUrlParam()
                    )
            );

            reverseNode.getReverseConnections().put(paramResp, reverseConnection);
        }

        initialNodes.put(key, reverseNode);
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
            Connection connection = new Connection(paramReq, new ArrayList<>(), paramInfo.isUrlParam());
            node.getConnections().put(paramReq, connection);
        }

        resultNodes.put(key, node);
    }

}
