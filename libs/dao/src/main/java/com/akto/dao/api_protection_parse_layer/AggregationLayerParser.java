package com.akto.dao.api_protection_parse_layer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.akto.dto.api_protection_parse_layer.AggregationRules;
import com.akto.dto.api_protection_parse_layer.Rule;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AggregationLayerParser {

    ObjectMapper objectMapper = new ObjectMapper();

    public AggregationLayerParser() {
    }

    public AggregationRules parse(Map<String, Object> aggregationRules) throws Exception {

        List<Rule> rules = new ArrayList<>();
        AggregationRules aggRules = new AggregationRules(rules);

        try {
            for (Object aggObj: (List) aggregationRules.get("aggregation_rules")) {
                Map<String, Object> aggObjMap = (Map) aggObj;
                Rule rule = objectMapper.convertValue(aggObjMap.get("rule"), Rule.class);
                rules.add(rule);
            }
            aggRules.setRule(rules);
        } catch (Exception e) {
            throw e;
        }
        return aggRules;
    }

}
