package com.akto.action.gpt.handlers;

import com.akto.action.ExportSampleDataAction;
import com.akto.action.gpt.GptAction;
import com.akto.action.gpt.result_fetchers.ResultFetcherStrategy;
import com.akto.action.gpt.utils.HeadersUtils;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.Info;
import com.mongodb.BasicDBObject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

public class SuggestTests implements QueryHandler {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GenerateCurlForTest.class);
    public static final String AUTH_TOKEN = "%AUTH_TOKEN%";
    public static final String ACCESS_TOKEN = "%ACCESS_TOKEN%";
    public static final String COOKIE = "%COOKIE%";

    private String auth_token_value = null;
    private String access_token_value = null;
    private String cookie_value = null;

    private final ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy;

    public SuggestTests(ResultFetcherStrategy<BasicDBObject> resultFetcherStrategy) {
        this.resultFetcherStrategy = resultFetcherStrategy;
    }

    @Override
    public BasicDBObject handleQuery(BasicDBObject meta) {
        BasicDBObject request = new BasicDBObject();
        String curl = "";
        String sampleData = meta.getString("sample_data");
        Pair<String, List<Pair<String, String>>> modifiedHeaders = HeadersUtils.minifyHeaders(sampleData);
        try {
            curl = ExportSampleDataAction.getCurl(modifiedHeaders.getLeft());
        } catch (Exception e) {
            e.printStackTrace();
        }
        request.put("query_type", GptQuery.SUGGEST_TESTS.getName());
        request.put("curl", curl);
        request.put("response_details", meta.getString("response_details"));
        request.put("test_details", getTestDetails());
        request.put(GptAction.USER_EMAIL, meta.getString(GptAction.USER_EMAIL));
        BasicDBObject resp = this.resultFetcherStrategy.fetchResult(request);
        String respStr = resp.toJson();
        respStr = HeadersUtils.replaceHeadersWithValues(Pair.of(respStr, modifiedHeaders.getRight()));
        return BasicDBObject.parse(respStr);
    }

    private String getTestDetails() {
        BasicDBObject testDetails = new BasicDBObject();
        Map<String, Info> testInfoMap = YamlTemplateDao.instance.fetchTestInfoMap();
        for (Map.Entry<String, Info> entry : testInfoMap.entrySet()) {
            Category category = entry.getValue().getCategory();
            testDetails.put(category.getName(), category.getShortName());
        }
        return testDetails.toJson();
    }
    
}