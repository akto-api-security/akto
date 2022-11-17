// package com.akto.runtime.merge;

// import java.util.*;

// import com.akto.dao.ApiCollectionsDao;
// import com.akto.dao.ApiInfoDao;
// import com.akto.dao.SampleDataDao;
// import com.akto.dao.SensitiveSampleDataDao;
// import com.akto.dao.SingleTypeInfoDao;
// import com.akto.dao.TrafficInfoDao;
// import com.akto.dto.ApiCollection;
// import com.akto.dto.ApiInfo;
// import com.akto.dto.SensitiveSampleData;
// import com.akto.dto.traffic.SampleData;
// import com.akto.dto.traffic.TrafficInfo;
// import com.akto.dto.type.SingleTypeInfo;
// import com.akto.dto.type.SingleTypeInfo.Domain;
// import com.akto.runtime.APICatalogSync;
// import com.akto.types.CappedSet;
// import com.mongodb.client.model.Filters;
// import com.mongodb.client.model.Updates;

// public class MergeOnSlash {
 
//     public void removeApiCollectionsDuplicates(int apiCollectionId){
//         ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id",apiCollectionId);

//         Set<String> urls=new HashSet<>();
//         for(String url:apiCollection.getUrls()){
//             if(url.startsWith("/") || url.startsWith("http")){
//                 urls.add(url);
//             } else {
//                 urls.add("/"+url);
//             }
//         }
//         ApiCollectionsDao.instance.updateOne("_id",apiCollectionId, 
//             Updates.set("urls",urls)
//         );
//     }

//     public void removeSTIDuplicates(int apiCollectionId) {
//         List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll("apiCollectionId", apiCollectionId);

//         for (SingleTypeInfo singleTypeInfo : singleTypeInfos) {
//             if (singleTypeInfo.getUrl().startsWith("/") || singleTypeInfo.getUrl().startsWith("http")) {
//                 continue;
//             }

//             String originalUrl = singleTypeInfo.getUrl();
//             String newUrl = "/" + originalUrl;
//             SingleTypeInfo newSingleTypeInfo = singleTypeInfo.copy();
//             newSingleTypeInfo.setUrl(newUrl);
//             SingleTypeInfo withslash = SingleTypeInfoDao.instance.findOne(SingleTypeInfoDao.createFilters(newSingleTypeInfo));

//             if (withslash == null) {
//                 SingleTypeInfoDao.instance.updateOne(
//                         SingleTypeInfoDao.createFilters(singleTypeInfo),
//                         Updates.set("url", newUrl));
//             } else {
//                 SingleTypeInfoDao.instance.deleteAll(SingleTypeInfoDao.createFilters(singleTypeInfo));
//             }

//             if(singleTypeInfo.getIsUrlParam()){

//                 String[] tokens = APICatalogSync.tokenize(newUrl);
//                 if( tokens[Integer.parseInt(singleTypeInfo.getParam())].equals("INTEGER") || tokens[Integer.parseInt(singleTypeInfo.getParam())].equals("FLOAT") ){
//                     continue;
//                 }

//                 singleTypeInfo.setUrl(newUrl);
//                 SingleTypeInfoDao.instance.updateOne(
//                     SingleTypeInfoDao.createFilters(singleTypeInfo),
//                     Updates.set("param", (Integer.parseInt(singleTypeInfo.getParam())+1)+"" ));

//             }
//         }
//     }

//     public void removeApiInfoDuplicates(int apiCollectionId){
//         List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId",apiCollectionId));

//         for(ApiInfo apiInfo:apiInfos){
//             if(apiInfo.getId().getUrl().startsWith("/") || apiInfo.getId().getUrl().startsWith("http")){
//                 continue;
//             }

//             String originalUrl = apiInfo.getId().getUrl();
//             String newUrl = "/" + originalUrl;
//             ApiInfo newApiInfo = apiInfo.copy();
//             newApiInfo.getId().setUrl(newUrl);
//             ApiInfo withSlash = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(newApiInfo.getId()));

//             if(withSlash == null){
//                 ApiInfoDao.instance.insertOne(newApiInfo);
//             }
//             ApiInfoDao.instance.deleteAll(ApiInfoDao.getFilter(apiInfo.getId()));
//         }
//     }

