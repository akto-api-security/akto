package com.akto.utils.security_news;

import com.akto.dto.GuardrailPolicyRecommendation;
import com.akto.utils.security_news.SecurityNewsFetcher.RawNewsItem;

import java.util.Locale;

/**
 * Maps vulnerability news items to guardrail section and "how we tackle it" copy.
 */
public class VulnToGuardrailMapper {

    private static final String SECTION_CONTENT_FILTERS = "Content Filters";
    private static final String SECTION_TOOL_ACCESS = "Tool Access Controls";
    private static final String SECTION_PROMPT_INJECTION = "Content & Prompt Injection Filtering";
    private static final String SECTION_PII = "Sensitive Information Scrubbing";
    private static final String SECTION_DENIED_TOPICS = "Denied Topics";
    private static final String SECTION_AUTH = "Authentication and Authorization";

    public static GuardrailPolicyRecommendation map(RawNewsItem item, int createdTimestamp) {
        GuardrailPolicyRecommendation rec = new GuardrailPolicyRecommendation();
        rec.setVulnerabilityHeadline(item.getTitle());
        rec.setVulnerabilityNewsUrl(item.getLink());
        rec.setVulnerabilitySummary(item.getDescription());
        rec.setCreatedTimestamp(createdTimestamp);
        rec.setSource("rss");

        String text = (item.getTitle() + " " + item.getDescription()).toLowerCase(Locale.US);
        if (text.contains("code execution") || text.contains("remote code") || text.contains(" rce ") || text.contains("code flaw")) {
            rec.setGuardrailSectionRef(SECTION_PROMPT_INJECTION);
            rec.setAktoTacklingDescription("Akto's Content & Prompt Injection Filtering, code detection, and secrets detection block risky code execution patterns and API key or credential exfiltration in prompts and responses.");
            rec.setPolicySummary("Enables Prompt Injection Filtering, code detection, and secrets detection; blocks RCE and credential exfiltration attempts.");
        } else if (text.contains("api key") && (text.contains("exfiltrat") || text.contains("leak") || text.contains("expose"))) {
            rec.setGuardrailSectionRef(SECTION_PII);
            rec.setAktoTacklingDescription("Akto's Sensitive Information Filters detect and block API keys, credentials, and PII in both prompts and responses, preventing exfiltration and data leaks.");
            rec.setPolicySummary("Enables Sensitive Information Filters; blocks API keys and credentials.");
        } else if (text.contains("prompt injection") || text.contains("jailbreak") || text.contains("override") || text.contains("ignore previous")) {
            rec.setGuardrailSectionRef(SECTION_PROMPT_INJECTION);
            rec.setAktoTacklingDescription("Akto's Content & Prompt Injection Filtering scans inputs for high-risk strings and known attack patterns (e.g. 'ignore previous', 'system mode', 'DAN'). It blocks malicious intent before it influences model behavior.");
            rec.setPolicySummary("Enables Content & Prompt Injection Filtering; blocks prompt injection and jailbreaking attempts.");
        } else if (text.contains("mcp") || text.contains("tool") && (text.contains("abuse") || text.contains("exploit") || text.contains("unauthorized"))) {
            rec.setGuardrailSectionRef(SECTION_TOOL_ACCESS);
            rec.setAktoTacklingDescription("Akto's Tool Access Controls use a hard allowlist of permitted functions and parameters. Any tool call not in the registry is rejected, and parameter values are validated against safe ranges to limit blast radius.");
            rec.setPolicySummary("Enables Tool Access Controls (allowlisting); validates MCP tool calls and parameters.");
        } else if (text.contains("pii") || text.contains("data leak") || text.contains("sensitive") || text.contains("credential")) {
            rec.setGuardrailSectionRef(SECTION_PII);
            rec.setAktoTacklingDescription("Akto's Sensitive Information Filters detect and block PII, credentials, and confidential data in both prompts and responses, helping meet GDPR/HIPAA and prevent data leaks.");
            rec.setPolicySummary("Enables Sensitive Information Filters; detects PII and credentials.");
        } else if (text.contains("auth") || text.contains("authorization") || text.contains("rbac") || text.contains("access control")) {
            rec.setGuardrailSectionRef(SECTION_AUTH);
            rec.setAktoTacklingDescription("Akto ensures every MCP tool request is bound to a validated user session. RBAC is enforced so agents cannot bypass user-level permissions.");
            rec.setPolicySummary("Enforces Authentication and Authorization; binds tool requests to user session.");
        } else if (text.contains("content") || text.contains("harmful") || text.contains("inappropriate")) {
            rec.setGuardrailSectionRef(SECTION_CONTENT_FILTERS);
            rec.setAktoTacklingDescription("Akto's Content Filters monitor for inappropriate behavior, malicious instructions, and technical vulnerabilities to keep outputs safe and compliant.");
            rec.setPolicySummary("Enables Content Filters; monitors for harmful or inappropriate content.");
        } else {
            rec.setGuardrailSectionRef(SECTION_DENIED_TOPICS);
            rec.setAktoTacklingDescription("Akto's Denied Topics and Content Filters restrict off-limits subjects and block requests that overlap with restricted areas, reducing risk from agentic AI abuse.");
            rec.setPolicySummary("Enables Denied Topics and Content Filtering; restricts off-limits subjects.");
        }
        return rec;
    }
}
