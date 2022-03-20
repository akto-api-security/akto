package com.akto.dao;

import com.akto.dto.Relationship;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class SensitiveSampleDataDao extends AccountsContextDao<SensitiveSampleData>{

    public static final SensitiveSampleDataDao instance = new SensitiveSampleDataDao();
    @Override
    public String getCollName() {
        return "sensitive_sample_data";
    }

    @Override
    public Class<SensitiveSampleData> getClassT() {
        return SensitiveSampleData.class;
    }

    public static Bson getFilters(SingleTypeInfo singleTypeInfo) {
        return Filters.and(
                Filters.eq("_id.url", singleTypeInfo.getUrl()),
                Filters.eq("_id.method", singleTypeInfo.getMethod()),
                Filters.eq("_id.responseCode", singleTypeInfo.getResponseCode()),
                Filters.eq("_id.isHeader", singleTypeInfo.getIsHeader()),
                Filters.eq("_id.param", singleTypeInfo.getParam()),
                Filters.eq("_id.subType", singleTypeInfo.getSubType()),
                Filters.eq("_id.apiCollectionId", singleTypeInfo.getApiCollectionId())
        );
    }
}
