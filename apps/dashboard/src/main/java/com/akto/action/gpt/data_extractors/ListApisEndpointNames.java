package com.akto.action.gpt.data_extractors;

import com.akto.action.gpt.data_extractors.filters.Filter;
import com.akto.action.observe.Utils;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ListApisEndpointNames implements DataExtractor<String>{

    private static final Logger logger = LoggerFactory.getLogger(ListApisEndpointNames.class);

    private final List<Filter<String>> filters;

    public ListApisEndpointNames() {
        filters = new ArrayList<>();
    }

    public ListApisEndpointNames(List<Filter<String>> filters){
        this.filters = filters;
    }
    @Override
    public List<String> extractData(BasicDBObject meta) {
        int apiCollectionId = meta.getInt("apiCollectionId");
        List<BasicDBObject> list =  Utils.fetchEndpointsInCollection(apiCollectionId, 0);

        List<String> result = new ArrayList<>();
        for (BasicDBObject obj : list) {
            String url = ((BasicDBObject)obj.get("_id")).getString("url", "");
            if(!url.isEmpty()){
                result.add(url);
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
