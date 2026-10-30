package com.akto.dao;

import com.akto.dto.ApiCollection;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestApiCollectionsDao extends MongoBasedTest {

    @Test
    public void testFetchNonTrafficApiCollectionsIds() {
        ApiCollectionsDao.instance.getMCollection().drop();

        // mirroring collection with host
        ApiCollection apiCollection1 = new ApiCollection(1000, "mirroring_with_host", 1000, new HashSet<>(), "akto.io", 123, false, true);
        // mirroring collections without hosts
        ApiCollection apiCollection2 = new ApiCollection(2000, "mirroring_without_host", 2000, new HashSet<>(), null, 456, false, true);
        // manually created collections with vxlanid == 0
        ApiCollection apiCollection3 = new ApiCollection(3000, "burp1", 3000, new HashSet<>(),null, 0, false, true);
        // manually created collections with vxlanid != 0
        ApiCollection apiCollection4 = new ApiCollection(4000, "burp2", 4000, new HashSet<>(),null, 4000, false, true);

        ApiCollectionsDao.instance.insertMany(Arrays.asList(apiCollection1, apiCollection2, apiCollection3, apiCollection4));

        List<Integer> apiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();

        assertEquals(3, apiCollectionIds.size());

        Set<Integer> apiCollectionIdsSet = new HashSet<>(apiCollectionIds);

        assertEquals(apiCollectionIdsSet, new HashSet<>(Arrays.asList(2000,3000,4000)));

    }

    @Test
    public void testListProjectionKeepsServiceTagDisplayName() {
        ApiCollectionsDao.instance.getMCollection().drop();

        ApiCollection serviceCollection = new ApiCollection(5000, "booking-api", 5000, new HashSet<>(), "booking-api-qa.example.com", 0, false, true);
        serviceCollection.setServiceTag("booking-api");
        ApiCollection hostCollection = new ApiCollection(6000, "booking-api", 6000, new HashSet<>(), "booking-api-qa.example.com", 0, false, true);

        ApiCollectionsDao.instance.insertMany(Arrays.asList(serviceCollection, hostCollection));

        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(Filters.empty(), ApiCollectionsDao.LIST_PROJECTION);
        Map<Integer, String> displayNames = new HashMap<>();
        for (ApiCollection c : collections) {
            displayNames.put(c.getId(), c.getDisplayName());
        }

        assertEquals("booking-api", displayNames.get(5000));
        assertEquals("booking-api-qa.example.com - booking-api", displayNames.get(6000));
    }
}
