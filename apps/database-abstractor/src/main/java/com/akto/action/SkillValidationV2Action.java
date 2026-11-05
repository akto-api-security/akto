package com.akto.action;

import com.akto.action.SkillEvidenceUtils.SkillDocument;
import com.akto.action.SkillEvidenceUtils.Span;
import com.akto.action.SkillTagValidator.Finding;
import com.akto.llm.ModelTier;
import com.akto.llm.ServiceKey;
import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.OwaspAstCategory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts2.ServletActionContext;

@Getter
@Setter
public class SkillValidationV2Action extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(SkillValidationV2Action.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();

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

    // Anchors that prove the shape of the statement but leave more to the model's reading of intent.
    private static final Set<SkillTagCriteria> MEDIUM_CONFIDENCE_CRITERIA = EnumSet.of(
            SkillTagCriteria.AST04_UNDISCLOSED_BEHAVIOR,
            SkillTagCriteria.AST07_SELF_UPDATE,
            SkillTagCriteria.AST08_HIDDEN_COMMENT);

    static final String SKILL_VALIDATION_PROMPT =
        "You are a security analyst reviewing one AI agent skill file (for Claude, Codex, Cursor or any\n" +
        "coding agent). Work through the three steps below IN ORDER. Each step uses what the previous one\n" +
        "established.\n" +
        "\n" +
        "You produce TWO different things. Never mix them up:\n" +
        "  FINDINGS — information. Each finding states one thing the skill actually does (\"runs sudo to\n" +
        "    install a package\", \"caches files under ~/.cache\"), proven by exact lines of the file. Findings\n" +
        "    are not accusations: a perfectly safe built-in skill that runs sudo gets the sudo finding.\n" +
        "  MALICIOUS — a verdict. true only when the skill genuinely attacks the user or their machine.\n" +
        "    This is rare. Only a malicious skill raises a security alert; findings never do.\n" +
        "\n" +
        "==================== INPUT ====================\n" +
        "\n" +
        "SKILL NAME: %s\n" +
        "FILE PATH: %s\n" +
        "SKILL DESCRIPTION: %s\n" +
        "SKILL CONTENT (full markdown, frontmatter removed; line numbers below refer to this text):\n" +
        "%s\n" +
        "\n" +
        "==================== STEP 1 — UNDERSTAND THE SKILL ====================\n" +
        "\n" +
        "Before looking for anything, read the name, the description and the WHOLE content, and work out:\n" +
        "  - what the skill is for (skillPurpose, one sentence), and what a user who installs it expects;\n" +
        "  - who ships it. FILE PATH tells you: skills under .cursor/skills-cursor, .codex/skills/.system,\n" +
        "    a claude-plugins-official plugin, or an openai-bundled / openai-primary-runtime plugin are\n" +
        "    built-in vendor skills (openai-curated holds third-party plugins). They are tagged like any\n" +
        "    other skill for what they really do, but they are never malicious;\n" +
        "  - which parts of the file are LIVE INSTRUCTIONS the agent will follow, and which are only\n" +
        "    documentation, examples for the user, explanations, warnings, or descriptions of what an\n" +
        "    attacker might do. Only live instructions can produce a finding.\n" +
        "Every later decision is judged against this understanding.\n" +
        "\n" +
        "==================== STEP 2 — FINDINGS (information) ====================\n" +
        "\n" +
        "The ONLY criteria you may use are listed below, in PRECEDENCE order (first = most specific). A\n" +
        "finding names exactly one criterion id; the tag shown to users comes from the criterion.\n" +
        "%s\n" +
        "CANDIDATE LINES — a pattern scan already located every line that carries the literal signal of a\n" +
        "criterion and is not under a warning. Patterns cannot read meaning, so many candidates are NOT real\n" +
        "findings. Go through EVERY candidate and decide:\n" +
        "  KEEP it when the line is a live instruction that really does what the criterion describes.\n" +
        "  DROP it when the line only mentions, explains, documents or warns about the behaviour, is an\n" +
        "  example of what an attacker does, is a placeholder, or the criterion's description simply does\n" +
        "  not fit what the line does.\n" +
        "You may also add a line that is not in the list if it clearly meets a criterion. Candidate lines:\n" +
        "%s\n" +
        "\n" +
        "How to choose the tag for a line — read what the line DOES, then pick the criterion whose\n" +
        "description says exactly that, so the tag explains the behaviour to someone who never opens the file:\n" +
        "  - Running sudo/su is AST06.c1. Writing or reading under ~ / $HOME (caches, config, keys) is\n" +
        "    AST03.c3. Editing /etc, /proc, chmod 777 or setuid is AST06.c3. cron/launchd/shell rc/git hooks\n" +
        "    is AST06.c4. Reading SSH keys, cloud credentials, .env or dumping the environment is AST03.c2.\n" +
        "  - Data exfiltration (AST03.c4, or AST01.c1 when malicious) means LOCAL DATA REALLY LEAVES THE\n" +
        "    MACHINE: file contents, command output, env vars or source code uploaded to an external host.\n" +
        "    Writing a file under ~ is NOT exfiltration. Downloading something is NOT exfiltration. Posting a\n" +
        "    message the user asked to send is NOT exfiltration.\n" +
        "  - Plain shell, git, build, test and package-manager commands, reading or writing files inside the\n" +
        "    project, and fetching from public docs or registries are not findings unless they carry one of the\n" +
        "    signals above.\n" +
        "\n" +
        "ONE LINE, ONE TAG — absolute rules:\n" +
        "  1. A line of the skill may back AT MOST ONE finding across the whole output. When a candidate\n" +
        "     lists several criteria, pick the single one that best describes what the line does (when two\n" +
        "     fit equally, the one listed first) and never quote that line anywhere else.\n" +
        "  2. ONE FINDING PER CRITERION. Every line showing the same criterion goes into that one finding's\n" +
        "     evidence array. Ten sudo lines are one AST06.c1 finding with ten quotes.\n" +
        "  3. ONE QUOTE PER LINE. Never quote two pieces of one line, or a longer/shorter form of a quote\n" +
        "     already listed.\n" +
        "\n" +
        "For each finding:\n" +
        "  reason — one concrete sentence: what these lines do, naming the actual command, path, host or\n" +
        "    data, and whether the purpose from STEP 1 explains it. Not generic risk prose.\n" +
        "  purposeAligned — true when the purpose explains the behaviour (an install skill running sudo, a\n" +
        "    firmware tool caching under ~/.cache, a deploy skill posting to its own API); false only when it\n" +
        "    has nothing to do with the purpose.\n" +
        "\n" +
        "==================== STEP 3 — MALICIOUS (verdict) ====================\n" +
        "\n" +
        "Assume NOT MALICIOUS. Powerful, over-broad, risky or sloppy is NOT malicious — those are findings.\n" +
        "Built-in vendor skills are never malicious.\n" +
        "\n" +
        "Use an AST01 criterion, and set isMalicious = true, ONLY when ALL FIVE hold for one line:\n" +
        "  1. LIVE DIRECTIVE    — the agent would actually execute it; not documentation or a warning.\n" +
        "  2. PURPOSE-UNALIGNED — STEP 1's purpose does not explain it (purposeAligned = false).\n" +
        "  3. CONCRETE HARM     — local or secret data reaching an untrusted remote destination, theft of\n" +
        "                         credentials the skill has no reason to touch, covert persistence that calls\n" +
        "                         out, an override of the agent's safety rules, or deception of the user.\n" +
        "                         \"Could be misused\" is NOT harm.\n" +
        "  4. COVERT            — hidden, disguised, or done without the user asking for it.\n" +
        "  5. QUOTABLE          — the exact proving text is in SKILL CONTENT.\n" +
        "Trusted destinations are NEVER exfiltration: loopback, private IPs, *.anthropic.com, *.claude.ai,\n" +
        "*.claude.com, *.openai.com, github.com, package registries, public docs, a host the user configured,\n" +
        "and the skill's own documented API that matches its purpose.\n" +
        "If any of the five fails, do not use AST01 — use the factual criterion (AST03.c4 for data sent to an\n" +
        "external host, AST03.c2 for a credential read, AST06.c4 for persistence) or nothing.\n" +
        "An AST01 line still counts once: it replaces the factual finding for that line, it is not added\n" +
        "next to it. isMalicious = true if and only if at least one AST01 finding is present.\n" +
        "\n" +
        "==================== STATIC FINDINGS ====================\n" +
        "\n" +
        "Hits from a local pattern scanner with no understanding of purpose; most are false positives. Treat\n" +
        "them like candidate lines: keep only those that meet a criterion under the rules above.\n" +
        "%s\n" +
        "\n" +
        "==================== HOW YOUR OUTPUT IS CHECKED ====================\n" +
        "\n" +
        "Every finding is machine-verified. It is DISCARDED when a quote is not found character for character\n" +
        "in SKILL CONTENT, does not contain the criterion's literal signal, comes from a warning or threat\n" +
        "description, or its line was already used by another finding. A wrong tag is worse than a missing\n" +
        "one, but a real behaviour you leave out is also a mistake: every candidate line deserves a decision.\n" +
        "\n" +
        "Each evidence entry is ONE quote: a contiguous piece of ONE line, copied character for character,\n" +
        "4-200 characters, containing the criterion's literal signal. No paraphrase, ellipsis, joined lines,\n" +
        "re-indenting or wrapping backticks. For encoded payloads quote the ENCODED text and explain what it\n" +
        "decodes to in reason.\n" +
        "\n" +
        "==================== SCORING ====================\n" +
        "\n" +
        "maliciousMatchScore (0.0-1.0): 0.9-1.0 an AST01 finding with a clear payload; 0.0-0.2 no AST01\n" +
        "  finding. Never above 0.2 without an AST01 finding.\n" +
        "toolNameDescriptionMatchScore (0.0-1.0): how well the name/description matches actual behaviour.\n" +
        "  Below 0.7 only when the content does something material the description never discloses — that is\n" +
        "  also the only time AST04.c1 may be used. A powerful skill that does what it says scores high.\n" +
        "overallConfidence: HIGH / MEDIUM / LOW.\n" +
        "\n" +
        "==================== OUTPUT ====================\n" +
        "Respond with VALID JSON ONLY, no markdown fences.\n" +
        "JSON-escape every quote you copy — double quotes as \\\" , backslashes as \\\\ — so the response parses.\n" +
        "Escaping is transport encoding, not editing: the underlying characters must stay exactly as in the file.\n" +
        "{\n" +
        "  \"skillPurpose\": \"One sentence: what is this skill legitimately trying to accomplish?\",\n" +
        "  \"findings\": [\n" +
        "    {\n" +
        "      \"criterion\": \"One criterion id from the list, e.g. AST06.c1\",\n" +
        "      \"purposeAligned\": true,\n" +
        "      \"reason\": \"One concrete sentence: what these lines do and whether the purpose explains it.\",\n" +
        "      \"evidence\": [\"exact quote from one line\", \"exact quote from another line\"]\n" +
        "    }\n" +
        "  ],\n" +
        "  \"isMalicious\": false,\n" +
        "  \"maliciousMatchScore\": 0.0,\n" +
        "  \"toolNameDescriptionMatchScore\": 0.0,\n" +
        "  \"couldBeBenign\": true,\n" +
        "  \"couldBeBenignReason\": \"Explanation.\",\n" +
        "  \"socAnalystSummary\": \"2-3 sentences covering intent, risk surface, and recommended action.\",\n" +
        "  \"overallConfidence\": \"HIGH | MEDIUM | LOW\",\n" +
        "  \"reason\": \"Why the verdict is what it is. If malicious, name the AST01 line and why it clears\n" +
        "    all five conditions. If safe, say which findings the purpose explains.\",\n" +
        "  \"overview\": \"GitHub-flavored Markdown for someone who has not read the skill file, written about\n" +
        "    THIS skill specifically — name the actual files, hosts, commands and behaviours you found. No\n" +
        "    generic security prose. Exactly two bolded lead-ins:\n" +
        "    - What is this? What the skill is, its stated purpose, and its intended use case.\n" +
        "    - Why is it dangerous? The AST01 line: what it does, which data it touches, where that data\n" +
        "      goes, and what an attacker gains. If there is no AST01 finding, say plainly that the skill\n" +
        "      is safe, name the findings, and explain why its purpose accounts for each.\n" +
        "    2-4 sentences per question, in prose.\",\n" +
        "  \"remediation\": \"GitHub-flavored Markdown: one short intro line, then a numbered list of concrete\n" +
        "    steps for THIS skill. If malicious, first REPAIR the skill — name the exact line to delete or\n" +
        "    change and what replaces it so the skill still does its job — then the containment steps\n" +
        "    (what to rotate, revoke or audit). If not malicious, brief hardening notes tied to the findings,\n" +
        "    and say the skill does not need removal. Do NOT restate OWASP category names.\"\n" +
        "}";

    private static final List<String> SEVERITY_ORDER = Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL");

    // Skill folders the agent vendors ship themselves. Their skills are tagged but never flagged.
    private static final List<String> VENDOR_SKILL_PATHS = Arrays.asList(
            "/.cursor/skills-cursor/",
            "/.codex/skills/.system/",
            "/plugins/marketplaces/claude-plugins-official/",
            "/plugins/cache/claude-plugins-official/",
            "/.codex/plugins/cache/openai-bundled/",
            "/.codex/plugins/cache/openai-primary-runtime/",
            "/.codex/.tmp/bundled-marketplaces/openai-bundled/");

    // Input fields
    private String skillName;
    private String skillDescription;
    private String skillContent;
    private String agentName;
    private String localAnalysis;
    // The agent's ingest host for this skill, e.g. "<device>.ai-agent.claude".
    private String collectionName;
    private String filePath;

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
        if (filePath == null) filePath = "";
        if (collectionName == null) collectionName = "";

        // Read before the LLM call: both are request-scoped and gone once publishing goes async.
        String authToken = requestAuthToken();
        int accountId = Context.accountId.get() == null ? 0 : Context.accountId.get();
        boolean vendorShipped = isVendorShipped(filePath);

        // Step 1: build prompt. Candidate lines are found here so the model judges every signal-bearing
        // line instead of having to spot them in a long file.
        SkillDocument doc = new SkillDocument(skillContent);
        List<String> candidates = SkillTagValidator.candidateLines(doc);
        String prompt = buildPrompt(skillName, filePath, skillDescription, skillContent, candidates, localAnalysis);

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
        Map<String, Object> parsed;
        try {
            parsed = parseLlmJson(rawContent);
        } catch (Exception e) {
            logger.error("Failed to parse LLM response for skill=" + skillName + ": " + rawContent);
            addActionError("Failed to parse LLM response");
            return Action.ERROR.toUpperCase();
        }

        boolean claimedMalicious = Boolean.TRUE.equals(parsed.get("isMalicious"));
        if (claimedMalicious && vendorShipped) {
            logger.infoAndAddToDb("[SkillValidation] verdict dropped, vendor-shipped skill skill=" + skillName
                    + " path=" + filePath, LogDb.DB_ABS);
            claimedMalicious = false;
        }
        double maliciousScore = parsed.containsKey("maliciousMatchScore")
                ? ((Number) parsed.get("maliciousMatchScore")).doubleValue() : 0.0;
        double matchScore = parsed.containsKey("toolNameDescriptionMatchScore")
                ? ((Number) parsed.get("toolNameDescriptionMatchScore")).doubleValue() : 1.0;
        String reason = parsed.containsKey("reason") ? String.valueOf(parsed.get("reason")) : "";

        String skillPurpose = parsed.containsKey("skillPurpose") ? String.valueOf(parsed.get("skillPurpose")) : "";
        String overallConfidence = parsed.containsKey("overallConfidence") ? String.valueOf(parsed.get("overallConfidence")) : "LOW";
        boolean couldBeBenign = Boolean.TRUE.equals(parsed.get("couldBeBenign"));
        String couldBeBenignReason = parsed.containsKey("couldBeBenignReason") ? String.valueOf(parsed.get("couldBeBenignReason")) : "";
        String socAnalystSummary = parsed.containsKey("socAnalystSummary") ? String.valueOf(parsed.get("socAnalystSummary")) : "";
        String overview = parsed.containsKey("overview") ? String.valueOf(parsed.get("overview")) : "";
        String modelRemediation = parsed.containsKey("remediation") ? String.valueOf(parsed.get("remediation")) : "";

        // Step 4: ground every finding in the skill file — exact quotes only, one line backs one tag,
        // and each quote must carry its criterion's literal signal.
        List<?> claimedFindings = parsed.get("findings") instanceof List ? (List<?>) parsed.get("findings") : new ArrayList<>();
        List<Finding> findings = SkillTagValidator.validate(claimedFindings, doc, claimedMalicious, matchScore);

        // Malicious if and only if the model said so AND an AST01 finding survived every check.
        Finding maliciousFinding = findings.stream()
                .filter(f -> f.criteria.isMalicious())
                .findFirst()
                .orElse(null);
        boolean flagged = claimedMalicious && maliciousFinding != null;
        if (claimedMalicious && !flagged) {
            logger.infoAndAddToDb("[SkillValidation] verdict dropped, no AST01 finding survived validation"
                    + " skill=" + skillName + " agent=" + agentName, LogDb.DB_ABS);
        }
        maliciousScore = flagged ? Math.max(maliciousScore, 0.8) : Math.min(maliciousScore, 0.2);
        String evidence = flagged ? maliciousFinding.evidenceText() : "";

        // Step 5: tags for every skill, one entry per OWASP category
        List<Map<String, String>> owaspCategories = buildCategoryEntries(findings);
        String remediation = flagged ? buildRemediation(modelRemediation, findings) : modelRemediation.trim();

        List<Map<String, Object>> reportedEvents = new ArrayList<>();
        if (flagged) {
            Map<String, Object> verdictEvent = new LinkedHashMap<>();
            verdictEvent.put("rule", maliciousFinding.criteria.getRule());
            verdictEvent.put("criterion", maliciousFinding.criteria.getId());
            verdictEvent.put("reason", maliciousFinding.reason);
            verdictEvent.put("evidence", evidence);
            verdictEvent.put("riskScore", maliciousScore);
            verdictEvent.put("owaspCategories", findings.stream()
                    .filter(f -> !f.purposeAligned)
                    .map(f -> f.criteria.getCategory().getId())
                    .distinct()
                    .collect(Collectors.toList()));
            verdictEvent.put("tag", "malicious_skill_detected");
            reportedEvents.add(verdictEvent);
        }

        logger.infoAndAddToDb(String.format(
                "[SkillValidation] skill=%s agent=%s flagged=%b maliciousScore=%.2f claimedFindings=%d keptFindings=%d reason=%s tags=%s",
                skillName, agentName, flagged, maliciousScore, claimedFindings.size(), findings.size(), reason,
                findings.stream().map(f -> f.criteria.getId() + "(" + f.spans.size() + ")")
                        .collect(Collectors.joining(","))), LogDb.DB_ABS);

        // Step 6: update audit DB
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

        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("isMalicious", flagged);
        verdict.put("maliciousMatchScore", maliciousScore);
        verdict.put("toolNameDescriptionMatchScore", matchScore);
        verdict.put("reason", reason);
        verdict.put("evidence", evidence);
        verdict.put("owaspCategories", owaspCategories);
        verdict.put("tags", buildTagFindings(findings));
        verdict.put("tagLabels", tagLabels(findings));
        verdict.put("skillPurpose", skillPurpose);
        verdict.put("overallConfidence", overallConfidence);
        verdict.put("couldBeBenign", couldBeBenign);
        verdict.put("couldBeBenignReason", couldBeBenignReason);
        verdict.put("socAnalystSummary", socAnalystSummary);
        verdict.put("maliciousEvents", reportedEvents);
        verdict.put("overview", overview);
        verdict.put("remediation", remediation);
        // What the model was shown and what it claimed before grounding, for judging recall when testing.
        verdict.put("candidateLines", candidates);
        verdict.put("llmFindings", claimedFindings);

        // Step 7: publish the skill from here — endpoint with its tags, plus the guardrail activity when
        // malicious. Off the request thread so the agent's validation call is not held up by either.
        if (authToken.isEmpty() || collectionName.isEmpty()) {
            logger.errorAndAddToDb("[SkillValidation] not publishing skill=" + skillName
                    + ": missing " + (authToken.isEmpty() ? "Authorization header" : "collectionName"), LogDb.DB_ABS);
        } else {
            SkillReporter.SkillRef skill = new SkillReporter.SkillRef(skillName, skillDescription, skillContent,
                    agentName, filePath, collectionName, accountId, authToken);
            List<String> labels = tagLabels(findings);
            SkillReporter.Threat threat = flagged ? buildThreat(maliciousFinding, maliciousScore, evidence,
                    overview, remediation, owaspCategories, reportedEvents.get(0)) : null;
            new Thread(() -> {
                Context.accountId.set(accountId);
                SkillReporter.ingestSkill(skill, labels);
                if (threat != null) SkillReporter.reportThreat(skill, threat);
            }, "skill-publisher").start();
        }

        // Step 8: answer the agent with a clean verdict. Cyborg has already published the skill, and the
        // agent raises threats and skill-tags from isMalicious / maliciousEvents itself — leaving those
        // empty keeps it from duplicating them. The real verdict travels under "verdict", which the agent
        // ignores.
        validationResult = new HashMap<>();
        validationResult.put("isMalicious", false);
        validationResult.put("maliciousMatchScore", 0.0);
        validationResult.put("toolNameDescriptionMatchScore", matchScore);
        validationResult.put("owaspCategories", new ArrayList<>());
        validationResult.put("maliciousEvents", new ArrayList<>());
        validationResult.put("verdict", verdict);
        return Action.SUCCESS.toUpperCase();
    }

    /** Distinct tag labels in precedence order — what the ingested endpoint carries as skill-tags. */
    static List<String> tagLabels(List<Finding> findings) {
        return findings.stream().map(f -> f.criteria.getLabel()).distinct().collect(Collectors.toList());
    }

    static SkillReporter.Threat buildThreat(Finding maliciousFinding, double maliciousScore, String evidence,
            String overview, String remediation, List<Map<String, String>> owaspCategories,
            Map<String, Object> verdictEvent) {
        List<?> eventIds = (List<?>) verdictEvent.get("owaspCategories");
        List<Map<String, String>> threatCategories = owaspCategories.stream()
                .filter(c -> eventIds.contains(c.get("id")))
                .collect(Collectors.toList());
        // As the agent does: the highest severity among the threat's OWASP categories, else HIGH.
        String severity = threatCategories.stream()
                .map(c -> c.get("severity"))
                .filter(s -> SEVERITY_ORDER.indexOf(s) >= 0)
                .max((a, b) -> SEVERITY_ORDER.indexOf(a) - SEVERITY_ORDER.indexOf(b))
                .orElse("HIGH");
        return new SkillReporter.Threat(
                maliciousFinding.criteria.getLabel(),
                severity,
                maliciousScore,
                maliciousFinding.reason,
                evidence,
                overview,
                remediation,
                threatCategories);
    }

    static String buildPrompt(String skillName, String filePath, String skillDescription, String skillContent,
            List<String> candidateLines, String localAnalysis) {
        String candidates = candidateLines.isEmpty()
                ? "(none — no line carries a criterion's signal; add a finding only if a line clearly meets one)"
                : String.join("\n", candidateLines)
                        + (candidateLines.size() >= SkillTagValidator.MAX_CANDIDATE_LINES ? "\n(list truncated)" : "");
        return String.format(SKILL_VALIDATION_PROMPT, skillName, filePath, skillDescription, skillContent,
                SkillTagCriteria.promptTable(), candidates, localAnalysis);
    }

    static boolean isVendorShipped(String filePath) {
        String path = filePath == null ? "" : filePath.replace('\\', '/');
        return VENDOR_SKILL_PATHS.stream().anyMatch(path::contains);
    }

    private static String requestAuthToken() {
        try {
            HttpServletRequest request = ServletActionContext.getRequest();
            String token = request == null ? null : request.getHeader("Authorization");
            return token == null ? "" : token.trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * One entry per OWASP category, flattened to strings so it fits ComponentRiskAnalysis. Evidence
     * lines are prefixed with their 1-based line number in the skill file.
     */
    static List<Map<String, String>> buildCategoryEntries(List<Finding> findings) {
        Map<OwaspAstCategory, List<Finding>> byCategory = new LinkedHashMap<>();
        for (Finding finding : findings) {
            byCategory.computeIfAbsent(finding.criteria.getCategory(), c -> new ArrayList<>()).add(finding);
        }

        List<Map<String, String>> entries = new ArrayList<>();
        for (Map.Entry<OwaspAstCategory, List<Finding>> e : byCategory.entrySet()) {
            OwaspAstCategory category = e.getKey();
            List<Finding> group = e.getValue();
            boolean medium = group.stream().allMatch(f -> MEDIUM_CONFIDENCE_CRITERIA.contains(f.criteria));

            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id", category.getId());
            entry.put("name", category.getDisplayName());
            entry.put("severity", category.getSeverity());
            entry.put("confidence", medium ? "MEDIUM" : "HIGH");
            entry.put("purposeAligned", String.valueOf(group.stream().allMatch(f -> f.purposeAligned)));
            entry.put("criteria", group.stream().map(f -> f.criteria.getId()).collect(Collectors.joining(",")));
            entry.put("reason", group.stream().map(f -> f.criteria.getId() + ": " + f.reason)
                    .collect(Collectors.joining("\n")));
            entry.put("evidence", group.stream().flatMap(f -> f.spans.stream())
                    .map(s -> "L" + (s.firstLine + 1) + ": " + s.text)
                    .collect(Collectors.joining("\n")));
            entries.add(entry);
        }
        return entries;
    }

    /** Structured per-criterion view of the same findings for callers that render evidence per line. */
    static List<Map<String, Object>> buildTagFindings(List<Finding> findings) {
        List<Map<String, Object>> tags = new ArrayList<>();
        for (Finding finding : findings) {
            OwaspAstCategory category = finding.criteria.getCategory();
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("id", category.getId());
            tag.put("name", category.getDisplayName());
            tag.put("severity", category.getSeverity());
            tag.put("criterion", finding.criteria.getId());
            tag.put("criterionDescription", finding.criteria.getDescription());
            tag.put("purposeAligned", finding.purposeAligned);
            tag.put("reason", finding.reason);
            List<Map<String, Object>> evidence = new ArrayList<>();
            for (Span span : finding.spans) {
                Map<String, Object> quote = new LinkedHashMap<>();
                quote.put("line", span.firstLine + 1);
                quote.put("text", span.text);
                evidence.add(quote);
            }
            tag.put("evidence", evidence);
            tags.add(tag);
        }
        return tags;
    }

    /**
     * Combines the model's own remediation narrative with deterministic, templated guidance for each
     * category the purpose does not explain, rendered as GitHub-flavored Markdown.
     */
    private String buildRemediation(String modelRemediation, List<Finding> findings) {
        StringBuilder sb = new StringBuilder();
        if (modelRemediation != null && !modelRemediation.isEmpty()) {
            sb.append(modelRemediation.trim());
        }

        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        StringBuilder templated = new StringBuilder();
        for (Finding finding : findings) {
            if (finding.purposeAligned) continue;
            String id = finding.criteria.getCategory().getId();
            if (!seenIds.add(id)) continue;
            String template = OWASP_REMEDIATION_TEMPLATES.get(id);
            if (template == null) continue;
            templated.append("- ").append(template).append("\n");
        }
        if (templated.length() > 0) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("### Category-Specific Remediation\n\n").append(templated);
        }

        return sb.toString().trim();
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
        // Azure books max_tokens against the deployment's TPM at admission, so keep it tight.
        payload.put("max_tokens", ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION.getMaxTokens());
        // JSON mode: without it the model now and then leaves a trailing comma after the last field.
        payload.put("response_format", Collections.singletonMap("type", "json_object"));
        Map<String, Object> llmResponse = LLMService.callLLM(payload, ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION, ModelTier.GPT_4O_MINI);
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

    /**
     * The model's JSON object. Providers without JSON mode sometimes leave a trailing comma after the
     * last field or array entry; that is the one defect repaired before giving up.
     */
    static Map<String, Object> parseLlmJson(String raw) {
        String json = extractJson(raw);
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        try {
            return gson.fromJson(json, type);
        } catch (Exception e) {
            return gson.fromJson(stripTrailingCommas(json), type);
        }
    }

    /** Drops commas that directly precede a closing brace or bracket, leaving string contents alone. */
    static String stripTrailingCommas(String json) {
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < json.length()) out.append(json.charAt(++i));
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == ',') {
                int next = i + 1;
                while (next < json.length() && Character.isWhitespace(json.charAt(next))) next++;
                if (next < json.length() && (json.charAt(next) == '}' || json.charAt(next) == ']')) continue;
            }
            out.append(c);
        }
        return out.toString();
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
