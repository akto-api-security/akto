package com.akto.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.function.FailableFunction;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

public class CalculateJob {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CalculateJob.class, LogDb.DASHBOARD);

    public static void apiInfoUpdateJob(FailableFunction<ApiInfo, UpdateOneModel<ApiInfo>, Exception> function) {
        List<WriteModel<ApiInfo>> apiInfosUpdates = new ArrayList<>();

        int skip = 0;
        int limit = 1000;
        boolean fetchMore = false;
        do {
            fetchMore = false;
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(new BasicDBObject(), skip, limit,
                    Sorts.descending(Constants.ID));
            loggerMaker.infoAndAddToDb("Read " + (apiInfos.size() + skip) + " api infos for calc job",
                    LogDb.DASHBOARD);
            for (ApiInfo apiInfo : apiInfos) {
                try {
                    UpdateOneModel<ApiInfo> update = function.apply(apiInfo);
                    if (update != null) {
                        apiInfosUpdates.add(update);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in calculating api info update for update job for " + apiInfo.getId().toString());
                }
            }

            if (apiInfos.size() == limit) {
                skip += limit;
                fetchMore = true;
            }

            loggerMaker.infoAndAddToDb("Finished " + (apiInfos.size() + skip) + " api infos for calc job", LogDb.DASHBOARD);

        } while (fetchMore);

        if (apiInfosUpdates.size() > 0) {
            loggerMaker.infoAndAddToDb("Updating " + (apiInfosUpdates.size()) + " api infos for calc job", LogDb.DASHBOARD);
            ApiInfoDao.instance.getMCollection().bulkWrite(apiInfosUpdates);
        }
    }

}
