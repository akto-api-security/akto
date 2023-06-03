package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestApiCollectionsAction extends MongoBasedTest {

    private void validate(String name) {
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject());

        if (name == null) {
            assertEquals(apiCollectionList.size(), 0);
            return;
        } else {
            assertEquals(apiCollectionList.size(), 1);
        }

        assertEquals(apiCollectionList.get(0).getName(), name);

    }

    @Test
    public void testHappy() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        String name = "Avneesh.123-_";
        apiCollectionsAction.setCollectionName(name);
        String result = apiCollectionsAction.createCollection();
        assertEquals(result, "SUCCESS");
        validate(name);
    }

    @Test
    public void testMaxSize() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        String name = "Avneesh.123-_33333333333333333333333333333333333333333333";
        apiCollectionsAction.setCollectionName(name);
        String result = apiCollectionsAction.createCollection();
        assertEquals(result, "ERROR");
        validate(null);
    }

    @Test
    public void testInvalidChars() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        String name = "Avneesh#123";
        apiCollectionsAction.setCollectionName(name);
        String result = apiCollectionsAction.createCollection();
        assertEquals(result, "ERROR");
        validate(null);
    }

    @Test
    public void testUniqueCollectionName() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        String name = "Avneesh123";
        apiCollectionsAction.setCollectionName(name);
        apiCollectionsAction.createCollection();
        validate(name);

        apiCollectionsAction.setCollectionName(name);
        String result = apiCollectionsAction.createCollection();
        validate(name);
        assertEquals(result, "ERROR");
    }
}
