package com.akto.dao.test_editor;

import java.io.File;
import java.util.Map;

import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dao.test_editor.info.InfoParser;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TestConfigYamlParser {

    public TestConfigYamlParser() { }

    public TestConfig parseTemplate(String filePath) {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try {
            //String filePath = new File("").getAbsolutePath() + "/libs/dao/src/main/java/com/akto/dto/test_editor/" + fileName + ".yaml";
            //String filePath = "/Users/admin/akto_code/akto/libs/dao/src/main/java/com/akto/dao/test_editor/inbuilt_test_yaml_files/" + fileName + ".yaml";
            Map<String, Object> config = mapper.readValue(new File(filePath), Map.class);
            ConfigParser testConfigParser = new ConfigParser();
            TestConfig configParserResult = parseConfig(config);
            return configParserResult;
        } catch (Exception e) {
            System.out.print(e);
            return null;
        }
    }

    public TestConfig parseConfig(Map<String, Object> config) {

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

        Object filterMap = config.get("api_selection_filters");
        if (filterMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, null, null, null);
        }
        
        ConfigParser configParser = new ConfigParser();
        ConfigParserResult filters = configParser.parse(filterMap);
        if (filters == null) {
            // todo: throw error
            new TestConfig(id, info, null, null, null);
        }

        Object executionMap = config.get("execute");
        if (executionMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, filters, null, null);
        }
        
        com.akto.dao.test_editor.executor.ConfigParser executorConfigParser = new com.akto.dao.test_editor.executor.ConfigParser();
        ExecutorConfigParserResult executeOperations = executorConfigParser.parseConfigMap(executionMap);
        if (executeOperations == null) {
            // todo: throw error
            new TestConfig(id, info, filters, null, null);
        }

        Object validationMap = config.get("validation");
        if (validationMap == null) {
            // todo: should not be null, throw error
            return new TestConfig(id, info, filters, executeOperations, null);
        }

        ConfigParserResult validations = configParser.parse(validationMap);
        if (validations == null) {
            // todo: throw error
            new TestConfig(id, info, null, null, null);
        }


        testConfig = new TestConfig(id, info, filters, executeOperations, validations);
        return testConfig;
    }

}
