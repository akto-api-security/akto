package com.akto.action;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bson.conversions.Bson;

import com.akto.ProtoMessageUtils;
import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchApiDistributionDataResponse;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;

public class TrafficAction extends UserAction {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(TrafficAction.class);
    // TODO: remove this, use API Executor.
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Getter
    @Setter
    int startWindow;
    
    @Getter
    @Setter
    int endWindow;

    @Getter
    @Setter
    List<Map<String, Object>> bucketStats = new ArrayList<>();

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

            AbstractThreatDetectionAction action = new AbstractThreatDetectionAction();
            HttpPost post = new HttpPost(String.format("%s/api/threat_detection/fetch_api_distribution_data", action.getBackendUrl()));
            post.addHeader("Authorization", "Bearer " + action.getApiToken());
            post.addHeader("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<String, Object>() {{
                put("apiCollectionId", apiCollectionId);
                put("url", url);
                put("method", method);
                put("startWindow", startWindow);
                put("endWindow", endWindow);
                // put("apiCollectionId", apiCollectionId);
            }};
        
            String msg = objectMapper.valueToTree(body).toString();
            StringEntity requestEntity = new StringEntity(msg, ContentType.APPLICATION_JSON);
            post.setEntity(requestEntity);

            try (CloseableHttpResponse resp = this.httpClient.execute(post)) {
                String responseBody = EntityUtils.toString(resp.getEntity());

                ProtoMessageUtils.<FetchApiDistributionDataResponse>toProtoMessage(
                    FetchApiDistributionDataResponse.class, responseBody)
                    .ifPresent(proto -> {
                        this.bucketStats = proto.getBucketStatsList().stream()
                            .map(pb -> {
                                Map<String, Object> stat = new HashMap<>();
                                stat.put("bucketLabel", pb.getBucketLabel());
                                if (pb.hasMin()) stat.put("min", pb.getMin());
                                if (pb.hasMax()) stat.put("max", pb.getMax());
                                if (pb.hasP25()) stat.put("p25", pb.getP25());
                                if (pb.hasP50()) stat.put("p50", pb.getP50());
                                if (pb.hasP75()) stat.put("p75", pb.getP75());
                                return stat;
                            })
                            .collect(Collectors.toList());
                    });

            } catch (Exception e) {
                e.printStackTrace();
                return Action.ERROR.toUpperCase();
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            String errMsg = "Error fetching api call stats: fetchApiCallStats " + e.getMessage();
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
