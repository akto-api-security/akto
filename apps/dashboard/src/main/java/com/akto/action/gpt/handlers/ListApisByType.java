package com.akto.action.gpt.handlers;

import com.akto.action.gpt.data_extractors.DataExtractor;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;

import java.util.List;

public class ListApisByType implements QueryHandler {

    private final DataExtractor<String> dataExtractor;

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(ListApisByType.class);

    public ListApisByType(DataExtractor<String> dataExtractor, ResultFetcherStrategy<BasicDBObject> strategy) {
        this.dataExtractor = dataExtractor;
        this.resultFetcherStrategy = strategy;
    }
    public BasicDBObject handleQuery(BasicDBObject meta) {
        List<String> urls =  dataExtractor.extractData(meta);
        logger.info("Found " + urls.size() + " endpoints");
        BasicDBObject data = new BasicDBObject();
        data.put("query_type", GptQuery.LIST_APIS_BY_TYPE.getName());
        data.put("type_of_apis", meta.getString("type_of_apis"));
        data.put("apis", urls);
        return this.resultFetcherStrategy.fetchResult(data);
    }




}