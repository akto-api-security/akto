package com.akto.action.gpt.handlers;

import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.data_extractors.ListSampleValues;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class FindSensitiveData implements QueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(FindSensitiveData.class);
    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public FindSensitiveData(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String kvPairs = "";
        try {
            List<String> listOfValues = new ListSampleValues().extractData(meta);
            kvPairs = StringUtils.join(listOfValues, "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
        request.put("query_type", GptQuery.FIND_SENSITIVE_DATA.getName());
        request.put("kv_pairs", kvPairs);
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        logger.info("request: " + request.toJson());
        BasicDBObject resp =  this.resultFetcherStrategy.fetchResult(request);
        String respStr = resp.toJson();
        return BasicDBObject.parse(respStr);
    }
}
