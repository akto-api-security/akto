package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dto.DependencyNode;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static junit.framework.TestCase.assertEquals;

public class TestDependencyAction extends MongoBasedTest {

    @Test
    public  void testExecute() {
        List<BasicDBObject> basicDBObjects = new ArrayList<>();
        //     1  2  3
        //     |  |  |
        //     4  5  6
        //      \ | /
        //        7
        //      /  \
        //     8     9
        //     |     |
        //     10    11
        insertDependencyGraphInDb(0, basicDBObjects);

        DependencyAction dependencyAction = new DependencyAction();
        dependencyAction.setUrl("url_7");
        dependencyAction.setApiCollectionId(0);
        dependencyAction.setMethod(URLMethods.Method.GET);

        String execute = dependencyAction.execute();
        assertEquals("SUCCESS", execute);

        List<BasicDBObject> result = dependencyAction.getResult();
        assertEquals(5, result.size());


        dependencyAction.setUrl("url_4");
        dependencyAction.setApiCollectionId(0);
        dependencyAction.setMethod(URLMethods.Method.GET);
        execute = dependencyAction.execute();
        assertEquals("SUCCESS", execute);
        result = dependencyAction.getResult();
        assertEquals(2, result.size());

        dependencyAction.setUrl("url_1");
        dependencyAction.setApiCollectionId(0);
        dependencyAction.setMethod(URLMethods.Method.GET);
        execute = dependencyAction.execute();
        assertEquals("SUCCESS", execute);
        result = dependencyAction.getResult();
        assertEquals(1, result.size());
    }


    static void fillBasicDBObjects(List<BasicDBObject> basicDBObjects, int apiCollectionId) {
        for (int i =1; i< 12; i++) {
            BasicDBObject endpoint = new BasicDBObject("url", "url_" + i);
            endpoint.put("method", "GET");
            endpoint.put("apiCollectionId", apiCollectionId);
            basicDBObjects.add(endpoint);
        }
    }

    public static void insertDependencyGraphInDb(int apiCollectionId, List<BasicDBObject> basicDBObjects) {
        DependencyNodeDao.instance.deleteAll(new BasicDBObject());

        List<DependencyNode> dependencyNodes = new ArrayList<>();
        // 11 endpoints link
        fillBasicDBObjects(basicDBObjects, apiCollectionId);

        List<DependencyNode.ParamInfo> paramInfos1 = new ArrayList<>();
        paramInfos1.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(0)), toMethod(basicDBObjects.get(0)),
                apiCollectionId+"", toUrl(basicDBObjects.get(3)), toMethod(basicDBObjects.get(3)),
                paramInfos1
        ));

        List<DependencyNode.ParamInfo> paramInfos2 = new ArrayList<>();
        paramInfos2.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(1)), toMethod(basicDBObjects.get(1)),
                apiCollectionId+"", toUrl(basicDBObjects.get(4)), toMethod(basicDBObjects.get(4)),
                paramInfos2
        ));

        List<DependencyNode.ParamInfo> paramInfos3 = new ArrayList<>();
        paramInfos3.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(2)), toMethod(basicDBObjects.get(2)),
                apiCollectionId+"", toUrl(basicDBObjects.get(5)), toMethod(basicDBObjects.get(5)),
                paramInfos3
        ));

        List<DependencyNode.ParamInfo> paramInfos4 = new ArrayList<>();
        paramInfos4.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(3)), toMethod(basicDBObjects.get(3)),
                apiCollectionId+"", toUrl(basicDBObjects.get(6)), toMethod(basicDBObjects.get(6)),
                paramInfos4
        ));

        List<DependencyNode.ParamInfo> paramInfos5 = new ArrayList<>();
        paramInfos5.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(4)), toMethod(basicDBObjects.get(4)),
                apiCollectionId+"", toUrl(basicDBObjects.get(6)), toMethod(basicDBObjects.get(6)),
                paramInfos5
        ));


        List<DependencyNode.ParamInfo> paramInfos6 = new ArrayList<>();
        paramInfos6.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(5)), toMethod(basicDBObjects.get(5)),
                apiCollectionId+"", toUrl(basicDBObjects.get(6)), toMethod(basicDBObjects.get(6)),
                paramInfos6
        ));

        List<DependencyNode.ParamInfo> paramInfos7 = new ArrayList<>();
        paramInfos7.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(6)), toMethod(basicDBObjects.get(6)),
                apiCollectionId+"", toUrl(basicDBObjects.get(7)), toMethod(basicDBObjects.get(7)),
                paramInfos7
        ));

        List<DependencyNode.ParamInfo> paramInfos8 = new ArrayList<>();
        paramInfos8.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(6)), toMethod(basicDBObjects.get(6)),
                apiCollectionId+"", toUrl(basicDBObjects.get(8)), toMethod(basicDBObjects.get(8)),
                paramInfos8
        ));

        List<DependencyNode.ParamInfo> paramInfos9 = new ArrayList<>();
        paramInfos9.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(7)), toMethod(basicDBObjects.get(7)),
                apiCollectionId+"", toUrl(basicDBObjects.get(9)), toMethod(basicDBObjects.get(9)),
                paramInfos9
        ));

        List<DependencyNode.ParamInfo> paramInfos10 = new ArrayList<>();
        paramInfos10.add(new DependencyNode.ParamInfo("param1", "param2", 10));
        dependencyNodes.add(new DependencyNode(
                apiCollectionId+"", toUrl(basicDBObjects.get(8)), toMethod(basicDBObjects.get(8)),
                apiCollectionId+"", toUrl(basicDBObjects.get(10)), toMethod(basicDBObjects.get(10)),
                paramInfos10
        ));

        DependencyNodeDao.instance.insertMany(dependencyNodes);


    }

    public static String toUrl(BasicDBObject basicDBObject)  {
        return basicDBObject.getString("url");
    }

    public static String toMethod(BasicDBObject basicDBObject)  {
        return basicDBObject.getString("method");
    }


}
