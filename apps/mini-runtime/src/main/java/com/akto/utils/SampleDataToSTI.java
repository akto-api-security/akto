package com.akto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.hybrid_parsers.HttpCallParser;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.type.APICatalog;
import com.akto.hybrid_runtime.APICatalogSync;
import com.akto.hybrid_runtime.URLAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SampleDataToSTI {

    // url -> method -> response code -> list(singleTypeInfo)
    private Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> stiList = new HashMap<>();
    private List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(SampleDataToSTI.class);


    public SampleDataToSTI(){

    }

    public void setSampleDataToSTI(List<SampleData> allData) {

        for (SampleData sampleData : allData) {

            Method method = sampleData.getId().getMethod();
            String url = sampleData.getId().getUrl();
            List<SingleTypeInfo> singleTypeInfoPerURL = new ArrayList<>();
            for (String dataString : sampleData.getSamples()) {
                singleTypeInfoPerURL.addAll(getSampleDataToSTIUtil(dataString, url));
            }
            Map<Integer, List<SingleTypeInfo>> responseCodeToSTI = new HashMap<>();
            for(SingleTypeInfo singleTypeInfo:singleTypeInfoPerURL){
                if(responseCodeToSTI.containsKey(singleTypeInfo.getResponseCode())){
                    responseCodeToSTI.get(singleTypeInfo.getResponseCode()).add(singleTypeInfo);
                }
                else{
                    List<SingleTypeInfo> temp = new ArrayList<>();
                    temp.add(singleTypeInfo);
                    responseCodeToSTI.put(singleTypeInfo.getResponseCode(),temp);
                }
            }
            if(stiList.containsKey(url)){
                stiList.get(url).put(method.toString(),responseCodeToSTI);
            }
            else{
                Map<String, Map<Integer, List<SingleTypeInfo>>> stiMap = new HashMap<>();
                stiMap.put(method.toString(), responseCodeToSTI);
                stiList.put(url,stiMap);
            }
            singleTypeInfos.addAll(singleTypeInfoPerURL);
        }
    }

    public void setSensitiveSampleDataToSTI(List<SensitiveSampleData> allData){

        HttpCallParser parse = new HttpCallParser("", 0, 0, 0, true);
        for (SensitiveSampleData sensitiveSampleData : allData) {

            String method = sensitiveSampleData.getId().getMethod();
            String url = sensitiveSampleData.getId().getUrl();
            List<SingleTypeInfo> singleTypeInfoPerURL = new ArrayList<>();
            for (String dataString : sensitiveSampleData.getSampleData()) {
                singleTypeInfoPerURL.addAll(getSampleDataToSTIUtil(dataString, url));
            }
            Map<Integer, List<SingleTypeInfo>> responseCodeToSTI = new HashMap<>();
            for(SingleTypeInfo singleTypeInfo:singleTypeInfoPerURL){
                if(responseCodeToSTI.containsKey(singleTypeInfo.getResponseCode())){
                    responseCodeToSTI.get(singleTypeInfo.getResponseCode()).add(singleTypeInfo);
                }
                else{
                    List<SingleTypeInfo> temp = new ArrayList<>();
                    temp.add(singleTypeInfo);
                    responseCodeToSTI.put(singleTypeInfo.getResponseCode(),temp);
                }
            }
            if(stiList.containsKey(url)){
                stiList.get(url).put(method.toString(),responseCodeToSTI);
            }
            else{
                Map<String, Map<Integer, List<SingleTypeInfo>>> stiMap = new HashMap<>();
                stiMap.put(method.toString(), responseCodeToSTI);
                stiList.put(url,stiMap);
            }
            singleTypeInfos.addAll(singleTypeInfoPerURL);
        }
    }

    public Map<String,Map<String, Map<Integer, List<SingleTypeInfo>>>> getSingleTypeInfoMap(){
        return this.stiList;
    }

    public List<SingleTypeInfo> getSingleTypeList(){
        return this.singleTypeInfos;
    }

    private List<SingleTypeInfo> getSampleDataToSTIUtil(String dataString, String url) {

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();

        HttpResponseParams httpResponseParams = new HttpResponseParams();
        boolean flag = false;

        try {
            httpResponseParams = HttpCallParser.parseKafkaMessage(dataString);
        } catch (Exception e) {
            flag = true;
            logger.error(e.getMessage());
        }

        if (flag) {
            return singleTypeInfos;
        }

        List<HttpResponseParams> responseParams = new ArrayList<>();
        responseParams.add(httpResponseParams);
        Map<Integer, URLAggregator> aggregatorMap = new HashMap<>();
        HttpCallParser.aggregate(responseParams, aggregatorMap);
        APICatalogSync apiCatalogSync = new APICatalogSync("0",0, true,false);
        for (int apiCollectionId : aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
            for (Integer key : apiCatalogSync.delta.keySet()) {
                APICatalog apiCatalog = apiCatalogSync.delta.get(key);
                singleTypeInfos.addAll(apiCatalog.getAllTypeInfo());
            }
        }

        for (int i = 0; i < singleTypeInfos.size(); i++) {
            singleTypeInfos.get(i).setUrl(url);
        }

        return singleTypeInfos;
    }
}