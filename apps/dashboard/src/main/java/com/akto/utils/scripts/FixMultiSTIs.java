package com.akto.utils.scripts;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLStatic;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

public class FixMultiSTIs {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InventoryAction.class, LogDb.DASHBOARD);;

    public static void run(Set<Integer> whiteList) {
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.getMetaAll();
        for (ApiCollection apiCollection: apiCollectionList) {
            if (apiCollection.getHostName() != null && whiteList.contains(apiCollection.getId())) {
                // because we want to run only for mirroring collections
                try {
                    int start = Context.now();
                    loggerMaker.debugAndAddToDb("Starting fix for collection: " + apiCollection.getId(), LoggerMaker.LogDb.DASHBOARD);
                    fixForCollection(apiCollection.getId());
                    loggerMaker.debugAndAddToDb("Finished fix for collection: " + apiCollection.getId() + " in " + (Context.now() - start) + " seconds" , LoggerMaker.LogDb.DASHBOARD);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while fixing collection: " + apiCollection.getId() + " : " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                }
            }
        }
    }

    public static void fixForCollection(int apiCollectionId) {
        int skip = 0;
        Set<URLStatic> allUrlsInCollection = new HashSet<>();
        int limit = 100;

        for (int i =0; i<1000; i++) { // creating max limit
            List<ObjectId> duplicates = new ArrayList<>();
            Bson filterForHostHeader = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
            Bson filterQ = Filters.and(filterForHostHeader, Filters.regex(SingleTypeInfo._URL, "STRING|INTEGER"));
            List<SingleTypeInfo> stiBatch = SingleTypeInfoDao.instance.findAll(filterQ, skip,limit, null);

            loggerMaker.debugAndAddToDb("stiBatch size: " + stiBatch.size(), LoggerMaker.LogDb.DASHBOARD);

            for (SingleTypeInfo singleTypeInfo: stiBatch) {
                String url = singleTypeInfo.getUrl();
                URLMethods.Method method = URLMethods.Method.valueOf(singleTypeInfo.getMethod());

                // todo: can be removed and put in filter
                boolean isTemplateUrl = APICatalog.isTemplateUrl(url);
                if (!isTemplateUrl) continue;

                URLStatic urlStatic = new URLStatic(url, method);
                if (allUrlsInCollection.contains(urlStatic)) continue;
                allUrlsInCollection.add(urlStatic);

                try {
                    loggerMaker.debugAndAddToDb("Starting fix for url: " + urlStatic, LoggerMaker.LogDb.DASHBOARD);
                    duplicates.addAll(fixForUrl(apiCollectionId, urlStatic));
                    loggerMaker.debugAndAddToDb("Finished fix for url: " + urlStatic, LoggerMaker.LogDb.DASHBOARD);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while fixing url: " + urlStatic + " : " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                }
            }

            // todo: batch
            if (duplicates.size() > 0) {
                loggerMaker.debugAndAddToDb("Delete count : " + duplicates.size(), LoggerMaker.LogDb.DASHBOARD);
                SingleTypeInfoDao.instance.deleteAll(Filters.in("_id", duplicates));
            }

            if (stiBatch.size() == limit) {
                skip += limit;
            } else {
                break;
            }
        }
    }

    public static List<ObjectId> fixForUrl(int apiCollectionId, URLStatic urlStatic) {
        Bson filters = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("url", urlStatic.getUrl()),
                Filters.eq("method", urlStatic.getMethod().name())
        );

        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(filters, 0, 10_000, Sorts.ascending("_id") , Projections.exclude(SingleTypeInfo._VALUES));
        Set<SingleTypeInfo> store = new HashSet<>();
        List<ObjectId> duplicates = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            if (store.contains(singleTypeInfo)) {
                duplicates.add(singleTypeInfo.getId());
            } else {
                store.add(singleTypeInfo);
            }
        }


        return duplicates;
    }

}
