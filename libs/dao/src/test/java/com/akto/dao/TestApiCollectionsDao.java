package com.akto.dao;

import com.akto.dto.ApiCollection;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestApiCollectionsDao extends MongoBasedTest {

    @Test
    public void testFetchNonTrafficApiCollectionsIds() {
        ApiCollectionsDao.instance.getMCollection().drop();

        // mirroring collection with host
        ApiCollection apiCollection1 = new ApiCollection(1000, "mirroring_with_host", 1000, new HashSet<>(), "akto.io", 123);
        // mirroring collections without hosts
        ApiCollection apiCollection2 = new ApiCollection(2000, "mirroring_without_host", 2000, new HashSet<>(), null, 456);
        // manually created collections
        ApiCollection apiCollection3 = new ApiCollection(3000, "burp", 300, new HashSet<>(),null, 0);

        ApiCollectionsDao.instance.insertMany(Arrays.asList(apiCollection1, apiCollection2, apiCollection3));

        List<Integer> apiCollectionIds = ApiCollectionsDao.instance.fetchNonTrafficApiCollectionsIds();

        assertEquals(1, apiCollectionIds.size());
        assertEquals(3000, apiCollectionIds.get(0),0);

    }
}
