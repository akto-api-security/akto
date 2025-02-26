package com.akto.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.User;
import com.akto.dto.traffic.Key;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;

public class InternalAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(InternalAction.class, LogDb.DASHBOARD);

    boolean actuallyDelete;
    int count;
    int timestamp;

    public String deleteApisWithoutHost() {

        User user = getSUser();
        if (user.getLogin() != null && !user.getLogin().contains("@akto")) {
            addActionError("Illegal operation");
            return ERROR.toUpperCase();
        }

        List<ApiCollection> apiCollections = ApiCollectionsDao.fetchAllHosts();

        Set<Key> keys = new HashSet<>();
        int time = Context.nowInMillis();
        int delta = Context.nowInMillis();
        Set<Integer> demosAndDeactivated = UsageMetricCalculator.getDemosAndDeactivated();
        for (ApiCollection apiCollection : apiCollections) {

            
            int apiCollectionId = apiCollection.getId();
            if (demosAndDeactivated.contains(apiCollectionId)) {
                loggerMaker.infoAndAddToDb("Skipping deleteApisBasedOnHeader for apiCollectionId " + apiCollectionId);
                continue;
            }
            
            List<Bson> pipeline = Arrays.asList(new Document("$match",
                    new Document("$and", Arrays.asList(new Document("$or", Arrays.asList(
                            new Document("lastSeen", new Document("$gt", timestamp)),
                            new Document("timestamp", new Document("$gt", timestamp)))),
                            new Document("apiCollectionId", apiCollectionId)))),
                    new Document("$group",
                            new Document("_id",
                                    new Document("apiCollectionId", "$apiCollectionId")
                                            .append("url", "$url")
                                            .append("method", "$method"))
                                    .append("hasHostParam",
                                            new Document("$max",
                                                    new Document("$cond",
                                                            Arrays.asList(new Document("$eq",
                                                                    Arrays.asList("$param", "host")), 1L, 0L))))
                                    .append("documents",
                                            new Document("$push", "$$ROOT"))),
                    new Document("$match",
                            new Document("hasHostParam",
                                    new Document("$eq", 0L))),
                    new Document("$project",
                            new Document("_id", 1L)));

            time = Context.nowInMillis();
            loggerMaker.infoAndAddToDb("Executing deleteApisBasedOnHeader find query " + apiCollectionId);
            List<ApiInfoKey> apiList = SingleTypeInfoDao.instance.processPipelineForEndpoint(pipeline);
            delta = Context.nowInMillis() - time;
            loggerMaker.infoAndAddToDb("Finished deleteApisBasedOnHeader find query " + delta + " " + apiCollectionId);
            if (apiList != null && !apiList.isEmpty()) {
                for (ApiInfoKey apiInfoKey : apiList) {
                    loggerMaker.infoAndAddToDb("deleteApisBasedOnHeader " + apiInfoKey.toString());
                    keys.add(
                            new Key(apiInfoKey.getApiCollectionId(), apiInfoKey.getUrl(), apiInfoKey.getMethod(), -1, 0,
                                    0));
                }
            }
        }

        count = keys.size();
        if (keys != null && !keys.isEmpty() && actuallyDelete) {
            try {
                time = Context.nowInMillis();
                loggerMaker.infoAndAddToDb("deleteApisBasedOnHeader deleting APIs");
                List<Key> keyList = new ArrayList<>();
                keyList.addAll(keys);
                com.akto.utils.Utils.deleteApis(keyList);
                delta = Context.nowInMillis() - time;
                loggerMaker.infoAndAddToDb("deleteApisBasedOnHeader deleted APIs " + delta);
            } catch (Exception e) {
                e.printStackTrace();
                addActionError("Error deleting APIs");
                return ERROR.toUpperCase();
            }
        }

        return SUCCESS.toUpperCase();
    }

    public boolean getActuallyDelete() {
        return actuallyDelete;
    }

    public void setActuallyDelete(boolean actuallyDelete) {
        this.actuallyDelete = actuallyDelete;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}
