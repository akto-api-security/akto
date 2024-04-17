package com.akto.dao;

import com.akto.dto.ApiCollection;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
}
