package com.akto.dao;

import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
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

    public static Bson getFilters(SensitiveParamInfo sensitiveParamInfo) {
        return getFilters(sensitiveParamInfo.getUrl(),sensitiveParamInfo.getMethod(),
                sensitiveParamInfo.getResponseCode(), sensitiveParamInfo.isIsHeader(),
                sensitiveParamInfo.getParam(), sensitiveParamInfo.getApiCollectionId());
    }

    public static Bson getFilters(String url, String method, int responseCode, boolean isHeader, String param, int apiCollectionId) {
        List<Bson> defaultFilters = new ArrayList<>();
        defaultFilters.add(Filters.eq("url", url));
        defaultFilters.add(Filters.eq("method", method));
        defaultFilters.add(Filters.eq("isHeader", isHeader));
        defaultFilters.add(Filters.eq("param", param));
        defaultFilters.add(Filters.eq("responseCode", responseCode));
        defaultFilters.add(Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId)));

        return Filters.and(defaultFilters);
    }

    public Set<String> getUniqueEndpoints(int apiCollectionId) {
        Bson filter = Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId));
        return instance.findDistinctFields("url", String.class, filter);
    }

    public List<SensitiveParamInfo> getUnsavedSensitiveParamInfos() {
        return SensitiveParamInfoDao.instance.findAll(
                Filters.and(
                        Filters.or(
                                Filters.eq(SensitiveParamInfo.SAMPLE_DATA_SAVED,false),
                                Filters.not(Filters.exists(SensitiveParamInfo.SAMPLE_DATA_SAVED))
                        ),
                        Filters.eq(SensitiveParamInfo.SENSITIVE, true)
                )
        );
    }


}
