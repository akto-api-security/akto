package com.akto.dao.monitoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.ConfigParserResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class FilterConfigYamlParser {

    public static FilterConfig parseTemplate(String content) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> config = mapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        return parseConfig(config);
    }

    public static FilterConfig parseConfig(Map<String, Object> config) throws Exception {

        FilterConfig filterConfig = null;

        String id = (String) config.get(FilterConfig.ID);
        if (id == null) {
            return filterConfig;
        }

        Object filterMap = config.get(FilterConfig.FILTER);
        if (filterMap == null) {
            // todo: should not be null, throw error
            return new FilterConfig(id, null, null);
        }

        ConfigParser configParser = new ConfigParser();
        ConfigParserResult filters = configParser.parse(filterMap);
        if (filters == null) {
            // todo: throw error
            new FilterConfig(id, null, null);
        }

        Map<String, List<String>> wordListMap = new HashMap<>();
        try {
            if (config.containsKey(FilterConfig.WORD_LISTS)) {
                wordListMap = (Map) config.get(FilterConfig.WORD_LISTS);
            }
        } catch (Exception e) {
            new FilterConfig(id, filters, null);

        }

        return new FilterConfig(id, filters, wordListMap);

    }

}
