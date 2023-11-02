package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                Filters.eq("_id.subType", singleTypeInfo.getSubType().getName()),
                Filters.eq("_id.apiCollectionId", singleTypeInfo.getApiCollectionId())
        );
    }

    public void createIndicesIfAbsent() {

        String dbName = Context.accountId.get() + "";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        List<Bson> indices = new ArrayList<>(Arrays.asList(
                Indexes.ascending(new String[] {
                        ApiInfo.ID_URL,
                        ApiInfo.ID_API_COLLECTION_ID,
                        ApiInfo.ID_METHOD }),
                Indexes.ascending(new String[] {
                        ApiInfo.ID_URL,
                        SingleTypeInfo._COLLECTION_IDS,
                        ApiInfo.ID_METHOD })
            ));

        createIndices(indices);
    }
}
