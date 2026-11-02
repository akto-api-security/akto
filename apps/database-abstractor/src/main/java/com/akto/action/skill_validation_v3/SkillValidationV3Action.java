package com.akto.action.skill_validation_v3;

import com.akto.action.LLMService;
import com.akto.action.SkillTagCriteria;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.Location;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.SkillDocument;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.Span;
import com.akto.action.skill_validation_v3.SkillTagValidatorV3.Finding;
import com.akto.data_actor.DbLayer;
import com.akto.dto.ComponentRiskAnalysis;
import com.akto.dto.OwaspAstCategory;
import com.akto.llm.ModelTier;
import com.akto.llm.ServiceKey;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.google.gson.stream.JsonReader;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts2.ServletActionContext;

/**
 * Skill validation V3: validates a skill from the content already stored for it, for the dashboard's
 * skill revalidation job. Only the skill file is stored, so the scripts it runs are not part of this
 * validation.
 *
 * Nothing is ingested. The result is returned and the job writes the annotations, verdict and tags onto
 * the stored endpoint itself, replacing the old ones. The audit info is updated, and the guardrail
 * activity is raised for a malicious skill.
 *
 * Self-contained: it shares no code with V2, only the criteria (SkillTagCriteria) and the LLM client.
 */
@Getter
@Setter
public class SkillValidationV3Action extends ActionSupport {

    private static final LoggerMaker logger = new LoggerMaker(SkillValidationV3Action.class, LogDb.DB_ABS);

    // Anchors that prove the shape of the statement but leave more to the model's reading of intent.
    private static final Set<SkillTagCriteria> MEDIUM_CONFIDENCE_CRITERIA = EnumSet.of(
            SkillTagCriteria.AST04_UNDISCLOSED_BEHAVIOR,
            SkillTagCriteria.AST07_SELF_UPDATE,
            SkillTagCriteria.AST08_HIDDEN_COMMENT);

