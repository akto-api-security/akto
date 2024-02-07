package com.akto.dao;

import com.akto.dto.dependency_flow.Connection;
import com.akto.dto.dependency_flow.Edge;
import com.akto.dto.dependency_flow.Node;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;

public class TestDependencyFlowNodesDao extends MongoBasedTest {

    @Test
    public void testDuplicateInserts() {
        DependencyFlowNodesDao.instance.getMCollection().drop();

        DependencyFlowNodesDao.instance.createIndicesIfAbsent();
        Node node = new Node("0", "url", "GET", new HashMap<>());
        DependencyFlowNodesDao.instance.insertOne(node);

        boolean erroredOut = false;
        node.getConnections().put("param", new Connection("param", Collections.singletonList(new Edge("0", "url_new", "POST", "param_new", false,20, 0)), false, false));
        try {
            DependencyFlowNodesDao.instance.insertOne(node);
        } catch (Exception e) {
            erroredOut = true;
        }

        assertTrue(erroredOut);
    }
}
