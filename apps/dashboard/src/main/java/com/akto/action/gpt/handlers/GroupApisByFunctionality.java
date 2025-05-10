package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.data_extractors.DataExtractor;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.action.gpt.validators.ValidateQuery;
import com.mongodb.BasicDBObject;
import org.slf4j.Logger;

import java.util.List;

public class GroupApisByFunctionality implements QueryHandler {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GroupApisByFunctionality.class);

    private final DataExtractor<String> dataExtractor;
    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;
    private final List<ValidateQuery> validators;

    public GroupApisByFunctionality(DataExtractor<String> dataExtractor, ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy, List<ValidateQuery> validators) {
        this.dataExtractor = dataExtractor;
        this.resultFetcherStrategy = resultFetcherStrategy;
        this.validators = validators;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) throws Exception{
        for (ValidateQuery validator : validators) {
            if (!validator.validate(meta)) {
                throw new Exception(validator.getErrorMessage());
            }
        }
        List<String> urls = this.dataExtractor.extractData(meta);
        logger.debug("Found " + urls.size() + " endpoints");
        BasicDBObject data = new BasicDBObject();
        data.put("query_type", GptQuery.GROUP_APIS_BY_FUNCTIONALITY.getName());
        data.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        data.put("apis", urls);
        return this.resultFetcherStrategy.fetchResult(data);
    }
}