//     public void removeSampleDataDuplicates(int apiCollectionId){
//         List<SampleData> sampleDatas = SampleDataDao.instance.findAll("_id.apiCollectionId",apiCollectionId);

//         for(SampleData sampleData:sampleDatas){
//             if(sampleData.getId().getUrl().startsWith("/") || sampleData.getId().getUrl().startsWith("http")){
//                 continue;
//             }

//             String originalUrl = sampleData.getId().getUrl();
//             String newUrl = "/"+originalUrl;
//             SampleData newSampleData = sampleData.copy();
//             newSampleData.getId().setUrl(newUrl);
//             SampleData withSlash = SampleDataDao.instance.findOne("_id",newSampleData.getId());

//             if(withSlash==null){
//                 SampleDataDao.instance.insertOne(newSampleData);
//             }
//             SampleDataDao.instance.deleteAll(Filters.eq("_id",sampleData.getId()));
//         }
//     }

//     public void removeSensitiveSampleDataDuplicates(int apiCollectionId){
//         List<SensitiveSampleData> sensitiveSampleDatas = SensitiveSampleDataDao.instance.findAll("_id.apiCollectionId",apiCollectionId);

//         for(SensitiveSampleData sensitiveSampleData:sensitiveSampleDatas){
//             if(sensitiveSampleData.getId().getUrl().startsWith("/") || sensitiveSampleData.getId().getUrl().startsWith("http")){
//                 continue;
//             }

//             String originalUrl = sensitiveSampleData.getId().getUrl();
//             String newUrl = "/"+originalUrl;
//             SensitiveSampleData newSensitiveSampleData = sensitiveSampleData.copy();
//             newSensitiveSampleData.getId().setUrl(newUrl);

//             SingleTypeInfo filter = new SingleTypeInfo(sensitiveSampleData.getId(), new HashSet<>() , new HashSet<>(), 0, 0,0, new CappedSet<>() , Domain.ENUM, 0,0);
//             SingleTypeInfo newFilter = new SingleTypeInfo(newSensitiveSampleData.getId(), new HashSet<>() , new HashSet<>(), 0, 0,0, new CappedSet<>() , Domain.ENUM, 0,0);
//             SensitiveSampleData withSlash = SensitiveSampleDataDao.instance.findOne(SensitiveSampleDataDao.getFilters(newFilter));

//             if(withSlash==null){
//                 SensitiveSampleDataDao.instance.insertOne(newSensitiveSampleData);
//             }
//             SensitiveSampleDataDao.instance.deleteAll(SensitiveSampleDataDao.getFilters(filter));
//         }
//     }

//     public void removeTrafficInfoDuplicates(int apiCollectionId){
//         List<TrafficInfo> trafficInfos = TrafficInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId",apiCollectionId));

//         for(TrafficInfo trafficInfo:trafficInfos){
//             if(trafficInfo.getId().getUrl().startsWith("/") || trafficInfo.getId().getUrl().startsWith("http")){
//                 continue;
//             }

//             String originalUrl = trafficInfo.getId().getUrl();
//             String newUrl = "/"+originalUrl;
//             TrafficInfo newTrafficInfo = trafficInfo.copy();
//             newTrafficInfo.getId().setUrl(newUrl);
//             TrafficInfo withSlash = TrafficInfoDao.instance.findOne("_id",newTrafficInfo.getId());

//             if(withSlash == null){
//                 TrafficInfoDao.instance.insertOne(newTrafficInfo);
//             }
//             TrafficInfoDao.instance.deleteAll(Filters.eq("_id",trafficInfo.getId()));
//         }
//     }

//     public void removeDuplicates(int apiCollectionId){
//         /*
//          * 1. only "/" exist -> continue
//          * 2. only -"/" exist -> convert to "/"
//          * 3. both exist -> delete -"/" and keep "/"
//          */

//         removeApiCollectionsDuplicates(apiCollectionId);
//         removeApiInfoDuplicates(apiCollectionId);
//         removeSensitiveSampleDataDuplicates(apiCollectionId);
//         removeSampleDataDuplicates(apiCollectionId);
//         removeSTIDuplicates(apiCollectionId);
//         removeTrafficInfoDuplicates(apiCollectionId);
//     }

// }
