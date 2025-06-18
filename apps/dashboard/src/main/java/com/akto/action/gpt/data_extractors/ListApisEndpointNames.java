package com.akto.action.gpt.data_extractors;

import com.akto.action.gpt.data_extractors.filters.Filter;
import com.akto.action.observe.Utils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.google.protobuf.Api;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;

public class ListApisEndpointNames implements DataExtractor<String>{
    private static final LoggerMaker logger = new LoggerMaker(ListApisEndpointNames.class, LogDb.DASHBOARD);

    private final List<Filter<String>> filters;

    public ListApisEndpointNames() {
        filters = new ArrayList<>();
    }

    public ListApisEndpointNames(List<Filter<String>> filters){
        this.filters = filters;
    }

    @Override
    public List<String> extractData(BasicDBObject meta) {
        int apiCollectionId = meta.getInt("apiCollectionId", -1);

        List<String> result = new ArrayList<>();

        if (apiCollectionId == -1) {

            ArrayList<Object> urlsObj = (ArrayList) (meta.get("urls"));
            for(Object o: urlsObj) {
                result.add(o.toString());
            }

        } else {
            ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId), Projections.include(ApiCollection.HOST_NAME));
            if(apiCollection == null){
                return new ArrayList<>();
            }
            List<BasicDBObject> list = new ArrayList<>();
            if(StringUtils.isEmpty(apiCollection.getHostName())){
                list = ApiCollectionsDao.fetchEndpointsInCollection(apiCollectionId, 0, Utils.LIMIT, Utils.DELTA_PERIOD_VALUE);
            }else{
                // For automated traffic collections, we use the host name to fetch endpoints
                list = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollectionId, 0, false);
            }
            
            for (BasicDBObject obj : list) {
                String url = ((BasicDBObject)obj.get("_id")).getString("url", "");
                if(!url.isEmpty()){
                    result.add(url);
                }
            }
        }

        if(!filters.isEmpty()){
            for(Filter<String> filter: filters) {
                int originalSize = result.size();
                result = filter.filterData(result);
                int newSize = result.size();
                if(originalSize > newSize){
                    logger.debug("Filtered " + (originalSize - newSize) + " endpoints");
                }
            }
        }
        return result;
    }
}
