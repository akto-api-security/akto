package com.akto.store;

import com.akto.MongoBasedTest;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestSampleMessageStore extends MongoBasedTest {

    @Test
    public void testFetchSampleMessages() {
        SampleDataDao.instance.getMCollection().drop();
        SampleData sampleData1 = new SampleData(new Key(0, "url1", URLMethods.Method.GET,0,0,0), null);
        SampleData sampleData2 = new SampleData(new Key(0, "url2", URLMethods.Method.GET,0,0,0), Arrays.asList("m1", "m2"));
        SampleData sampleData3 = new SampleData(new Key(0, "url3", URLMethods.Method.GET,0,0,0), Collections.emptyList());
        SampleData sampleData4 = new SampleData(new Key(1, "url1", URLMethods.Method.GET,0,0,0), Arrays.asList("m3", "m4", "m5"));
        SampleDataDao.instance.insertMany(Arrays.asList(sampleData1, sampleData2, sampleData3, sampleData4));

        SampleMessageStore sample =  SampleMessageStore.create();
        Set<Integer> apiCollectionIds = new HashSet<>();
        apiCollectionIds.add(0);
        apiCollectionIds.add(1);
        sample.fetchSampleMessages(apiCollectionIds);
        Map<ApiInfo.ApiInfoKey, List<String>> sampleDataMap =  sample.getSampleDataMap();

        assertEquals(sampleDataMap.size(), 3);
        List<String> messages = sampleDataMap.get(new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET));
        assertEquals(messages.size(), 2);

        messages = sampleDataMap.get(new ApiInfo.ApiInfoKey(1, "url1", URLMethods.Method.GET));
        assertEquals(messages.size(), 3);

        SampleDataDao.instance.getMCollection().drop();
        sampleData2 = new SampleData(new Key(0, "url2", URLMethods.Method.GET,0,0,0), Arrays.asList("m1", "m2", "m3"));
        SampleDataDao.instance.insertMany(Arrays.asList(sampleData1, sampleData2));
        sample.fetchSampleMessages(apiCollectionIds);
        sampleDataMap =  sample.getSampleDataMap();
        assertEquals(sampleDataMap.size(), 1);
        messages = sampleDataMap.get(new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET));
        assertEquals(messages.size(), 3);

    }
}
