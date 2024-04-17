package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.*;

public class MoveSampleDataToSTI {

    public static void main1(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        System.out.println("START A");
        Context.accountId.set(1_000_000);
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());
        System.out.println("END A");
        DaoInit.init(new ConnectionString("mongodb://3.218.244.163:27017/admini"));
        System.out.println("START B");
        SampleDataDao.instance.insertMany(sampleDataList);
        System.out.println("END B");
    }

    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);

        MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection().find().projection(null).cursor();
        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        Set<Integer> aci = new HashSet<>();
        while (cursor.hasNext()) {
            Key id = cursor.next().getId();
            aci.add(id.getApiCollectionId());
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                id.getUrl(), id.getMethod().name(),-1, true, "host", SingleTypeInfo.GENERIC, id.getApiCollectionId(), false
            );
            SingleTypeInfo singleTypeInfo = new SingleTypeInfo(
                    paramId,new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE
            );
            Bson updateKey = SingleTypeInfoDao.createFilters(singleTypeInfo);
            Bson update = Updates.set("timestamp", Context.now());
            bulkUpdates.add(new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true)));
        }

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);

        List<ApiCollection> apiCollectionList = new ArrayList<>();
        for (Integer i: aci) {
            apiCollectionList.add(new ApiCollection(i, i+"", 0, new HashSet<>(), null, i, false, true));
        }
        ApiCollectionsDao.instance.insertMany(apiCollectionList);
    }



    public static void main4(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        TestingRunResultDao.instance.getMCollection().drop();

//        List<TestingRun> testingRuns = new ArrayList<>();
//        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject());
//        for (ApiCollection apiCollection: apiCollectionList) {
//            testingRuns.add(new TestingRun(Context.now(), "", new CollectionWiseTestingEndpoints(apiCollection.getId()),0, TestingRun.State.SCHEDULED));
//        }
//
//        TestingRunDao.instance.insertMany(testingRuns);

        TestingRunDao.instance.getMCollection().updateMany(
                new BasicDBObject(), Updates.set("state", TestingRun.State.SCHEDULED)
        );
    }
}
