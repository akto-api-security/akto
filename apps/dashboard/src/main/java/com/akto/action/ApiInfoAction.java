package com.akto.action;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.MissingUrlResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiInfoAction extends UserAction {
    private static final Logger logger = LoggerFactory.getLogger(ApiInfoAction.class);

    @Override
    public String execute() {
        return SUCCESS;
    }

    private List<ApiInfo> apiInfoList;
    private int apiCollectionId;
    public String fetchApiInfoList() {
        apiInfoList= ApiInfoDao.instance.findAll(Filters.eq("_id.apiCollectionId", apiCollectionId));
        for (ApiInfo apiInfo: apiInfoList) {
            apiInfo.calculateActualAuth();
        }
        return SUCCESS.toUpperCase();
    }

    private String url ;
    private String method;
    private ApiInfo apiInfo;
    @Setter
    private boolean showApiInfo;

    @Getter
    private int unauthenticatedApis;
    @Getter
    private List<ApiInfo> unauthenticatedApiList;

    @Getter
    @Setter
    private List<String> missingUrls;

    @Getter
    private List<MissingUrlResult> missingUrlsResults;

    public String fetchApiInfo(){
        Bson filter = ApiInfoDao.getFilter(url, method, apiCollectionId);
        this.apiInfo = ApiInfoDao.instance.findOne(filter);
        if(this.apiInfo == null){
            // case of slash missing in first character of url
            // search for url having no leading slash
            if (url != null && url.startsWith("/")) {
                String urlWithoutLeadingSlash = url.substring(1);
                filter = ApiInfoDao.getFilter(urlWithoutLeadingSlash, method, apiCollectionId);
                this.apiInfo = ApiInfoDao.instance.findOne(filter);   
            }
        }
        return SUCCESS.toUpperCase();
    }
    public String fetchAllUnauthenticatedApis(){
        Bson filter = Document.parse(
            "{ \"allAuthTypesFound\": { \"$elemMatch\": { \"$elemMatch\": { \"$eq\": \"UNAUTHENTICATED\" } } } }"
        );
        if(!showApiInfo){
            this.unauthenticatedApis = (int) ApiInfoDao.instance.count(filter);
        }else{
            int count = 0;
            int skip = 0;
            int limit = 1000;
            Bson sort = Sorts.ascending(Constants.ID);
            while(true){
                List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(filter, skip, limit, sort);
                if(apiInfos.isEmpty()) {
                    break;
                }
                for(ApiInfo apiInfo: apiInfos) {
                    apiInfo.calculateActualAuth();
                }
                if(unauthenticatedApiList == null) {
                    unauthenticatedApiList = apiInfos;
                } else {
                    unauthenticatedApiList.addAll(apiInfos);
                }
                count += apiInfos.size();
                skip += limit;
            }
            this.unauthenticatedApis = count;
        }
        return SUCCESS.toUpperCase();
    }

    /**
     * Queries api_info collection for matching URLs and returns a map of URL -> Collection IDs
     */
    private Map<String, Set<Integer>> fetchApiInfoCollections(List<String> urls) {
        Map<String, Set<Integer>> urlsCollectionIdsMap = new HashMap<>();
        for (String url : urls) {
            urlsCollectionIdsMap.put(url, new HashSet<>());
        }

        logger.debug("Querying api_info for {} URLs", urls.size());
        Bson filter = Filters.in("_id.url", urls);
        Bson projection = Projections.fields(Projections.include("_id.url", "collectionIds"));
        List<ApiInfo> apiInfoList = ApiInfoDao.instance.findAll(filter, projection);
        logger.debug("Found {} matching api_info records", apiInfoList.size());

        for (ApiInfo apiInfo : apiInfoList) {
            String url = apiInfo.getId().getUrl();
            List<Integer> collectionIds = apiInfo.getCollectionIds();
            if (collectionIds != null && !collectionIds.isEmpty()) {
                urlsCollectionIdsMap.get(url).addAll(collectionIds);
            }
        }

        return urlsCollectionIdsMap;
    }

    /**
     * Queries api_collections to get hostName or displayName for collection IDs
     */
    private Map<Integer, String> fetchCollectionNames(Set<Integer> collectionIds) {
        Map<Integer, String> collectionIdNameMap = new HashMap<>();

        if (collectionIds.isEmpty()) {
            return collectionIdNameMap;
        }

        logger.debug("Querying api_collections for {} collection IDs", collectionIds.size());
        Bson filter = Filters.in("_id", collectionIds);
        Bson projection = Projections.fields(Projections.include("_id", "hostName", "displayName"));
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(filter, projection);
        logger.debug("Found {} collection documents", collections.size());

        for (ApiCollection collection : collections) {
            String name = collection.getHostName() != null && !collection.getHostName().isEmpty()
                ? collection.getHostName()
                : (collection.getDisplayName() != null ? collection.getDisplayName() : "");
            collectionIdNameMap.put(collection.getId(), name);
        }

        return collectionIdNameMap;
    }

    /**
     * Queries single_type_info collection for matching URLs with param="host"
     */
    private Map<String, Set<Integer>> fetchSingleTypeInfoCollections(List<String> urls) {
        Map<String, Set<Integer>> stiUrlsCollectionIdsMap = new HashMap<>();
        for (String url : urls) {
            stiUrlsCollectionIdsMap.put(url, new HashSet<>());
        }

        logger.debug("Querying single_type_info for {} URLs", urls.size());
        Bson filter = Filters.and(Filters.in("url", urls), Filters.eq("param", "host"));
        Bson projection = Projections.fields(Projections.include("url", "collectionIds"));
        List<SingleTypeInfo> stiList = SingleTypeInfoDao.instance.findAll(filter, projection);
        logger.debug("Found {} matching single_type_info records", stiList.size());

        for (SingleTypeInfo sti : stiList) {
            String url = sti.getUrl();
            List<Integer> collectionIds = sti.getCollectionIds();
            if (collectionIds != null && !collectionIds.isEmpty()) {
                stiUrlsCollectionIdsMap.get(url).addAll(collectionIds);
            }
        }

        return stiUrlsCollectionIdsMap;
    }

    /**
     * Builds the final results list by combining api_info and single_type_info data
     */
    private List<MissingUrlResult> buildResults(
            List<String> urls,
            Map<String, Set<Integer>> apiInfoCollectionsMap,
            Map<String, Set<Integer>> stiCollectionsMap,
            Map<Integer, String> collectionNamesMap) {

        List<MissingUrlResult> results = new ArrayList<>();

        for (String url : urls) {
            Set<Integer> apiInfoCollectionIds = apiInfoCollectionsMap.get(url);
            Set<Integer> stiCollectionIds = stiCollectionsMap.get(url);
            boolean hasApiInfo = !apiInfoCollectionIds.isEmpty();

            if (!hasApiInfo) {
                results.add(new MissingUrlResult(url, null, "", false, false));
                continue;
            }

            for (Integer collectionId : apiInfoCollectionIds) {
                String name = collectionNamesMap.getOrDefault(collectionId, "");
                boolean hasSingleTypeInfo = stiCollectionIds.contains(collectionId);
                results.add(new MissingUrlResult(url, collectionId, name, true, hasSingleTypeInfo));
            }
        }

        results.sort(Comparator.comparing(MissingUrlResult::getUrl));
        return results;
    }

    public String findMissingUrls(){
        try {
            logger.info("Starting findMissingUrls with {} URLs to check",
                missingUrls != null ? missingUrls.size() : 0);

            if (missingUrls == null || missingUrls.isEmpty()) {
                logger.warn("No URLs provided to check");
                missingUrlsResults = new ArrayList<>();
                return SUCCESS.toUpperCase();
            }

            // Fetch data from api_info collection
            Map<String, Set<Integer>> apiInfoCollectionsMap = fetchApiInfoCollections(missingUrls);

            // Extract unique collection IDs
            Set<Integer> allCollectionIds = new HashSet<>();
            for (Set<Integer> collectionIds : apiInfoCollectionsMap.values()) {
                allCollectionIds.addAll(collectionIds);
            }
            logger.debug("Found {} unique collection IDs", allCollectionIds.size());

            // Fetch collection names
            Map<Integer, String> collectionNamesMap = fetchCollectionNames(allCollectionIds);

            // Fetch data from single_type_info collection
            Map<String, Set<Integer>> stiCollectionsMap = fetchSingleTypeInfoCollections(missingUrls);

            // Build final results
            List<MissingUrlResult> results = buildResults(
                missingUrls,
                apiInfoCollectionsMap,
                stiCollectionsMap,
                collectionNamesMap
            );

            this.missingUrlsResults = results;
            logger.info("Successfully processed {} URLs, generated {} results",
                missingUrls.size(), results.size());

            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.error("Error in findMissingUrls", e);
            missingUrlsResults = new ArrayList<>();
            return ERROR.toUpperCase();
        }
    }

    public List<ApiInfo> getApiInfoList() {
        return apiInfoList;
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

    public ApiInfo getApiInfo() {
        return apiInfo;
    }


}


