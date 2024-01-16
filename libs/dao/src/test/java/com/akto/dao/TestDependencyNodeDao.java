package com.akto.dao;

import com.akto.dto.DependencyNode;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class TestDependencyNodeDao extends MongoBasedTest {

    @Test
    public void testDuplicateInserts() {
        DependencyNodeDao.instance.getMCollection().drop();
        DependencyNodeDao.instance.createIndicesIfAbsent();

        DependencyNode dependencyNode = new DependencyNode(
                "0", "url1", "GET", "0", "url2", "POST", new ArrayList<>(), 0
        );
        DependencyNodeDao.instance.insertOne(dependencyNode);

        boolean erroredOut = false;
        try {
            DependencyNodeDao.instance.insertOne(dependencyNode);
        } catch (Exception e) {
            erroredOut = true;
        }

        assertTrue(erroredOut);
    }
}
