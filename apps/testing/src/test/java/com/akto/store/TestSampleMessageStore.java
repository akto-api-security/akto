package com.akto.store;

import com.akto.MongoBasedTest;
import com.akto.dao.SampleDataDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import org.junit.Test;
import org.springframework.security.core.parameters.P;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestSampleMessageStore extends MongoBasedTest {

    @Test
    public void testFetchSampleMessages() {
        SampleDataDao.instance.getMCollection().drop();
        SampleData sampleData1 = new SampleData(new Key(0, "url1", URLMethods.Method.GET,0,0,0), null);
        SampleData sampleData2 = new SampleData(new Key(0, "url2", URLMethods.Method.GET,0,0,0), Arrays.asList("m1", "m2"));
        SampleData sampleData3 = new SampleData(new Key(0, "url3", URLMethods.Method.GET,0,0,0), Collections.emptyList());
        SampleDataDao.instance.insertMany(Arrays.asList(sampleData1, sampleData2, sampleData3));

        SampleMessageStore.fetchSampleMessages();

        assertEquals(SampleMessageStore.sampleDataMap.size(), 2);
        List<String> messages = SampleMessageStore.sampleDataMap.get(new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET));
        assertEquals(messages.size(), 2);

        SampleDataDao.instance.getMCollection().drop();
        sampleData2 = new SampleData(new Key(0, "url2", URLMethods.Method.GET,0,0,0), Arrays.asList("m1", "m2", "m3"));
        SampleDataDao.instance.insertMany(Arrays.asList(sampleData1, sampleData2));

        SampleMessageStore.fetchSampleMessages();
        assertEquals(SampleMessageStore.sampleDataMap.size(), 1);
        messages = SampleMessageStore.sampleDataMap.get(new ApiInfo.ApiInfoKey(0, "url2", URLMethods.Method.GET));
        assertEquals(messages.size(), 3);

    }
}
