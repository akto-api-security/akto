package com.akto.dto.testing;

import com.akto.dto.testing.config.TestSuites;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultTestSuites extends TestSuites {
    public static final String SUITE_TYPE = "suiteType";
    private DefaultSuitesType suiteType;

    private static final Map<String, Integer> testSuitesPerType = new HashMap<>();
    static {
        testSuitesPerType.put(DefaultSuitesType.OWASP.name(), 10);
        testSuitesPerType.put(DefaultSuitesType.TESTING_METHODS.name(), 2);
        testSuitesPerType.put(DefaultSuitesType.SEVERITY.name(), 4);
        testSuitesPerType.put(DefaultSuitesType.DURATION.name(), 2);
        testSuitesPerType.put(DefaultSuitesType.MCP_SECURITY.name(), 8);
        testSuitesPerType.put(DefaultSuitesType.AI_AGENT_SECURITY.name(), 8);
    }

    public DefaultTestSuites() {}

    public DefaultTestSuites(int createdAt, String createdBy, int lastUpdated, String name, List<String> subCategoryList, DefaultSuitesType suiteType) {
        super(name, subCategoryList, createdBy, lastUpdated, createdAt);
        this.suiteType = suiteType;
    }

    public enum DefaultSuitesType {
        OWASP,
        TESTING_METHODS,
        SEVERITY,
        DURATION,
        MCP_SECURITY,
        AI_AGENT_SECURITY
    }

    public static final Map<String, List<String>> owaspTop10List = new HashMap<>();
    static {
        owaspTop10List.put("Broken Object Level Authorization", Arrays.asList("BOLA"));
        owaspTop10List.put("Broken Authentication", Arrays.asList("NO_AUTH"));
        owaspTop10List.put("Broken Object Property Level Authorization", Arrays.asList("EDE", "MA"));
        owaspTop10List.put("Unrestricted Resource Consumption", Arrays.asList("RL"));
        owaspTop10List.put("Broken Function Level Authorization", Arrays.asList("BFLA"));
        owaspTop10List.put("Unrestricted Access to Sensitive Business Flows", Arrays.asList("INPUT"));
        owaspTop10List.put("Server Side Request Forgery", Arrays.asList("SSRF"));
        owaspTop10List.put("Security Misconfiguration", Arrays.asList("SM", "UHM", "VEM", "MHH", "SVD", "CORS", "ILM"));
        owaspTop10List.put("Improper Inventory Management", Arrays.asList("IAM", "IIM"));
        owaspTop10List.put("Unsafe Consumption of APIs", Arrays.asList("COMMAND_INJECTION", "INJ", "CRLF", "SSTI", "LFI", "XSS", "INJECT"));
    }

    public static final Map<String, List<String>> mcpSecurityList = new HashMap<>();
    static {
        mcpSecurityList.put("MCP Protocol Security", Arrays.asList("MCP"));
        mcpSecurityList.put("MCP Authentication", Arrays.asList("MCP_AUTH"));
        mcpSecurityList.put("MCP Input Validation", Arrays.asList("MCP_INPUT_VALIDATION"));
        mcpSecurityList.put("MCP Prompt Injection Attacks", Arrays.asList("MCP_PROMPT_INJECTION", "MCP_INDIRECT_PROMPT_INJECTION"));
        mcpSecurityList.put("MCP Tool Poisoning", Arrays.asList("MCP_TOOL_POISONING"));
        mcpSecurityList.put("MCP Data Security", Arrays.asList("MCP_SENSITIVE_DATA_LEAKAGE", "MCP_PRIVILEGE_ABUSE"));
        mcpSecurityList.put("MCP Denial of Service", Arrays.asList("MCP_DOS"));
        mcpSecurityList.put("MCP Malicious Code Execution", Arrays.asList("MCP_MALICIOUS_CODE_EXECUTION"));
    }

    public static final Map<String, List<String>> aiAgentSecurityList = new HashMap<>();
    static {
        aiAgentSecurityList.put("LLM & Prompt Security", Arrays.asList("LLM", "PROMPT_INJECTION"));
        aiAgentSecurityList.put("Information Disclosure", Arrays.asList("SENSITIVE_INFORMATION_DISCLOSURE", "SYSTEM_PROMPT_LEAKAGE"));
        aiAgentSecurityList.put("Supply Chain Security", Arrays.asList("SUPPLY_CHAIN"));
        aiAgentSecurityList.put("Model Integrity", Arrays.asList("DATA_AND_MODEL_POISONING", "VECTOR_AND_EMBEDDING_WEAKNESSES"));
        aiAgentSecurityList.put("Output Handling", Arrays.asList("IMPROPER_OUTPUT_HANDLING"));
        aiAgentSecurityList.put("Excessive Agency", Arrays.asList("EXCESSIVE_AGENCY"));
        aiAgentSecurityList.put("Misinformation", Arrays.asList("MISINFORMATION"));
        aiAgentSecurityList.put("Resource Management", Arrays.asList("UNBOUNDED_CONSUMPTION"));
    }

    public DefaultSuitesType getSuiteType() {
        return suiteType;
    }

    public void setSuiteType(DefaultSuitesType suiteType) {
        this.suiteType = suiteType;
    }

    public static int countOfDefaultTestSuites() {
        return testSuitesPerType.values().stream()
            .reduce(0, Integer::sum);
    }
}
