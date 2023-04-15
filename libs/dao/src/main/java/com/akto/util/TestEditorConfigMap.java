package com.akto.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dto.test_editor.TestConfig;

public class TestEditorConfigMap {
    
    public static Map<String, TestConfig> testConfigMap = new HashMap<>();

    static {
        initTestConfigMap();
    }

    public static void initTestConfigMap() {

        String path = "/Users/admin/akto_code/akto/libs/dao/src/main/java/com/akto/dao/test_editor/inbuilt_test_yaml_files";

        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();
        TestConfigYamlParser testConfigYamlParser = new TestConfigYamlParser();
        File file;
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                // System.out.println("File " + listOfFiles[i].getName());
                file = listOfFiles[i];
                TestConfig config = testConfigYamlParser.parseTemplate(file.getAbsolutePath());
                if (config != null) {
                    testConfigMap.put(config.getId(), config);
                }
            }
        }
    }

}
