package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.action.gpt.validators.ValidateQuery;
import com.mongodb.BasicDBObject;

import java.util.List;

public class ListSensitiveParameters implements QueryHandler{


    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;
    private final List<ValidateQuery> validators;
    public ListSensitiveParameters(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy, List<ValidateQuery> validators) {
        this.resultFetcherStrategy = resultFetcherStrategy;
        this.validators = validators;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) throws Exception {
        for (ValidateQuery validator : validators) {
            if (!validator.validate(meta)) {
                throw new Exception(validator.getErrorMessage());
            }
        }
        String sampleData =meta.getString("sampleData");
        BasicDBObject request = new BasicDBObject();
        request.put("query_type", GptQuery.LIST_SENSITIVE_PARAMS.getName());
        request.put("req_resp_str", sampleData);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        return this.resultFetcherStrategy.fetchResult(request);
    }
}