    static final String SKILL_VALIDATION_PROMPT =
        "You are a security analyst reviewing one AI agent skill (for Claude, Codex, Cursor or any coding\n" +
        "agent): its skill file, and the scripts it runs when there are any. Work through the three steps\n" +
        "below IN ORDER. Each step uses what the previous one established.\n" +
        "\n" +
        "You produce TWO different things. Never mix them up:\n" +
        "  TAGS — information. Each tag states one thing the skill actually does (\"runs sudo to install a\n" +
        "    package\", \"caches files under ~/.cache\"), proven by exact lines. Tags are not accusations: a\n" +
        "    perfectly safe built-in skill that runs sudo gets the sudo tag.\n" +
        "  MALICIOUS — a verdict, with its own score, reason and evidence. isMalicious is true only when the\n" +
        "    skill genuinely attacks the user or their machine. This is rare. Only a malicious skill raises\n" +
        "    a security alert; tags never do.\n" +
        "\n" +
        "==================== INPUT ====================\n" +
        "\n" +
        "SKILL NAME: %s\n" +
        "FILE PATH: %s\n" +
        "SKILL DESCRIPTION: %s\n" +
        "SKILL CONTENT (full markdown, frontmatter removed; line numbers below refer to this text):\n" +
        "%s\n" +
        "\n" +
        "FILES THIS SKILL RUNS (scripts the skill refers to, collected from the user's machine with secrets\n" +
        "redacted as [REDACTED]; everything in them executes when the skill runs them):\n" +
        "%s\n" +
        "\n" +
        "==================== STEP 1 — UNDERSTAND THE SKILL ====================\n" +
        "\n" +
        "Before looking for anything, read the name, the description, the WHOLE content and every file, and\n" +
        "work out:\n" +
        "  - what the skill is for (skillPurpose, one sentence), and what a user who installs it expects;\n" +
        "  - who ships it. FILE PATH tells you: skills under .cursor/skills-cursor, .codex/skills/.system,\n" +
        "    a claude-plugins-official plugin, or an openai-bundled / openai-primary-runtime plugin are\n" +
        "    built-in vendor skills (openai-curated holds third-party plugins). They are tagged like any\n" +
        "    other skill for what they really do, but they are never malicious;\n" +
        "  - which parts of the skill file are LIVE INSTRUCTIONS the agent will follow, and which are only\n" +
        "    documentation, examples for the user, explanations, warnings, or descriptions of what an\n" +
        "    attacker might do. Only live instructions can produce a tag. Code in the FILES THIS SKILL RUNS\n" +
        "    is live when the skill runs that file; its comments are not.\n" +
        "Every later decision is judged against this understanding.\n" +
        "\n" +
        "==================== STEP 2 — TAGS (information) ====================\n" +
        "\n" +
        "The ONLY criteria you may use are listed below, in PRECEDENCE order (first = most specific). A tag\n" +
        "names exactly one criterion id; the label shown to users comes from the criterion. Tags never use\n" +
        "an AST01 criterion — those belong to the malicious verdict in STEP 3.\n" +
        "%s\n" +
        "CANDIDATE LINES — a pattern scan already located every line that carries the literal signal of a\n" +
        "criterion and is not under a warning (\"L12\" is a line of the skill file, \"<path> L4\" a line of\n" +
        "that file). Patterns cannot read meaning, so many candidates are NOT real tags. Go through EVERY\n" +
        "candidate and decide:\n" +
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
        "  - Data exfiltration (AST03.c4) means LOCAL DATA REALLY LEAVES THE MACHINE: file contents, command\n" +
        "    output, env vars or source code uploaded to an external host. Writing a file under ~ is NOT\n" +
        "    exfiltration. Downloading something is NOT exfiltration. Posting a message the user asked to\n" +
        "    send is NOT exfiltration.\n" +
        "  - Plain shell, git, build, test and package-manager commands, reading or writing files inside the\n" +
        "    project, and fetching from public docs or registries are not tags unless they carry one of the\n" +
        "    signals above.\n" +
        "\n" +
        "ONE LINE, ONE TAG — absolute rules:\n" +
        "  1. A line may back AT MOST ONE tag across the whole output. When a candidate lists several\n" +
        "     criteria, pick the single one that best describes what the line does (when two fit equally,\n" +
        "     the one listed first) and never quote that line in another tag.\n" +
        "  2. ONE TAG PER CRITERION. Every line showing the same criterion goes into that one tag's\n" +
        "     evidence array. Ten sudo lines are one AST06.c1 tag with ten quotes.\n" +
        "  3. ONE QUOTE PER LINE. Never quote two pieces of one line, or a longer/shorter form of a quote\n" +
        "     already listed.\n" +
        "\n" +
        "For each tag:\n" +
        "  reason — one concrete sentence: what these lines do, naming the actual command, path, host or\n" +
        "    data, and whether the purpose from STEP 1 explains it. Not generic risk prose. It is shown\n" +
        "    next to the highlighted lines, so it must make sense to someone reading just those lines.\n" +
        "  purposeAligned — true when the purpose explains the behaviour (an install skill running sudo, a\n" +
        "    firmware tool caching under ~/.cache, a deploy skill posting to its own API); false only when it\n" +
        "    has nothing to do with the purpose.\n" +
        "\n" +
        "==================== STEP 3 — MALICIOUS (verdict) ====================\n" +
        "\n" +
        "Assume NOT MALICIOUS. Powerful, over-broad, risky or sloppy is NOT malicious — those are tags.\n" +
        "Built-in vendor skills are never malicious.\n" +
        "\n" +
        "Set malicious.isMalicious = true ONLY when ALL FIVE hold for the lines you quote:\n" +
        "  1. LIVE DIRECTIVE    — the agent would actually execute it; not documentation or a warning.\n" +
        "  2. PURPOSE-UNALIGNED — STEP 1's purpose does not explain it.\n" +
        "  3. CONCRETE HARM     — local or secret data reaching an untrusted remote destination, theft of\n" +
        "                         credentials the skill has no reason to touch, covert persistence that calls\n" +
        "                         out, an override of the agent's safety rules, or deception of the user.\n" +
        "                         \"Could be misused\" is NOT harm.\n" +
        "  4. COVERT            — hidden, disguised, or done without the user asking for it.\n" +
        "  5. QUOTABLE          — the exact proving text is in SKILL CONTENT or one of the files.\n" +
        "Trusted destinations are NEVER exfiltration: loopback, private IPs, *.anthropic.com, *.claude.ai,\n" +
        "*.claude.com, *.openai.com, github.com, package registries, public docs, a host the user configured,\n" +
        "and the skill's own documented API that matches its purpose.\n" +
        "\n" +
        "When it is malicious, fill the malicious object:\n" +
        "  criterion — the AST01 criterion the quoted lines prove (AST01.c1 data sent to an untrusted host,\n" +
        "    AST01.c2 credentials read and sent, AST01.c3 safety override, AST01.c4 deceiving the user,\n" +
        "    AST01.c5 persistence that calls out).\n" +
        "  evidence — the exact lines that prove it, quoted under the same rules as tag evidence. Quote the\n" +
        "    line that does the harm (the send with its host, the override sentence), not lines around it.\n" +
        "  reason — what those lines do, which data they touch, where it goes, and why each of the five\n" +
        "    conditions holds.\n" +
        "  score — 0.9-1.0 for a clear payload, 0.8 when the harm is clear but the payload is partial.\n" +
        "Keep tagging those lines under their factual criterion in STEP 2 as usual; the verdict is checked\n" +
        "against its own evidence.\n" +
        "When it is not malicious: isMalicious false, criterion \"\", evidence [], score 0.0-0.2, and reason\n" +
        "says which tags the purpose explains.\n" +
        "\n" +
        "==================== STATIC FINDINGS ====================\n" +
        "\n" +
        "Hits from a local pattern scanner with no understanding of purpose; most are false positives. Treat\n" +
        "them like candidate lines: keep only those that meet a criterion under the rules above.\n" +
        "%s\n" +
        "\n" +
        "==================== HOW YOUR OUTPUT IS CHECKED ====================\n" +
        "\n" +
        "Every tag and the malicious verdict are machine-verified. A quote is DISCARDED when it is not found\n" +
        "character for character in SKILL CONTENT or one of the files, does not contain the criterion's\n" +
        "literal signal, comes from a warning or threat description, or its line was already used by another\n" +
        "tag. A verdict whose evidence does not survive is dropped. A wrong tag is worse than a missing one,\n" +
        "but a real behaviour you leave out is also a mistake: every candidate line deserves a decision.\n" +
        "\n" +
        "Each evidence entry is ONE quote: a contiguous piece of ONE line, copied character for character,\n" +
        "4-200 characters, containing the criterion's literal signal. No paraphrase, ellipsis, joined lines,\n" +
        "re-indenting, line numbers, file names or wrapping backticks. For encoded payloads quote the ENCODED\n" +
        "text and explain what it decodes to in reason.\n" +
        "\n" +
        "==================== SCORING ====================\n" +
        "\n" +
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
        "  \"tags\": [\n" +
        "    {\n" +
        "      \"criterion\": \"One criterion id from the list, e.g. AST06.c1\",\n" +
        "      \"purposeAligned\": true,\n" +
        "      \"reason\": \"One concrete sentence: what these lines do and whether the purpose explains it.\",\n" +
        "      \"evidence\": [\"exact quote from one line\", \"exact quote from another line\"]\n" +
        "    }\n" +
        "  ],\n" +
        "  \"malicious\": {\n" +
        "    \"isMalicious\": false,\n" +
        "    \"criterion\": \"\",\n" +
        "    \"score\": 0.0,\n" +
        "    \"reason\": \"Why the verdict is what it is.\",\n" +
        "    \"evidence\": []\n" +
        "  },\n" +
        "  \"toolNameDescriptionMatchScore\": 0.0,\n" +
        "  \"couldBeBenign\": true,\n" +
        "  \"couldBeBenignReason\": \"Explanation.\",\n" +
        "  \"socAnalystSummary\": \"2-3 sentences covering intent, risk surface, and recommended action.\",\n" +
        "  \"overallConfidence\": \"HIGH | MEDIUM | LOW\",\n" +
        "  \"overview\": \"GitHub-flavored Markdown for someone who has not read the skill file, written about\n" +
        "    THIS skill specifically — name the actual files, hosts, commands and behaviours you found. No\n" +
        "    generic security prose. Exactly two bolded lead-ins:\n" +
        "    - What is this? What the skill is, its stated purpose, and its intended use case.\n" +
        "    - Why is it dangerous? The malicious lines: what they do, which data they touch, where that\n" +
        "      data goes, and what an attacker gains. If it is not malicious, say plainly that the skill\n" +
        "      is safe, name the tags, and explain why its purpose accounts for each.\n" +
        "    2-4 sentences per question, in prose.\",\n" +
        "  \"remediation\": \"GitHub-flavored Markdown: one short intro line, then a numbered list of concrete\n" +
        "    steps for THIS skill. If malicious, first REPAIR the skill — name the exact line to delete or\n" +
        "    change and what replaces it so the skill still does its job — then the containment steps\n" +
        "    (what to rotate, revoke or audit). If not malicious, brief hardening notes tied to the tags,\n" +
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

