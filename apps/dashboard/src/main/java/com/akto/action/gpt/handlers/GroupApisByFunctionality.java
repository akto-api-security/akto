package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.data_extractors.DataExtractor;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;

import java.util.List;

public class GroupApisByFunctionality implements QueryHandler {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GroupApisByFunctionality.class);

    private final DataExtractor<String> dataExtractor;
    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public GroupApisByFunctionality(DataExtractor<String> dataExtractor, ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.dataExtractor = dataExtractor;
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        List<String> urls = this.dataExtractor.extractData(meta);
        logger.info("Found " + urls.size() + " endpoints");
        BasicDBObject data = new BasicDBObject();
        data.put("query_type", GptQuery.GROUP_APIS_BY_FUNCTIONALITY.getName());
        data.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        data.put("apis", urls);
        return this.resultFetcherStrategy.fetchResult(data);
    }
}
