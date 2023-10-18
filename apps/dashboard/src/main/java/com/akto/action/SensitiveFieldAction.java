package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.RelationshipDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Relationship;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.*;

public class SensitiveFieldAction extends UserAction{

    private String url;
    private String method;
    private int responseCode;
    private boolean isHeader;
    private String param;
    private int apiCollectionId;
    private boolean sensitive;
    
    private BasicDBObject ret;

    @Override
    public String execute() {
        ret = new BasicDBObject();
        Bson filter = SensitiveParamInfoDao.getFilters(url, method, responseCode, isHeader, param, apiCollectionId);
        // null means user wants Akto to decide the sensitivity
        if (!sensitive)  {
            SensitiveParamInfoDao.instance.getMCollection().deleteOne(filter);
            return Action.SUCCESS.toUpperCase();
        }

        Bson update = Updates.combine(
            Updates.set("sensitive", sensitive),
            Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId))
        );

        FindOneAndUpdateOptions findOneAndUpdateOptions = new FindOneAndUpdateOptions();
        findOneAndUpdateOptions.upsert(false);
        SensitiveParamInfo param = SensitiveParamInfoDao.instance.updateOne(filter, update);

        ret.append("data", param);

        // only if sensitive is marked true then only find other params
        if (!sensitive) {
            return Action.SUCCESS.toUpperCase();
        }

        /*

        Bson parentFilters = Filters.and(
                Filters.eq("parent.url", url),
                Filters.eq("parent.method", method),
                Filters.eq("parent.isHeader", isHeader),
                Filters.eq("parent.param", param)
        );

        Bson childFilters = Filters.and(
                Filters.eq("child.url", url),
                Filters.eq("child.method", method),
                Filters.eq("child.isHeader", isHeader),
                Filters.eq("child.param", param)
        );

        // find all relationships based on either parent or child matches with the param user marked sensitive
        List<Relationship> relationshipList =  RelationshipDao.instance.findAll(
                Filters.or(parentFilters,childFilters)
        );

        // either parent or child will match. We take parent to be default and if it matches then child is the related one
        List<Bson> filtersForSingleTypeInfo = new ArrayList<>();
        for (Relationship relationship: relationshipList) {
            Relationship.ApiRelationInfo main = relationship.getParent();
            if (Objects.equals(main.getUrl(), url) && Objects.equals(main.getMethod(), method) &&
                    Objects.equals(main.getParam(), param) && main.isHeader() == isHeader && main.getResponseCode() == responseCode) {
                main = relationship.getChild();
            }
            filtersForSingleTypeInfo.add(getFiltersForApiRelation(main));
        }

        if (filtersForSingleTypeInfo.isEmpty()) {
            return Action.SUCCESS.toUpperCase();
        }

        relatedSingleTypeInfo = SingleTypeInfoDao.instance.findAll(Filters.or(filtersForSingleTypeInfo));

        if (relatedSingleTypeInfo.isEmpty()) {
            return Action.SUCCESS.toUpperCase();
        }

        // find and remove those which user has already marked sensitive
        List<Bson> filtersForAdditionalSensitiveParams = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: relatedSingleTypeInfo) {
            filtersForAdditionalSensitiveParams.add(
                    getFilters(
                            singleTypeInfo.getUrl(), singleTypeInfo.getMethod(), singleTypeInfo.getResponseCode(),
                            singleTypeInfo.isIsHeader(), singleTypeInfo.getParam()
                    )
            );
        }

        List<SensitiveParamInfo> sensitiveParamInfoList = SensitiveParamInfoDao.instance.findAll(
                Filters.or(filtersForAdditionalSensitiveParams)
        );

        Set<String> sensitiveParamInfoSet = new HashSet<>();
        for (SensitiveParamInfo sensitiveParamInfo: sensitiveParamInfoList) {
            String key = sensitiveParamInfo.getUrl() + "." + sensitiveParamInfo.getMethod() + "." + sensitiveParamInfo.getParam() + "." + sensitiveParamInfo.getResponseCode();
            sensitiveParamInfoSet.add(key);
        }

        Iterator<SingleTypeInfo> i = relatedSingleTypeInfo.iterator();
        while (i.hasNext()) {
            SingleTypeInfo singleTypeInfo = i.next();
            String key = singleTypeInfo.getUrl() + "." + singleTypeInfo.getMethod() + "." + singleTypeInfo.getParam() + "." + singleTypeInfo.getResponseCode();
            if (sensitiveParamInfoSet.contains(key)) {
                i.remove();
            }
        }

        */ 

        return Action.SUCCESS.toUpperCase();
    }


    public String listAllSensitiveFields() {
        List<SensitiveParamInfo> sensitiveParams = SensitiveParamInfoDao.instance.findAll(Filters.eq("sensitive", true));
        ret = new BasicDBObject();
        ret.append("data", sensitiveParams);
        return Action.SUCCESS.toUpperCase();
    }


    // private static Bson getFiltersForApiRelation(Relationship.ApiRelationInfo apiRelationInfo) {
    //     return getFilters(apiRelationInfo.getUrl(), apiRelationInfo.getMethod(), apiRelationInfo.getResponseCode(),
    //             apiRelationInfo.isHeader(), apiRelationInfo.getParam());
    // }

    private BasicDBList items;
    public String bulkMarkSensitive() {
        ArrayList<WriteModel<SensitiveParamInfo>> bulkUpdates = new ArrayList<>();
        for (Object item: items) {
            HashMap<String, Object> xObj = (HashMap) item;
            String url = xObj.get("url").toString();
            String method = xObj.get("method").toString();
            long responseCode = (Long) xObj.get("responseCode");
            boolean isHeader =  (Boolean) xObj.get("isHeader");
            String param = xObj.get("param").toString();
            long apiCollectionId = Long.parseLong(xObj.get("apiCollectionId").toString());
            Bson filter = SensitiveParamInfoDao.getFilters(url, method, (int) responseCode, isHeader, param, (int) apiCollectionId);

            Bson bson = Updates.combine(
            Updates.set("sensitive", sensitive),
            Updates.setOnInsert(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId))
        );
            
            bulkUpdates.add(
                new UpdateOneModel<>(filter, bson, new UpdateOptions().upsert(true))
            );
        }

        BulkWriteResult res = SensitiveParamInfoDao.instance.getMCollection().bulkWrite(bulkUpdates);
        ret = new BasicDBObject();
        ret.append("updates", res.getModifiedCount()).append("inserts", res.getInsertedCount());
        return Action.SUCCESS.toUpperCase();
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public void setIsHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public void setApiCollectionId (int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public void setRet(BasicDBObject ret) {
        this.ret = ret;
    }

    public BasicDBObject getRet() {
        return this.ret;
    }

    public void setItems(BasicDBList items) {
        this.items = items;
    }
}
