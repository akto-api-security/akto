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

export function getCategoriesBasedOnDashboardCategory(dashboardCategory, categoryMap){
    let categoriesName = Object.keys(categoryMap);
    if(dashboardCategory === "MCP Security"){
        categoriesName = mcpCategories;
    } else if (dashboardCategory === "Agentic Security" || dashboardCategory === "Endpoint Security") {
        categoriesName = [...llmCategories, ...mcpCategories];
    }
    return categoriesName;
}

export function filterSubCategoriesBasedOnCategories(subCategories, categoriesName){
    return subCategories.filter(
        (subCategory) => categoriesName.includes(subCategory.superCategory.name)
    )
}