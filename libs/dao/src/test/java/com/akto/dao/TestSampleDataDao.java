package com.akto.dao;

import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;
import com.akto.utils.MongoBasedTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestSampleDataDao extends MongoBasedTest {

    @Test
    public void testFetchSampleDataPaginated() {
        SampleDataDao.instance.getMCollection().drop();

        int limit = 10;

        List<SampleData> sampleDataList = new ArrayList<>();
        List<String> urls = Arrays.asList(
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P"
        );
        for (String u: urls) {
            String url = "/api/" + u;
            for (URLMethods.Method method: Arrays.asList(URLMethods.Method.POST, URLMethods.Method.GET)) {
                Key key = new Key(123, url, method, 200, 0,0);
                SampleData sampleData = new SampleData(key, Arrays.asList("1", "2", "3", "4"));
                sampleDataList.add(sampleData);
            }
        }

        SampleDataDao.instance.insertMany(sampleDataList);


        int sliceLimit = 1;
        sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                123, null, null, limit, sliceLimit
        );

        for (SampleData sampleData: sampleDataList) {
            assertEquals(sliceLimit,sampleData.getSamples().size());
        }

        int lastIdx = sampleDataList.size()-1;

        SampleData firstSampleData = sampleDataList.get(0);
        SampleData lastSampleData = sampleDataList.get(lastIdx);

        assertEquals("/api/A", firstSampleData.getId().getUrl());
        assertEquals("GET", firstSampleData.getId().getMethod().name());
        assertEquals("/api/E", lastSampleData.getId().getUrl());
        assertEquals("POST", lastSampleData.getId().getMethod().name());

        // continue
        sliceLimit = 2;
        sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                123, lastSampleData.getId().getUrl(), lastSampleData.getId().getMethod().name(), limit, sliceLimit
        );

        for (SampleData sampleData: sampleDataList) {
            assertEquals(sliceLimit,sampleData.getSamples().size());
        }

        lastIdx = sampleDataList.size()-1;

        firstSampleData = sampleDataList.get(0);
        lastSampleData = sampleDataList.get(lastIdx);

        assertEquals("/api/F", firstSampleData.getId().getUrl());
        assertEquals("GET", firstSampleData.getId().getMethod().name());
        assertEquals("/api/J", lastSampleData.getId().getUrl());
        assertEquals("POST", lastSampleData.getId().getMethod().name());

    }
}
