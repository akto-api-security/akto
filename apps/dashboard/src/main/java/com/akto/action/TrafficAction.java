package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

public class TrafficAction {
    
    int apiCollectionId;
    String url;
    String method;
    int startEpoch;
    int endEpoch;

    Map<Integer, Integer> traffic = new HashMap<>();

    public String fetchEndpointTrafficData() {
        traffic = new HashMap<>();
        List<TrafficInfo> trafficInfoList = TrafficInfoDao.instance.findAll(Filters.and(
            Filters.eq("_id.url", url),
            Filters.eq("_id.apiCollectionId", apiCollectionId),
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
            Filters.eq("_id.apiCollectionId", apiCollectionId),
            Filters.eq("_id.responseCode", -1),
            Filters.eq("_id.method", method),
            Filters.gte("_id.bucketStartEpoch", 0),
            Filters.lte("_id.bucketEndEpoch", 0)
        ));

        return Action.SUCCESS.toUpperCase();
    }

    Map<String, List<SingleTypeInfo.ParamId>> sensitiveSampleData = new HashMap<>();
    public String fetchSensitiveSampleData() {
        List<SensitiveSampleData> sensitiveSampleDataList = SensitiveSampleDataDao.instance.findAll(
                Filters.and(
                        Filters.eq("_id.url", url),
                        Filters.eq("_id.apiCollectionId", apiCollectionId),
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

}
