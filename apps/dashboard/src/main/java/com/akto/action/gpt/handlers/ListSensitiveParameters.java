package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;

public class ListSensitiveParameters implements QueryHandler{


    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;
    public ListSensitiveParameters(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        String sampleData =meta.getString("sampleData");
        BasicDBObject request = new BasicDBObject();
        request.put("query_type", GptQuery.LIST_SENSITIVE_PARAMS.getName());
        request.put("req_resp_str", sampleData);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        return this.resultFetcherStrategy.fetchResult(request);
    }
}
