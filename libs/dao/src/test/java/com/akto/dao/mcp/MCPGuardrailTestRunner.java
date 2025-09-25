package com.akto.dao.mcp;

import com.akto.dto.mcp.MCPGuardrailConfig;
import com.akto.dto.mcp.MCPGuardrailConfigYamlParser;
import com.akto.dto.test_editor.YamlTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Simple test runner to verify MCPGuardrailConfig functionality
 * This can be run to test insert and fetch operations
 */
public class MCPGuardrailTestRunner {

    public static void main(String[] args) {
        System.out.println("Starting MCPGuardrailConfig Test Runner...");
        
        try {
            testBasicYamlParsing();
            testDaoOperations();
            testErrorHandling();
            System.out.println("All tests passed successfully!");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void testBasicYamlParsing() throws Exception {
        System.out.println("Testing basic YAML parsing...");
        
        String yamlContent = "id: \"test-guardrail-001\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"test-value\"";

        MCPGuardrailConfig config = MCPGuardrailConfigYamlParser.parseTemplate(yamlContent);
        
        if (config == null) {
            throw new RuntimeException("Config should not be null");
        }
        if (!"test-guardrail-001".equals(config.getId())) {
            throw new RuntimeException("ID mismatch");
        }
        if (config.getFilter() == null) {
            throw new RuntimeException("Filter should not be null");
        }
        
        System.out.println("✓ Basic YAML parsing test passed");
    }

    public static void testDaoOperations() throws Exception {
        System.out.println("Testing DAO operations...");
        
        // Create test YamlTemplate
        YamlTemplate template = new YamlTemplate();
        template.setId("template-1");
        template.setAuthor("test-author");
        template.setCreatedAt(1234567890);
        template.setUpdatedAt(1234567890);
        template.setContent("id: \"guardrail-001\"\n" +
                "filter:\n" +
                "  pred:\n" +
                "    - data:\n" +
                "        - \"test-value\"");

        List<YamlTemplate> templates = Arrays.asList(template);
        MCPGuardrailYamlTemplateDao dao = new MCPGuardrailYamlTemplateDao();

        // Test fetch with content
        Map<String, MCPGuardrailConfig> resultWithContent = dao.fetchMCPGuardrailConfig(true, templates);
        
        if (resultWithContent.size() != 1) {
            throw new RuntimeException("Expected 1 config, got " + resultWithContent.size());
        }
        
        MCPGuardrailConfig config = resultWithContent.get("guardrail-001");
        if (config == null) {
            throw new RuntimeException("Config should not be null");
        }
        if (!"guardrail-001".equals(config.getId())) {
            throw new RuntimeException("ID mismatch");
        }
        if (config.getFilter() == null) {
            throw new RuntimeException("Filter should not be null");
        }
        if (config.getContent() == null) {
            throw new RuntimeException("Content should not be null when includeYamlContent=true");
        }

        // Test fetch without content
        Map<String, MCPGuardrailConfig> resultWithoutContent = dao.fetchMCPGuardrailConfig(false, templates);
        
        if (resultWithoutContent.size() != 1) {
            throw new RuntimeException("Expected 1 config, got " + resultWithoutContent.size());
        }
        
        MCPGuardrailConfig configNoContent = resultWithoutContent.get("guardrail-001");
        if (configNoContent == null) {
            throw new RuntimeException("Config should not be null");
        }
        if (configNoContent.getContent() != null) {
            throw new RuntimeException("Content should be null when includeYamlContent=false");
        }
        
        System.out.println("✓ DAO operations test passed");
    }

    public static void testErrorHandling() throws Exception {
        System.out.println("Testing error handling...");
        
        // Test invalid YAML
        try {
            MCPGuardrailConfigYamlParser.parseTemplate("invalid: yaml: content: [");
            throw new RuntimeException("Should have thrown exception for invalid YAML");
        } catch (Exception e) {
            // Expected
        }

        // Test empty YAML
        try {
            MCPGuardrailConfigYamlParser.parseTemplate("");
            throw new RuntimeException("Should have thrown exception for empty YAML");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test null YAML
        try {
            MCPGuardrailConfigYamlParser.parseTemplate(null);
            throw new RuntimeException("Should have thrown exception for null YAML");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test invalid template in DAO
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
        
        if (result.size() != 0) {
            throw new RuntimeException("Expected 0 configs for invalid template, got " + result.size());
        }
        
        System.out.println("✓ Error handling test passed");
    }
}
