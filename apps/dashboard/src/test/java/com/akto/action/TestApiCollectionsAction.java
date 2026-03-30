package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
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
        Context.userId.set(null);
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
        String name = "Avneesh.123-_33333333333333333333333333333333333333333333_33333333333333333333333333333333333333333333";
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
        Context.userId.set(null);
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

    @Test
    public void fetchAllCollections() {
        ApiCollectionsDao.instance.getMCollection().drop();
        Context.userId.set(null);
        List<ApiCollection> apiCollectionList = new ArrayList<>();

        // mirroring collection with host
        Set<String> urls1 = new HashSet<>(Arrays.asList("1", "2", "3", "4", "5", "6"));
        apiCollectionList.add(new ApiCollection(1000, "one", 1000, urls1, "one.com", 1000, false, true));

        // mirroring collections without hosts
        Set<String> urls2 = new HashSet<>(Arrays.asList("1", "2", "3"));
        apiCollectionList.add(new ApiCollection(2000, "two", 2000, urls2, null,2000, false, true));

        // manually created collections
        Set<String> urls3 = new HashSet<>(Arrays.asList("1", "2", "3", "4"));
        apiCollectionList.add(new ApiCollection(3000, "three", 3000, urls3, null,0, false, true));

        ApiCollectionsDao.instance.insertMany(apiCollectionList);
        Method method = Method.GET;

        List<ApiInfo> apiInfos = new ArrayList<>();
        for (int c=1; c<4; c++) {
            int apiCollectionId = c*1000;
            for (int i = 0; i < 100; i++) {
                String url = "/api/v1" + i;
                ApiInfo apiInfo= new ApiInfo(
                    new ApiInfoKey(apiCollectionId, url, method)
                );
                apiInfos.add(apiInfo);
            }
        }

        ApiInfoDao.instance.insertMany(apiInfos);

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        for (int c=1; c<4; c++) {
            for (int j = 0; j < 100; j++) {
                for (int i = 0; i < 100; i++) {
                    int apiCollectionId = c*1000;
                    int responseCode = i % 2 == 0 ? -1 : 200;
                    String param = i == 0 ? "host" : "param_" + i;
                    boolean isHeader = i == 0;
                    String url = "url_" + j;
                    SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", responseCode, isHeader, param, SingleTypeInfo.GENERIC, apiCollectionId, false);
                    SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
                    singleTypeInfos.add(sti);
                }
            }
        }

        SingleTypeInfoDao.instance.insertMany(singleTypeInfos);

        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        apiCollectionsAction.fetchAllCollections();
        List<ApiCollection> apiCollections = apiCollectionsAction.apiCollections;

        assertEquals(3,apiCollections.size());

        Map<Integer, ApiCollection> apiCollectionMap = new HashMap<>();
        for (ApiCollection apiCollection: apiCollections)  {
            apiCollectionMap.put(apiCollection.getId(), apiCollection);
        }

        assertEquals(100, apiCollectionMap.get(1000).getUrlsCount());
        assertEquals(3, apiCollectionMap.get(2000).getUrlsCount()); // because burp collection we use count from urls stored in set
        assertEquals(4, apiCollectionMap.get(3000).getUrlsCount()); // because burp collection we use count from urls stored in set

    }

    @Test
    public void testTagsForCollections(){
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollection collection1 = new ApiCollection();
        collection1.setId(1);
        collection1.setHostName("akto.demo.io");

        ApiCollection collection2 = new ApiCollection();
        collection2.setId(2);
        collection2.setHostName("akto.svc.local");

        ApiCollection collection3 = new ApiCollection();
        collection3.setId(3);
        collection3.setHostName("locahost:3000");

        ApiCollection collection4 = new ApiCollection();
        collection4.setId(4);
        collection4.setHostName("akto.localnet");

        ApiCollection collection5 = new ApiCollection();
        collection5.setId(5);
        collection5.setHostName("kubernetes-121212-akto.io");

        // getEnvType() returns DB only; no tags yet so null for all
        assertTrue(collection1.getEnvType() == null);
        assertTrue(collection3.getEnvType() == null);

        // computeEnvTypeFromHostname() returns derived tag from hostname
        CollectionTags t1 = collection1.computeEnvTypeFromHostname();
        assertTrue(t1 != null && ApiCollection.ENV_TYPE.STAGING.name().equals(t1.getValue()));
        CollectionTags t2 = collection2.computeEnvTypeFromHostname();
        assertTrue(t2 != null && ApiCollection.ENV_TYPE.STAGING.name().equals(t2.getValue()));
        assertTrue(collection3.computeEnvTypeFromHostname() == null);
        CollectionTags t4 = collection4.computeEnvTypeFromHostname();
        assertTrue(t4 != null && ApiCollection.ENV_TYPE.STAGING.name().equals(t4.getValue()));
        CollectionTags t5 = collection5.computeEnvTypeFromHostname();
        assertTrue(t5 != null && ApiCollection.ENV_TYPE.INTERNAL.name().equals(t5.getValue()));
    }
}