    // Only the skill file is stored; the prompt's script section says so.
    private static final String NO_FILES = "(none — only the skill file is stored, so no scripts are available)";

    // A reply that does not parse is asked for once more: it is rare and does not repeat.
    static final int LLM_ATTEMPTS = 2;

    // Input fields, as stored in the skill's sample
    private String skillName;
    private String skillDescription;
    private String skillContent;
    private String agentName;
    private String filePath;
    // The skill's collection host, e.g. "<device>.ai-agent.claude"
    private String collectionName;

    // Output field: tagLabels, annotations, verdict (what is stored as skill_verdict)
    private Map<String, Object> validationResult;

    @SuppressWarnings("unchecked")
    public String validateSkillV3() {
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
        if (filePath == null) filePath = "";
        if (collectionName == null) collectionName = "";

        SkillDocument doc = new SkillDocument(skillContent);
        String prompt = buildPrompt(SkillTagValidatorV3.candidateLines(doc));

        Map<String, Object> parsed = null;
        for (int attempt = 1; parsed == null; attempt++) {
            String rawContent;
            try {
                rawContent = callLLM(prompt);
            } catch (Exception e) {
                logger.error("[SkillValidationV3] LLM call failed for skill=" + skillName + ": " + e.getMessage());
                addActionError("LLM call failed: " + e.getMessage());
                return Action.ERROR.toUpperCase();
            }
            try {
                parsed = parseLlmJson(rawContent);
            } catch (Exception e) {
                logger.error("[SkillValidationV3] failed to parse LLM response for skill=" + skillName
                        + " attempt=" + attempt + ": " + e.getMessage() + "\n" + rawContent);
                if (attempt >= LLM_ATTEMPTS) {
                    addActionError("Failed to parse LLM response: " + e.getMessage());
                    return Action.ERROR.toUpperCase();
                }
            }
        }

        Map<String, Object> malicious = parsed.get("malicious") instanceof Map
                ? (Map<String, Object>) parsed.get("malicious") : new HashMap<>();
        boolean claimedMalicious = Boolean.TRUE.equals(malicious.get("isMalicious")) && !isVendorShipped(filePath);
        double maliciousScore = numberOf(malicious.get("score"), 0.0);
        double matchScore = numberOf(parsed.get("toolNameDescriptionMatchScore"), 1.0);
        String reason = stringOf(malicious.get("reason"));
        String overview = stringOf(parsed.get("overview"));
        String remediation = stringOf(parsed.get("remediation")).trim();

        List<?> claimedTags = parsed.get("tags") instanceof List ? (List<?>) parsed.get("tags") : new ArrayList<>();
        List<Finding> findings = SkillTagValidatorV3.validate(claimedTags, claimedMalicious ? malicious : null, doc,
                matchScore);
        Finding maliciousFinding = findings.stream().filter(f -> f.criteria.isMalicious()).findFirst().orElse(null);
        boolean flagged = claimedMalicious && maliciousFinding != null;
        maliciousScore = flagged ? Math.max(maliciousScore, 0.8) : Math.min(maliciousScore, 0.2);
        String evidence = flagged ? labelledEvidence(maliciousFinding, doc) : "";
        List<Map<String, String>> owaspCategories = buildCategoryEntries(findings, doc);

        logger.infoAndAddToDb(String.format(
                "[SkillValidationV3] skill=%s collection=%s flagged=%b maliciousScore=%.2f claimedTags=%d keptTags=%d",
                skillName, collectionName, flagged, maliciousScore, claimedTags.size(), findings.size()), LogDb.DB_ABS);

        try {
            String evidenceText = evidence.isEmpty() ? reason : reason + "\n\n" + evidence;
            if (!skillDescription.isEmpty()) {
                evidenceText = "Description: " + skillDescription + "\n\n" + evidenceText;
            }
            DbLayer.updateMcpAuditInfo("AGENT_SKILL", skillName, agentName,
                    new ComponentRiskAnalysis(matchScore < 0.7, flagged, evidenceText, owaspCategories));
        } catch (Exception e) {
            logger.error("[SkillValidationV3] failed to update audit DB for skill=" + skillName + ": " + e.getMessage());
        }

        if (flagged && !collectionName.isEmpty()) {
            List<String> threatCategoryIds = findings.stream()
                    .filter(f -> !f.purposeAligned)
                    .map(f -> f.criteria.getCategory().getId())
                    .distinct()
                    .collect(Collectors.toList());
            SkillThreatReporterV3.reportThreat(
                    new SkillThreatReporterV3.SkillRef(skillName, skillDescription, skillContent, agentName,
                            collectionName, requestAuthToken()),
                    buildThreat(maliciousFinding, maliciousScore, evidence, overview, remediation, owaspCategories,
                            threatCategoryIds));
        }

        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("isMalicious", flagged);
        verdict.put("score", maliciousScore);
        verdict.put("criterion", flagged ? maliciousFinding.criteria.getId() : "");
        verdict.put("label", flagged ? maliciousFinding.criteria.getLabel() : "");
        verdict.put("reason", reason);
        verdict.put("evidence", flagged ? evidenceEntries(maliciousFinding, doc) : new ArrayList<>());

        validationResult = new HashMap<>();
        validationResult.put("tagLabels", tagLabels(findings));
        validationResult.put("annotations", buildAnnotations(findings, doc));
        validationResult.put("verdict", verdict);
        return Action.SUCCESS.toUpperCase();
    }

