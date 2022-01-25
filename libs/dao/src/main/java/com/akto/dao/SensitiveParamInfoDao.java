package com.akto.dao;

import com.akto.dto.SensitiveParamInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;
import java.util.Set;

public class SensitiveParamInfoDao extends AccountsContextDao<SensitiveParamInfo> {

    public static final SensitiveParamInfoDao instance = new SensitiveParamInfoDao();

    @Override
    public String getCollName() {
        return "sensitive_param_info";
    }

    @Override
    public Class<SensitiveParamInfo> getClassT() {
        return SensitiveParamInfo.class;
    }

    public Set<String> getUniqueEndpoints(int apiCollectionId) {
        Bson filter = Filters.eq("apiCollectionId", apiCollectionId);
        return instance.findDistinctFields("url", String.class, filter);
    }
}
