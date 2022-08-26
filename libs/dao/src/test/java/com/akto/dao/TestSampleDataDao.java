package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SampleData;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestSampleDataDao {

    @Test
    public void testFetchSampleDataPaginated() {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);

        List<SampleData> sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                123, null, null, 99
        );

        int lastIdx = sampleDataList.size()-1;

        SampleData firstSampleData = sampleDataList.get(0);
        SampleData lastSampleData = sampleDataList.get(lastIdx);

        System.out.println(firstSampleData.getId().url);
        System.out.println(firstSampleData.getId().method);
        System.out.println(lastSampleData.getId().url);
        System.out.println(lastSampleData.getId().method);

        //
        System.out.println("\n");

        sampleDataList = SampleDataDao.instance.fetchSampleDataPaginated(
                123, lastSampleData.getId().url,lastSampleData.getId().method.name(), 100
        );

        lastIdx = sampleDataList.size()-1;

        firstSampleData = sampleDataList.get(0);
        lastSampleData = sampleDataList.get(lastIdx);

        System.out.println(firstSampleData.getId().url);
        System.out.println(firstSampleData.getId().method);
        System.out.println(lastSampleData.getId().url);
        System.out.println(lastSampleData.getId().method);
    }
}
