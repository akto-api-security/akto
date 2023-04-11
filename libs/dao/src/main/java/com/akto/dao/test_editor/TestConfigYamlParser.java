package com.akto.dao.test_editor;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dao.test_editor.data_operands_impl.DataOperandsImpl;
import com.akto.dao.test_editor.data_operands_impl.ApiTypeFilter;
import com.akto.dao.test_editor.data_operands_impl.ExcludeMethodsFilter;
import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Config;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.TestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TestConfigYamlParser {

    public TestConfigYamlParser() { }

    public ConfigParserResult parseTemplate(String fileName) {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> x = new HashMap<>();

        try {
            //String filePath = new File("").getAbsolutePath() + "/libs/dao/src/main/java/com/akto/dto/test_editor/" + fileName + ".yaml";
            String filePath = "/Users/admin/akto_code/akto/libs/dao/src/main/java/com/akto/dao/test_editor/inbuilt_test_yaml_files/" + fileName + ".yaml";
            Map<String, Map<String, Object>> config = mapper.readValue(new File(filePath), Map.class);
            TestConfigParser testConfigParser = new TestConfigParser();
            ConfigParserResult configParserResult = testConfigParser.parse(config.get("api_selection_filters"));
            return configParserResult;
        } catch (Exception e) {
            System.out.print(e);
            return null;
        }
    }

}
