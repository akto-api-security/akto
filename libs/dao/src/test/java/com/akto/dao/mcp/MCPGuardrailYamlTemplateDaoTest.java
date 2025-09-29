package com.akto.dao.mcp;

import com.akto.dto.mcp.MCPGuardrailConfig;
import com.akto.dto.mcp.MCPGuardrailConfigYamlParser;
import com.akto.dto.test_editor.YamlTemplate;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MCPGuardrailYamlTemplateDaoTest {

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
        java.util.Map<String, Object> configMap = new java.util.HashMap<>();
        configMap.put("id", "map-test-001");
        
        java.util.Map<String, Object> filterMap = new java.util.HashMap<>();
        java.util.List<Object> predList = new java.util.ArrayList<>();
        java.util.Map<String, Object> predItem = new java.util.HashMap<>();
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
        java.util.Map<String, Object> configMap = new java.util.HashMap<>();
        configMap.put("name", "No ID Test");
        
        MCPGuardrailConfig config = MCPGuardrailConfigYamlParser.parseConfig(configMap);
        
        assertNull(config);
    }

    @Test
    public void testFetchMCPGuardrailConfigWithYamlTemplates() {
        // Create test YamlTemplate objects
        YamlTemplate template1 = new YamlTemplate();
        template1.setId("template-1");
        template1.setAuthor("author1");
        template1.setCreatedAt(1234567890);
        template1.setUpdatedAt(1234567890);
        template1.setContent("id: \"guardrail-001\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"test-value-1\"");

        YamlTemplate template2 = new YamlTemplate();
        template2.setId("template-2");
        template2.setAuthor("author2");
        template2.setCreatedAt(1234567891);
        template2.setUpdatedAt(1234567891);
        template2.setContent("id: \"guardrail-002\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"test-value-2\"");

        List<YamlTemplate> templates = Arrays.asList(template1, template2);

        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();

        // Test fetchMCPGuardrailConfig with includeYamlContent = true
        Map<String, MCPGuardrailConfig> resultWithContent = dao.fetchMCPGuardrailConfig(true, templates);
        
        assertEquals(2, resultWithContent.size());
        
        MCPGuardrailConfig config1 = resultWithContent.get("guardrail-001");
        assertNotNull(config1);
        assertEquals("guardrail-001", config1.getId());
        assertNotNull(config1.getFilter());
        assertEquals("author1", config1.getAuthor());
        assertEquals(1234567890, config1.getCreatedAt());
        assertEquals(1234567890, config1.getUpdatedAt());
        assertNotNull(config1.getContent());
        assertTrue(config1.getContent().contains("guardrail-001"));

        MCPGuardrailConfig config2 = resultWithContent.get("guardrail-002");
        assertNotNull(config2);
        assertEquals("guardrail-002", config2.getId());
        assertNotNull(config2.getFilter());
        assertEquals("author2", config2.getAuthor());
        assertEquals(1234567891, config2.getCreatedAt());
        assertEquals(1234567891, config2.getUpdatedAt());
        assertNotNull(config2.getContent());
        assertTrue(config2.getContent().contains("guardrail-002"));

        // Test fetchMCPGuardrailConfig with includeYamlContent = false
        Map<String, MCPGuardrailConfig> resultWithoutContent = dao.fetchMCPGuardrailConfig(false, templates);
        
        assertEquals(2, resultWithoutContent.size());
        
        MCPGuardrailConfig config1NoContent = resultWithoutContent.get("guardrail-001");
        assertNotNull(config1NoContent);
        assertNull(config1NoContent.getContent()); // Content should be null when includeYamlContent = false
    }

    @Test
    public void testFetchMCPGuardrailConfigWithInvalidYaml() {
        YamlTemplate invalidTemplate = new YamlTemplate();
        invalidTemplate.setId("invalid-template");
        invalidTemplate.setAuthor("invalid-author");
        invalidTemplate.setCreatedAt(1234567890);
        invalidTemplate.setUpdatedAt(1234567890);
        invalidTemplate.setContent("invalid: yaml: content: [");

        List<YamlTemplate> templates = Arrays.asList(invalidTemplate);

        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        
        // Should not throw exception, but should skip invalid templates
        Map<String, MCPGuardrailConfig> result = dao.fetchMCPGuardrailConfig(true, templates);
        
        assertEquals(0, result.size()); // Invalid template should be skipped
    }

    @Test
    public void testFetchMCPGuardrailConfigWithNullTemplate() {
        List<YamlTemplate> templates = Arrays.asList((YamlTemplate) null);

        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        Map<String, MCPGuardrailConfig> result = dao.fetchMCPGuardrailConfig(true, templates);
        
        assertEquals(0, result.size()); // Null template should be skipped
    }

    @Test
    public void testGetCollName() {
        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        assertEquals("mcp_guardrail_yaml_templates", dao.getCollName());
    }

    @Test
    public void testGetClassT() {
        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        assertEquals(YamlTemplate.class, dao.getClassT());
    }
}
