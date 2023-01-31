package com.akto.action.observe;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestInventoryAction extends MongoBasedTest {

    private SingleTypeInfo buildHostSti(String url, String method, String param, int apiCollection) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method, -1, true, param, SingleTypeInfo.GENERIC, apiCollection, false
        );
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(),0,0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0,0);
    }

    @Test
    public void testFetchEndpointsBasedOnHostName() {
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();


        ApiCollection apiCollection = new ApiCollection(0, "petstore-lb",0, new HashSet<>(), "petstore.com", 0);
        ApiCollectionsDao.instance.insertOne(apiCollection);

        SingleTypeInfo sti1 = buildHostSti("url1", "GET", "host", 0);
        SingleTypeInfo sti2 = buildHostSti("url1", "POST", "host", 0);
        SingleTypeInfo sti3 = buildHostSti("url2", "PUT", "host", 0);
        SingleTypeInfo sti4 = buildHostSti("url3", "GET", "host", 0);
        SingleTypeInfo sti5 = buildHostSti("url4", "GET", "host", 0);
        SingleTypeInfo sti6 = buildHostSti("url5", "GET", "random", 0);
        SingleTypeInfo sti7 = buildHostSti("url6", "GET", "host", 1);

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(sti1, sti2, sti3, sti4, sti5, sti6, sti7));

        InventoryAction inventoryAction = new InventoryAction();
        String result = inventoryAction.fetchEndpointsBasedOnHostName();
        assertEquals("ERROR", result);

        inventoryAction.setHostName("idodopeshit.in");
        result = inventoryAction.fetchEndpointsBasedOnHostName();
        assertEquals("ERROR", result);

        inventoryAction.setHostName(apiCollection.getHostName());
        result = inventoryAction.fetchEndpointsBasedOnHostName();
        assertEquals("SUCCESS", result);


        assertEquals(5, inventoryAction.getEndpoints().size());

        BasicDBObject basicDBObject = inventoryAction.getEndpoints().get(0);
        assertEquals(2, basicDBObject.size());
        assertNotNull(basicDBObject.get("url"));
        assertNotNull(basicDBObject.get("method"));
    }
}
