package com.akto.dto.dependency_flow;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;


public class TestNode {

    @Test
    public void testReplaceDots() {
        Node node = new Node();
        Map<String, Connection> connectionMap = new HashMap<>();
        connectionMap.put("user.address.country", new Connection());
        connectionMap.put("user.address.city", new Connection());
        connectionMap.put("user", new Connection());
        connectionMap.put("$user.name", new Connection());
        node.setConnections(connectionMap);

        node.replaceDots();

        Map<String, Connection> connections = node.getConnections();
        assertEquals(4,connections.size());
        assertNotNull(connections.get("user"+Node.DOT+"address"+Node.DOT+"country"));
        assertNotNull(connections.get("user"+Node.DOT+"address"+Node.DOT+"city"));
        assertNotNull(connections.get("user"));
        assertNotNull(connections.get(Node.DOLLAR + "user" + Node.DOT + "name"));
    }
}
