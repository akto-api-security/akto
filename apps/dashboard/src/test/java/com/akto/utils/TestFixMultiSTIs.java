package com.akto.utils;

import com.akto.DaoInit;
import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.akto.utils.scripts.FixMultiSTIs;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestFixMultiSTIs extends MongoBasedTest {

    @Test
    public void testRun() {
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        Context.userId.set(null);

        int urlsCount = 100;
        int paramCountWithoutHost = 9;
        int totalCountPerUrl = 10;

        ApiCollection apiCollection = new ApiCollection(1, "test",0, new HashSet<>(), "akto", 1, false, true);
        ApiCollectionsDao.instance.insertOne(apiCollection);

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

        for (int i=0; i<urlsCount;i++) {
            String url = "api/books_"+i+"/STRING";
            String method = "POST";

            SingleTypeInfo.ParamId paramIdHost = new SingleTypeInfo.ParamId(
                    url, method, -1, true, "host", SingleTypeInfo.GENERIC, apiCollection.getId(), false
            );
            SingleTypeInfo singleTypeInfoHost = new SingleTypeInfo(
                    paramIdHost, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0,10
            );

            singleTypeInfos.add(singleTypeInfoHost);

            for (int param=0; param<paramCountWithoutHost; param++) {
                SingleTypeInfo.SubType subType = param % 2 ==0 ?  SingleTypeInfo.GENERIC : SingleTypeInfo.EMAIL;
                int responseCode = param % 3 == 0 ? -1 : 200;
                SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                        url, method, responseCode, false, param+"", subType, apiCollection.getId(), false
                );
                SingleTypeInfo singleTypeInfo = new SingleTypeInfo(
                        paramId, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0,10
                );
                singleTypeInfos.add(singleTypeInfo);
            }
        }

        if (singleTypeInfos.size() > 0) {
            for (int t=0; t<totalCountPerUrl; t++) {
                for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                    singleTypeInfo.setId(new ObjectId());
                    singleTypeInfo.setTimestamp(t);
                }
                SingleTypeInfoDao.instance.insertMany(singleTypeInfos);
            }
        }

        long estimatedDocumentCount = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(urlsCount * (paramCountWithoutHost+1) * totalCountPerUrl, estimatedDocumentCount);

        long duplicateCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.gt(SingleTypeInfo._TIMESTAMP, 0));
        assertEquals(urlsCount * (paramCountWithoutHost+1) * (totalCountPerUrl-1), duplicateCount);

        Set<Integer> whiteList = new HashSet<>();
        whiteList.add(1000000);
        FixMultiSTIs.run(whiteList);

        // shouldn't affect because whiteList
        duplicateCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.gt(SingleTypeInfo._TIMESTAMP, 0));
        assertEquals(urlsCount * (paramCountWithoutHost+1) * (totalCountPerUrl-1), duplicateCount);

        whiteList.add(apiCollection.getId());
        FixMultiSTIs.run(whiteList);

        estimatedDocumentCount = SingleTypeInfoDao.instance.getEstimatedCount();
        assertEquals(urlsCount * (paramCountWithoutHost + 1), estimatedDocumentCount);

        duplicateCount = SingleTypeInfoDao.instance.getMCollection().countDocuments(Filters.gt(SingleTypeInfo._TIMESTAMP, 0));
        assertEquals(0, duplicateCount);
    }



}
