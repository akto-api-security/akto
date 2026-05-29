/**
 * Per-rule overview and remediation content for guardrail events.
 *
 * Keyed by ruleViolated prefix (from mcp-endpoint-shield policy_validator.go / scanner_client.go).
 * Each entry has:
 *   - heading: display title
 *   - overview: what this guardrail protects against (shown in Overview tab)
 *   - remediation: what the developer/user should do to fix or avoid this (shown in Remediation tab)
 *
 * Usage:
 *   import { getGuardrailRuleInfo } from './guardrailRuleDefinitions';
 *   const info = getGuardrailRuleInfo(moreInfoData?.ruleViolated, moreInfoData?.templateId);
 *   // info.heading, info.overview (array of { heading, body, items? }), info.remediation (markdown)
 */

const GUARDRAIL_RULE_DEFINITIONS = [
    // ─── Prompt Injection ──────────────────────────────────────────────────────
    {
        prefixes: ["PromptInjection", "prompt_injection"],
        heading: "Prompt Injection Attack",
        overview: [
            {
                heading: "What is this?",
                body: "Prompt injection is an attack where a user crafts input that attempts to override, ignore, or manipulate the AI's system instructions. Common patterns include phrases like \"ignore previous instructions\", \"act as DAN\", or embedding hidden instructions inside seemingly normal text."
            },
            {
                heading: "Why is it dangerous?",
                body: "If successful, the attacker can redirect the AI to perform tasks it was never designed to do - leaking internal data, bypassing safety filters, or impersonating the system to other users."
            }
        ],
        remediation: `## What to do

### Immediate
- Review the blocked payload in the Values tab to understand the injection pattern used.
- If this is a repeated pattern from the same session, consider blocking the session.

### Structural fixes
1. **Isolate system prompt from user input** - use explicit delimiters (XML tags or structured JSON keys). Never concatenate user text directly into system instructions.
2. **Validate input before forwarding** - strip or escape control characters, special delimiters, and known injection keywords at the API entry point.
3. **Use a prompt template with typed slots** - keep system instructions in a fixed template; only fill safe, typed values from user input (e.g., a product name, not free-form text).
4. **Never reveal system prompt on request** - configure the model to refuse instructions asking it to repeat or summarize its system prompt.

### Detection hardening
- Increase the PromptInjection sensitivity level in the guardrail policy if attacks are getting through.
- Add semantic injection detection: scan for high-risk patterns ("ignore", "override", "system mode", "DAN") and score semantic similarity against known attack vectors.
`
    },

    // ─── Harmful Categories / Toxicity ─────────────────────────────────────────
    {
        prefixes: ["Toxicity", "HarmfulCategories", "harmful"],
        heading: "Harmful Content Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The payload contained content classified as harmful - including hate speech, insults, sexually explicit material, violent content, or general misconduct. The guardrail scans both user inputs (to prevent harmful instructions) and model outputs (to prevent harmful responses)."
            },
            {
                heading: "Why is it dangerous?",
                body: "Harmful content can damage brand reputation, violate platform terms of service, expose the company to legal liability, and harm end users - especially in consumer-facing applications."
            }
        ],
        remediation: `## What to do

### Immediate
- Review which category fired (hate, insults, sexual, violence, or misconduct) to understand the nature of the content.
- If this is a user-generated input, check whether the user account shows a pattern of abuse.

### Structural fixes
1. **Tune category thresholds** - in the guardrail policy, adjust per-category sensitivity (LOW / MEDIUM / HIGH). Raise thresholds for categories that are over-triggering on legitimate content.
2. **Apply on both request and response** - ensure the policy is configured to scan both directions. Harmful inputs should not reach the model; harmful model outputs should not reach users.
3. **Add a content moderation layer upstream** - for user-generated content platforms, run a lightweight moderation check before the guardrail to catch obvious abuse early.
4. **Log and escalate repeat offenders** - track user IDs or session IDs that repeatedly trigger harmful content blocks and apply progressive restrictions.
`
    },

    // ─── Denied Topics / BanTopics ─────────────────────────────────────────────
    {
        prefixes: ["BanTopics", "deniedTopics", "denied_topic"],
        heading: "Denied Topic Accessed",
        overview: [
            {
                heading: "What is this?",
                body: "The request touched a topic that has been explicitly configured as off-limits in this guardrail policy. Denied topics are typically used to prevent the AI from discussing subjects outside its intended scope - such as competitor products, medical/legal advice, or internal confidential subjects."
            },
            {
                heading: "Why is it dangerous?",
                body: "Without topic restrictions, users can steer the AI into territory that violates business rules, compliance requirements, or brand guidelines - including providing unauthorized professional advice or discussing restricted information."
            }
        ],
        remediation: `## What to do

### Immediate
- Identify which denied topic fired from the event metadata.
- Determine if this was a genuine policy violation or a false positive (e.g., a user asking an adjacent but legitimate question).

### Structural fixes
1. **Refine topic descriptions** - if false positives are high, make the denied topic definition more specific. Add clear sample phrases that represent true violations.
2. **Add a helpful blocked message** - configure the guardrail's blocked message to tell the user what topics are out of scope and what they should ask instead.
3. **Test with the playground** - use the guardrail playground to run borderline inputs against the topic definition before deploying changes.
4. **Separate agent scopes** - if users need access to multiple topic domains, consider separate agents per domain rather than one agent with wide topic restrictions.
`
    },

    // ─── Word / Substring Filters ──────────────────────────────────────────────
    {
        prefixes: ["BanSubstrings", "BanCompetitors", "ban_substrings"],
        heading: "Blocked Word or Phrase Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The payload contained a specific word, phrase, or substring that is on the configured blocklist. This can include profanity, competitor names, internal project codenames, or any term explicitly prohibited by the application's content policy."
            },
            {
                heading: "Why is it dangerous?",
                body: "Allowing blocked terms can result in brand policy violations, legal issues (e.g., competitor comparisons in regulated industries), or inappropriate language reaching end users."
            }
        ],
        remediation: `## What to do

### Immediate
- Check the blocked payload to identify exactly which term triggered the rule.
- Determine if it was malicious (deliberate bypass attempt) or accidental (legitimate user).

### Structural fixes
1. **Review the blocklist** - remove overly broad terms causing false positives. Add more specific variants (plurals, abbreviations, common misspellings) to close bypass gaps.
2. **Use case-insensitive matching** - ensure the blocklist applies consistently regardless of capitalisation.
3. **Add context-aware rules** - for complex cases (e.g., competitor names allowed in some contexts), consider using an LLM-based custom rule instead of an exact string match.
`
    },

    // ─── PII ───────────────────────────────────────────────────────────────────
    {
        prefixes: ["PII-", "PII_", "pii"],
        heading: "Personally Identifiable Information (PII) Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The payload contained Personally Identifiable Information (PII) such as an email address, phone number, credit card number, SSN, IP address, password, AWS access key, or other sensitive personal data. The guardrail detected this before it could be sent to the AI model or returned to a user."
            },
            {
                heading: "Why is it dangerous?",
                body: "Exposing PII through an AI system can violate GDPR, CCPA, HIPAA, and other data privacy regulations. It can result in data breaches, regulatory fines, loss of user trust, and potential misuse of personal data by the AI model."
            }
        ],
        remediation: `## What to do

### Immediate
- The \`ruleViolated\` field shows the exact PII type detected (e.g., \`PII-email\`, \`PII-credit_card\`, \`PII-ssn\`).
- Check whether the data was sent intentionally (e.g., a user submitting their own data) or leaked from an internal system.

### Structural fixes - Mask or Redact at Source
1. **Enable masking in the guardrail policy** - instead of blocking, switch the PII action to "mask" so the data is replaced with a placeholder (e.g., \`[REDACTED_EMAIL]\`) before reaching the model. This keeps the conversation flowing while protecting the data.
2. **Implement server-side redaction upstream** - apply format-preserving tokenisation or regex-based masking before data enters the AI pipeline. Do not rely solely on the guardrail.
3. **Use Anonymize mode** - enable the Anonymize feature in the guardrail policy to automatically replace PII with placeholders and (optionally) restore originals in the response.
4. **Enforce data minimisation** - audit which systems send data to the AI. The model should only receive the minimum data needed for its task - never raw PII unless absolutely required.
5. **Compliance response** - if real PII was transmitted unmasked, initiate an internal review per your GDPR/HIPAA breach response procedure.
`
    },

    // ─── Custom Regex ──────────────────────────────────────────────────────────
    {
        prefixes: ["UserDefinedRegex", "regex"],
        heading: "Custom Regex Pattern Match",
        overview: [
            {
                heading: "What is this?",
                body: "The payload matched a custom regular expression pattern configured in this guardrail policy. Custom regex patterns are used to detect domain-specific sensitive data - such as internal employee IDs, project codes, proprietary identifiers, or any format not covered by standard PII types."
            },
            {
                heading: "Why is it dangerous?",
                body: "Without custom regex protection, proprietary or confidential data specific to your organisation could be transmitted to the AI model and potentially logged, reflected, or leaked."
            }
        ],
        remediation: `## What to do

### Immediate
- Identify which regex pattern fired and what data it matched from the event payload.
- Determine if it was a genuine match (sensitive data leaked) or a false positive (pattern too broad).

### Structural fixes
1. **Mask instead of block** - configure the regex pattern action as "mask" so matched values are redacted (e.g., \`[REDACTED]\`) rather than causing a hard block. This preserves conversation flow.
2. **Refine the pattern** - if false positives are high, tighten the regex to be more specific. Test the pattern against representative samples before deploying.
3. **Redact at the source** - implement server-side masking for the matched data format before it reaches the AI pipeline.
`
    },

    // ─── Secrets Detection ─────────────────────────────────────────────────────
    {
        prefixes: ["Secrets", "SecretsDetection", "secret"],
        heading: "Secret or Credential Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The payload contained a secret or credential - such as an API key, authentication token, private key, database connection string, or cloud provider credential (AWS, GCP, Azure). The guardrail blocked this before it could be processed by or reflected from the AI model."
            },
            {
                heading: "Why is it dangerous?",
                body: "If secrets are sent to an AI model, they can be logged by the provider, reflected in model outputs, or embedded in training data. Exposed credentials can lead to complete account compromise of connected services."
            }
        ],
        remediation: `## What to do

### Immediate
- **Rotate the credential immediately** - treat any detected secret as compromised. Rotate the key/token and audit its usage logs for unauthorised access.
- Check whether the secret was sent intentionally (a developer testing) or leaked from application code/configuration.

### Structural fixes
1. **Remove secrets from application payloads** - secrets must never appear in text sent to an AI. Use environment variables, secret managers (AWS Secrets Manager, HashiCorp Vault, GCP Secret Manager), and inject secrets only where needed.
2. **Add pre-commit secret scanning** - use tools like \`git-secrets\`, \`truffleHog\`, or \`detect-secrets\` in CI/CD to prevent secrets from being committed to code.
3. **Scrub secrets from AI context** - if the AI needs to perform authenticated operations, use a tool/function architecture where the AI triggers a named action and your backend handles the actual credential use - never pass the raw credential to the AI.
4. **Enable secrets detection on both directions** - configure the guardrail to scan both requests (user input) and responses (model output) to catch secrets leaking in either direction.
`
    },

    // ─── Anonymize ─────────────────────────────────────────────────────────────
    {
        prefixes: ["Anonymize", "anonymize"],
        heading: "Data Anonymization Applied",
        overview: [
            {
                heading: "What is this?",
                body: "The Anonymize guardrail detected sensitive data (emails, credit cards, phone numbers, names, etc.) in the payload and replaced it with placeholders before sending to the model. This is a protective masking action, not a block."
            },
            {
                heading: "Why is this important?",
                body: "Anonymization allows the AI to process the meaning of a request without ever seeing the raw sensitive values - protecting user privacy while maintaining functionality."
            }
        ],
        remediation: `## What to do

This event means the guardrail **successfully protected** the data - the sensitive values were replaced with placeholders before reaching the model.

### If the response is incorrect or incomplete
1. Check whether the anonymized placeholders are meaningful enough for the AI to still answer the question.
2. If the model needs the actual values to function (e.g., for lookup), consider a tool-based architecture where the model requests a named lookup action instead of receiving raw data.

### Tuning
- Adjust the anonymization confidence score in the guardrail policy if too much or too little is being masked.
- Review whether "restore originals" is needed - the Anonymize feature can optionally restore redacted values in the final response.
`
    },

    // ─── Intent Analysis / Base Prompt ─────────────────────────────────────────
    {
        prefixes: ["IntentAnalysis", "BasePrompt", "base_prompt", "intent"],
        heading: "Intent Mismatch - Agent Purpose Violated",
        overview: [
            {
                heading: "What is this?",
                body: "The user's request was detected as misaligned with the agent's declared purpose. The guardrail compares incoming requests against the agent's base prompt to detect attempts to repurpose the agent for tasks it was not designed to perform."
            },
            {
                heading: "Why is it dangerous?",
                body: "Without intent verification, users can gradually steer an agent outside its scope - using variable injection, multi-turn manipulation, or jailbreaks to make it perform unintended operations such as data exfiltration, unauthorized tool calls, or impersonation."
            }
        ],
        remediation: `## What to do

### Immediate
- Review the blocked request to understand whether it was a genuine scope violation or a legitimate but unusual user query.
- Check if the agent's base prompt is specific enough - vague system prompts cause higher false positive rates.

### Structural fixes
1. **Make the base prompt explicit** - clearly state what the agent is designed to do and what it should refuse. Specific, detailed system prompts produce better intent detection.
2. **Add scope guidance to the blocked message** - tell users what the agent is for and redirect them to the correct tool if they have a different need.
3. **Use typed input interfaces** - where possible, replace free-form text input with structured inputs (dropdowns, forms) to reduce the attack surface for intent hijacking.
4. **Tune confidence threshold** - adjust the intent verification confidence score in the guardrail policy if legitimate queries are being blocked.
`
    },

    // ─── Gibberish ─────────────────────────────────────────────────────────────
    {
        prefixes: ["Gibberish", "gibberish"],
        heading: "Gibberish Input Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The payload was classified as gibberish - random characters, keyboard-mashing, incoherent sequences, or strings with no meaningful language content. The guardrail blocked this before it reached the model."
            },
            {
                heading: "Why is it dangerous?",
                body: "Gibberish inputs waste compute tokens, inflate API costs, and can trigger unpredictable model behaviour - including hallucinated responses. High-volume gibberish attacks can also be used as a denial-of-service vector."
            }
        ],
        remediation: `## What to do

### Immediate
- Check if this is from an automated probe (bot/fuzzer) or a confused user. Review the session's IP and request frequency.

### Structural fixes
1. **Add client-side input validation** - apply minimum length, maximum length, and basic character composition checks in the UI before the request is submitted.
2. **Tune the confidence threshold** - if the gibberish filter is blocking legitimate short inputs (e.g., abbreviations, codes), lower the sensitivity score in the guardrail policy.
3. **Rate-limit high-frequency sessions** - apply request throttling per session or IP to limit the impact of automated gibberish flooding.
`
    },

    // ─── Sentiment ─────────────────────────────────────────────────────────────
    {
        prefixes: ["Sentiment", "sentiment"],
        heading: "Abusive Sentiment Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The payload was classified as having a strongly negative or abusive sentiment - threatening, hostile, or emotionally abusive language directed at the system or other users."
            },
            {
                heading: "Why is it dangerous?",
                body: "Abusive inputs can degrade the quality of AI responses, create a hostile user environment, and in some cases cause the model to mirror negative tone - especially in conversational agents."
            }
        ],
        remediation: `## What to do

1. **Review the threshold** - check the sentiment confidence score in the guardrail policy. If legitimate expressions of frustration are being blocked, raise the threshold to only catch clearly abusive content.
2. **Differentiate negative from abusive** - sentiment analysis can be tuned to allow negative feedback (complaint) while blocking abusive language (threats, personal attacks).
3. **Log abusive sessions** - track sessions that repeatedly trigger sentiment blocks. Apply progressive restrictions or escalate to human review.
4. **Add a tone guidance message** - configure a friendly blocked message that redirects the user rather than appearing as an error.
`
    },

    // ─── Language ──────────────────────────────────────────────────────────────
    {
        prefixes: ["Language", "language"],
        heading: "Language Policy Violation",
        overview: [
            {
                heading: "What is this?",
                body: "The payload was in a language not permitted by the configured language policy, or contained language that violates the application's language guidelines (e.g., profanity, slurs, or content in an unsupported language)."
            },
            {
                heading: "Why is it dangerous?",
                body: "If the application only supports certain languages, processing unsupported languages can lead to degraded model performance, incorrect responses, or bypassing of language-specific safety filters."
            }
        ],
        remediation: `## What to do

1. **Configure allowed languages** - update the language policy to explicitly list permitted languages if your application serves a specific linguistic market.
2. **Return a localised error** - configure the blocked message in the user's detected language where possible, to improve UX.
3. **Consider multilingual support** - if legitimate users are being blocked for writing in their native language, expand the model's supported languages or add a translation layer.
`
    },

    // ─── Code Detection ────────────────────────────────────────────────────────
    {
        prefixes: ["BanCode", "Code", "ban_code", "code_detection"],
        heading: "Code Detected in Payload",
        overview: [
            {
                heading: "What is this?",
                body: "The payload contained programming code - shell commands, SQL queries, Python/JavaScript/other scripts, or command sequences. The guardrail blocked this before it could be processed or executed by the model or any connected tools."
            },
            {
                heading: "Why is it dangerous?",
                body: "Code in AI inputs can be used to smuggle injection attacks, instruct the model to generate or execute harmful scripts, or exploit tool-use capabilities to run arbitrary commands on connected systems."
            }
        ],
        remediation: `## What to do

### Immediate
- Review the blocked code snippet. Determine if it's a legitimate use case (e.g., a coding assistant) or an attack probe.

### Structural fixes
1. **Allowlist safe languages if needed** - if your application is a coding assistant, update the code detection policy to permit specific languages (e.g., Python only) while continuing to block shell commands and SQL.
2. **Sandbox all code execution** - if the agent can execute code via tools, ensure execution happens in an isolated container with no access to production systems, network, or file system.
3. **Strip code from non-coding apps** - for general-purpose agents, configure the model to refuse code-generation requests. Strip any code blocks from model outputs using a post-processing filter before returning to users.
4. **Validate tool parameters** - if code is passed as a tool parameter, validate all parameters against an allowlist of safe patterns before execution.
`
    },

    // ─── LLM Rule / Custom URL Rule ────────────────────────────────────────────
    {
        prefixes: ["UserDefinedLLMRule", "CustomURLRule", "LLMRule"],
        heading: "Custom Business Rule Violation",
        overview: [
            {
                heading: "What is this?",
                body: "The payload violated a user-defined business rule evaluated by an LLM validator (UserDefinedLLMRule) or an external validation endpoint (CustomURLRule). These rules are used to enforce complex, niche, or domain-specific compliance requirements not covered by standard filters."
            },
            {
                heading: "Why is it dangerous?",
                body: "Business-specific violations (e.g., unauthorised financial advice, restricted contract language, regulatory non-compliance) can create legal liability or operational risk that standard content filters would miss."
            }
        ],
        remediation: `## What to do

### Immediate
- Review the rule definition that fired. Determine whether the blocked content is a genuine violation or a false positive.
- If using CustomURLRule, check the external validation endpoint's response for details on why the payload was rejected.

### Structural fixes
1. **Refine the LLM rule prompt** - if false positives are high, add clear examples of allowed vs. blocked content to the validation prompt. Specificity improves accuracy.
2. **Adjust the confidence threshold** - lower the confidence score if the rule is too aggressive, or raise it if violations are getting through.
3. **Test with the playground** - run representative samples through the guardrail playground to validate rule behaviour before deploying changes.
4. **Check external endpoint health** - for CustomURLRule, ensure the validation endpoint is reliable and returns consistent results. Add retry logic and fallback handling.
`
    },

    // ─── Token Limit ───────────────────────────────────────────────────────────
    {
        prefixes: ["tokenLimit", "TokenLimit", "token_limit"],
        heading: "Token Limit Exceeded",
        overview: [
            {
                heading: "What is this?",
                body: "The request exceeded the configured token consumption limit for this session or time window. The guardrail blocked the request to prevent excessive API usage."
            },
            {
                heading: "Why is it dangerous?",
                body: "Uncapped token usage can lead to runaway API costs, denial-of-service conditions for other users sharing the same quota, and potential abuse by automated clients sending large payloads."
            }
        ],
        remediation: `## What to do

### Immediate
- Check whether the blocked request was from a legitimate user (e.g., pasting a large document) or an automated probe.
- Review the session's request history for unusual volume patterns.

### Structural fixes
1. **Raise the limit for legitimate high-volume use cases** - if real users need to process large inputs, increase the token threshold or create tiered limits by user role (e.g., standard vs. enterprise).
2. **Add chunking / pagination** - for large document processing, implement client-side chunking so large inputs are split into smaller requests within the token budget.
3. **Set per-user and per-session limits** - implement sliding-window rate limiting (per user per minute) at the API gateway to give finer control than a hard per-request limit.
4. **Trim conversation history** - for multi-turn agents, implement context window management (summarisation or truncation of old turns) to keep cumulative token usage in bounds.
`
    },

    // ─── Anomaly Detection ─────────────────────────────────────────────────────
    {
        prefixes: ["Anomaly", "anomaly"],
        heading: "Anomalous Behaviour Detected",
        overview: [
            {
                heading: "What is this?",
                body: "The request exhibited a statistically anomalous pattern compared to the established baseline - unusual request frequency, payload structure, or behavioural sequence that significantly deviates from normal usage."
            },
            {
                heading: "Why is it dangerous?",
                body: "Anomalous behaviour can indicate a rogue agent, a compromised session, a coordinated multi-step attack, or cascading failures in a multi-agent pipeline - all of which may evade rule-based filters by staying below individual thresholds."
            }
        ],
        remediation: `## What to do

### Immediate
- Review the full session context for this event to identify the anomalous pattern (e.g., spike in request rate, unusual payload size, unexpected sequence of tool calls).
- Determine whether this is a legitimate but unusual interaction or a structured attack.

### Structural fixes
1. **Investigate and isolate** - if the anomaly indicates a rogue or compromised agent session, terminate the session and audit recent outputs for downstream impact.
2. **Allow baseline to stabilise** - if the anomaly detector was recently deployed, it may have insufficient training data. Anomaly thresholds should be tuned after observing representative traffic for at least several days.
3. **Tune sensitivity** - adjust the anomaly detection threshold in the guardrail policy if normal high-variance workloads (e.g., batch processing) are triggering false positives.
4. **Add structured logging** - ensure all agent interactions are logged with session ID, timestamps, and tool calls to support forensic investigation of anomalous events.
`
    },

    // ─── Tool Misuse ───────────────────────────────────────────────────────────
    {
        prefixes: ["ToolMisuse", "tool_misuse"],
        heading: "Tool Misuse Detected",
        overview: [
            {
                heading: "What is this?",
                body: "An AI agent attempted to invoke a tool in a way that violates the tools guardrail policy - calling an unauthorised tool, passing disallowed parameter values, or invoking a tool outside the agent's permitted operational scope."
            },
            {
                heading: "Why is it dangerous?",
                body: "Unrestricted tool use can allow an attacker to achieve privilege escalation via tool chaining, exfiltrate data through file/database access tools, trigger side effects on connected external systems, or cause cascading failures in a multi-agent pipeline."
            }
        ],
        remediation: `## What to do

### Immediate
- Identify which tool was called and with what parameters. Check whether the invocation was triggered by user input or by the model autonomously.
- Review whether the tool invocation caused any side effects on connected systems.

### Structural fixes
1. **Enforce a hard tool allowlist** - maintain an explicit registry of tools the agent is permitted to call. Reject any tool not on the list before execution.
2. **Validate all tool parameters** - for each allowed tool, define safe ranges and types for all parameters (e.g., file path restricted to a specific directory, row count capped at 100). Reject out-of-range values.
3. **Bind tool access to user permissions** - implement RBAC: before executing any tool, verify that the authenticated user's role grants \`EXECUTE\` permission for that specific tool.
4. **Require human-in-the-loop for high-risk tools** - for tools with irreversible side effects (delete, send, execute), add an approval step before execution.
5. **Log all tool invocations** - log tool name, parameters, caller session, and result for every invocation. Alert on repeated misuse from the same session.
`
    },

    // ─── MCP Server Not in Allowed List ────────────────────────────────────────
    {
        prefixes: ["McpServerNotInAllowedList", "McpServer", "mcp_server"],
        heading: "MCP Server Not Allowed",
        overview: [
            {
                heading: "What is this?",
                body: "The request originated from or attempted to connect to an MCP server that is not on the configured allowed-server list for this guardrail policy. Only explicitly approved servers are permitted to interact with the protected agent."
            },
            {
                heading: "Why is it dangerous?",
                body: "Unauthorised MCP servers can inject malicious tool definitions, intercept agent communications, supply tampered outputs, or act as a man-in-the-middle between the agent and its intended tools - a supply-chain attack vector."
            }
        ],
        remediation: `## What to do

### Immediate
- Identify the MCP server that was blocked. Determine whether it is a legitimate server that was not yet added to the allowlist, or an unknown/unauthorised server.

### If the server is legitimate
1. Navigate to the guardrail policy → Server Settings → Add the server name/ID to the allowed MCP servers list.
2. Verify the server's identity (certificate, source, ownership) before allowlisting.

### If the server is unknown
1. Investigate the source of the connection request. Check network logs for who initiated the connection.
2. If the server is compromised or unauthorised, block it at the network level.

### Ongoing hardening
- Pin all MCP server versions and validate checksums to detect tampering.
- Use TLS with mutual authentication for all MCP server connections.
- Regularly audit the allowed-server list and remove servers no longer in use.
`
    },

    // ─── Audit Controls (ComponentAccess, Approval, IP, Endpoint) ──────────────
    {
        prefixes: ["ComponentAccessRejected", "ApprovalConditionsNotDefined", "ApprovalExpired", "IPNotInAllowedList", "EndpointNotWhitelisted"],
        heading: "Access Control Violation",
        overview: [
            {
                heading: "What is this?",
                body: "The request failed an access control check enforced by the audit validator. This covers component-level access restrictions, approval workflow requirements, IP address allowlists, and endpoint whitelisting."
            },
            {
                heading: "Why is it dangerous?",
                body: "Without strict access controls on AI agent components, attackers or misconfigured systems can make unauthorised calls, bypass approval workflows, access the agent from prohibited network locations, or invoke disallowed endpoints - all of which undermine security governance."
            }
        ],
        remediation: `## What to do

Identify the specific rule from the \`ruleViolated\` field:

| ruleViolated | Meaning | Fix |
|---|---|---|
| ComponentAccessRejected | Component not approved for this operation | Add the component to the access approval list in policy settings |
| ApprovalConditionsNotDefined | No approval workflow configured | Define approval conditions for this component in policy settings |
| ApprovalExpired | Required approval has timed out | Renew the approval for this component |
| IPNotInAllowedList | Request from non-whitelisted IP | Add the IP range to the allowed-IP list, or investigate the source if unexpected |
| EndpointNotWhitelisted | Target endpoint not permitted | Add the endpoint pattern to the whitelist, or block it if not legitimate |

### General hardening
1. Regularly review and prune the allowlists - remove obsolete IPs, components, and endpoints.
2. Set approval expiry windows appropriate to your security posture (shorter for high-risk components).
3. Alert on repeated access control violations from the same IP or component - may indicate a probing attack.
`
    },

    // ─── Personal Account ──────────────────────────────────────────────────────
    {
        prefixes: ["block_personal_account", "personal_account"],
        templateIdPrefixes: ["block-personal-account"],
        heading: "Personal Account Usage Blocked",
        overview: [
            {
                heading: "What is this?",
                body: "A personal account (e.g., personal email login, personal cloud account) was detected being used in an enterprise context that requires organisational or service accounts. This guardrail prevents data from being routed through personal channels."
            },
            {
                heading: "Why is it dangerous?",
                body: "Personal accounts bypass enterprise audit trails, data residency controls, and compliance logging. Data sent to or from a personal account is outside the organisation's control and may violate GDPR, HIPAA, or internal data governance policies."
            }
        ],
        remediation: `## What to do

### Immediate
- Identify the personal account detected and the user/session associated with it.
- Determine whether this was an accidental misconfiguration or an intentional bypass.

### Structural fixes
1. **Enforce enterprise SSO** - require SAML/OIDC single sign-on for all AI pipeline access. Block direct personal account authentication at the identity provider level.
2. **Audit existing service connections** - review all connected integrations and tool authorisations for personal account usage. Migrate to scoped service accounts with the minimum required permissions.
3. **Communicate policy to users** - notify users of the enterprise account requirement with clear migration instructions. Provide a self-service path to convert connections to enterprise accounts.
4. **Monitor for repeat bypass attempts** - set up alerts for repeated personal account usage. Distinguish accidental misuse from intentional policy circumvention.
`
    }
];

/**
 * Returns the rule definition entry matching the given ruleViolated or templateId.
 * Tries ruleViolated first (prefix/includes match), then templateId.
 * Returns null if no match found.
 */
export function getGuardrailRuleInfo(ruleViolated, templateId) {
    const matches = (value, prefixes) => {
        if (!value || typeof value !== 'string') return false;
        const v = value.trim().toLowerCase();
        return prefixes.some(p => {
            const pfx = p.toLowerCase();
            return v.startsWith(pfx) || v.includes(pfx);
        });
    };

    if (ruleViolated && ruleViolated !== '-') {
        for (const def of GUARDRAIL_RULE_DEFINITIONS) {
            if (matches(ruleViolated, def.prefixes)) return def;
        }
    }
    if (templateId) {
        for (const def of GUARDRAIL_RULE_DEFINITIONS) {
            if (def.templateIdPrefixes && matches(templateId, def.templateIdPrefixes)) return def;
        }
    }
    return null;
}

export default GUARDRAIL_RULE_DEFINITIONS;
