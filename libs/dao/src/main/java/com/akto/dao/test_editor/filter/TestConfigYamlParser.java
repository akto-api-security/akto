package com.akto.dao.test_editor;

import java.io.File;
import java.util.Map;

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
            TestConfigParser testConfigParser = new TestConfigParser();
            TestConfig configParserResult = testConfigParser.parseConfig(config);
            return configParserResult;
        } catch (Exception e) {
        System.out.print(e);
            return null;
        }
    }

}
