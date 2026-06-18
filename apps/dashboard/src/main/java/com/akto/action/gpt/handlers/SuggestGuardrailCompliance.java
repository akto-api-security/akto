package com.akto.action.gpt.handlers;

import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;

public class SuggestGuardrailCompliance implements QueryHandler {

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public SuggestGuardrailCompliance(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) throws Exception {
        BasicDBObject request = new BasicDBObject();
        request.put("query_type", GptQuery.SUGGEST_GUARDRAIL_COMPLIANCE.getName());
        request.put("inputType", meta.getString("inputType"));
        request.put("topicName", meta.getString("topicName"));
        request.put("topicDescription", meta.getString("topicDescription"));
        request.put("samplePhrases", meta.get("samplePhrases"));
        request.put("llmRule", meta.getString("llmRule"));

        BasicDBObject result = this.resultFetcherStrategy.fetchResult(request);
        return result != null ? result : new BasicDBObject();
    }
}
