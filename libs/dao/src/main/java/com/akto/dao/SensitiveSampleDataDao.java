package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.conversions.Bson;

public class SensitiveSampleDataDao extends AccountsContextDaoWithRbac<SensitiveSampleData>{

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

    public static Map<String, Object> getFiltersMap(SingleTypeInfo singleTypeInfo) {
        Map<String, Object> filterMap = new HashMap<>();

        filterMap.put("_id.url", singleTypeInfo.getUrl());
        filterMap.put("_id.method", singleTypeInfo.getMethod());
        filterMap.put("_id.responseCode", singleTypeInfo.getResponseCode());
        filterMap.put("_id.isHeader", singleTypeInfo.getIsHeader());
        filterMap.put("_id.param", singleTypeInfo.getParam());
        filterMap.put("_id.subType", singleTypeInfo.getSubType().getName());
        filterMap.put("_id.apiCollectionId", singleTypeInfo.getApiCollectionId());
        return filterMap;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"_id.url", "_id.apiCollectionId", "_id.method"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { ApiInfo.ID_URL, SingleTypeInfo._COLLECTION_IDS, ApiInfo.ID_METHOD }, true);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { SingleTypeInfo._COLLECTION_IDS }, true);
    }
    private static final Pattern QUERY_PARAM = Pattern.compile("(\\?|&)[^=\\s&]+=[^&\\s]+");

    public static boolean hasAnyQueryParam(String raw, String param) {
        if (raw == null || raw.isEmpty()) return false;

        // Fast path: if there is no '?' at all, there cannot be query params
        int q = raw.indexOf('?');
        if (q < 0) return false;

        // Optional micro filter: if there is a '?' but no '=' after it, likely no params
        if (raw.indexOf('=', q) < 0) return false;
        if(param != null && !param.isEmpty()) {
            String regex = "(?:\\?|&)" + param + "=";
            return raw.matches(".*" + regex + ".*");
        }
        // Fallback to accurate regex
        return QUERY_PARAM.matcher(raw).find();
    }

    public void backFillIsQueryParamInSingleTypeInfo(int apiCollectionId) {
        List<String> subTypes = SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames();
        System.out.println("Subtypes: " + subTypes.size());
        Bson matchFilter = Filters.and(
            Filters.eq(Constants.ID + "." + SingleTypeInfo._RESPONSE_CODE, -1),
            Filters.eq(Constants.ID + "." + SingleTypeInfo._IS_HEADER, false),
            Filters.eq(Constants.ID + "." + SingleTypeInfo._API_COLLECTION_ID, apiCollectionId),
            Filters.in(Constants.ID + "." + SingleTypeInfo.SUB_TYPE, subTypes)
        );
        
        int limit = 100;
        int skip = 0;
        int totalProcessed = 0;
        
        while (true) {
            List<SensitiveSampleData> sensitiveSampleDataList = instance.findAll(matchFilter, skip, limit, Sorts.ascending("_id"));
            
            if (sensitiveSampleDataList == null || sensitiveSampleDataList.isEmpty()) {
                break;
            }
            
            ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo = new ArrayList<>();
            for (SensitiveSampleData sensitiveSampleData : sensitiveSampleDataList) {
                List<String> samples = sensitiveSampleData.getSampleData();
                for (String sample : samples) {
                    if (hasAnyQueryParam(sample, "")) {
                        bulkUpdatesForSingleTypeInfo.add(new UpdateOneModel<>(SingleTypeInfo.getFilterFromParamId(sensitiveSampleData.getId()), Updates.set("isQueryParam", true)));
                        break;
                    }
                }
            }
            
            if(!bulkUpdatesForSingleTypeInfo.isEmpty()) {
                System.out.println("Backfilling isQueryParam for apiCollectionId: " + apiCollectionId + " " + bulkUpdatesForSingleTypeInfo.size() + " single type infos (batch " + (skip/limit + 1) + ")");
                SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForSingleTypeInfo);
            }
            
            totalProcessed += sensitiveSampleDataList.size();
            skip += limit;
            
            // If we got fewer results than the limit, we've reached the end
            if (sensitiveSampleDataList.size() < limit) {
                break;
            }
        }
        if(totalProcessed > 0){
            System.out.println("Completed backfilling isQueryParam. Total records processed: " + totalProcessed + " for apiCollectionId: " + apiCollectionId);
        }
    }

    @Override
    public String getFilterKeyString() {
        return TestingEndpoints.getFilterPrefix(ApiCollectionUsers.CollectionType.Id_ApiCollectionId) + ApiInfo.ApiInfoKey.API_COLLECTION_ID;
    }
}
