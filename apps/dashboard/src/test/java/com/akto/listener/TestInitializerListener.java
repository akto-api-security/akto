package com.akto.listener;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestInitializerListener extends MongoBasedTest {

    private SingleTypeInfo generateSti(String url, int apiCollectionId) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, "GET",200, false, "param#key", SingleTypeInfo.EMAIL, apiCollectionId, false
        );
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0,1000,0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
    }

    @Test
    public void testChangesInfo() {
        ApiCollection apiCollection1 = new ApiCollection(0, "coll1", Context.now(), new HashSet<>(), "akto.io", 1);
        ApiCollection apiCollection2 = new ApiCollection(1, "coll2", Context.now(), new HashSet<>(), "app.akto.io", 2);
        ApiCollection apiCollection3 = new ApiCollection(2, "coll3", Context.now(), new HashSet<>(), null, 3);
        ApiCollectionsDao.instance.insertMany(Arrays.asList(apiCollection1, apiCollection2, apiCollection3));

        SingleTypeInfo sti1 = generateSti("/api/books", 0);
        SingleTypeInfo sti2 = generateSti("api/books/INTEGER", 0);
        SingleTypeInfo sti3 = generateSti("/api/cars", 1);
        SingleTypeInfo sti4 = generateSti("/api/toys", 2);
        SingleTypeInfo sti5 = generateSti("/api/bus", 9999);

        SingleTypeInfoDao.instance.insertMany(Arrays.asList(sti1, sti2, sti3, sti4, sti5));

        InitializerListener.ChangesInfo changesInfo = InitializerListener.getChangesInfo(Context.now(), Context.now());
        assertNotNull(changesInfo);
        List<String> newEndpointsLast7Days = changesInfo.newEndpointsLast7Days;
        Map<String, String> newSensitiveParams = changesInfo.newSensitiveParams;

        assertEquals(5,newEndpointsLast7Days.size());

        assertTrue(newEndpointsLast7Days.contains("GET akto.io/api/books"));
        assertTrue(newEndpointsLast7Days.contains("GET akto.io/api/books/INTEGER"));
        assertTrue(newEndpointsLast7Days.contains("GET app.akto.io/api/cars"));
        assertTrue(newEndpointsLast7Days.contains("GET /api/toys"));
        assertTrue(newEndpointsLast7Days.contains("GET /api/bus"));

        assertTrue(newSensitiveParams.containsKey("GET akto.io/api/books: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET akto.io/api/books/INTEGER: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET app.akto.io/api/cars: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET /api/toys: EMAIL"));
        assertTrue(newSensitiveParams.containsKey("GET /api/bus: EMAIL"));
    }

}
