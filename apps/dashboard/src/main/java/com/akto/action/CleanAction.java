package com.akto.action;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.DaoInit;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.DeleteResult;
import com.opensymphony.xwork2.Action;

public class CleanAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanAction.class, LogDb.DASHBOARD);

    /*
     * delete api info if corresponding sti not found.
     */

    List<Integer> apiCollectionIds;
    boolean runActually;

    public String deleteExtraApiInfo() {

        List<ApiInfoKey> apiInfoKeys = new ArrayList<>();
        int count = 0;
        for (int apiCollectionId : apiCollectionIds) {
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", apiCollectionId),
                    Projections.include("_id"));

            if(apiInfos == null) {
                loggerMaker.infoAndAddToDb("No API Info found for API Collection Id: " + apiCollectionId);
                continue;
            }

            loggerMaker.infoAndAddToDb("Checking ApiInfos count: " + apiInfos.size());
            for (ApiInfo apiInfo : apiInfos) {
                ApiInfoKey key = apiInfo.getId();

                SingleTypeInfo sti = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao
                        .filterForSTIUsingURL(key.getApiCollectionId(), key.getUrl(), key.getMethod()));

                if (sti != null) {
                    continue;
                }
                count++;

                loggerMaker.infoAndAddToDb("No STI found for API Info: " + key.toString());

                if (runActually) {
                    apiInfoKeys.add(key);
                }
            }
        }
        loggerMaker.infoAndAddToDb("Total API Info to delete: " + count);

        if (runActually && apiInfoKeys.size() > 0) {
            List<Bson> filters = new ArrayList<>();
            for(ApiInfoKey key : apiInfoKeys) {
                filters.add(ApiInfoDao.getFilter(key));
            }
            loggerMaker.infoAndAddToDb("deleteExtraApiInfo Actually deleting : " + count);
            DeleteResult res = ApiInfoDao.instance.deleteAll(Filters.or(filters));
            loggerMaker.infoAndAddToDb("deleteExtraApiInfo Actually deleted : " + res.getDeletedCount());
        }

        return Action.SUCCESS.toUpperCase();
    }

    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    public boolean getRunActually() {
        return runActually;
    }

    public void setRunActually(boolean runActually) {
        this.runActually = runActually;
    }
}
