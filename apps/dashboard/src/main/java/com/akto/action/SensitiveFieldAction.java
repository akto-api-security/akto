package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.RelationshipDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Relationship;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Updates;
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
        Bson filter = getFilters(url, method, responseCode, isHeader, param, apiCollectionId);
        // null means user wants Akto to decide the sensitivity
        if (!sensitive)  {
            SensitiveParamInfoDao.instance.getMCollection().deleteOne(filter);
            return Action.SUCCESS.toUpperCase();
        }

        Bson update = Updates.set("sensitive", sensitive);
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

    public static Bson getFilters(String url, String method, int responseCode, boolean isHeader, String param, int apiCollectionId) {
        List<Bson> defaultFilters = new ArrayList<>();
        defaultFilters.add(Filters.eq("url", url));
        defaultFilters.add(Filters.eq("method", method));
        defaultFilters.add(Filters.eq("isHeader", isHeader));
        defaultFilters.add(Filters.eq("param", param));
        defaultFilters.add(Filters.eq("responseCode", responseCode));
        defaultFilters.add(Filters.eq("apiCollectionId", apiCollectionId));

        return Filters.and(defaultFilters);
    }

    // private static Bson getFiltersForApiRelation(Relationship.ApiRelationInfo apiRelationInfo) {
    //     return getFilters(apiRelationInfo.getUrl(), apiRelationInfo.getMethod(), apiRelationInfo.getResponseCode(),
    //             apiRelationInfo.isHeader(), apiRelationInfo.getParam());
    // }


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
}
