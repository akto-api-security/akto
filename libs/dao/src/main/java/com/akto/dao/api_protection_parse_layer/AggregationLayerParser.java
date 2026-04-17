package com.akto.dao.api_protection_parse_layer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.akto.dto.test_editor.ConfigParserResult;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AggregationLayerParser {

    ObjectMapper objectMapper = new ObjectMapper();
    ConfigParser configParser = new ConfigParser();

    public AggregationLayerParser() {
    }

    public AggregationRules parse(Map<String, Object> aggregationRules) throws Exception {

        List<Rule> rules = new ArrayList<>();
        AggregationRules aggRules = new AggregationRules(rules);

        try {
            for (Object aggObj: (List) aggregationRules.get("aggregation_rules")) {
                Map<String, Object> aggObjMap = (Map) aggObj;
                Rule rule = objectMapper.convertValue(aggObjMap.get("rule"), Rule.class);

                // Parse post_aggregate_condition if present (same syntax as filter)
                Object evaluatorMap = aggObjMap.get("post_aggregate_condition");
                if (evaluatorMap != null) {
                    ConfigParserResult evaluator = configParser.parse(evaluatorMap);
                    if (evaluator != null && evaluator.getIsValid()) {
                        rule.setAggregationEvaluator(evaluator);
                    }
                }

                rules.add(rule);
            }
            aggRules.setRule(rules);
        } catch (Exception e) {
            throw e;
        }
        return aggRules;
    }

}
