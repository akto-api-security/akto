package com.akto.runtime.merge;

import org.junit.Test;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

import com.akto.MongoBasedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.*;

public class TestMergeOnHostOnly extends MongoBasedTest {

    private WriteModel<SingleTypeInfo> createSingleTypeInfoUpdate(String url, String method, SingleTypeInfo.SubType subType, int apiCollectionId, int responseCode) {
        SingleTypeInfoDao.instance.getMCollection().drop();
        CappedSet<String> values = new CappedSet<>();
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method,responseCode, false, "param", subType, apiCollectionId, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 100,0,0, values, SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        return new InsertOneModel<>(singleTypeInfo);
    }

    private WriteModel<SingleTypeInfo> createSingleTypeInfoHostUpdate(int apiCollectionId, String url, String method) {
        SingleTypeInfoDao.instance.getMCollection().drop();
        CappedSet<String> values = new CappedSet<>();
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method,-1,true, "host", SingleTypeInfo.GENERIC, apiCollectionId, false
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 100,0,0, values, SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        return new InsertOneModel<>(singleTypeInfo);
    }

    @Test
    public void test1(){
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
        SensitiveSampleDataDao.instance.getMCollection().drop();
        TrafficInfoDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();

        Set<String> url1 = new HashSet<>();
        Set<String> url2 = new HashSet<>();
        ApiCollection ap1 = new ApiCollection(1, "something-red", 0, url1, "http://www.akto.io", 1, false, true);
        ApiCollection ap2 = new ApiCollection(2, "something-blue", 0, url2, "http://www.akto.io", 2, false, true);

        ApiCollection ap3 = new ApiCollection(3, "something-red", 0, url1, "http://www.notakto.io", 1, false, true);
        ApiCollection ap4 = new ApiCollection(4, "something-blue", 0, url2, "http://www.notakto.io", 2, false, true);
        ApiCollection ap5 = new ApiCollection(5, "something-yellow", 0, url2, "http://www.notakto.io", 2, false, true);
        ApiCollectionsDao.instance.getMCollection().insertOne(ap1);
        ApiCollectionsDao.instance.getMCollection().insertOne(ap2);
        ApiCollectionsDao.instance.getMCollection().insertOne(ap3);
        ApiCollectionsDao.instance.getMCollection().insertOne(ap4);
        ApiCollectionsDao.instance.getMCollection().insertOne(ap5);

        List<WriteModel<SingleTypeInfo>> bulkWrites = new ArrayList<>();
        bulkWrites.add(createSingleTypeInfoUpdate("/api/book", "GET", SingleTypeInfo.EMAIL, 1,200));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/book", "GET", SingleTypeInfo.EMAIL, 1,-1));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/duck", "GET", SingleTypeInfo.EMAIL, 1,200));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/cat", "GET", SingleTypeInfo.EMAIL, 1,200));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/car", "POST", SingleTypeInfo.EMAIL, 2,200));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/duck", "GET", SingleTypeInfo.EMAIL, 2,200));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/carrot", "GET", SingleTypeInfo.EMAIL, 2,200));
        bulkWrites.add(createSingleTypeInfoUpdate("/api/book", "GET", SingleTypeInfo.EMAIL, 2,200));

        bulkWrites.add(createSingleTypeInfoHostUpdate(1, "/api/book", "GET"));
        bulkWrites.add(createSingleTypeInfoHostUpdate(1, "/api/duck", "GET"));
        bulkWrites.add(createSingleTypeInfoHostUpdate(1, "/api/cat", "GET"));
        bulkWrites.add(createSingleTypeInfoHostUpdate(2, "/api/car", "POST"));
        bulkWrites.add(createSingleTypeInfoHostUpdate(2, "/api/duck", "GET"));
        bulkWrites.add(createSingleTypeInfoHostUpdate(2, "/api/carrot", "GET"));
        bulkWrites.add(createSingleTypeInfoHostUpdate(2, "/api/book", "GET"));
        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkWrites);

        List<ApiInfo> apiInfos = new ArrayList<>();
        apiInfos.add( new ApiInfo(new ApiInfoKey(1, "/api/book", Method.GET)));
        apiInfos.add( new ApiInfo(new ApiInfoKey(1, "/api/duck", Method.GET)));
        apiInfos.add( new ApiInfo(new ApiInfoKey(1, "/api/cat", Method.GET)));
        apiInfos.add( new ApiInfo(new ApiInfoKey(2, "/api/car", Method.POST)));
        apiInfos.add( new ApiInfo(new ApiInfoKey(2, "/api/duck", Method.GET)));
        apiInfos.add( new ApiInfo(new ApiInfoKey(2, "/api/carrot", Method.GET)));
        apiInfos.add( new ApiInfo(new ApiInfoKey(2, "/api/book", Method.GET)));
        ApiInfoDao.instance.insertMany(apiInfos);

        List<SampleData> sampleDatas = new ArrayList<>();
        sampleDatas.add(new SampleData(new Key(1, "/api/book", Method.GET, 200, 0,0), Collections.singletonList("")));
        sampleDatas.add(new SampleData(new Key(1, "/api/duck", Method.GET, 200, 0,0), Collections.singletonList("")));
        sampleDatas.add(new SampleData(new Key(1, "/api/cat", Method.GET, 200, 0,0), Collections.singletonList("")));
        sampleDatas.add(new SampleData(new Key(2, "/api/car", Method.POST, 200, 0,0), Collections.singletonList("")));
        sampleDatas.add(new SampleData(new Key(2, "/api/duck", Method.GET, 200, 0,0), Collections.singletonList("")));
        sampleDatas.add(new SampleData(new Key(2, "/api/carrot", Method.GET, 200, 0,0), Collections.singletonList("")));
        sampleDatas.add(new SampleData(new Key(2, "/api/book", Method.GET, 200, 0,0), Collections.singletonList("")));
        SampleDataDao.instance.insertMany(sampleDatas);

        List<SensitiveSampleData> sensitiveSampleDatas = new ArrayList<>();
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/book", "GET",200, false, "param", SingleTypeInfo.EMAIL, 1, false), Collections.singletonList("")));
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/duck", "GET",200, false, "param", SingleTypeInfo.EMAIL, 1, false), Collections.singletonList("")));
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/cat", "GET",200, false, "param", SingleTypeInfo.EMAIL, 1, false), Collections.singletonList("")));
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/car", "POST",200, false, "param", SingleTypeInfo.EMAIL, 2, false), Collections.singletonList("")));
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/duck", "GET",200, false, "param", SingleTypeInfo.EMAIL, 2, false), Collections.singletonList("")));
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/carrot", "GET",200, false, "param", SingleTypeInfo.EMAIL, 2, false), Collections.singletonList("")));
        sensitiveSampleDatas.add(new SensitiveSampleData(new SingleTypeInfo.ParamId("/api/book", "GET",200, false, "param", SingleTypeInfo.EMAIL, 2, false), Collections.singletonList("")));
        SensitiveSampleDataDao.instance.insertMany(sensitiveSampleDatas);

        List<TrafficInfo> trafficInfos = new ArrayList<>();
        trafficInfos.add(new TrafficInfo(new Key(1, "/api/book", Method.GET, 200, 0,0),new HashMap<>()));
        trafficInfos.add(new TrafficInfo(new Key(1, "/api/duck", Method.GET, 200, 0,0),new HashMap<>()));
        trafficInfos.add(new TrafficInfo(new Key(1, "/api/cat", Method.GET, 200, 0,0),new HashMap<>()));
        trafficInfos.add(new TrafficInfo(new Key(2, "/api/car", Method.POST, 200, 0,0),new HashMap<>()));
        trafficInfos.add(new TrafficInfo(new Key(2, "/api/duck", Method.GET, 200, 0,0),new HashMap<>()));
        trafficInfos.add(new TrafficInfo(new Key(2, "/api/carrot", Method.GET, 200, 0,0),new HashMap<>()));
        trafficInfos.add(new TrafficInfo(new Key(2, "/api/book", Method.GET, 200, 0,0),new HashMap<>()));
        TrafficInfoDao.instance.insertMany(trafficInfos);

        MergeOnHostOnly me = new MergeOnHostOnly();
        me.mergeHosts();

        List<ApiCollection> newCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        assertEquals(2, newCollections.size());
        int newId = newCollections.get(1).getId();

        List<SingleTypeInfo> si = SingleTypeInfoDao.instance.findAll("apiCollectionId",newId);
        List<SingleTypeInfo> siAll = SingleTypeInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(11,si.size());
        assertEquals(11,siAll.size());

        List<ApiInfo> ai = ApiInfoDao.instance.findAll("_id.apiCollectionId",newId);
        List<ApiInfo> aiAll = ApiInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(5,ai.size());
        assertEquals(5,aiAll.size());

        List<SampleData> sa = SampleDataDao.instance.findAll("_id.apiCollectionId",newId);
        List<SampleData> saAll = SampleDataDao.instance.findAll(new BasicDBObject());
        assertEquals(5,sa.size());
        assertEquals(5,saAll.size());

        List<SensitiveSampleData> se = SensitiveSampleDataDao.instance.findAll("_id.apiCollectionId",newId);
        List<SensitiveSampleData> seAll = SensitiveSampleDataDao.instance.findAll(new BasicDBObject());
        assertEquals(5,se.size());
        assertEquals(5,seAll.size());

        List<TrafficInfo> tr = TrafficInfoDao.instance.findAll("_id.apiCollectionId",newId);
        List<TrafficInfo> trAll = TrafficInfoDao.instance.findAll(new BasicDBObject());
        assertEquals(5,tr.size());
        assertEquals(5,trAll.size());
    }
}
