package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MoveSampleDataToSTI {

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
                id.getUrl(), id.getMethod().name(),-1, true, "host", SingleTypeInfo.GENERIC, id.getApiCollectionId()
            );
            SingleTypeInfo singleTypeInfo = new SingleTypeInfo(
                    paramId,new HashSet<>(), new HashSet<>(), 0, Context.now(), 0
            );
            Bson updateKey = SingleTypeInfoDao.createFilters(singleTypeInfo);
            Bson update = Updates.set("timestamp", Context.now());
            bulkUpdates.add(new UpdateOneModel<>(updateKey, update, new UpdateOptions().upsert(true)));
        }

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);

        List<ApiCollection> apiCollectionList = new ArrayList<>();
        for (Integer i: aci) {
            apiCollectionList.add(new ApiCollection(i, i+"", 0, new HashSet<>(), null, i));
        }
        ApiCollectionsDao.instance.insertMany(apiCollectionList);
    }
}
