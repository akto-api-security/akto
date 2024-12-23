package com.akto.action.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            if (value.size() == 0) continue;
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
                case SingleTypeInfo._API_COLLECTION_ID:
                    filterList.add(Filters.in(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID, collectionIds));
                    break;
                case "categoryFilter":
                    filterList.add(Filters.in(TestingRunResult.TEST_SUPER_TYPE, value));
                    break;
                case "severityStatus":
                    filterList.add(Filters.in(TestingRunResult.TEST_RESULTS + ".0." + GenericTestResult._CONFIDENCE, value));
                default:
                    break;
            }
        }
        if(filterList.isEmpty()){
            return Filters.empty();
        }
        return Filters.and(filterList);
    }

}
