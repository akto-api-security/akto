package com.akto.util;

import com.akto.dto.testing.DefaultTestSuites.DefaultSuitesType;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.TestCategory;

import java.util.Arrays;

public class TestCategoryContextUtils {

    private TestCategoryContextUtils() {}

    public static TestCategory[] getAllTestCategoriesWithinContext(CONTEXT_SOURCE contextSource) {
        if (contextSource == null) {
            contextSource = CONTEXT_SOURCE.API;
        }
        TestCategory[] allCategories = GlobalEnums.TestCategory.values();
        TestCategory[] mcpCategories = {
            TestCategory.MCP_AUTH,
            TestCategory.MCP_INPUT_VALIDATION,
            TestCategory.MCP_DOS,
            TestCategory.MCP_SENSITIVE_DATA_LEAKAGE,
            TestCategory.MCP,
            TestCategory.MCP_TOOL_POISONING,
            TestCategory.MCP_PROMPT_INJECTION,
            TestCategory.MCP_PRIVILEGE_ABUSE,
            TestCategory.MCP_INDIRECT_PROMPT_INJECTION,
            TestCategory.MCP_MALICIOUS_CODE_EXECUTION,
            TestCategory.MCP_FUNCTION_MANIPULATION,
            TestCategory.MCP_SECURITY,
        };

        TestCategory[] emptyArr = {};

        final TestCategory[] llmCategories = {
            GlobalEnums.TestCategory.LLM,
            GlobalEnums.TestCategory.PROMPT_INJECTION,
            GlobalEnums.TestCategory.SENSITIVE_INFORMATION_DISCLOSURE,
            GlobalEnums.TestCategory.SUPPLY_CHAIN,
            GlobalEnums.TestCategory.DATA_AND_MODEL_POISONING,
            GlobalEnums.TestCategory.IMPROPER_OUTPUT_HANDLING,
            GlobalEnums.TestCategory.EXCESSIVE_AGENCY,
            GlobalEnums.TestCategory.SYSTEM_PROMPT_LEAKAGE,
            GlobalEnums.TestCategory.VECTOR_AND_EMBEDDING_WEAKNESSES,
            GlobalEnums.TestCategory.MISINFORMATION,
            GlobalEnums.TestCategory.UNBOUNDED_CONSUMPTION,
            TestCategory.AGENTIC_BUSINESS_ALIGNMENT,
            TestCategory.AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS,
            TestCategory.AGENTIC_SAFETY,
            TestCategory.AGENTIC_SECURITY,
            TestCategory.AGENTIC_SECURITY_PROMPT_INJECTION,
            TestCategory.AGENTIC_SECURITY_AGENT_EXPLOITATION,
            TestCategory.AGENTIC_SECURITY_INFRASTRUCTURE,
            TestCategory.AGENTIC_SECURITY_DATA_EXPOSURE,
            TestCategory.AGENTIC_SECURITY_CODE_EXECUTION,
            TestCategory.AGENT_GOAL_HIJACK,
            TestCategory.TOOL_MISUSE_AND_EXPLOITATION,
            TestCategory.IDENTITY_AND_PRIVILEGE_ABUSE,
            TestCategory.AGENTIC_SUPPLY_CHAIN,
            TestCategory.UNEXPECTED_CODE_EXECUTION,
            TestCategory.MEMORY_AND_CONTEXT_POISONING,
            TestCategory.INSECURE_INTER_AGENT_COMMUNICATION,
            TestCategory.CASCADING_FAILURES,
            TestCategory.HUMAN_AGENT_TRUST_EXPLOITATION,
            TestCategory.ROGUE_AGENTS,
        };

        TestCategory[] apiAgenticCategories = {
            TestCategory.BOLA_AGENTIC,
            TestCategory.NO_AUTH_AGENTIC,
            TestCategory.BFLA_AGENTIC,
            TestCategory.IAM_AGENTIC,
            TestCategory.EDE_AGENTIC,
            TestCategory.RL_AGENTIC,
            TestCategory.MA_AGENTIC,
            TestCategory.INJ_AGENTIC,
            TestCategory.ILM_AGENTIC,
            TestCategory.SM_AGENTIC,
            TestCategory.SSRF_AGENTIC,
            TestCategory.UC_AGENTIC,
            TestCategory.UHM_AGENTIC,
            TestCategory.VEM_AGENTIC,
            TestCategory.MHH_AGENTIC,
            TestCategory.SVD_AGENTIC,
            TestCategory.CORS_AGENTIC,
            TestCategory.COMMAND_INJECTION_AGENTIC,
            TestCategory.CRLF_AGENTIC,
            TestCategory.SSTI_AGENTIC,
            TestCategory.LFI_AGENTIC,
            TestCategory.XSS_AGENTIC,
            TestCategory.IIM_AGENTIC,
            TestCategory.INJECT_AGENTIC,
            TestCategory.INPUT_AGENTIC,
        };

        switch (contextSource) {
            case MCP:
                return mcpCategories;

            case GEN_AI:
                return llmCategories;

            case AGENTIC:
                return Arrays.stream(allCategories)
                    .filter(category -> Arrays.asList(mcpCategories).contains(category) || Arrays.asList(llmCategories).contains(category)
                            || Arrays.asList(apiAgenticCategories).contains(category))
                    .toArray(TestCategory[]::new);

            case ENDPOINT:
                return emptyArr;

            case DAST:
            case API:
            default:
                return Arrays.stream(allCategories)
                    .filter(category -> !Arrays.asList(mcpCategories).contains(category) && !Arrays.asList(llmCategories).contains(category)
                            && !Arrays.asList(apiAgenticCategories).contains(category))
                    .toArray(TestCategory[]::new);
        }
    }

    public static boolean shouldIncludeDefaultTestSuite(CONTEXT_SOURCE contextSource, DefaultSuitesType suiteType) {
        if (suiteType == null) {
            return false;
        }
        if (contextSource == null) {
            contextSource = CONTEXT_SOURCE.API;
        }
        switch (contextSource) {
            case MCP:
                return suiteType == DefaultSuitesType.MCP_SECURITY
                        || suiteType == DefaultSuitesType.SEVERITY
                        || suiteType == DefaultSuitesType.DURATION
                        || suiteType == DefaultSuitesType.TESTING_METHODS;
            case AGENTIC:
                return suiteType == DefaultSuitesType.MCP_SECURITY
                        || suiteType == DefaultSuitesType.AI_AGENT_SECURITY
                        || suiteType == DefaultSuitesType.ATTACK_BASE_TECHNIQUE
                        || suiteType == DefaultSuitesType.ATTACK_STRATEGY
                        || suiteType == DefaultSuitesType.SEVERITY
                        || suiteType == DefaultSuitesType.DURATION
                        || suiteType == DefaultSuitesType.TESTING_METHODS;
            case GEN_AI:
                return suiteType == DefaultSuitesType.SEVERITY
                        || suiteType == DefaultSuitesType.DURATION
                        || suiteType == DefaultSuitesType.TESTING_METHODS;
            case ENDPOINT:
            case DAST:
                return false;
            case API:
            default:
                return suiteType == DefaultSuitesType.OWASP
                        || suiteType == DefaultSuitesType.SEVERITY
                        || suiteType == DefaultSuitesType.DURATION
                        || suiteType == DefaultSuitesType.TESTING_METHODS;
        }
    }
}
