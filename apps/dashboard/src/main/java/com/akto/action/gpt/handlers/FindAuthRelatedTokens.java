package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.data_extractors.ListHeaderNames;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FindAuthRelatedTokens implements QueryHandler{
    private static final Logger logger = LoggerFactory.getLogger(GenerateCurlForTest.class);
    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public FindAuthRelatedTokens(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String csvHeaders = "";
        try {
            List<String> headerKeys = new ListHeaderNames().extractData(meta);
            csvHeaders = StringUtils.join(headerKeys, ", ");
        } catch (Exception e) {
            e.printStackTrace();
        }
        request.put("query_type", GptQuery.FIND_AUTH_RELATED_TOKENS.getName());
        request.put("csv_headers", csvHeaders);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        logger.info("request: " + request.toJson());
        BasicDBObject resp =  this.resultFetcherStrategy.fetchResult(request);
        String respStr = resp.toJson();
        return BasicDBObject.parse(respStr);
    }




}
