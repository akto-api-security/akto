package com.akto.dao.mcp;

import com.akto.dto.mcp.MCPGuardrailConfig;
import com.akto.dto.mcp.MCPGuardrailConfigYamlParser;
import com.akto.dto.test_editor.YamlTemplate;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MCPGuardrailIntegrationTest {

    @Test
    public void testCompleteInsertAndFetchFlow() throws Exception {
        // Create a test YAML template
        String yamlContent = "id: \"integration-test-001\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"integration-test-value\"";

        // Create YamlTemplate object
        YamlTemplate yamlTemplate = new YamlTemplate();
        yamlTemplate.setId("template-integration-001");
        yamlTemplate.setAuthor("integration-author");
        yamlTemplate.setCreatedAt(1234567890);
        yamlTemplate.setUpdatedAt(1234567890);
        yamlTemplate.setContent(yamlContent);

        // Test parsing the YAML content
        MCPGuardrailConfig parsedConfig = MCPGuardrailConfigYamlParser.parseTemplate(yamlContent);
        
        assertNotNull(parsedConfig);
        assertEquals("integration-test-001", parsedConfig.getId());
        assertNotNull(parsedConfig.getFilter());

        // Test DAO functionality
        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        List<YamlTemplate> templates = Arrays.asList(yamlTemplate);

        // Test fetch with content
        Map<String, MCPGuardrailConfig> resultWithContent = dao.fetchMCPGuardrailConfig(true, templates);
        
        assertEquals(1, resultWithContent.size());
        MCPGuardrailConfig fetchedConfig = resultWithContent.get("integration-test-001");
        
        assertNotNull(fetchedConfig);
        assertEquals("integration-test-001", fetchedConfig.getId());
        assertNotNull(fetchedConfig.getFilter());
        assertEquals("integration-author", fetchedConfig.getAuthor());
        assertEquals(1234567890, fetchedConfig.getCreatedAt());
        assertEquals(1234567890, fetchedConfig.getUpdatedAt());
        assertNotNull(fetchedConfig.getContent());
        assertTrue(fetchedConfig.getContent().contains("integration-test-001"));

        // Test fetch without content
        Map<String, MCPGuardrailConfig> resultWithoutContent = dao.fetchMCPGuardrailConfig(false, templates);
        
        assertEquals(1, resultWithoutContent.size());
        MCPGuardrailConfig fetchedConfigNoContent = resultWithoutContent.get("integration-test-001");
        
        assertNotNull(fetchedConfigNoContent);
        assertEquals("integration-test-001", fetchedConfigNoContent.getId());
        assertNull(fetchedConfigNoContent.getContent()); // Content should be null
    }

    @Test
    public void testMultipleTemplatesFlow() throws Exception {
        // Create multiple test templates
        YamlTemplate template1 = new YamlTemplate();
        template1.setId("template-1");
        template1.setAuthor("author1");
        template1.setCreatedAt(1234567890);
        template1.setUpdatedAt(1234567890);
        template1.setContent("id: \"guardrail-001\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"value1\"");

        YamlTemplate template2 = new YamlTemplate();
        template2.setId("template-2");
        template2.setAuthor("author2");
        template2.setCreatedAt(1234567891);
        template2.setUpdatedAt(1234567891);
        template2.setContent("id: \"guardrail-002\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"value2\"");

        YamlTemplate template3 = new YamlTemplate();
        template3.setId("template-3");
        template3.setAuthor("author3");
        template3.setCreatedAt(1234567892);
        template3.setUpdatedAt(1234567892);
        template3.setContent("id: \"guardrail-003\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"value3\"");

        List<YamlTemplate> templates = Arrays.asList(template1, template2, template3);

        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        Map<String, MCPGuardrailConfig> result = dao.fetchMCPGuardrailConfig(true, templates);

        assertEquals(3, result.size());

        // Verify first guardrail
        MCPGuardrailConfig config1 = result.get("guardrail-001");
        assertNotNull(config1);
        assertEquals("guardrail-001", config1.getId());
        assertNotNull(config1.getFilter());
        assertEquals("author1", config1.getAuthor());

        // Verify second guardrail
        MCPGuardrailConfig config2 = result.get("guardrail-002");
        assertNotNull(config2);
        assertEquals("guardrail-002", config2.getId());
        assertNotNull(config2.getFilter());
        assertEquals("author2", config2.getAuthor());

        // Verify third guardrail
        MCPGuardrailConfig config3 = result.get("guardrail-003");
        assertNotNull(config3);
        assertEquals("guardrail-003", config3.getId());
        assertNotNull(config3.getFilter());
        assertEquals("author3", config3.getAuthor());
    }


    @Test
    public void testErrorHandlingWithInvalidTemplates() {
        // Create template with invalid YAML
        YamlTemplate invalidTemplate = new YamlTemplate();
        invalidTemplate.setId("invalid-template");
        invalidTemplate.setAuthor("invalid-author");
        invalidTemplate.setCreatedAt(1234567890);
        invalidTemplate.setUpdatedAt(1234567890);
        invalidTemplate.setContent("invalid: yaml: content: [");

        // Create template with null content
        YamlTemplate nullContentTemplate = new YamlTemplate();
        nullContentTemplate.setId("null-content-template");
        nullContentTemplate.setAuthor("null-author");
        nullContentTemplate.setCreatedAt(1234567891);
        nullContentTemplate.setUpdatedAt(1234567891);
        nullContentTemplate.setContent(null);

        // Create template with empty content
        YamlTemplate emptyContentTemplate = new YamlTemplate();
        emptyContentTemplate.setId("empty-content-template");
        emptyContentTemplate.setAuthor("empty-author");
        emptyContentTemplate.setCreatedAt(1234567892);
        emptyContentTemplate.setUpdatedAt(1234567892);
        emptyContentTemplate.setContent("");

        List<YamlTemplate> templates = Arrays.asList(invalidTemplate, nullContentTemplate, emptyContentTemplate);

        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();
        
        // Should not throw exception, but should skip invalid templates
        Map<String, MCPGuardrailConfig> result = dao.fetchMCPGuardrailConfig(true, templates);
        
        assertEquals(0, result.size()); // All invalid templates should be skipped
    }
}
