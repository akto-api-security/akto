package com.akto.action.testing;

import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.model.Filters;

public class Utils {

    public static Bson createFiltersForTestingReport(Map<String, List<String>> filterMap){
        List<Bson> filterList = new ArrayList<>();
        for(Map.Entry<String, List<String>> entry: filterMap.entrySet()) {
            String key = entry.getKey();
            List<String> value = entry.getValue();

            if (value.isEmpty()) continue;
            List<Integer> collectionIds = new ArrayList<>();
            if(key.equals(SingleTypeInfo._API_COLLECTION_ID)){
                for(String str: value){
                    collectionIds.add(Integer.parseInt(str));
                }
            }

            switch (key) {
                case SingleTypeInfo._METHOD:
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.METHOD, value));
                    break;
                case SingleTypeInfo._COLLECTION_IDS:
                case SingleTypeInfo._API_COLLECTION_ID:
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID, collectionIds));
                    break;
                case "categoryFilter":
                    filterList.add(Filters.in(TestingRunResult.TEST_SUPER_TYPE, value));
                    break;
                case "severityStatus":
                    filterList.add(Filters.in(TestingRunResult.TEST_RESULTS + ".0." + GenericTestResult._CONFIDENCE, value));
                case "testFilter":
                    filterList.add(Filters.in(TestingRunResult.TEST_SUB_TYPE, value));
                    break;
                case "apiNameFilter":
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.URL, value));
                default:
                    break;
            }
        }
        if(filterList.isEmpty()){
            return Filters.empty();
        }
        return Filters.and(filterList);
    }

    public static Map<String, String> mapIssueDescriptions(List<TestingRunIssues> issues,
        Map<TestingIssuesId, TestingRunResult> idToResultMap) {

        if (CollectionUtils.isEmpty(issues) || MapUtils.isEmpty(idToResultMap)) {
            return Collections.emptyMap();
        }

        Map<String, String> finalResult = new HashMap<>();
        for (TestingRunIssues issue : issues) {
            if (StringUtils.isNotBlank(issue.getDescription())) {
                TestingRunResult result = idToResultMap.get(issue.getId());
                if (result != null) {
                    finalResult.put(result.getHexId(), issue.getDescription());
                }
            }
        }
        return finalResult;
    }

}
