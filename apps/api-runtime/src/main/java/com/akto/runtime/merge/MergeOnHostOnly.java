package com.akto.runtime.merge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.TrafficInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.FilterSampleData;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.TrafficInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Util;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

public class MergeOnHostOnly {

    /*
     * apicollections -> findall and create a map of hostnames -> list of apicollection ids
     * make a new collection and add all data in it for api collection (and remove duplicates)
     * set the apicollection id as the first one for all the others in the list for
     * apiinfos, sampledata, sensitivesampledata, singletypeinfo, trafficInfo
     * delete in case the _id exists ( i.e. the apicollection already contains this url+method)
     */

    public MergeOnHostOnly() {} 

    public void updateAllCollections(int oldId, int newId) {

        InsertManyOptions options = new InsertManyOptions();
        options.ordered(false);
        // this allows to insert regradless of failures, i.e. in case an insert fails, it will move to insert the next one and so on...

        List<ApiInfo> apiInfos =  ApiInfoDao.instance.findAll("_id.apiCollectionId", oldId);
        if(apiInfos!=null && apiInfos.size()>0){
            apiInfos.forEach((apiInfo)->{
                apiInfo.getId().setApiCollectionId(newId);
                apiInfo.setCollectionIds(Util.replaceElementInList(apiInfo.getCollectionIds(), newId, oldId));
            });
            try{
                ApiInfoDao.instance.getMCollection().insertMany(apiInfos,options);
            } catch(Exception e){

            }
            ApiInfoDao.instance.getMCollection().deleteMany(Filters.eq("_id.apiCollectionId", oldId));
        }

        List<SampleData> sampleDatas =  SampleDataDao.instance.findAll("_id.apiCollectionId", oldId);
        if(sampleDatas!=null && sampleDatas.size()>0){
            sampleDatas.forEach((sampleData) -> {
                sampleData.getId().setApiCollectionId(newId);
                sampleData.setCollectionIds(Util.replaceElementInList(sampleData.getCollectionIds(), newId, oldId));
            });
            try{
                SampleDataDao.instance.getMCollection().insertMany(sampleDatas,options);
            } catch(Exception e){

            }
            SampleDataDao.instance.getMCollection().deleteMany(Filters.eq("_id.apiCollectionId", oldId));
        }

        List<SensitiveSampleData> sensitiveSampleDatas =  SensitiveSampleDataDao.instance.findAll("_id.apiCollectionId", oldId);
        if(sensitiveSampleDatas!=null && sensitiveSampleDatas.size()>0){
            sensitiveSampleDatas.forEach((sensitiveSampleData)->{
                sensitiveSampleData.getId().setApiCollectionId(newId);
                sensitiveSampleData.setCollectionIds(Util.replaceElementInList(sensitiveSampleData.getCollectionIds(), newId, oldId));
            });
            try{
                SensitiveSampleDataDao.instance.getMCollection().insertMany(sensitiveSampleDatas,options);
            } catch(Exception e){

            }
            SensitiveSampleDataDao.instance.getMCollection().deleteMany(Filters.eq("_id.apiCollectionId", oldId));
        }

        List<TrafficInfo> trafficInfos =  TrafficInfoDao.instance.findAll("_id.apiCollectionId", oldId);
        if(trafficInfos!=null && trafficInfos.size()>0){
            trafficInfos.forEach((trafficInfo)->{
                trafficInfo.getId().setApiCollectionId(newId);
                trafficInfo.setCollectionIds(Util.replaceElementInList(trafficInfo.getCollectionIds(), newId, oldId));
            });
            try{
                TrafficInfoDao.instance.getMCollection().insertMany(trafficInfos,options);
            } catch(Exception e){

            }
            TrafficInfoDao.instance.getMCollection().deleteMany(Filters.eq("_id.apiCollectionId", oldId));
        }

        SensitiveParamInfoDao.instance.getMCollection().deleteMany(Filters.eq("apiCollectionId", oldId));
        FilterSampleDataDao.instance.getMCollection().deleteMany(Filters.eq("_id.apiInfoKey.apiCollectionId", oldId));
    }

