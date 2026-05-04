package com.akto.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.test_editor.TestConfig;
import com.akto.gpt.handlers.gpt_prompts.SuggestedTestsHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.TestTemplateUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuggestedTestsAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(SuggestedTestsAction.class, LogDb.DASHBOARD);

    private int apiCollectionId;
    private String url;
    private String method;

    private List<String> suggestedTestIds = new ArrayList<>();
    private String error;

    public String getSuggestedTests() {
        try {
            String sampleDataStr = SampleDataDao.getLatestSampleData(apiCollectionId, url, method);
            if (sampleDataStr == null) {
                sampleDataStr = "";
            } else if (sampleDataStr.length() > SuggestedTestsHandler.MAX_SAMPLE_DATA_LENGTH) {
                sampleDataStr = sampleDataStr.substring(0, SuggestedTestsHandler.MAX_SAMPLE_DATA_LENGTH);
            }

            CONTEXT_SOURCE contextSource = Context.contextSource.get();
            Set<String> allowedCategoryNames = Arrays.stream(
                    TestTemplateUtils.getAllTestCategoriesWithinContext(contextSource))
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            Map<String, TestConfig> testConfigMap = YamlTemplateDao.instance.fetchTestConfigMap(
                    false, true, 0, SuggestedTestsHandler.MAX_TESTS_COUNT, Filters.empty());

            List<BasicDBObject> tests = new ArrayList<>();
            for (Map.Entry<String, TestConfig> e : testConfigMap.entrySet()) {
                TestConfig config = e.getValue();
                if (config == null || config.getInfo() == null || config.getInfo().getCategory() == null) continue;
                String categoryName = config.getInfo().getCategory().getName();
                if (categoryName == null || !allowedCategoryNames.contains(categoryName)) continue;
                if ("FUZZING".equals(config.getInfo().getName())) continue;
                BasicDBObject test = new BasicDBObject();
                test.put("id", config.getId());
                String name = config.getInfo().getName() != null ? config.getInfo().getName() : config.getId();
                test.put("name", name);
                String categoryDisplay = config.getInfo().getCategory().getDisplayName() != null
                        ? config.getInfo().getCategory().getDisplayName() : categoryName;
                test.put("category", categoryDisplay);
                tests.add(test);
            }

            BasicDBObject queryData = new BasicDBObject();
            queryData.put(SuggestedTestsHandler.SAMPLE_DATA, sampleDataStr);
            queryData.put(SuggestedTestsHandler.TESTS, tests);

            SuggestedTestsHandler handler = new SuggestedTestsHandler();
            BasicDBObject result = handler.handle(queryData);

            if (result.containsKey("error")) {
                error = (String) result.get("error");
                return Action.ERROR.toUpperCase();
            }
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) result.get(SuggestedTestsHandler.SUGGESTED_TEST_IDS);
            if (ids != null) {
                suggestedTestIds = ids;
            }
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "getSuggestedTests failed");
            error = e.getMessage();
            return Action.ERROR.toUpperCase();
        }
    }
}
