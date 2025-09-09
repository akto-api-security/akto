package com.akto.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.opensymphony.xwork2.Action;

public class CleanAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CleanAction.class, LogDb.DASHBOARD);

    private static final ExecutorService service = Executors.newFixedThreadPool(1);

    /*
     * delete api info if corresponding sti not found.
     */

    List<Integer> apiCollectionIds;
    boolean runActually;

    public String deleteExtraApiInfo() {

        int accountId = Context.accountId.get();

        service.submit(() -> {
            Context.accountId.set(accountId);

        List<Bson> deleteFilters = new ArrayList<>();
        for (int apiCollectionId : apiCollectionIds) {
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", apiCollectionId),
                    Projections.include("_id"));

            if(apiInfos == null) {
                loggerMaker.debugAndAddToDb("No API Info found for API Collection Id: " + apiCollectionId);
                continue;
            }

            loggerMaker.debugAndAddToDb("Checking ApiInfos count: " + apiInfos.size());
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
        loggerMaker.debugAndAddToDb("Total API Info to delete: " + deleteFilters.size());

        if (runActually && deleteFilters.size() > 0) {
            loggerMaker.debugAndAddToDb("deleteExtraApiInfo Actually deleting : " + deleteFilters.size());
            DeleteResult res = ApiInfoDao.instance.deleteAll(Filters.or(deleteFilters));
            loggerMaker.debugAndAddToDb("deleteExtraApiInfo Actually deleted : " + res.getDeletedCount());
        }
        
    });

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
            loggerMaker.debugAndAddToDb("STI not found for STI: " + key.toString());
            if (runActually) {
                deleteFilters.add(ApiInfoDao.getFilter(key));
            }
        }

        return deleteFilters;
    }

    private static final String TEMP_RETAIN = "temp_retain";

    public String deleteNonHostSTIs() {

        int accountId = Context.accountId.get();


        service.submit(() -> {
            Context.accountId.set(accountId);

            for (int apiCollectionId : apiCollectionIds) {

                List<SingleTypeInfo> urls = SingleTypeInfoDao.instance
                        .findAll(SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true), Projections.include(
                                SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._URL, SingleTypeInfo._METHOD));

                if (urls != null && !urls.isEmpty()) {

                    loggerMaker.debugAndAddToDb("deleteNonHostSTIs STIs with host found: " + urls.size());
                    List<Bson> filters = new ArrayList<>();
                    for (SingleTypeInfo url : urls) {
                        filters.add(SingleTypeInfoDao.filterForSTIUsingURL(url.getApiCollectionId(), url.getUrl(),
                                Method.valueOf(url.getMethod())));
                    }

                    Bson filter = Filters.and(
                            Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                            Filters.nor(filters));

                    long count = SingleTypeInfoDao.instance.count(filter);

                    loggerMaker.debugAndAddToDb("deleteNonHostSTIs STIs for deletion found: " + count);

                    if (runActually) {

                        try {
                            List<Bson> batchFilter = new ArrayList<>();
                            for (SingleTypeInfo url : urls) {
                                batchFilter.add(
                                        SingleTypeInfoDao.filterForSTIUsingURL(url.getApiCollectionId(), url.getUrl(),
                                                Method.valueOf(url.getMethod())));

                                if (batchFilter.size() >= 50) {
                                    UpdateResult res = SingleTypeInfoDao.instance.updateMany(Filters.or(batchFilter),
                                            Updates.set(TEMP_RETAIN, true));
                                    loggerMaker.debugAndAddToDb("deleteNonHostSTIs temp retain initial update: matched: "
                                            + res.getMatchedCount() + " modified: " + res.getModifiedCount());
                                    batchFilter.clear();
                                }
                            }
                            if (batchFilter.size() > 0) {
                                UpdateResult res = SingleTypeInfoDao.instance.updateMany(Filters.or(batchFilter),
                                        Updates.set(TEMP_RETAIN, true));
                                loggerMaker.debugAndAddToDb("deleteNonHostSTIs temp retain initial update: matched: "
                                        + res.getMatchedCount() + " modified: " + res.getModifiedCount());
                                batchFilter.clear();
                            }

                            Bson deleteFilter = Filters.and(
                                    Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                                    Filters.exists(TEMP_RETAIN, false));

                            long countVerify = SingleTypeInfoDao.instance.count(deleteFilter);

                            if (countVerify == count) {

                                DeleteResult res = SingleTypeInfoDao.instance.deleteAll(deleteFilter);
                                loggerMaker.debugAndAddToDb("deleteNonHostSTIs deleted STIs: " + res.getDeletedCount());

                            } else {
                                loggerMaker.debugAndAddToDb("deleteNonHostSTIs delete count mismatch: " + count
                                        + " deleteCount: " + countVerify);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        return Action.SUCCESS.toUpperCase();
    }

    public String unsetTemp() {
        for (int apiCollectionId : apiCollectionIds) {
            Bson updateFilter = Filters.and(
                    Filters.eq(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
                    Filters.exists(TEMP_RETAIN, true));

            UpdateResult res = SingleTypeInfoDao.instance.updateMany(updateFilter,
                    Updates.unset(TEMP_RETAIN));
            loggerMaker.debugAndAddToDb("unsetTemp temp retain undo update: matched: "
                    + res.getMatchedCount() + " modified: " + res.getModifiedCount());
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteDuplicateHosts(){
        int accountId = Context.accountId.get();
        CONTEXT_SOURCE contextSource = Context.contextSource.get();

        service.submit(() -> {
            Context.accountId.set(accountId);
            Context.contextSource.set(contextSource);
           
        });
        return SUCCESS.toUpperCase();
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
