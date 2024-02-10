package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.types.CappedSet;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.*;

public class VCDemoData {
    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://52.91.42.30:27017/admini"));
        Context.accountId.set(1_000_000);
        RuntimeFilterDao.instance.initialiseFilters();

//        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), new HashSet<>(), null,0));
        Set<String> urls1 = new HashSet<>();
        for (int i=0; i<8; i++) urls1.add(i+"");
        ApiCollectionsDao.instance.insertOne(new ApiCollection(1, null, Context.now(), urls1,"api.akto.io",0, false, true));
        Set<String> urls2 = new HashSet<>();
        for (int i=0; i<10; i++) urls2.add(i+"");
        ApiCollectionsDao.instance.insertOne(new ApiCollection(2, null, Context.now(), urls2,"staging.akto.io",0, false, true));
        Set<String> urls3 = new HashSet<>();
        for (int i=0; i<9; i++) urls3.add(i+"");
        ApiCollectionsDao.instance.insertOne(new ApiCollection(3, null, Context.now(), urls3,"prod.akto.io",0, false, true));


        List<SingleTypeInfo> singleTypeInfoList = new ArrayList<>();
        List<ApiInfo> apiInfos = new ArrayList<>();
        int acid= 1;

        fill(singleTypeInfoList, apiInfos, "/api/users", SingleTypeInfo.EMAIL, acid, 23);
        fill(singleTypeInfoList, apiInfos, "/api/players", SingleTypeInfo.EMAIL, acid, 29);
        fill(singleTypeInfoList, apiInfos, "/api/users/payment", SingleTypeInfo.CREDIT_CARD, acid, 17);
        fill(singleTypeInfoList, apiInfos, "/api/users/contact", SingleTypeInfo.PHONE_NUMBER, acid,19);
        fill(singleTypeInfoList, apiInfos, "/api/games", SingleTypeInfo.GENERIC, acid, 25);
        fill(singleTypeInfoList, apiInfos, "/api/dashboard", SingleTypeInfo.GENERIC, acid, 29);
        fill(singleTypeInfoList, apiInfos, "/api/config", SingleTypeInfo.GENERIC, acid, 21);
        fill(singleTypeInfoList, apiInfos, "/api/dashboard/INTEGER", SingleTypeInfo.GENERIC, acid,38);
        fill(singleTypeInfoList, apiInfos, "/api/cron", SingleTypeInfo.GENERIC, acid,33);
        fill(singleTypeInfoList, apiInfos, "/api/boards", SingleTypeInfo.GENERIC, acid,33);
        fill(singleTypeInfoList, apiInfos, "/api/inventory", SingleTypeInfo.GENERIC, acid,33);
        fill(singleTypeInfoList, apiInfos, "/api/login", SingleTypeInfo.EMAIL, acid,33);
        fill(singleTypeInfoList, apiInfos, "/api/settings", SingleTypeInfo.GENERIC, acid,33);
        fill(singleTypeInfoList, apiInfos, "/api/events", SingleTypeInfo.GENERIC, acid,33);


        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfoList) {
            Bson filters = SingleTypeInfoDao.createFilters(singleTypeInfo);
            bulkUpdates.add(new UpdateOneModel<>(filters, Updates.set("timestamp", Context.now()), new UpdateOptions().upsert(true)));
        }

        SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
        ApiInfoDao.instance.insertMany(apiInfos);


    }

    public static void fill(List<SingleTypeInfo> singleTypeInfoList, List<ApiInfo> apiInfos, String url, SingleTypeInfo.SubType subType, int acid, int r) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", 200, false, "", subType, acid, false);
        SingleTypeInfo sit = new SingleTypeInfo(paramId, null,null, 0,Context.now(),0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        singleTypeInfoList.add(sit);

        for (int i=0;i < r; i++) {
            SingleTypeInfo.ParamId paramId1 = new SingleTypeInfo.ParamId(url, "GET", 200, false, i+"", SingleTypeInfo.GENERIC, acid, false);
            SingleTypeInfo sit1 = new SingleTypeInfo(paramId1, null,null, 0,Context.now(),0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
            singleTypeInfoList.add(sit1);
        }

        ApiInfo apiInfo = new ApiInfo(acid, url, URLMethods.Method.GET);
        apiInfo.setApiAccessTypes(new HashSet<>(Collections.singleton(ApiInfo.ApiAccessType.PUBLIC)));
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_TOKEN);
        Set<Set<ApiInfo.AuthType>> g = new HashSet<>();
        g.add(s);
        apiInfo.setAllAuthTypesFound(g);
        apiInfos.add(apiInfo);
    }
}