    private String buildPrompt(List<String> candidateLines) {
        String candidates = candidateLines.isEmpty()
                ? "(none — no line carries a criterion's signal; add a tag only if a line clearly meets one)"
                : String.join("\n", candidateLines)
                        + (candidateLines.size() >= SkillTagValidatorV3.MAX_CANDIDATE_LINES ? "\n(list truncated)" : "");
        return String.format(SKILL_VALIDATION_PROMPT, skillName, filePath, skillDescription, skillContent,
                NO_FILES, SkillTagCriteria.promptTable(), candidates, "[]");
    }

    /** Evidence quotes, each prefixed with its line: "L12: ...". */
    static String labelledEvidence(Finding finding, SkillDocument doc) {
        return finding.spans.stream()
                .map(s -> doc.locationOf(s).label() + ": " + s.text)
                .collect(Collectors.joining("\n"));
    }

    /** Evidence quotes with their file and 1-based line. */
    static List<Map<String, Object>> evidenceEntries(Finding finding, SkillDocument doc) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Span span : finding.spans) {
            Location location = doc.locationOf(span);
            Map<String, Object> quote = new LinkedHashMap<>();
            quote.put("file", location.file);
            quote.put("line", location.startLine + 1);
            quote.put("text", span.text);
            entries.add(quote);
        }
        return entries;
    }

    /**
     * Where each piece of evidence sits, so the dashboard can highlight it in the stored skill content
     * and say which tag it backs and why. One entry per quoted line; one line never backs two tags, so
     * entries never overlap.
     *
     *   file                 "SKILL.md"
     *   line, endLine        1-based lines
     *   startColumn, endColumn  0-based, end exclusive, in UTF-16 units (JavaScript string indices)
     *   start, end           offsets into skill_content
     *   malicious            true for the lines behind the malicious verdict
     */
    static List<Map<String, Object>> buildAnnotations(List<Finding> findings, SkillDocument doc) {
        List<Map<String, Object>> annotations = new ArrayList<>();
        for (Finding finding : findings) {
            OwaspAstCategory category = finding.criteria.getCategory();
            for (Span span : finding.spans) {
                Location location = doc.locationOf(span);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("file", location.file);
                entry.put("line", location.startLine + 1);
                entry.put("endLine", location.endLine + 1);
                entry.put("startColumn", location.startColumn);
                entry.put("endColumn", location.endColumn);
                entry.put("start", span.start);
                entry.put("end", span.end);
                entry.put("text", span.text);
                entry.put("tag", finding.criteria.getLabel());
                entry.put("criterion", finding.criteria.getId());
                entry.put("criterionDescription", finding.criteria.getDescription());
                entry.put("category", category.getId());
                entry.put("categoryName", category.getDisplayName());
                entry.put("severity", category.getSeverity());
                entry.put("reason", finding.reason);
                entry.put("purposeAligned", finding.purposeAligned);
                entry.put("malicious", finding.criteria.isMalicious());
                annotations.add(entry);
            }
        }
        annotations.sort(Comparator
                .comparing((Map<String, Object> a) -> (Integer) a.get("line"))
                .thenComparing(a -> (Integer) a.get("startColumn")));
        return annotations;
    }

    /** Distinct tag labels in precedence order — what the stored endpoint carries as skill-tags. */
    static List<String> tagLabels(List<Finding> findings) {
        return findings.stream().map(f -> f.criteria.getLabel()).distinct().collect(Collectors.toList());
    }

    static SkillThreatReporterV3.Threat buildThreat(Finding maliciousFinding, double maliciousScore, String evidence,
            String overview, String remediation, List<Map<String, String>> owaspCategories,
            List<String> threatCategoryIds) {
        List<Map<String, String>> threatCategories = owaspCategories.stream()
                .filter(c -> threatCategoryIds.contains(c.get("id")))
                .collect(Collectors.toList());
        // As the agent does: the highest severity among the threat's OWASP categories, else HIGH.
        String severity = threatCategories.stream()
                .map(c -> c.get("severity"))
                .filter(s -> SEVERITY_ORDER.indexOf(s) >= 0)
                .max((a, b) -> SEVERITY_ORDER.indexOf(a) - SEVERITY_ORDER.indexOf(b))
                .orElse("HIGH");
        return new SkillThreatReporterV3.Threat(
                maliciousFinding.criteria.getLabel(),
                severity,
                maliciousScore,
                maliciousFinding.reason,
                evidence,
                overview,
                remediation,
                threatCategories);
    }

    /**
     * One entry per OWASP category, flattened to strings so it fits ComponentRiskAnalysis. Evidence
     * lines are prefixed with their line in the skill file, e.g. "L12".
     */
    static List<Map<String, String>> buildCategoryEntries(List<Finding> findings, SkillDocument doc) {
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
            entry.put("evidence", group.stream().map(f -> labelledEvidence(f, doc))
                    .collect(Collectors.joining("\n")));
            entries.add(entry);
        }
        return entries;
    }

    static boolean isVendorShipped(String filePath) {
        String path = filePath == null ? "" : filePath.replace('\\', '/');
        return VENDOR_SKILL_PATHS.stream().anyMatch(path::contains);
    }

    @SuppressWarnings("unchecked")
    private static String callLLM(String prompt) throws Exception {
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
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) throw new RuntimeException("No message in LLM response");
        Object content = message.get("content");
        if (content == null) throw new RuntimeException("No content in LLM message");
        return content.toString();
    }

    /**
     * The model's JSON object. Providers without JSON mode sometimes leave a trailing comma after the
     * last field or array entry; that is the one defect repaired before giving up.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseLlmJson(String raw) {
        String json = extractJson(raw);
        Object parsed;
        try {
            parsed = readJson(json);
        } catch (Exception e) {
            parsed = readJson(stripTrailingCommas(json));
        }
        if (!(parsed instanceof Map)) throw new IllegalStateException("empty LLM response");
        return (Map<String, Object>) parsed;
    }

    /**
     * Reads JSON into maps and lists, except that a key the model wrote twice is accepted: two lists under
     * the same key (the model splitting "tags" in two) are concatenated so no entry is lost; otherwise the
     * later value wins.
     */
    private static Object readJson(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        try {
            return readValue(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object readValue(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                Map<String, Object> object = new LinkedHashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    Object value = readValue(reader);
                    Object previous = object.get(key);
                    if (previous instanceof List && value instanceof List) {
                        ((List<Object>) previous).addAll((List<Object>) value);
                    } else {
                        object.put(key, value);
                    }
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                List<Object> array = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader));
                reader.endArray();
                return array;
            case STRING:
                return reader.nextString();
            case NUMBER:
                return reader.nextDouble();
            case BOOLEAN:
                return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return null;
            default:
                throw new IllegalStateException("unexpected JSON token " + reader.peek());
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

    private static String requestAuthToken() {
        try {
            HttpServletRequest request = ServletActionContext.getRequest();
            String token = request == null ? null : request.getHeader("Authorization");
            return token == null ? "" : token.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static double numberOf(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
