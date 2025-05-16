package com.akto.action.gpt.result_fetchers;

import static com.mongodb.client.model.Filters.type;

import com.akto.action.gpt.handlers.GptQuery;
import com.akto.action.gpt.handlers.gpt_prompts.AnalyzeRequestResponseHeaders;
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
        } else {    
            BasicDBObject error = new BasicDBObject();
            logger.error("Error fetching result from Model Hosted on GCP");
            error.put("error", "Something went wrong. Please try again later." + data.get("query_type"));
            return error;
        }
    }
}

  