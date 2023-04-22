package com.akto.action.gpt.handlers;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.mongodb.BasicDBObject;

public class GenerateCurlForTest implements QueryHandler{

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public GenerateCurlForTest(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String curl = "";
        try {
            curl = ExportSampleDataAction.getCurl(meta.getString("sample_data"));
            System.out.println("curl: " + curl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        request.put("query_type", GptQuery.GENERATE_CURL_FOR_TEST.getName());
        request.put("curl", curl);
        request.put("test_type", meta.getString("test_type"));
        request.put("response_details", meta.getString("response_details"));
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        System.out.println("request: " + request.toJson());
        return this.resultFetcherStrategy.fetchResult(request);
    }
}
