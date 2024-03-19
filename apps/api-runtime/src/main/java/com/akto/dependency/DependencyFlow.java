package com.akto.dependency;

import com.akto.DependencyFlowHelper;
import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.akto.dto.dependency_flow.Node;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.InsertManyOptions;

import java.util.*;

public class DependencyFlow {
    public Map<Integer, Node> resultNodes = new HashMap<>();

    public void syncWithDb() {
        List<Node> nodes = new ArrayList<>(resultNodes.values());
        if (nodes.size() > 0) {
            for (Node node: nodes) {
                node.fillMaxDepth();
                node.replaceDots();
            }
            DependencyFlowNodesDao.instance.getMCollection().drop();
            DependencyFlowNodesDao.instance.getMCollection().insertMany(nodes, new InsertManyOptions().ordered(false));
        }
    }

    public void run() {
        List<DependencyNode> dependencyNodeList = DependencyNodeDao.instance.findAll(new BasicDBObject());
        DependencyFlowHelper dependencyFlowHelper = new DependencyFlowHelper(dependencyNodeList);
        dependencyFlowHelper.run();
    }

}
