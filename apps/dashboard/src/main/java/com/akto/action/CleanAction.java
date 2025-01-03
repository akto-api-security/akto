package com.akto.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
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

        List<Bson> deleteFilters = new ArrayList<>();
        for (int apiCollectionId : apiCollectionIds) {
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", apiCollectionId),
                    Projections.include("_id"));

            if(apiInfos == null) {
                loggerMaker.infoAndAddToDb("No API Info found for API Collection Id: " + apiCollectionId);
                continue;
            }

            loggerMaker.infoAndAddToDb("Checking ApiInfos count: " + apiInfos.size());
            List<ApiInfoKey> filters = new ArrayList<>();
            for (ApiInfo apiInfo : apiInfos) {
                ApiInfoKey key = apiInfo.getId();

                filters.add(key);

                if (filters.size() >= 100) {
                    deleteFilters.addAll(checkSTIs(filters, runActually));
                    filters.clear();
                }
            }
            if (!filters.isEmpty()) {
                deleteFilters.addAll(checkSTIs(filters, runActually));
            }
        }
        loggerMaker.infoAndAddToDb("Total API Info to delete: " + deleteFilters.size());

        if (runActually && deleteFilters.size() > 0) {
            loggerMaker.infoAndAddToDb("deleteExtraApiInfo Actually deleting : " + deleteFilters.size());
            DeleteResult res = ApiInfoDao.instance.deleteAll(Filters.or(deleteFilters));
            loggerMaker.infoAndAddToDb("deleteExtraApiInfo Actually deleted : " + res.getDeletedCount());
        }

        return Action.SUCCESS.toUpperCase();
    }

    private static List<Bson> checkSTIs(List<ApiInfoKey> filters, boolean runActually) {
        List<Bson> deleteFilters = new ArrayList<>();
        List<Bson> filters2 = new ArrayList<>();
        for(ApiInfoKey key : filters) {
            filters2.add(SingleTypeInfoDao.filterForSTIUsingURL(key.getApiCollectionId(), key.getUrl(), key.getMethod()));
        }
        List<SingleTypeInfo> sti = SingleTypeInfoDao.instance.findAll(Filters.or(filters2));
        HashSet<ApiInfoKey> stiSet = new HashSet<>();
        if (sti != null && !sti.isEmpty()) {
            for (SingleTypeInfo st : sti) {
                stiSet.add(new ApiInfoKey(st.getApiCollectionId(), st.getUrl(), Method.valueOf(st.getMethod())));
            }
        }
        for(ApiInfoKey key : filters) {
            if(stiSet.contains(key)) {
                continue;
            }
            loggerMaker.infoAndAddToDb("STI not found for STI: " + key.toString());
            if (runActually) {
                deleteFilters.add(ApiInfoDao.getFilter(key));
            }
        }

        return deleteFilters;
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
