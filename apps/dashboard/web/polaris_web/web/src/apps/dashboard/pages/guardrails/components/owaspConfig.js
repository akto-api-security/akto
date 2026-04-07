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
 *   - ToolMisuse     → Tools Guardrails
 *   - McpServer, AgentServer, SupplyChain → Server Settings
 */

export const STEP_CONFIG = [
    {
        stepNumber: 2,
        title: "Content & Policy Guardrails",
        owaspThreats: [
            { id: "ASI01", name: "Agent Goal Hijack" },
            { id: "ASI06", name: "Memory & Context Poisoning" },
            { id: "ASI09", name: "Human-Agent Trust Exploitation" }
        ],
        // scanner names and rule_violated prefixes from agent-guard
        ruleViolatedPrefixes: ["PromptInjection", "IntentAnalysis", "BanTopics", "Toxicity", "BanSubstrings", "BanCompetitors", "ContextPoisoning", "context_poisoning", "prompt_injection", "intent", "denied_topic", "harmful", "base_prompt"]
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
        stepNumber: 8,
        title: "Anomaly Detection",
        owaspThreats: [
            { id: "ASI08", name: "Cascading Failures" },
            { id: "ASI10", name: "Rogue Agents" },
            { id: "ASI07", name: "Insecure Inter-Agent Communication" }
        ],
        ruleViolatedPrefixes: ["Anomaly", "anomaly", "behavioral", "statistical"]
    },
    {
        stepNumber: 9,
        title: "Tools Guardrails",
        owaspThreats: [
            { id: "ASI02", name: "Tool Misuse and Exploitation" },
            { id: "ASI04", name: "Agentic Supply Chain Vulnerabilities" }
        ],
        ruleViolatedPrefixes: ["ToolMisuse", "tool_misuse", "tool misuse"]
    },
];

/**
 * OWASP tag(s) shown beside each filter/rule name (for UI).
 * Rule name → threat id(s). Use with OwaspTag threats={[...]}.
 *
 * Step 2 Content & Policy: prompt injection → ASI01; context poisoning → ASI06; denied topics → ASI09;
 *   harmful categories → ASI09; intent verification → ASI01, ASI09.
 * Step 3 Language Safety: gibberish, sentiment, profanity/custom words → ASI09.
 * Step 4 Sensitive Info: PII, regex, secrets, anonymize → ASI03.
 * Step 5 Code Detection: code filter, ban code → ASI05.
 * Step 6 Custom: LLM prompt rule, external model → ASI02.
 * Step 7 Usage: token limit → ASI08.
 * Step 8 Anomaly: (categories) → ASI08, ASI10.
 * Step 9 Tools: tool misuse, malicious tools, name/description mismatch → ASI02.
 * Step 10 Server: MCP servers, agent servers, application settings → ASI04, ASI07.
 */
export const RULE_OWASP_THREATS = {
    // Step 2
    promptInjection: [{ id: "ASI01", name: "Agent Goal Hijack" }],
    contextPoisoning: [{ id: "ASI06", name: "Memory & Context Poisoning" }],
    intentVerification: [{ id: "ASI01", name: "Agent Goal Hijack" }, { id: "ASI09", name: "Human-Agent Trust Exploitation" }],

    // Step 4
    pii: [{ id: "ASI03", name: "Identity and Privilege Abuse" }],
    regex: [{ id: "ASI03", name: "Identity and Privilege Abuse" }],
    secrets: [{ id: "ASI03", name: "Identity and Privilege Abuse" }],
    anonymize: [{ id: "ASI03", name: "Identity and Privilege Abuse" }],
    // Step 5
    codeFilter: [{ id: "ASI05", name: "Unexpected Code Execution" }],
    banCode: [{ id: "ASI05", name: "Unexpected Code Execution" }],
    // Step 7
    tokenLimit: [{ id: "ASI08", name: "Cascading Failures" }],
    // Step 8
    anomaly: [{ id: "ASI08", name: "Cascading Failures" }, { id: "ASI10", name: "Rogue Agents" }],
    // Step 9
    toolMisuse: [{ id: "ASI02", name: "Tool Misuse and Exploitation" }],
    maliciousTools: [{ id: "ASI04", name: "Agentic Supply Chain Vulnerabilities" }],
    toolNameDescriptionMismatch: [{ id: "ASI04", name: "Agentic Supply Chain Vulnerabilities" }],
    // Step 10
    server: [{ id: "ASI04", name: "Agentic Supply Chain Vulnerabilities" }, { id: "ASI07", name: "Insecure Inter-Agent Communication" }]
};

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
