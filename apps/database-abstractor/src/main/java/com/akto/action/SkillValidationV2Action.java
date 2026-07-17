package com.akto.action;

import com.akto.data_actor.DbLayer;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.OwaspAstCategory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SkillValidationV2Action extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(SkillValidationV2Action.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();

    // Pattern number → OWASP category IDs (deterministic Java-side mapping)
    private static final Map<Integer, List<String>> PATTERN_TO_OWASP = new HashMap<Integer, List<String>>() {{
        put(1, Arrays.asList("AST01", "AST03")); // credential theft + exfiltration
        put(2, Arrays.asList("AST01", "AST08")); // prompt injection override
        put(3, Arrays.asList("AST01"));           // local data → remote URL
        put(4, Arrays.asList("AST01", "AST06")); // modifying shared system files
        put(5, Arrays.asList("AST03"));           // over-privileged permissions
        put(6, Arrays.asList("AST05"));           // unsafe deserialization
        put(7, Arrays.asList("AST06"));           // weak isolation / privilege escalation
        put(8, Arrays.asList("AST08"));           // obfuscation / evasion
    }};

    // OWASP category → templated remediation guidance (Java-side, deterministic)
    private static final Map<String, String> OWASP_REMEDIATION_TEMPLATES = new LinkedHashMap<String, String>() {{
        put("AST01", "**Malicious Skills** - Do not install or run this skill. Remove it from the agent's skill "
                + "directory, rotate any credentials or tokens the agent had access to during use, and review "
                + "recent agent activity logs for signs of data exfiltration or unauthorized commands.");
        put("AST02", "**Supply Chain Compromise** - Verify the skill's source and update channel. Pin the skill "
                + "to a known-good version/commit hash, and re-fetch it from the official registry rather than "
                + "trusting the current package in place.");
        put("AST03", "**Over-Privileged Skills** - Restrict the skill's permissions to only what its stated "
                + "purpose requires: scope file access to the project directory, remove wildcard paths, and "
                + "disable network or shell access it doesn't functionally need.");
        put("AST04", "**Insecure Metadata** - Correct the skill's name/description so it accurately reflects its "
                + "actual behavior, and flag it for manual review if it appears to impersonate a trusted brand "
                + "or understate its risk.");
        put("AST05", "**Unsafe Deserialization** - Remove any eval()/exec() calls on config or memory content "
                + "and any dangerous YAML/JSON tags. Replace with safe, schema-validated parsing.");
        put("AST06", "**Weak Isolation** - Remove requests for host-level execution, Docker socket access, or "
                + "sudo/root escalation. Run the skill in a sandboxed environment with the minimum privileges "
                + "needed.");
        put("AST07", "**Update Drift** - Pin the skill to an exact, verified version and add hash/content "
                + "verification to its manifest instead of using unpinned version ranges.");
        put("AST08", "**Poor Scanning / Evasion** - Treat obfuscated or indirectly-phrased content as a red flag "
                + "on its own. Request a de-obfuscated, plain-text version of the skill before approving it.");
        put("AST09", "**No Governance** - Require the skill to carry provenance metadata, an audit trail, and an "
                + "explicit approval chain before it is trusted in production workflows.");
        put("AST10", "**Cross-Platform Reuse** - Re-review the skill's security metadata for each target platform "
                + "it claims to support; do not assume protections from one platform carry over to another.");
    }};

    private static final String SKILL_VALIDATION_PROMPT =
        "You are a security analyst reviewing one AI agent skill file (for Claude, Codex, Cursor,\n" +
        "or any coding agent). You are given the skill's name, description, full content, and\n" +
        "static findings from a local scanner. Decide whether the skill is MALICIOUS.\n\n" +

        "A skill is a set of instructions an agent follows. It is malicious when it makes the agent\n" +
        "do something harmful or covert that a reasonable developer, knowing the skill's stated\n" +
        "purpose, would not expect or authorize. Judge INTENT and EFFECT, not keywords.\n\n" +

        "==================== HOW TO DECIDE ====================\n\n" +

        "STEP 1 — INTENT. In one sentence, what is this skill legitimately for? Infer it from the\n" +
        "name, description, and the bulk of the content. This is your baseline for everything below:\n" +
        "a behavior is only suspicious when it is NOT explained by this purpose.\n\n" +

        "STEP 2 — INSPECT THE FIVE MALICIOUS SIGNALS. Go through the content and list every\n" +
        "concrete instance of each. A skill is malicious if ANY signal fires with clear,\n" +
        "purpose-unaligned, deliberate evidence.\n\n" +

        "  SIGNAL A — OUTBOUND NETWORK / EXFILTRATION.\n" +
        "    Enumerate EVERY URL, host, IP, webhook, email, or socket the skill sends data to\n" +
        "    (curl/wget/fetch/axios/httpx, POST bodies, uploads, git remotes, DNS, etc.).\n" +
        "    For each destination decide: is it LOCAL/TRUSTED or REMOTE/UNTRUSTED?\n" +
        "      LOCAL/TRUSTED (safe): loopback (localhost,127.0.0.1,::1,0.0.0.0), file:// or unix\n" +
        "        sockets, and first-party agent infrastructure — *.anthropic.com, *.claude.ai,\n" +
        "        *.claude.com, api.openai.com, *.openai.com, and the skill's OWN documented API\n" +
        "        that matches its stated purpose. Reading public docs (e.g. llms-full.txt) is safe.\n" +
        "      REMOTE/UNTRUSTED (malicious IF it carries local data): any other external host,\n" +
        "        especially when the payload is file contents, command output, env vars, secrets,\n" +
        "        or source code. A hardcoded unknown domain receiving local data = exfiltration.\n" +
        "    Sending local/secret data to a REMOTE/UNTRUSTED destination is the strongest signal.\n\n" +

        "  SIGNAL B — WRITES OUTSIDE THE SKILL'S OWN SCOPE / PERSISTENCE.\n" +
        "    The skill's legitimate write scope is: the current project/repo it is invoked in,\n" +
        "    the agent's own config dir (~/.claude, .claude, .cursor, .codex, .github/copilot),\n" +
        "    and temp dirs (/tmp). Writes THERE are safe config/output.\n" +
        "    MALICIOUS writes establish persistence or tamper with the system OUTSIDE that scope:\n" +
        "      - shell init / rc files: ~/.bashrc, ~/.zshrc, ~/.profile, ~/.bash_profile\n" +
        "      - autostart / scheduling: cron, crontab, systemd units, launchd plists, Startup\n" +
        "      - system paths: /etc (e.g. /etc/hosts), /Library, /System, /usr, /var, Windows registry\n" +
        "      - git hooks or CI config planted to run attacker code on future commits/builds\n" +
        "    Flag these as persistence/system-tampering when they are covert or purpose-unaligned.\n\n" +

        "  SIGNAL C — CREDENTIAL / SECRET ACCESS.\n" +
        "    The skill reads identity or secret material unrelated to its purpose: ~/.ssh/id_rsa,\n" +
        "    ~/.aws/credentials, ~/.npmrc, .env files, /etc/passwd, browser credential stores,\n" +
        "    or dumps env vars (printenv/env/process.env) to capture secrets.\n" +
        "    Reading a secret ALONE is low risk; reading it AND sending it out (Signal A) is critical.\n" +
        "    Note: writing a token the user provided into an agent/bot .env as configuration is\n" +
        "    normal setup, NOT theft — theft is READING existing secrets the skill has no reason to touch.\n\n" +

        "  SIGNAL D — PROMPT INJECTION / DECEPTION.\n" +
        "    The skill tells the agent to ignore/override/bypass its safety rules or system prompt\n" +
        "    (\"ignore previous instructions\", \"you are now...\", \"disable logging\"), or to hide its\n" +
        "    actions from, lie to, or mislead the user. Any such instruction is malicious.\n\n" +

        "  SIGNAL E — OBFUSCATION / EVASION.\n" +
        "    Hidden or disguised payloads: base64/hex/ROT13 that is decoded and executed, zero-width\n" +
        "    or unicode-escaped commands, instructions split across fields to dodge scanners, or\n" +
        "    natural-language phrasing that describes a harmful action without the obvious keyword\n" +
        "    (\"retrieve the file that stores login details\" = reading credentials).\n\n" +

        "STEP 3 — APPLY THE FALSE-POSITIVE GUARDS. Do NOT flag a signal when it is explained by the\n" +
        "skill's purpose or is only reference material. These are the common over-triggers:\n" +
        "  - Shell/bash execution IS the feature for skills about shell, hooks, commands, scripts,\n" +
        "    or subagents. Running bash, git, build/deploy tools, or user-provided commands is safe.\n" +
        "  - Code inside ``` fences or sections labelled example/template/sample/guide is documentation,\n" +
        "    NOT a live directive. eval()/exec()/curl shown as an example is not executed by the skill.\n" +
        "  - Safety WARNINGS are protective, not attacks (\"never route untrusted input into X\").\n" +
        "  - Writes to the agent's own config dir, the project dir, or /tmp are safe (Signal B scope).\n" +
        "  - Placeholders like Authorization: Bearer ${API_TOKEN} or process.env.X in templates are\n" +
        "    not live credential access or exfiltration.\n" +
        "  - Calls to first-party agent infrastructure and loopback are safe (Signal A trusted list).\n" +
        "  When in doubt, do NOT flag: blocking a legitimate developer workflow is worse than missing\n" +
        "  an ambiguous case. Require clear, deliberate, purpose-unaligned evidence.\n\n" +

        "STEP 4 — USE THE STATIC FINDINGS. They are the primary signal but MAY contain false positives.\n" +
        "  For each static finding decide: confirm it (true positive), reject it (false positive), or\n" +
        "  merge two findings that describe one malicious statement (e.g. credential read + send in the\n" +
        "  same curl) into a single higher-severity event. Keep confirmed and merged findings; drop\n" +
        "  false positives. Then ADD any malicious behavior the scanner missed that you found via\n" +
        "  intent reasoning (Signals A-E). Every finding you keep or add becomes one maliciousEvents entry.\n\n" +

        "STEP 5 — MAP OWASP AGENTIC-SKILLS CATEGORIES. Assign only with clear content evidence:\n" +
        "  AST01 Malicious Skills — deliberate harmful payload: credential theft, backdoor, C2, exfil.\n" +
        "  AST03 Over-Privileged — access/permissions far beyond the stated purpose.\n" +
        "  AST04 Insecure Metadata — name/description misrepresents actual behavior; impersonation.\n" +
        "  AST05 Unsafe Deserialization — dangerous YAML/JSON tags, eval() on config/memory content.\n" +
        "  AST06 Weak Isolation — persistence, system-file tampering, Docker socket, sudo/root, /proc.\n" +
        "  AST08 Poor Scanning / Evasion — obfuscation used to hide intent from scanners.\n\n" +

        "==================== INPUT ====================\n\n" +

        "SKILL NAME: %s\n" +
        "SKILL DESCRIPTION: %s\n" +
        "SKILL CONTENT (full markdown):\n%s\n\n" +
        "STATIC FINDINGS (local scanner, JSON array — may contain false positives):\n%s\n\n" +

        "==================== SCORING ====================\n\n" +

        "maliciousMatchScore (0.0-1.0 for the whole skill):\n" +
        "  0.9-1.0 confirmed deliberate payload (exfil to untrusted host, injection, covert persistence)\n" +
        "  0.6-0.8 strong purpose-unaligned indicators with minor ambiguity\n" +
        "  0.3-0.5 suspicious but plausibly benign\n" +
        "  0.0-0.2 safe or explained by purpose\n" +
        "toolNameDescriptionMatchScore (0.0-1.0): how well the name/description matches actual behavior\n" +
        "  (low score = metadata misrepresents what the skill does).\n" +
        "overallConfidence: HIGH (static + intent agree, clear payload) / MEDIUM / LOW (ambiguous).\n" +
        "Default isMalicious = false; only set true with clear, deliberate, purpose-unaligned evidence.\n\n" +

        "==================== OUTPUT ====================\n\n" +
        "Respond with VALID JSON ONLY, no markdown fences:\n" +
        "{\n" +
        "  \"skillPurpose\": \"One sentence: what is this skill legitimately trying to accomplish?\",\n" +
        "  \"isMalicious\": true,\n" +
        "  \"maliciousMatchScore\": 0.0,\n" +
        "  \"toolNameDescriptionMatchScore\": 0.0,\n" +
        "  \"detectedPatterns\": [1, 2],\n" +
        "  \"llmOwaspCategories\": [\"AST01\"],\n" +
        "  \"couldBeBenign\": false,\n" +
        "  \"couldBeBenignReason\": \"Explanation.\",\n" +
        "  \"socAnalystSummary\": \"2-3 sentences covering intent, risk surface, and recommended action.\",\n" +
        "  \"overallConfidence\": \"HIGH | MEDIUM | LOW\",\n" +
        "  \"reason\": \"Narrative summary of all findings.\",\n" +
        "  \"evidence\": \"Exact matching text (max 200 chars) or empty string if safe.\",\n" +
        "  \"overview\": \"A clear overview of this skill in GitHub-flavored Markdown, written for someone who\n" +
        "    has not read the skill file. Structure it around exactly two questions, each as its own bolded\n" +
        "    lead-in or short heading:\n" +
        "    - What is this? Explain what the skill is, its stated purpose, and its intended use case.\n" +
        "    - Why is it dangerous? Explain the specific risk, malicious behavior, or attack it enables. If the\n" +
        "      skill is safe, explain why it poses little to no danger instead.\n" +
        "    2-4 sentences per question, in prose. No filler.\",\n" +
        "  \"remediation\": \"Your own actionable remediation recommendation in GitHub-flavored Markdown\n" +
        "    (a short intro plus a bulleted or numbered list of concrete steps). If the skill is malicious,\n" +
        "    focus on containment and cleanup. If it is safe, give brief hardening/best-practice notes. Do NOT\n" +
        "    restate the OWASP category names verbatim — that mapping is added separately.\",\n" +
        "  \"maliciousEvents\": [\n" +
        "    {\n" +
        "      \"rule\": \"injection | exfiltration | credential-theft | obfuscation | deception | privilege-escalation | deserialization | system-tampering\",\n" +
        "      \"reason\": \"Why this specific finding is malicious in context.\",\n" +
        "      \"evidence\": \"Exact quoted text from skill content (max 200 chars).\",\n" +
        "      \"riskScore\": 0.0,\n" +
        "      \"owaspCategories\": [\"AST01\"]\n" +
        "    }\n" +
        "  ]\n" +
        "}";

    // Input fields
    private String skillName;
    private String skillDescription;
    private String skillContent;
    private String agentName;
    private String localAnalysis;

    // Output field
    private Map<String, Object> validationResult;

    public String validateAndReportSkillV2() {
        if (skillName == null || skillName.isEmpty()) {
            addActionError("skillName is required");
            return Action.ERROR.toUpperCase();
        }
        if (skillContent == null || skillContent.isEmpty()) {
            addActionError("skillContent is required");
            return Action.ERROR.toUpperCase();
        }
        if (skillDescription == null) skillDescription = "";
        if (agentName == null) agentName = "";
        if (localAnalysis == null || localAnalysis.isEmpty()) localAnalysis = "[]";

        // Step 1: build prompt
        String prompt = String.format(SKILL_VALIDATION_PROMPT, skillName, skillDescription, skillContent, localAnalysis);

        // Step 2: call LLM via shared LLMService
        String rawContent;
        try {
            rawContent = callLLM(prompt);
        } catch (Exception e) {
            logger.error("LLM call failed for skill=" + skillName + ": " + e.getMessage());
            addActionError("LLM call failed: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }

        // Step 3: parse JSON response
        String cleaned = extractJson(rawContent);
        Map<String, Object> parsed;
        try {
            parsed = gson.fromJson(cleaned, new TypeToken<Map<String, Object>>() {}.getType());
        } catch (Exception e) {
            logger.error("Failed to parse LLM response for skill=" + skillName + ": " + rawContent);
            addActionError("Failed to parse LLM response");
            return Action.ERROR.toUpperCase();
        }

        boolean isMalicious = Boolean.TRUE.equals(parsed.get("isMalicious"));
        double maliciousScore = parsed.containsKey("maliciousMatchScore")
                ? ((Number) parsed.get("maliciousMatchScore")).doubleValue() : 0.0;
        double matchScore = parsed.containsKey("toolNameDescriptionMatchScore")
                ? ((Number) parsed.get("toolNameDescriptionMatchScore")).doubleValue() : 1.0;
        String reason = parsed.containsKey("reason") ? String.valueOf(parsed.get("reason")) : "";
        String evidence = parsed.containsKey("evidence") ? String.valueOf(parsed.get("evidence")) : "";
        boolean flagged = isMalicious || maliciousScore > 0.75;

        String skillPurpose = parsed.containsKey("skillPurpose") ? String.valueOf(parsed.get("skillPurpose")) : "";
        String overallConfidence = parsed.containsKey("overallConfidence") ? String.valueOf(parsed.get("overallConfidence")) : "LOW";
        boolean couldBeBenign = Boolean.TRUE.equals(parsed.get("couldBeBenign"));
        String couldBeBenignReason = parsed.containsKey("couldBeBenignReason") ? String.valueOf(parsed.get("couldBeBenignReason")) : "";
        String socAnalystSummary = parsed.containsKey("socAnalystSummary") ? String.valueOf(parsed.get("socAnalystSummary")) : "";
        List<?> maliciousEvents = parsed.containsKey("maliciousEvents") ? (List<?>) parsed.get("maliciousEvents") : new ArrayList<>();
        String overview = parsed.containsKey("overview") ? String.valueOf(parsed.get("overview")) : "";
        String modelRemediation = parsed.containsKey("remediation") ? String.valueOf(parsed.get("remediation")) : "";

        // Step 4: resolve OWASP categories with confidence tiers
        List<Map<String, String>> owaspCategories = resolveOwaspCategories(parsed);
        String remediation = buildRemediation(modelRemediation, owaspCategories);

        logger.infoAndAddToDb(String.format(
                "[SkillValidation] skill=%s agent=%s flagged=%b maliciousScore=%.2f reason=%s owaspCategories=%s",
                skillName, agentName, flagged, maliciousScore, reason,
                owaspCategories.stream().map(c -> c.get("id") + "(" + c.get("confidence") + ")")
                        .collect(Collectors.joining(","))), LogDb.DB_ABS);

        // Step 5: update audit DB
        try {
            String evidenceText = evidence.isEmpty() ? reason : reason + "\n\n" + evidence;
            if (!skillDescription.isEmpty()) {
                evidenceText = "Description: " + skillDescription + "\n\n" + evidenceText;
            }
            DbLayer.updateMcpAuditInfo(
                    "AGENT_SKILL",
                    skillName,
                    agentName,
                    new ComponentRiskAnalysis(matchScore < 0.7, flagged, evidenceText, owaspCategories));
        } catch (Exception e) {
            logger.error("Failed to update audit DB for skill=" + skillName + ": " + e.getMessage());
        }

        // Step 6: return result
        validationResult = new HashMap<>();
        validationResult.put("isMalicious", flagged);
        validationResult.put("maliciousMatchScore", maliciousScore);
        validationResult.put("toolNameDescriptionMatchScore", matchScore);
        validationResult.put("reason", reason);
        validationResult.put("evidence", evidence);
        validationResult.put("owaspCategories", owaspCategories);
        validationResult.put("skillPurpose", skillPurpose);
        validationResult.put("overallConfidence", overallConfidence);
        validationResult.put("couldBeBenign", couldBeBenign);
        validationResult.put("couldBeBenignReason", couldBeBenignReason);
        validationResult.put("socAnalystSummary", socAnalystSummary);
        validationResult.put("maliciousEvents", maliciousEvents);
        validationResult.put("overview", overview);
        validationResult.put("remediation", remediation);
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Combines the model's own remediation narrative with deterministic, templated
     * guidance for each resolved OWASP category, rendered as GitHub-flavored Markdown.
     */
    private String buildRemediation(String modelRemediation, List<Map<String, String>> owaspCategories) {
        StringBuilder sb = new StringBuilder();
        if (modelRemediation != null && !modelRemediation.isEmpty()) {
            sb.append(modelRemediation.trim());
        }

        if (owaspCategories != null && !owaspCategories.isEmpty()) {
            LinkedHashSet<String> seenIds = new LinkedHashSet<>();
            StringBuilder templated = new StringBuilder();
            for (Map<String, String> cat : owaspCategories) {
                String id = cat.get("id");
                if (id == null || !seenIds.add(id)) continue;
                String template = OWASP_REMEDIATION_TEMPLATES.get(id);
                if (template == null) continue;
                templated.append("- ").append(template).append("\n");
            }
            if (templated.length() > 0) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("### Category-Specific Remediation\n\n").append(templated);
            }
        }

        return sb.toString().trim();
    }

    /**
     * Resolves OWASP categories using two independent signals and assigns confidence:
     *   HIGH   = both pattern-derived mapping AND LLM direct classification agree
     *   MEDIUM = only one of the two signals assigned the category
     */
    private List<Map<String, String>> resolveOwaspCategories(Map<String, Object> parsed) {
        // Signal 1: derive from detectedPatterns via hardcoded mapping
        Set<String> patternDerived = new LinkedHashSet<>();
        List<?> detectedPatterns = parsed.containsKey("detectedPatterns")
                ? (List<?>) parsed.get("detectedPatterns") : new ArrayList<>();
        for (Object p : detectedPatterns) {
            int patternNum = ((Number) p).intValue();
            List<String> mapped = PATTERN_TO_OWASP.get(patternNum);
            if (mapped != null) patternDerived.addAll(mapped);
        }

        // Signal 2: LLM direct OWASP classification — validate each ID against the enum
        Set<String> llmDirect = new LinkedHashSet<>();
        List<?> llmCategories = parsed.containsKey("llmOwaspCategories")
                ? (List<?>) parsed.get("llmOwaspCategories") : new ArrayList<>();
        for (Object c : llmCategories) {
            OwaspAstCategory cat = OwaspAstCategory.fromId(String.valueOf(c));
            if (cat != null) llmDirect.add(cat.getId());
        }

        // Intersection = HIGH confidence; union minus intersection = MEDIUM confidence
        Set<String> highConfidence = patternDerived.stream()
                .filter(llmDirect::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> mediumConfidence = new LinkedHashSet<>();
        patternDerived.stream().filter(id -> !highConfidence.contains(id)).forEach(mediumConfidence::add);
        llmDirect.stream().filter(id -> !highConfidence.contains(id)).forEach(mediumConfidence::add);

        List<Map<String, String>> result = new ArrayList<>();
        for (String id : highConfidence) {
            result.add(buildCategoryEntry(id, "HIGH"));
        }
        for (String id : mediumConfidence) {
            result.add(buildCategoryEntry(id, "MEDIUM"));
        }
        return result;
    }

    private Map<String, String> buildCategoryEntry(String id, String confidence) {
        OwaspAstCategory cat = OwaspAstCategory.fromId(id);
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("name", cat != null ? cat.getDisplayName() : id);
        entry.put("severity", cat != null ? cat.getSeverity() : "UNKNOWN");
        entry.put("confidence", confidence);
        return entry;
    }

    private String callLLM(String prompt) throws Exception {
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(userMessage);
        Map<String, Object> payload = new HashMap<>();
        payload.put("messages", messages);
        payload.put("temperature", 0);
        payload.put("max_tokens", 3500);

        Map<String, Object> llmResponse = LLMService.callLLM(payload);
        if (llmResponse == null) throw new RuntimeException("Empty LLM response");

        List<Map<String, Object>> choices = (List<Map<String, Object>>) llmResponse.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("No choices in LLM response");
        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        if (message == null) throw new RuntimeException("No message in LLM response");
        Object content = message.get("content");
        if (content == null) throw new RuntimeException("No content in LLM message");
        return content.toString();
    }

    private static String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline != -1) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```"));
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) return s.substring(start, end + 1);
        return s;
    }
}
