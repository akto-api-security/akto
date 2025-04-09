package com.akto.action.gpt.data_extractors;

import com.akto.action.gpt.data_extractors.filters.Filter;
import com.akto.action.observe.Utils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.List;

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
            List<BasicDBObject> list = Utils.fetchEndpointsInCollectionUsingHost(apiCollectionId, 0);
            
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
                    logger.info("Filtered " + (originalSize - newSize) + " endpoints");
                }
            }
        }
        return result;
    }
}
