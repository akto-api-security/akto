import claudeSettingsRemediations from './claude_settings_remediations.json';

export const CLAUDE_SETTINGS_RISK_MAP = claudeSettingsRemediations;


export const SETTINGS_RISK_CONFIGS = {
    claude_settings_risk: { riskMap: CLAUDE_SETTINGS_RISK_MAP, urlPrefix: '/claude/config/' },
    codex_config_risk: null,
    copilot_cli_settings_risk: null,
};

/**
 * Fixed remediation markdown content for Agentic and Endpoint security categories
 * This is displayed in the Remediation tab instead of fetched YAML remediation data
 */
export const GUARDRAIL_REMEDIATION_MARKDOWN = `# Remediation Strategy

## 1. Input Validation and Sanitization
**Logic:** Implement a schema-based validation layer at the API entry point.

**Checks:**
- Validate data types (String, Int, Float)
- Enforce character limits
- Use regex to strip control characters or hidden formatting

**Application:** Prevents Gibberish and malformed payloads from reaching the LLM or MCP tools.

---

## 2. Context Isolation
**Logic:** Separate "System Instructions" from "User Input" using clear delimiters (e.g., XML tags or specific JSON keys).

**Checks:** Ensure the execution engine treats content inside user tags as literal strings, never as executable commands or system-level overrides.

**Application:** Prevents prompt injection and "jailbreaking" attempts.

---

## 3. Authentication and Authorization
**Logic:** Bind every MCP tool request to a validated User Session/Identity.

**Checks:**
- Implement Role-Based Access Control (RBAC)
- Before a tool executes, verify if the specific User ID has the 'EXECUTE' permission for that specific tool name

**Application:** Ensures AI agents cannot bypass user-level security restrictions.

---

## 4. Tool Access Controls (Allowlisting)
**Logic:** Maintain a "Hard Allowlist" of permitted functions and parameters.

**Checks:**
- Reject any tool call not explicitly defined in the registry
- Validate that parameter values (e.g., file paths, row counts) stay within pre-defined safe ranges

**Application:** Limits the blast radius of an agent if it is manipulated.

---

## 5. Content & Prompt Injection Filtering
**Logic:** Implement a heuristic scanner between the user input and the model.

**Checks:**
- Scan for high-risk strings (e.g., "ignore previous," "system mode," "DAN," or "override")
- Use semantic similarity to compare inputs against known attack patterns

**Application:** Blocks malicious intent before it influences model behavior.

---

## 6. Sensitive Information Scrubbing
**Logic:** Use a Pattern-Matching or NER (Named Entity Recognition) engine.

**Checks:** Automatically detect and mask PII (Emails, SSNs, API Keys) in both inbound prompts and outbound responses.

**Application:** Ensures compliance with GDPR/HIPAA and prevents data leaks.

---

## 7. Rate Limiting and Request Throttling
**Logic:** Implement a sliding-window rate limiter per User ID or API Key.

**Checks:**
- Monitor request frequency and token consumption
- Trigger a "cool-down" period if thresholds are exceeded

**Application:** Protects against Denial of Service (DoS) and cost spikes.

---

## 8. Comprehensive Logging and Monitoring
**Logic:** Centralized telemetry for all AI/MCP interactions.

**Checks:**
- Log Input text, Intent classification, Tool calls, and Guardrail violations
- Flag accounts that repeatedly trigger safety blocks

**Application:** Creates an audit trail for forensic analysis and security audits.

---

## 9. Gibberish and Entropy Detection
**Logic:** Calculate character distribution and string entropy.

**Checks:** If the randomness score exceeds a set threshold (indicating keyboard mashing), reject the request with a 400 error.

**Application:** Saves computational resources and prevents "junk" data processing.

---

## 10. Regular Security Testing (Re-validation)
**Logic:** Automated "Red Teaming" or penetration testing.

**Checks:** Periodically run known injection attacks against the guardrails to ensure filters are still effective against evolving bypass techniques.

**Application:** Maintains long-term system integrity.
`;
