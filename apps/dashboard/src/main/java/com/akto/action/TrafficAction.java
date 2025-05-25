package com.akto.action;

import java.util.*;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.DaoInit;
import com.akto.action.metrics.MetricsAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.MCollection;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dao.threat_detection.IpLevelApiHitCountDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.threat_detection.IpLevelApiHitCount;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

public class TrafficAction {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(TrafficAction.class);
    int apiCollectionId;
    String url;
    String method;
    int startEpoch;
    int endEpoch;
    int skip;
    int limit;
    int startTs;
    int endTs;
    
    @Getter
    @Setter
    Map<String, Object> result = new HashMap<>();

    Map<Integer, Integer> traffic = new HashMap<>();

    public String fetchEndpointTrafficData() {
        traffic = new HashMap<>();
        List<TrafficInfo> trafficInfoList = TrafficInfoDao.instance.findAll(Filters.and(
            Filters.eq("_id.url", url),
            Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
            Filters.eq("_id.responseCode", -1),
            Filters.eq("_id.method", method),
            Filters.gte("_id.bucketStartEpoch", startEpoch/3600/24/30),
            Filters.lte("_id.bucketEndEpoch", endEpoch/3600/24/30 + 1)
        ));

        for(TrafficInfo trafficInfo: trafficInfoList) {
            for(Map.Entry<String, Integer> entry: trafficInfo.mapHoursToCount.entrySet()) {
                int count = entry.getValue();
                int startEpoch = Integer.parseInt(entry.getKey()) * 3600;
                int yyyyMMdd = Context.convertEpochToDateInt(startEpoch, "US/Pacific");
                traffic.compute(yyyyMMdd, (k, v) -> count + (v == null ? 0 : v));
            }
        }

        return Action.SUCCESS.toUpperCase();
    }

    List<SampleData> sampleDataList;
    public String fetchSampleData() {
        traffic = new HashMap<>();
        sampleDataList = new ArrayList<>();
        sampleDataList = SampleDataDao.instance.findAll(Filters.and(
            Filters.eq("_id.url", url),
            Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
            Filters.eq("_id.responseCode", -1),
            Filters.eq("_id.method", method),
            Filters.gte("_id.bucketStartEpoch", 0),
            Filters.lte("_id.bucketEndEpoch", 0)
        ));
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchAllSampleData() {
        sampleDataList = SampleDataDao.instance.findAll(Filters.eq(Constants.ID + "." + ApiInfoKey.API_COLLECTION_ID, apiCollectionId), skip, limit == 0 ? 50 : limit, null);
        return Action.SUCCESS.toUpperCase();
    }

    Map<String, List<SingleTypeInfo.ParamId>> sensitiveSampleData = new HashMap<>();
    public String fetchSensitiveSampleData() {
        List<SensitiveSampleData> sensitiveSampleDataList = SensitiveSampleDataDao.instance.findAll(
                Filters.and(
                        Filters.eq("_id.url", url),
                        Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionId),
                        Filters.eq("_id.method", method)
                )
        );

        for (SensitiveSampleData sensitiveSampleData: sensitiveSampleDataList) {
            if (sensitiveSampleData.getInvalid()) {
                continue;
            }
            for (String data: sensitiveSampleData.getSampleData()) {
                List<SingleTypeInfo.ParamId> s = this.sensitiveSampleData.getOrDefault(data, new ArrayList<>());
                s.add(sensitiveSampleData.getId());
                this.sensitiveSampleData.put(data, s);
            }
        }


        return Action.SUCCESS.toUpperCase();
    }

    public String fetchApiCallStats() {
        try {
            int startTs = startEpoch/60;
            int endTs = endEpoch/60;
            Bson filters = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("method", method),
                Filters.eq("url", url),
                Filters.gte("ts", startTs),
                Filters.lte("ts", endTs)
            );
            Bson projection = Projections.include("ts", "count");
            List<ApiHitCountInfo> apiHitCountInfos = ApiHitCountInfoDao.instance.findAll(filters, projection);
            if (apiHitCountInfos == null || apiHitCountInfos.size() == 0) {
                loggerMaker.infoAndAddToDb("No api call metrics found for apicollection " + apiCollectionId + " url " + url + " method " + method, LogDb.DASHBOARD);
                result.put("apiCallStats", new ArrayList<>());
                return Action.SUCCESS.toUpperCase();
            }
            result.put("apiCallStats", apiHitCountInfos);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = "Error fetching api call stats: apicollection " + apiCollectionId + " url " + url + " method " + method + " error " + e.getMessage();
            loggerMaker.errorAndAddToDb(errMsg, LogDb.DASHBOARD);
            result.put("error", errMsg);
            return Action.ERROR.toUpperCase();
        }
    }

    public String fetchIpLevelApiCallStats() {
        try {
            int startTs = startEpoch / 60;
            int endTs = endEpoch / 60;
    
            Bson filters = Filters.and(
                Filters.eq("apiCollectionId", apiCollectionId),
                Filters.eq("method", method),
                Filters.eq("url", url),
                Filters.gte("ts", startTs),
                Filters.lte("ts", endTs)
            );
    
            Bson projection = Projections.include("ts", "ipCount");
    
            List<IpLevelApiHitCount> docs = IpLevelApiHitCountDao.instance.findAll(filters, projection);

            if (docs == null || docs.isEmpty()) {
                loggerMaker.infoAndAddToDb("No ip level api call metrics found for apicollection " + apiCollectionId + " url " + url + " method " + method, LogDb.DASHBOARD);
                result.put("apiCallStats", new ArrayList<>());
                return Action.SUCCESS.toUpperCase();
            }
    
            Map<String, Integer> ipCountMap = new HashMap<>();
            for (IpLevelApiHitCount doc : docs) {
                for (Map.Entry<String, Integer> entry : doc.getIpCount().entrySet()) {
                    String ip = entry.getKey();
                    int count = (int) entry.getValue();
                    ipCountMap.merge(ip, count, Integer::sum);
                }
            }
    
            // Reverse map: count -> number of IPs with that count
            Map<Integer, Integer> countToUserCount = new HashMap<>();
            for (int count : ipCountMap.values()) {
                countToUserCount.merge(count, 1, Integer::sum);
            }
    
            // Build final list
            List<Map<String, Object>> apiCallStats = new ArrayList<>();
            countToUserCount.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey()) // Optional: sort by count
                .forEach(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("count", entry.getKey());
                    item.put("users", entry.getValue());
                    apiCallStats.add(item);
                });
    
            result.put("apiCallStats", apiCallStats);
            return Action.SUCCESS.toUpperCase();
    
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = "Error fetching ip level api call stats: apicollection " + apiCollectionId + " url " + url + " method " + method + " error " + e.getMessage();
            loggerMaker.errorAndAddToDb(errMsg, LogDb.DASHBOARD);
            result.put("error", errMsg);
            return Action.ERROR.toUpperCase();
        }
    }
    

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setStartEpoch(int startEpoch) {
        this.startEpoch = startEpoch;
    }

    public void setEndEpoch(int endEpoch) {
        this.endEpoch = endEpoch;
    }

    public void setTraffic(Map<Integer, Integer> traffic) {
        this.traffic = traffic;
    }
    
    public Map<Integer, Integer> getTraffic() {
        return this.traffic;
    }

    public void setSampleDataList(List<SampleData> sampleDataList) {
        this.sampleDataList = sampleDataList;
    }

    public List<SampleData> getSampleDataList() {
        return this.sampleDataList;
    }

    public Map<String,List<SingleTypeInfo.ParamId>> getSensitiveSampleData() {
        return this.sensitiveSampleData;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }
    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

}
