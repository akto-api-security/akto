package com.akto.dao.test_editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.akto.dao.test_editor.auth.Parser;
import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dao.test_editor.info.InfoParser;
import com.akto.dao.test_editor.settings.SettingsParser;
import com.akto.dao.test_editor.strategy.StrategyParser;
import com.akto.dto.test_editor.*;
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

    public static Map<String, List<String>> parseComplianceTemplate(String content) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> config = mapper.readValue(content, Map.class);

        Map<String, List<String>> ret = new HashMap<>();
        for(String complianceName: config.keySet()) {
            List<Object> listObj = (List) config.get(complianceName);
            List<String> listStr = listObj.stream().map(x -> x.toString()).collect(Collectors.toList());
            ret.put(complianceName.toUpperCase(), listStr);
        }

        return ret;
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

        Object settingsMap = config.get("attributes");
        TemplateSettings attributes = null;
        if (settingsMap != null) {
            SettingsParser settingsParser = new SettingsParser();
            attributes = settingsParser.parse(settingsMap);
            if (attributes == null) {
                return new TestConfig(id, info, null, null, null, null, null, null, null);
            }
        }

        Object authMap = config.get("auth");
        Auth auth = null;
        if (authMap != null) {
            Parser authParser = new Parser();
            auth = authParser.parse(authMap);
            if (auth == null) {
                return new TestConfig(id, info, null, null, null, null, null, null, attributes);
            }
        }

        Object filterMap = config.get("api_selection_filters");
        if (filterMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, auth, null, null, null, null, null, attributes);
        }

        ConfigParser configParser = new ConfigParser();
        ConfigParserResult filters = configParser.parse(filterMap);
        if (filters == null) {
            // todo: throw error
            new TestConfig(id, info, auth, null, null, null, null, null, attributes);
        }

        Map<String, List<String>> wordListMap = new HashMap<>();
        try {
            if (config.containsKey("wordLists")) {
                wordListMap = (Map) config.get("wordLists");
            }
        } catch (Exception e) {
            return new TestConfig(id, info, null, null, null, null, null, null, attributes);
        }

        Object executionMap = config.get("execute");
        if (executionMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, auth, filters, wordListMap, null, null, null, attributes);
        }
        
        com.akto.dao.test_editor.executor.ConfigParser executorConfigParser = new com.akto.dao.test_editor.executor.ConfigParser();
        ExecutorConfigParserResult executeOperations = executorConfigParser.parseConfigMap(executionMap);
        if (executeOperations == null) {
            // todo: throw error
            new TestConfig(id, info, auth, filters, wordListMap, null, null, null, attributes);
        }

        Object validationMap = config.get("validate");
        if (validationMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, auth, filters, wordListMap, executeOperations, null, null, attributes);
        }

        ConfigParserResult validations = configParser.parse(validationMap);
        if (validations == null) {
            // todo: throw error
            new TestConfig(id, info, auth, filters, wordListMap, executeOperations, null, null, attributes);
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

        testConfig = new TestConfig(id, info, auth, filters, wordListMap, executeOperations, validations, strategy, attributes);
        testConfig.setDynamicSeverityList(dynamicSeverityList);
        return testConfig;
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

    public static Object getFieldIfExists(String content, String field) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> config = mapper.readValue(content, new TypeReference<Map<String, Object>>() {});

        return config.get(field);
    }

}
