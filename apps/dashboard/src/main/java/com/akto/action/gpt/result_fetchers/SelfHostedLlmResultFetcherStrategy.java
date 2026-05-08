package com.akto.action.gpt.result_fetchers;

import com.akto.action.gpt.handlers.GptQuery;
import com.akto.gpt.handlers.gpt_prompts.AnalyzeRequestResponseHeaders;
import com.akto.gpt.handlers.gpt_prompts.AnalyzeVulnerabilityPromptHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;

public class SelfHostedLlmResultFetcherStrategy implements ResultFetcherStrategy<BasicDBObject> {
    
    private static final LoggerMaker logger = new LoggerMaker(SelfHostedLlmResultFetcherStrategy.class, LogDb.DASHBOARD);

    @Override
    public BasicDBObject fetchResult(BasicDBObject data) {
        String queryType = data.getString("query_type");
        if (GptQuery.getQuery(queryType).equals(GptQuery.ANALYZE_REQUEST_RESPONSE_HEADERS)) {
            return new AnalyzeRequestResponseHeaders().handle(data);
        } else if (GptQuery.getQuery(queryType).equals(GptQuery.ANALYZE_VULNERABILITY)) {
            // For vulnerability analysis, use the proper PromptHandler
            return new AnalyzeVulnerabilityPromptHandler().handle(data);
        } else {    
            BasicDBObject error = new BasicDBObject();
            logger.error("Error fetching result from Model Hosted on GCP");
            error.put("error", "Something went wrong. Please try again later." + data.get("query_type"));
            return error;
        }
    }
}

  