package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.data_extractors.ListHeaderNames;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class FindAuthRelatedTokens implements QueryHandler{

    private static final LoggerMaker logger = new LoggerMaker(FindAuthRelatedTokens.class, LogDb.DASHBOARD);
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
            logger.error(e.getMessage(), e);
        }
        request.put("query_type", GptQuery.FIND_AUTH_RELATED_TOKENS.getName());
        request.put("csv_headers", csvHeaders);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        logger.debug("request: " + request.toJson());
        BasicDBObject resp =  this.resultFetcherStrategy.fetchResult(request);
        String respStr = resp.toJson();
        return BasicDBObject.parse(respStr);
    }




}
