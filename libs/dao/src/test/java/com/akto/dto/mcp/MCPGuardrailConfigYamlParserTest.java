package com.akto.dto.mcp;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

public class MCPGuardrailConfigYamlParserTest {

    @Test
    public void testParseValidYamlTemplate() throws Exception {
        String yamlContent = "id: \"test-guardrail-001\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"test-value\"";

        MCPGuardrailConfig config = MCPGuardrailConfigYamlParser.parseTemplate(yamlContent);
        
        assertNotNull(config);
        assertEquals("test-guardrail-001", config.getId());
        assertNotNull(config.getFilter());
    }

    @Test
    public void testParseYamlWithDefaults() throws Exception {
        String yamlContent = "id: \"minimal-guardrail\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"default-value\"";

        MCPGuardrailConfig config = MCPGuardrailConfigYamlParser.parseTemplate(yamlContent);
        
        assertNotNull(config);
        assertEquals("minimal-guardrail", config.getId());
        assertNotNull(config.getFilter());
    }


    @Test(expected = Exception.class)
    public void testParseInvalidYaml() throws Exception {
        String invalidYaml = "invalid: yaml: content: [";
        MCPGuardrailConfigYamlParser.parseTemplate(invalidYaml);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseEmptyYaml() throws Exception {
        MCPGuardrailConfigYamlParser.parseTemplate("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullYaml() throws Exception {
        MCPGuardrailConfigYamlParser.parseTemplate(null);
    }

    @Test
    public void testParseConfigWithMap() throws Exception {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("id", "map-test-001");
        
        Map<String, Object> filterMap = new HashMap<>();
        java.util.List<Object> predList = new java.util.ArrayList<>();
        Map<String, Object> predItem = new HashMap<>();
        java.util.List<String> dataList = new java.util.ArrayList<>();
        dataList.add("test-data");
        predItem.put("data", dataList);
        predList.add(predItem);
        filterMap.put("pred", predList);
        configMap.put("filter", filterMap);
        

        MCPGuardrailConfig config = MCPGuardrailConfigYamlParser.parseConfig(configMap);
        
        assertNotNull(config);
        assertEquals("map-test-001", config.getId());
        assertNotNull(config.getFilter());
    }

    @Test
    public void testParseConfigWithNullId() throws Exception {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("name", "No ID Test");
        
        MCPGuardrailConfig config = MCPGuardrailConfigYamlParser.parseConfig(configMap);
        
        assertNull(config);
    }
}
