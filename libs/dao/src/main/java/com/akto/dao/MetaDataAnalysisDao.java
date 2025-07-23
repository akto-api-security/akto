package com.akto.dao;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.MetaDataAnalysis;
import com.akto.util.Pair;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class MetaDataAnalysisDao extends CommonContextDao<MetaDataAnalysis> {

    private static final int THRESHOLD_VALUE = 5; // threshold for number of headers to be stored in the map

    public static final MetaDataAnalysisDao instance = new MetaDataAnalysisDao();
    private final AtomicInteger lastSyncedAt = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, String> finalHeadersTechMap = new ConcurrentHashMap<>();
    // need to store the count as well, taking into account the threshold, by reaching the threshold, we will add the header to the finalHeadersTechMap
    private static final ConcurrentHashMap<String, Pair<String,Integer>> headersTechMapForAnalysis = new ConcurrentHashMap<>();

    @Override
    public String getCollName() {
        return "meta_data_analysis";
    }

    @Override
    public Class<MetaDataAnalysis> getClassT() {
        return MetaDataAnalysis.class;
    }

    private void checkAndSyncHeadersMapWithDB(){
        if(finalHeadersTechMap.isEmpty() || (Context.now() - lastSyncedAt.get() > 15 * 60)){
            // sync with DB
            MetaDataAnalysis headersAnalysis = instance.findOne(
                Filters.eq(MetaDataAnalysis.ANALYSIS_TYPE, "headers_analysis")
            );
            Bson updates = Updates.set(MetaDataAnalysis.LAST_SYNCED_AT, Context.now());
            if(finalHeadersTechMap.isEmpty()){
                // initializing when the dashboard restarts
                if (headersAnalysis != null && headersAnalysis.getAnalysisData() != null) {
                    headersAnalysis.getAnalysisData().forEach((k, v) -> finalHeadersTechMap.put(k, (String) v));
                }
            }else{
                // updating the last synced at time
                updates = Updates.combine(updates, Updates.set(MetaDataAnalysis.ANALYSIS_DATA, finalHeadersTechMap));
            }
            instance.updateOne(
                Filters.eq(MetaDataAnalysis.ANALYSIS_TYPE, "headers_analysis"),
                updates
            );
            lastSyncedAt.set(Context.now());
        }
    }
    
    public void insertHeaderForAnalysis(String header, String value) {
        headersTechMapForAnalysis.compute(header, (k, v) -> {
            if (v == null) {
                return new Pair<>(value, 1);
            } else {
                int count = v.getSecond() + 1;
                if (count >= THRESHOLD_VALUE) {
                    finalHeadersTechMap.put(k, v.getFirst());
                    headersTechMapForAnalysis.remove(k);
                    return null; // remove from headersTechMapForAnalysis
                }
                return new Pair<>(v.getFirst(), count);
            }
        });
    }

    public String getValueFromFinalHeadersTechMap(String header) {
        checkAndSyncHeadersMapWithDB();
        return finalHeadersTechMap.get(header);
    }
}
