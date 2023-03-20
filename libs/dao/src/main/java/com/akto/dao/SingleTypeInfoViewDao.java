package com.akto.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dto.testing.SingleTypeInfoView;
import com.mongodb.client.model.Filters;

public class SingleTypeInfoViewDao extends AccountsContextDao<SingleTypeInfoView>{
    
    public static final SingleTypeInfoViewDao instance = new SingleTypeInfoViewDao();
    @Override
    public String getCollName() {
        return "single_type_info_view";
    }

    @Override
    public Class<SingleTypeInfoView> getClassT() {
        return SingleTypeInfoView.class;
    }

    public List<String> calculateAuthTypes(SingleTypeInfoView singleTypeInfoView) {

        List<String> result = new ArrayList<>();

        Set<String> uniqueAuthTypes = new HashSet<>();

        List<String> authTypes = singleTypeInfoView.getAllAuthTypes();

        if (authTypes == null) {
            return new ArrayList<>();
        }

        if (authTypes.contains("UNAUTHENTICATED")) {
            result.add("UNAUTHENTICATED");
            return result;
        }

        for (String authType: singleTypeInfoView.getAllAuthTypes()) {
            if (uniqueAuthTypes.contains(authType)) {
                continue;
            }
            uniqueAuthTypes.add(authType);
            result.add(authType);
        }

        return result;
    }

    public int getUrlCount(int apiCollectionId) {
        Bson filters = Filters.eq("_id.apiCollectionId", apiCollectionId);
        return (int) SingleTypeInfoViewDao.instance.findCount(filters);
    }

    public List<String> calculateSensitiveTypes(SingleTypeInfoView singleTypeInfoView) {

        Set<String> sensitiveParams = new HashSet<>();
        List<String> result = new ArrayList<>();
        
        for (String param: singleTypeInfoView.getReqSubTypes()) {
            if (isSensitive(param, true)) {
                sensitiveParams.add(param);
            }
        }

        for (String param: singleTypeInfoView.getRespSubTypes()) {
            if (isSensitive(param, false)) {
                sensitiveParams.add(param);
            }
        }

        for (String param: sensitiveParams) {
            result.add(param);
        }

        return result;
    }

    public Boolean isSensitive(String param, Boolean isReqSubtype) {
        
        List<String> alwaysSensitiveSubTypes = SingleTypeInfoDao.instance.sensitiveSubTypeNames();

        List<String> sensitiveInResponse = SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames();
        List<String> sensitiveInRequest = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();

        if (isReqSubtype) {
            if (sensitiveInRequest.contains(param) || alwaysSensitiveSubTypes.contains(param)) {
                return true;
            }
        } else {
            if (sensitiveInResponse.contains(param) || alwaysSensitiveSubTypes.contains(param)) {
                return true;
            }
        }

        return false;
    }

}
