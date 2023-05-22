package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;


public class GenerateRegex implements QueryHandler{

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public GenerateRegex(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) throws Exception {
        String query = meta.getString("input_query");
        BasicDBObject request = new BasicDBObject();
        request.put("query_type", GptQuery.GENERATE_REGEX.getName());
        request.put("input_query", query);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        return this.resultFetcherStrategy.fetchResult(request);
    }
}
