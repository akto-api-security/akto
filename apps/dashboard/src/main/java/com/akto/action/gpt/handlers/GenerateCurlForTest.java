package com.akto.action.gpt.handlers;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.action.gpt.utils.HeadersUtils;
import com.akto.dto.OriginalHttpRequest;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

public class GenerateCurlForTest implements QueryHandler{

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public GenerateCurlForTest(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String curl = "";
        String sampleData = meta.getString("sample_data");
        Pair<String, List<Pair<String, String>>> modifiedHeaders = HeadersUtils.minifyHeaders(sampleData);
        String modifiedSampleData = modifiedHeaders.getLeft();
        try {
            curl = ExportSampleDataAction.getCurl(modifiedSampleData);
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
        BasicDBObject resp =  this.resultFetcherStrategy.fetchResult(request);
        String respStr = resp.toJson();
        respStr = HeadersUtils.replaceHeadersWithValues(Pair.of(respStr, modifiedHeaders.getRight()));
        return BasicDBObject.parse(respStr);
    }




}
