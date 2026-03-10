/**
 * Central OWASP Agentic mapping for guardrail steps.
 *
 * ruleViolatedPrefixes: values that appear as rule_violated in event metadata.
 *   - PII_*          → Sensitive Information (PII types from agent-guard Anonymize/Sensitive scanners)
 *   - UserDefinedRegex → Sensitive Information (custom regex patterns)
 *   - Secrets        → Sensitive Information (Secrets scanner)
 *   - UserDefinedLLMRule → Custom Guardrails (LLM prompt-based rules)
 *   - CustomURLRule  → Custom Guardrails (external model URL)
 *   - PromptInjection, IntentAnalysis, BanTopics, Toxicity, BanSubstrings → Content & Policy
 *   - BanCode, Code  → Advanced Code Detection
 *   - Gibberish, Sentiment, Language → Language Safety
 *   - TokenLimit     → Usage based Guardrails
 *   - Anomaly        → Anomaly Detection
 *   - McpServer, AgentServer, SupplyChain → Server Settings
 */

export const STEP_CONFIG = [
    {
        stepNumber: 2,
        title: "Content & Policy Guardrails",
        owaspThreats: [
            { id: "ASI01", name: "Agent Goal Hijack" },
            { id: "ASI09", name: "Human-Agent Trust Exploitation" }
        ],
        // scanner names and rule_violated prefixes from agent-guard
        ruleViolatedPrefixes: ["PromptInjection", "IntentAnalysis", "BanTopics", "Toxicity", "BanSubstrings", "BanCompetitors", "prompt_injection", "intent", "denied_topic", "harmful", "base_prompt"]
    },
    {
        stepNumber: 3,
        title: "Language Safety & Abuse Guardrails",
        owaspThreats: [
            { id: "ASI09", name: "Human-Agent Trust Exploitation" }
        ],
        ruleViolatedPrefixes: ["Gibberish", "Sentiment", "Language", "gibberish", "sentiment", "profanity", "word_filter"]
    },
    {
        stepNumber: 4,
        title: "Sensitive Information Guardrails",
        owaspThreats: [
            { id: "ASI03", name: "Identity and Privilege Abuse" }
        ],
        // PII_ prefix covers all PII types (PII_EMAIL, PII_PHONE, etc.)
        // UserDefinedRegex covers custom regex patterns
        // Secrets covers the Secrets scanner
        // Anonymize/Sensitive are the underlying scanners
        ruleViolatedPrefixes: ["PII_", "UserDefinedRegex", "Secrets", "Anonymize", "Sensitive", "pii", "credential", "secret", "regex"]
    },
    {
        stepNumber: 5,
        title: "Advanced Code Detection Filters",
        owaspThreats: [
            { id: "ASI05", name: "Unexpected Code Execution" }
        ],
        ruleViolatedPrefixes: ["BanCode", "Code", "ban_code", "code_detection"]
    },
    {
        stepNumber: 6,
        title: "Custom Guardrails",
        owaspThreats: [
            { id: "ASI02", name: "Tool Misuse and Exploitation" }
        ],
        // UserDefinedLLMRule = custom LLM prompt rule, CustomURLRule = external model URL
        ruleViolatedPrefixes: ["UserDefinedLLMRule", "CustomURLRule", "llm_rule", "custom_url", "custom_guardrail"]
    },
    {
        stepNumber: 7,
        title: "Usage based Guardrails",
        owaspThreats: [
            { id: "ASI08", name: "Cascading Failures" }
        ],
        ruleViolatedPrefixes: ["TokenLimit", "token_limit", "rate_limit", "usage"]
    },
    {
        stepNumber: 8,
        title: "Anomaly Detection",
        owaspThreats: [
            { id: "ASI08", name: "Cascading Failures" },
            { id: "ASI10", name: "Rogue Agents" }
        ],
        ruleViolatedPrefixes: ["Anomaly", "anomaly", "behavioral", "statistical"]
    },
    {
        stepNumber: 9,
        title: "Server and application settings",
        owaspThreats: [
            { id: "ASI04", name: "Agentic Supply Chain Vulnerabilities" },
            { id: "ASI07", name: "Insecure Inter-Agent Communication" }
        ],
        ruleViolatedPrefixes: ["McpServer", "AgentServer", "mcp", "inter_agent", "supply_chain", "server"]
    }
];

/**
 * Returns OWASP threats for a given rule_violated value.
 * Matches by checking if ruleViolated starts with or contains any known prefix.
 * Falls back to all threats if no match found.
 */
export const getOwaspThreatsForRule = (ruleViolated) => {
    if (typeof ruleViolated === 'string' && ruleViolated.trim() && ruleViolated !== "-") {
        const rv = ruleViolated.trim().toLowerCase();
        for (const step of STEP_CONFIG) {
            if (step.ruleViolatedPrefixes.some(prefix => {
                const pfx = prefix.toLowerCase();
                return rv.startsWith(pfx) || rv.includes(pfx);
            })) {
                return step.owaspThreats;
            }
        }
    }
    // No specific match — return empty array
    return [];
};
