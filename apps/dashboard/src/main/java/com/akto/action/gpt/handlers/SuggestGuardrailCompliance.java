package com.akto.action.gpt.handlers;

import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.gpt.handlers.gpt_prompts.GuardrailComplianceSuggestionHandler;
import com.mongodb.BasicDBObject;

public class SuggestGuardrailCompliance implements QueryHandler {

    private static final String QUERY_TYPE = "query_type";

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public SuggestGuardrailCompliance(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) throws Exception {
        BasicDBObject request = new BasicDBObject();
        request.put(QUERY_TYPE, GptQuery.SUGGEST_GUARDRAIL_COMPLIANCE.getName());
        request.put(GuardrailComplianceSuggestionHandler.INPUT_TYPE, meta.getString(GuardrailComplianceSuggestionHandler.INPUT_TYPE));
        request.put(GuardrailComplianceSuggestionHandler.TOPIC_NAME, meta.getString(GuardrailComplianceSuggestionHandler.TOPIC_NAME));
        request.put(GuardrailComplianceSuggestionHandler.TOPIC_DESCRIPTION, meta.getString(GuardrailComplianceSuggestionHandler.TOPIC_DESCRIPTION));
        request.put(GuardrailComplianceSuggestionHandler.SAMPLE_PHRASES, meta.get(GuardrailComplianceSuggestionHandler.SAMPLE_PHRASES));
        request.put(GuardrailComplianceSuggestionHandler.LLM_RULE, meta.getString(GuardrailComplianceSuggestionHandler.LLM_RULE));

        BasicDBObject result = this.resultFetcherStrategy.fetchResult(request);
        return result != null ? result : new BasicDBObject();
    }
}
