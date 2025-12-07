package com.akto.dao.test_editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.akto.dao.test_editor.auth.Parser;
import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dao.test_editor.info.InfoParser;
import com.akto.dao.test_editor.strategy.StrategyParser;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.SeverityParserResult;
import com.akto.dto.test_editor.Strategy;
import com.akto.dto.test_editor.TestConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TestConfigYamlParser {

    public TestConfigYamlParser() { }

    public static TestConfig parseTemplate(String content) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> config = mapper.readValue(content, Map.class);
        return parseConfig(config);
    }

    public static TestConfig parseConfig(Map<String, Object> config) throws Exception {

        TestConfig testConfig = null;

        String id = (String) config.get("id");
        if (id == null) {
            return testConfig;
        }

        Object infoMap = config.get("info");
        if (infoMap == null) {
            return testConfig;
        }

        InfoParser infoParser = new InfoParser();
        Info info = infoParser.parse(infoMap);
        if (info == null) {
            return testConfig;
        }

        Object authMap = config.get("auth");
        Auth auth = null;
        if (authMap != null) {
            Parser authParser = new Parser();
            auth = authParser.parse(authMap);
            if (auth == null) {
                return new TestConfig(id, info, null, null, null, null, null, null);
            }
        }

        Object filterMap = config.get("api_selection_filters");
        if (filterMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, auth, null, null, null, null, null);
        }

        ConfigParser configParser = new ConfigParser();
        ConfigParserResult filters = configParser.parse(filterMap);
        if (filters == null) {
            // todo: throw error
            new TestConfig(id, info, auth, null, null, null, null, null);
        }

        Map<String, List<String>> wordListMap = new HashMap<>();
        try {
            if (config.containsKey("wordLists")) {
                wordListMap = (Map) config.get("wordLists");
            }
        } catch (Exception e) {
            return new TestConfig(id, info, null, null, null, null, null, null);
        }

        Object executionMap = config.get("execute");
        if (executionMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, auth, filters, wordListMap, null, null, null);
        }
        
        com.akto.dao.test_editor.executor.ConfigParser executorConfigParser = new com.akto.dao.test_editor.executor.ConfigParser();
        ExecutorConfigParserResult executeOperations = executorConfigParser.parseConfigMap(executionMap);
        if (executeOperations == null) {
            // todo: throw error
            new TestConfig(id, info, auth, filters, wordListMap, null, null, null);
        }

        Object validationMap = config.get("validate");
        if (validationMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, auth, filters, wordListMap, executeOperations, null, null);
        }

        ConfigParserResult validations = configParser.parse(validationMap);
        if (validations == null) {
            // todo: throw error
            new TestConfig(id, info, auth, filters, wordListMap, executeOperations, null, null);
        }

        List<Object> apiSeverityTemp = new ArrayList<>();
        List<SeverityParserResult> dynamicSeverityList = new ArrayList<>();
        try {
            if (config.containsKey(TestConfig.DYNAMIC_SEVERITY)) {
                apiSeverityTemp = (List) config.get(TestConfig.DYNAMIC_SEVERITY);
            }
            for (Object temp : apiSeverityTemp) {
                Map<String, Object> keys = (Map) temp;
                Object filter = keys.get(SeverityParserResult._CHECK);
                ConfigParserResult parsedFilter = null;
                if (filter != null) {
                    parsedFilter = configParser.parse(filter);
                }
                String str = (String)keys.get(SeverityParserResult._RETURN);
                SeverityParserResult spr = new SeverityParserResult(parsedFilter, str);
                dynamicSeverityList.add(spr);
            }
        } catch (Exception e) {
        }

        Object strategyObject = config.get("strategy");
        Strategy strategy = null;
        if (strategyObject != null) {
            StrategyParser strategyParser = new StrategyParser();   
            strategy = strategyParser.parse(strategyObject);
        }

        testConfig = new TestConfig(id, info, auth, filters, wordListMap, executeOperations, validations, strategy);
        testConfig.setDynamicSeverityList(dynamicSeverityList);
        return testConfig;
    }

    public static Object getFieldIfExists(String content, String field) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> config = mapper.readValue(content, new TypeReference<Map<String, Object>>() {});

        return config.get(field);
    }

    public static Map<String, List<String>> parseWordLists(String content) {
        Map<String, List<String>> wordListMap = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            Map<String, Object> config = mapper.readValue(content, Map.class);
            if (config.containsKey("wordLists")) {
                wordListMap = (Map) config.get("wordLists");
            }
        } catch (Exception e) {
            return wordListMap;
        }
        return wordListMap;
    }

}
