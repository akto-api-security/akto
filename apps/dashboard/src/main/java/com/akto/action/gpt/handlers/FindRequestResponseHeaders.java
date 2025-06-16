package com.akto.action.gpt.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.data_extractors.ListHeaderNamesWithValues;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.dao.MetaDataAnalysisDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;

public class FindRequestResponseHeaders implements QueryHandler {

    private static final LoggerMaker logger = new LoggerMaker(FindRequestResponseHeaders.class, LogDb.DASHBOARD);
    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public FindRequestResponseHeaders(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String headersWithValues = "";
        Map<String, String> knownAnsMap = new HashMap<>();
        try {
            List<Pair<String, String>> headerKeysWithValues = new ListHeaderNamesWithValues().extractData(meta);
            for (Pair<String, String> pair : headerKeysWithValues) {
                // filter headers with known answer values
                String knownAnswer = MetaDataAnalysisDao.instance.getValueFromFinalHeadersTechMap(pair.getFirst());
                if(knownAnswer != null) {
                    knownAnsMap.put(pair.getFirst(), knownAnswer);
                    continue;
                }
                String currentHeaderString = pair.getFirst() + ": " + pair.getSecond();
                if (headersWithValues.isEmpty()) {
                    headersWithValues = currentHeaderString;
                } else {
                    headersWithValues += ", " + currentHeaderString;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        request.put("query_type", GptQuery.ANALYZE_REQUEST_RESPONSE_HEADERS.getName());
        request.put("headers_with_values", headersWithValues);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        logger.debug("request: " + request.toJson());
        BasicDBObject resp =  this.resultFetcherStrategy.fetchResult(request);
        if(!knownAnsMap.isEmpty()){
            knownAnsMap.keySet().forEach(header -> {
                String value = knownAnsMap.get(header);
                if (value != null && !value.isEmpty()) {
                    resp.put(header, value);
                }
            });
        }
        return resp;
    }
    
}
