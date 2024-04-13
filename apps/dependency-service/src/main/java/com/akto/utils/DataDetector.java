package com.akto.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

public class DataDetector {
    public static boolean isJSON(String data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(data);
            return true;
        } catch(JsonProcessingException e) {
            return false;
        }
    }

    public static boolean isYAML(String data) {
        try {
            Yaml yaml = new Yaml();
            yaml.load(data);
            return true;
        } catch(YAMLException e) {
            return false;
        }
    }
}
