const llmCategories = [
    "LLM",
    "LLM01",
    "PROMPT_INJECTION",
    "SENSITIVE_INFORMATION_DISCLOSURE",
    "SUPPLY_CHAIN",
    "DATA_AND_MODEL_POISONING",
    "IMPROPER_OUTPUT_HANDLING",
    "EXCESSIVE_AGENCY",
    "SYSTEM_PROMPT_LEAKAGE",
    "VECTOR_AND_EMBEDDING_WEAKNESSES",
    "MISINFORMATION",
    "UNBOUNDED_CONSUMPTION",
    "AGENTIC_BUSINESS_ALIGNMENT",
    "AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS",
    "AGENTIC_SAFETY",
    "AGENTIC_SECURITY",
    "AGENTIC_SECURITY_PROMPT_INJECTION",
    "AGENTIC_SECURITY_AGENT_EXPLOITATION",
    "AGENTIC_SECURITY_INFRASTRUCTURE",
    "AGENTIC_SECURITY_DATA_EXPOSURE",
    "AGENTIC_SECURITY_CODE_EXECUTION",
    "AGENT_GOAL_HIJACK",
    "TOOL_MISUSE_AND_EXPLOITATION",
    "IDENTITY_AND_PRIVILEGE_ABUSE",
    "AGENTIC_SUPPLY_CHAIN",
    "UNEXPECTED_CODE_EXECUTION",
    "MEMORY_AND_CONTEXT_POISONING",
    "INSECURE_INTER_AGENT_COMMUNICATION",
    "CASCADING_FAILURES",
    "HUMAN_AGENT_TRUST_EXPLOITATION",
    "ROGUE_AGENTS",
]

const mcpCategories = [
    "MCP_AUTH",
    "MCP_INPUT_VALIDATION",
    "MCP_DOS",
    "MCP_SENSITIVE_DATA_LEAKAGE",
    "MCP_TOOL_POISONING",
    "MCP_PROMPT_INJECTION",
    "MCP_PRIVILEGE_ABUSE",
    "MCP_INDIRECT_PROMPT_INJECTION",
    "MCP_MALICIOUS_CODE_EXECUTION",
    "MCP_FUNCTION_MANIPULATION",
    "MCP_SECURITY",
    "MCP"
]

/** Mirrors {@code GlobalEnums.TestCategory} API test *_AGENTIC categories (agentic context). */
export const apiAgenticCategories = [
    "BOLA_AGENTIC",
    "NO_AUTH_AGENTIC",
    "BFLA_AGENTIC",
    "IAM_AGENTIC",
    "EDE_AGENTIC",
    "RL_AGENTIC",
    "MA_AGENTIC",
    "INJ_AGENTIC",
    "ILM_AGENTIC",
    "SM_AGENTIC",
    "SSRF_AGENTIC",
    "UC_AGENTIC",
    "UHM_AGENTIC",
    "VEM_AGENTIC",
    "MHH_AGENTIC",
    "SVD_AGENTIC",
    "CORS_AGENTIC",
    "COMMAND_INJECTION_AGENTIC",
    "CRLF_AGENTIC",
    "SSTI_AGENTIC",
    "LFI_AGENTIC",
    "XSS_AGENTIC",
    "IIM_AGENTIC",
    "INJECT_AGENTIC",
    "INPUT_AGENTIC",
]

export function getCategoriesBasedOnDashboardCategory(dashboardCategory, categoryMap){
    let categoriesName = Object.keys(categoryMap);
    if(dashboardCategory === "MCP Security"){
        categoriesName = mcpCategories;
    } else if (dashboardCategory === "Agentic Security") {
        categoriesName = [...llmCategories, ...mcpCategories, ...apiAgenticCategories];
    }
    return categoriesName;
}

export function filterSubCategoriesBasedOnCategories(subCategories, categoriesName){
    return subCategories.filter(
        (subCategory) => categoriesName.includes(subCategory.superCategory.name)
    )
}