    public void updateSTI(int oldId, int newId) {
        SingleTypeInfoDao.instance.getMCollection().updateMany(
                Filters.and(
                        Filters.eq(SingleTypeInfo._API_COLLECTION_ID, oldId),
                        Filters.exists(SingleTypeInfo._COLLECTION_IDS, false)),
                Updates.set(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(newId)));

        // the below query fails when collectionIds is not present, thus the above query.
        SingleTypeInfoDao.instance.getMCollection().updateMany(
                Filters.eq(SingleTypeInfo._API_COLLECTION_ID, oldId),
                Updates.combine(
                        Updates.set(SingleTypeInfo._API_COLLECTION_ID, newId),
                        Updates.set("collectionIds.$[element]", newId)),
                new UpdateOptions().arrayFilters(
                        Arrays.asList(
                                Filters.in("element", oldId))));
    }

    public void deleteFromAllCollections(int apiCollectionId, List<String> urls ) {

        Bson filter = Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.in("_id.url", urls));

        ApiInfoDao.instance.getMCollection().deleteMany(filter);
        SampleDataDao.instance.getMCollection().deleteMany(filter);
        SensitiveSampleDataDao.instance.getMCollection().deleteMany(filter);
        TrafficInfoDao.instance.getMCollection().deleteMany(filter);
                
        SingleTypeInfoDao.instance.getMCollection().deleteMany(
            Filters.and(
                    Filters.eq("apiCollectionId", apiCollectionId),
                    Filters.in("url",urls)));
    }

    public Set<String> getUrlList(String host, int apiCollectionId){
        Set<String> ret = new HashSet<>();
        
        Bson filterQ = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(filterQ);

        for(SingleTypeInfo s: singleTypeInfos){
            // urls will be merged without method: might result in some data loss
            ret.add(s.getUrl());
        }
        return ret;
    }

    public void mergeHostUtil(String host, List<Integer> apiCollectionIds) {
        if (apiCollectionIds.size() == 0) return;

        int newApiCollectionId = host.hashCode();
        
        if (apiCollectionIds.contains(newApiCollectionId)) {
            apiCollectionIds.remove(apiCollectionIds.indexOf(newApiCollectionId));
        } else {

            ApiCollection old = ApiCollectionsDao.instance.findOne("_id", apiCollectionIds.get(0));
            old.setId(newApiCollectionId);

            try {
                ApiCollectionsDao.instance.insertOne(new ApiCollection(newApiCollectionId, null, old.getStartTs(), new HashSet<>(), host, 0));
            } catch (Exception e) {
                return;
            }

            int currOldId = apiCollectionIds.get(0);

            ApiCollectionsDao.instance.getMCollection().deleteOne(Filters.eq( "_id", currOldId));

            updateSTI(currOldId, newApiCollectionId);
            updateAllCollections(currOldId, newApiCollectionId);

            apiCollectionIds.remove(0);
        }

        try {

            Set<String> urls = getUrlList(host, newApiCollectionId);
            for (int i = 0; i < apiCollectionIds.size(); i++) {    
    
                List<String> urlList = new ArrayList<>(urls);
                int sz = urlList.size();
                int j = 0;
                int currOldId = apiCollectionIds.get(i);
                do { 
                    deleteFromAllCollections(currOldId, urlList.subList(j, Math.min(j + 1000, sz)));
                    j += 1000;
                } while (j < sz);
                
                urls.addAll(getUrlList(host, currOldId));

                ApiCollectionsDao.instance.getMCollection().deleteOne(Filters.eq("_id", currOldId));
                updateSTI(currOldId, newApiCollectionId);
                updateAllCollections(currOldId, newApiCollectionId);
            }

        } catch (Exception e) {
            
            
        }

    }

    public void mergeHosts() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaAll();

        Map<String, List<Integer>> hostToApiCollectionId = new HashMap<>();

        for (ApiCollection it : apiCollections) {
            if (it.getHostName() == null){
                continue;
            }

            List<Integer> apiCollectionIds = hostToApiCollectionId.get(it.getHostName());

            if (apiCollectionIds == null) {
                apiCollectionIds = new ArrayList<>();
                hostToApiCollectionId.put(it.getHostName(), apiCollectionIds);
            }

            apiCollectionIds.add(it.getId());
        }

        for (String host : hostToApiCollectionId.keySet()) {
            mergeHostUtil(host, hostToApiCollectionId.get(host));
        }
    }
}
