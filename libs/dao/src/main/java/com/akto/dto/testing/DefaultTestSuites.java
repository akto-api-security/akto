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
        testSuitesPerType.put(DefaultSuitesType.ATTACK_BASE_TECHNIQUE.name(), 13);
        testSuitesPerType.put(DefaultSuitesType.ATTACK_BASE_TECHNIQUE.name(), 13);
        testSuitesPerType.put(DefaultSuitesType.ATTACK_STRATEGY.name(), 10);

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
        AI_AGENT_SECURITY,
        ATTACK_BASE_TECHNIQUE,
        ATTACK_STRATEGY,
        ATTACK_TECHNIQUE
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

    /* Attack strategy suites, keyed by the agentic OWASP top 10 (2026) categories */
    public static final Map<String, List<String>> attackStrategyList = new HashMap<>();
    static {
        attackStrategyList.put("Agent Goal Hijack", Arrays.asList("AGENT_GOAL_HIJACK"));
        attackStrategyList.put("Tool Misuse and Exploitation", Arrays.asList("TOOL_MISUSE_AND_EXPLOITATION"));
        attackStrategyList.put("Identity and Privilege Abuse", Arrays.asList("IDENTITY_AND_PRIVILEGE_ABUSE"));
        attackStrategyList.put("Agentic Supply Chain", Arrays.asList("AGENTIC_SUPPLY_CHAIN"));
        attackStrategyList.put("Unexpected Code Execution", Arrays.asList("UNEXPECTED_CODE_EXECUTION"));
        attackStrategyList.put("Memory and Context Poisoning", Arrays.asList("MEMORY_AND_CONTEXT_POISONING"));
        attackStrategyList.put("Insecure Inter-Agent Communication", Arrays.asList("INSECURE_INTER_AGENT_COMMUNICATION"));
        attackStrategyList.put("Cascading Failures", Arrays.asList("CASCADING_FAILURES"));
        attackStrategyList.put("Human-Agent Trust Exploitation", Arrays.asList("HUMAN_AGENT_TRUST_EXPLOITATION"));
        attackStrategyList.put("Rogue Agents", Arrays.asList("ROGUE_AGENTS"));
    }

    public static final String OTHERS_SUITE = "Others";

    /* Base attack techniques, matched against the trailing part of info.name */
    public static final List<String> attackBaseTechniqueList = Arrays.asList(
            "Base64",
            "Leetspeak",
            "Math Problem",
            "Multilingual",
            "Prompt Injection",
            "Roleplay",
            "ROT13",
            "Context Poisoning",
            "Goal Redirection",
            "Input Bypass",
            "Permission Escalation",
            "Semantic Manipulation",
            "System Override"
    );

    public static String resolveAttackBaseTechnique(String templateName) {
        // send 0 if it doesnt contains, 1 if contains and 2 if ends with
        // ends with:  single shot { base test }, contains multi-shot
        if (templateName == null) {
            return OTHERS_SUITE;
        }

        String name = templateName.trim();
        String[] parts = name.split("-");
        for (String baseTechnique : attackBaseTechniqueList) {
            if (name.toLowerCase().contains(baseTechnique.toLowerCase())) {
                String lastPart = parts[parts.length -1];
                if(lastPart.toLowerCase().equals(baseTechnique.toLowerCase())){
                    return baseTechnique + "-basic";
                }
                return baseTechnique;
            }
        }
        return OTHERS_SUITE;
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
