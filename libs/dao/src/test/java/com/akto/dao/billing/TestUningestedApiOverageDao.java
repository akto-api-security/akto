package com.akto.dao.billing;

import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.type.URLMethods;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestUningestedApiOverageDao extends MongoBasedTest {

    @Test
    public void testGetCountByCollectionExcludesOptionsApis() {
        // Clear the collection before test
        UningestedApiOverageDao.instance.getMCollection().drop();

        // Create test data with different HTTP methods
        UningestedApiOverage getApi1 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.GET, "/api/users");
        UningestedApiOverage getApi2 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.GET, "/api/users/1");
        UningestedApiOverage postApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.POST, "/api/users");
        UningestedApiOverage putApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.PUT, "/api/users/1");
        UningestedApiOverage deleteApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.DELETE, "/api/users/1");
        
        // Create OPTIONS APIs that should be excluded
        UningestedApiOverage optionsApi1 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OPTIONS, "/api/users");
        UningestedApiOverage optionsApi2 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OPTIONS, "/api/users/1");
        
        // Create APIs for a different collection
        UningestedApiOverage getApiOtherCollection = new UningestedApiOverage(2000, "STATIC", URLMethods.Method.GET, "/api/products");
        UningestedApiOverage optionsApiOtherCollection = new UningestedApiOverage(2000, "STATIC", URLMethods.Method.OPTIONS, "/api/products");

        // Insert all test data
        List<UningestedApiOverage> testData = Arrays.asList(
            getApi1, getApi2, postApi, putApi, deleteApi, optionsApi1, optionsApi2,
            getApiOtherCollection, optionsApiOtherCollection
        );
        
        UningestedApiOverageDao.instance.insertMany(testData);

        // Call the method under test
        Map<Integer, Integer> countMap = UningestedApiOverageDao.instance.getCountByCollection();

        // Verify results
        assertNotNull("Count map should not be null", countMap);
        assertEquals("Should have 2 collections", 2, countMap.size());
        
        // Collection 1000 should have 5 APIs (excluding the 2 OPTIONS APIs)
        assertEquals("Collection 1000 should have 5 non-OPTIONS APIs", 
                    Integer.valueOf(5), countMap.get(1000));
        
        // Collection 2000 should have 1 API (excluding the 1 OPTIONS API)
        assertEquals("Collection 2000 should have 1 non-OPTIONS API", 
                    Integer.valueOf(1), countMap.get(2000));
        
        // Verify that OPTIONS APIs are not counted
        assertTrue("OPTIONS APIs should be excluded from count", 
                  countMap.get(1000) < 7); // 7 would be the total including OPTIONS
        assertTrue("OPTIONS APIs should be excluded from count", 
                  countMap.get(2000) < 2); // 2 would be the total including OPTIONS
    }

    @Test
    public void testGetCountByCollectionWithOnlyOptionsApis() {
        // Clear the collection before test
        UningestedApiOverageDao.instance.getMCollection().drop();

        // Create only OPTIONS APIs
        UningestedApiOverage optionsApi1 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OPTIONS, "/api/users");
        UningestedApiOverage optionsApi2 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OPTIONS, "/api/users/1");
        UningestedApiOverage optionsApi3 = new UningestedApiOverage(2000, "STATIC", URLMethods.Method.OPTIONS, "/api/products");

        List<UningestedApiOverage> testData = Arrays.asList(optionsApi1, optionsApi2, optionsApi3);
        UningestedApiOverageDao.instance.insertMany(testData);

        // Call the method under test
        Map<Integer, Integer> countMap = UningestedApiOverageDao.instance.getCountByCollection();

        // Verify results - should be empty since all APIs are OPTIONS
        assertNotNull("Count map should not be null", countMap);
        assertEquals("Should have no collections since all APIs are OPTIONS", 0, countMap.size());
    }

    @Test
    public void testGetCountByCollectionWithMixedMethods() {
        // Clear the collection before test
        UningestedApiOverageDao.instance.getMCollection().drop();

        // Create a mix of different HTTP methods
        UningestedApiOverage getApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.GET, "/api/users");
        UningestedApiOverage postApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.POST, "/api/users");
        UningestedApiOverage putApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.PUT, "/api/users/1");
        UningestedApiOverage deleteApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.DELETE, "/api/users/1");
        UningestedApiOverage headApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.HEAD, "/api/users");
        UningestedApiOverage traceApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.TRACE, "/api/users");
        UningestedApiOverage patchApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.PATCH, "/api/users/1");
        UningestedApiOverage connectApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.CONNECT, "/api/users");
        UningestedApiOverage trackApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.TRACK, "/api/users");
        UningestedApiOverage otherApi = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OTHER, "/api/users");
        
        // Create OPTIONS APIs that should be excluded
        UningestedApiOverage optionsApi1 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OPTIONS, "/api/users");
        UningestedApiOverage optionsApi2 = new UningestedApiOverage(1000, "STATIC", URLMethods.Method.OPTIONS, "/api/users/1");

        List<UningestedApiOverage> testData = Arrays.asList(
            getApi, postApi, putApi, deleteApi, headApi, traceApi, patchApi, connectApi, trackApi, otherApi,
            optionsApi1, optionsApi2
        );
        
        UningestedApiOverageDao.instance.insertMany(testData);

        // Call the method under test
        Map<Integer, Integer> countMap = UningestedApiOverageDao.instance.getCountByCollection();

        // Verify results
        assertNotNull("Count map should not be null", countMap);
        assertEquals("Should have 1 collection", 1, countMap.size());
        
        // Should have 10 APIs (excluding the 2 OPTIONS APIs)
        assertEquals("Collection 1000 should have 10 non-OPTIONS APIs", 
                    Integer.valueOf(10), countMap.get(1000));
    }
} 