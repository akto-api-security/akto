import { isAgenticSecurityCategory, isEndpointSecurityCategory } from '../../../../main/labelHelper';

/**
 * Structured guardrail content with 7 main sections for Argus/Atlas dashboards
 * This is displayed instead of YAML template data when in
 * Agentic Security or Endpoint Security categories.
 */
export const GUARDRAIL_SECTIONS = [
    {
        heading: "Content Filters",
        description: "A multi-layered safety engine that monitors interactions for inappropriate behavior, malicious instructions, and technical vulnerabilities.",
        subSections: [
            {
                subHeading: "Details",
                description: "This guardrail scans for Harmful Categories including hate speech, insults, sexual content, violence, and general misconduct. It also includes protection against Prompt Attacks (attempts to override safety protocols) and Code Detection to identify programming snippets that may pose security risks."
            },
            {
                subHeading: "Impact",
                description: "Failure to filter content can cause the application to:",
                items: [
                    "Generate offensive, biased, or discriminatory responses that damage brand reputation.",
                    "Expose users to inappropriate or violent material.",
                    "Allow malicious code to be processed, potentially leading to technical exploitation. This undermines the safety standards of the application and violates acceptable use policies."
                ]
            }
        ]
    },
    {
        heading: "Denied Topics",
        description: "A restrictive boundary that prevents the AI from engaging in discussion regarding specific, predefined subject matters.",
        subSections: [
            {
                subHeading: "Details",
                description: "Users define high-level topics that are off-limits. The guardrail monitors the context of the conversation; if the input or output overlaps with these restricted areas, the system terminates the request."
            },
            {
                subHeading: "Impact",
                description: "Unauthorized topic discussion can cause the agents to:",
                items: [
                    "Provide legal, medical, or financial advice without authorization.",
                    "Engage in controversial or political debates that alienate users.",
                    "Stray from the business use case, leading to \"hallucinations\" or irrelevant outputs. This compromises the professional boundaries and reliability of the AI service."
                ]
            }
        ]
    },
    {
        heading: "Word Filters",
        description: "A precise blocklist mechanism that monitors for specific prohibited terms or phrases.",
        subSections: [
            {
                subHeading: "Details",
                description: "Performs an exact match scan of the data payload against a user-defined list of blocked words. If a restricted word is detected in the input or output, the interaction is blocked immediately."
            },
            {
                subHeading: "Impact",
                description: "Ineffective word filtering can cause the system to:",
                items: [
                    "Display profanity or slang that is inconsistent with the application's tone.",
                    "Mention competitor names or prohibited internal project codenames.",
                    "Facilitate the use of restricted jargon. This weakens administrative control over the model's vocabulary and output quality."
                ]
            }
        ]
    },
    {
        heading: "Sensitive Information Filters",
        description: "A privacy-first layer designed to detect and block the transmission of confidential or personal data.",
        subSections: [
            {
                subHeading: "Details",
                description: "It scans traffic for patterns related to sensitive information such as PII (Personally Identifiable Information), financial records, or credentials. It prevents this data from being sent to the LLM or returned to the user."
            },
            {
                subHeading: "Impact",
                description: "A breach of sensitive information can cause the system to:",
                items: [
                    "Leak private user data, such as emails, phone numbers, or credit card details.",
                    "Expose internal corporate secrets or proprietary data to the model.",
                    "Violate data privacy regulations like GDPR, CCPA, or HIPAA. This leads to significant legal liabilities, loss of user trust, and potential financial penalties."
                ]
            }
        ]
    },
    {
        heading: "LLM Prompt-Based Guardrail",
        description: "A flexible, natural-language validation system where users define custom logic for blocking payloads.",
        subSections: [
            {
                subHeading: "Details",
                description: "Users specify their own validation rules or \"prompts.\" The system evaluates the payload against these specific instructions. If the payload is found to be in violation of the user-defined logic, it is blocked."
            },
            {
                subHeading: "Impact",
                description: "Without custom prompt-based validation, the system may:",
                items: [
                    "Fail to enforce complex, niche, or company-specific compliance rules.",
                    "Allow payloads that technically pass safety filters but violate specific business logic.",
                    "Lack the nuance required for high-stakes decision-making environments. This limits the ability to customize AI safety to the unique needs of a specific application."
                ]
            }
        ]
    },
    {
        heading: "Intent Verification (AI Agents)",
        description: "A specialized guardrail for AI Agents that ensures user interactions remain aligned with the agent's core purpose and base instructions.",
        subSections: [
            {
                subHeading: "Details",
                description: "The system automatically detects the \"Base Prompt\" from traffic. When users provide inputs to fill placeholders (e.g., {var} or {}), the guardrail checks these inputs against the original intent of the base prompt to ensure the agent stays on task."
            },
            {
                subHeading: "Impact",
                description: "Intent misalignment can cause the agents to:",
                items: [
                    "Be manipulated into performing tasks outside of their original scope.",
                    "Use variable inputs to \"hijack\" the agent's logic for unintended operations.",
                    "Execute tool commands that were never intended by the developer. This undermines agent alignment and allows users to repurpose the AI for unauthorized activities."
                ]
            }
        ]
    },
    {
        heading: "Gibberish Detection",
        description: "A quality-control filter that identifies and blocks nonsensical, random, or meaningless text in user inputs.",
        subSections: [
            {
                subHeading: "Details",
                description: "This detects \"keyboard mashing,\" random character sequences, or strings that do not form coherent language, preventing them from being processed by the AI."
            },
            {
                subHeading: "Impact",
                description: "Allowing gibberish inputs can cause the system to:",
                items: [
                    "Generate unpredictable, nonsensical, or \"hallucinated\" responses.",
                    "Waste computational tokens and increase API costs on meaningless data.",
                    "Be used as a vector for \"denial of service\" attacks by flooding the model with junk text. This reduces overall system efficiency and degrades the quality of the AI interaction."
                ]
            }
        ]
    }
];

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
