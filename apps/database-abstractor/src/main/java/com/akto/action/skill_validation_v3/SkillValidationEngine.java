package com.akto.action.skill_validation_v3;

import com.akto.action.LLMService;
import com.akto.action.SkillTagCriteria;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.Location;
import com.akto.action.skill_validation_v3.SkillEvidenceV3.Part;
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
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one skill validation both callers share:
 *   - api/validateSkillV3, the dashboard's revalidation job, which has only the stored skill file;
 *   - api/validateAndReportSkillV2, the endpoint-shield agent, which also sends the scripts the skill
 *     runs (referencedFiles).
 * With no scripts the two are identical. With scripts, each one is its own file in the document: its
 * evidence is located in that file and a quote never runs from one file into the next.
 *
 * The model returns tags (risks) and a separate malicious verdict, both machine-checked against the
 * text, plus a risk score that is moved into the band the verified tags and verdict fall in.
 */
public final class SkillValidationEngine {

    private static final LoggerMaker logger = new LoggerMaker(SkillValidationEngine.class, LogDb.DB_ABS);

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
        "  TAGS — risks. Each tag states one thing the skill does that can hurt the user, their machine or\n" +
        "    their data if it goes wrong or is abused (\"runs sudo\", \"uploads the repo to a paste site\",\n" +
        "    \"adds a line to ~/.zshrc\"), proven by exact lines. A tag is NOT an inventory of what the skill\n" +
        "    touches: the routine steps a skill needs to do its job are never tags. Most skills — and nearly\n" +
        "    every built-in one — have NO tags, and an empty tags array is the normal answer.\n" +
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
        "redacted as [REDACTED]; everything in them executes when the skill runs them). Each file sits\n" +
        "between its own FILE and END markers; \"<path> L4\" is line 4 of that file. Quote a file's lines\n" +
        "only from that file. A file marked \"content not sent\" was not collected: never guess what it does\n" +
        "from its name or path — it cannot back a tag or the verdict:\n" +
        "%s\n" +
        "\n" +
        "==================== STEP 1 — UNDERSTAND THE SKILL ====================\n" +
        "\n" +
        "Before looking for anything, read the name, the description, the WHOLE content and every file, and\n" +
        "work out:\n" +
        "  - what the skill is for (skillPurpose, one sentence), and what a user who installs it expects;\n" +
        "  - who ships it. FILE PATH tells you: skills under .cursor/skills-cursor, .codex/skills/.system,\n" +
        "    .codex/vendor_imports/skills/skills/.curated, .gemini/antigravity/builtin,\n" +
        "    a claude-plugins-official plugin, or an openai-bundled / openai-primary-runtime plugin are\n" +
        "    built-in vendor skills (openai-curated holds third-party plugins). They are never malicious, and\n" +
        "    they get a tag only for a line whose risk their purpose does NOT explain;\n" +
        "  - which parts of the skill file are LIVE INSTRUCTIONS the agent will follow, and which are only\n" +
        "    documentation, examples for the user, explanations, warnings, or descriptions of what an\n" +
        "    attacker might do. Only live instructions can produce a tag. Code in the FILES THIS SKILL RUNS\n" +
        "    is live when the skill runs that file; its comments are not.\n" +
        "Every later decision is judged against this understanding.\n" +
        "\n" +
        "==================== STEP 2 — TAGS (risks) ====================\n" +
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
        "NEVER A TAG — drop these even when a candidate line matches a criterion's pattern:\n" +
        "  - installing or running the tool the skill is about from its official registry, even unpinned\n" +
        "    (npm install -D wrangler@latest in a Wrangler skill, npx some-cli@latest in that CLI's skill);\n" +
        "  - the agent's own directories: where skills, plugins or agent config live (~/.codex, $CODEX_HOME,\n" +
        "    ~/.claude, ~/.cursor, ~/.agents and their skills/ folders), and documentation saying so;\n" +
        "  - an API token or key for the service the skill integrates, read from the env var or config the\n" +
        "    user set up for it (export SENTRY_AUTH_TOKEN in a Sentry skill);\n" +
        "  - the tool's own cache or config under ~ (~/.cache/<tool>, ~/.config/<tool>, ~/.npm);\n" +
        "  - setup or usage documentation, examples and placeholders, even when written as commands;\n" +
        "  - shell, git, build, test, lint and package-manager commands, and file reads/writes inside the\n" +
        "    project;\n" +
        "  - calls to the skill's own documented API, first-party hosts, registries and public docs.\n" +
        "  - a line that only names or reads a file, setting or permission that \"may contain\" secrets or\n" +
        "    \"could\" grant too much \"if misconfigured\" or \"if mishandled\": the line itself has to do the\n" +
        "    risky thing (dump the secrets, send them, grant the wildcard), not leave room for it;\n" +
        "  - running sudo in a built-in vendor skill (installing the packages it needs).\n" +
        "When in doubt, it is not a tag. A false tag on a legitimate skill is a worse mistake than a\n" +
        "missing one.\n" +
        "\n" +
        "How to choose the tag for a line — read what the line DOES, then pick the criterion whose\n" +
        "description says exactly that, so the tag explains the behaviour to someone who never opens the file:\n" +
        "  - Running sudo/su is AST06.c1. Writing, deleting or overwriting the user's files under ~ / $HOME\n" +
        "    outside the agent's and the tool's own directories, or reading other users' files, is AST03.c3.\n" +
        "    Editing /etc, /proc, chmod 777 or setuid is AST06.c3. cron/launchd/shell rc/git hooks\n" +
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
        "  reason — one concrete sentence on the RISK: what these lines do, naming the actual command, path,\n" +
        "    host or data, and what can go wrong because of it — what is exposed, changed, deleted or run,\n" +
        "    and for whom. Never justify the line: no \"necessary\", \"required\", \"standard\", \"expected\",\n" +
        "    \"default\" or \"aligned with its purpose\". If the only honest reason is that the skill needs\n" +
        "    the line to work, it is not a tag — drop it. It is shown next to the highlighted lines, so it\n" +
        "    must make sense to someone reading just those lines.\n" +
        "  purposeAligned — true when the purpose explains the behaviour and it is still a real risk (an\n" +
        "    install skill running sudo, a deploy skill uploading the build to its own host); false when it\n" +
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
        "riskScore (0.0-5.0): how much harm this skill can do to the user, judged ONLY from the tags you keep\n" +
        "  and the malicious verdict — never from how powerful the skill sounds:\n" +
        "    4.0-5.0  malicious. 5.0 for a clear, working payload (data or credentials really sent out, a\n" +
        "             safety override, persistence that calls out); 4.0 when the payload is partial.\n" +
        "    2.0-3.9  not malicious, with at least one tag on a HIGH or CRITICAL category (credentials, root,\n" +
        "             system files, the user's files, data sent out) whose risk the purpose does NOT explain;\n" +
        "             higher the more such tags there are and the more damage they can do.\n" +
        "    1.0-1.9  not malicious, and every tag is explained by the purpose or is MEDIUM severity.\n" +
        "    0.0-0.9  no tags.\n" +
        "  It is checked against the verified tags and verdict, and moved into the band they fall in.\n" +
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
        "  \"riskScore\": 0.0,\n" +
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
            "/.codex/vendor_imports/skills/skills/.curated/",
            "/.gemini/antigravity/builtin/",
            "/plugins/marketplaces/claude-plugins-official/",
            "/plugins/cache/claude-plugins-official/",
            "/.codex/plugins/cache/openai-bundled/",
            "/.codex/plugins/cache/openai-primary-runtime/",
            "/.codex/.tmp/bundled-marketplaces/openai-bundled/");

    // A reply that does not parse is asked for once more: it is rare and does not repeat.
    static final int LLM_ATTEMPTS = 2;

    // Script text shown to the model, on top of the skill file. The agent may send up to 256 KiB of
    // scripts; past this the rest are listed as not sent rather than cut mid-file.
    static final int MAX_FILES_CHARS = 120_000;
    static final int MAX_FILES = 25;

    // Risk score bands, on the 0-5 scale of an endpoint's risk and threat score.
    static final double RISK_MAX = 5.0;
    static final double MALICIOUS_RISK_FLOOR = 4.0;
    static final double UNEXPLAINED_RISK_FLOOR = 2.0;
    static final double TAGGED_RISK_FLOOR = 1.0;
    private static final double BAND_GAP = 0.1;

    private SkillValidationEngine() {}

    /** What one validation is about. */
    public static final class Request {
        final String skillName;
        final String skillDescription;
        final String skillContent;
        final String filePath;
        final List<SkillReferencedFile> referencedFiles;
        final String noFilesNote;
        final String localAnalysis;
        final String logTag;

        /**
         * @param filePath        already normalized (see {@link #normalizePath})
         * @param referencedFiles the scripts the skill runs; null or empty when there are none
         * @param noFilesNote     what the prompt says when there are no scripts
         * @param localAnalysis   the agent's static findings as JSON; null or empty for none
         */
        public Request(String skillName, String skillDescription, String skillContent, String filePath,
                List<SkillReferencedFile> referencedFiles, String noFilesNote, String localAnalysis, String logTag) {
            this.skillName = skillName;
            this.skillDescription = skillDescription == null ? "" : skillDescription;
            this.skillContent = skillContent;
            this.filePath = filePath == null ? "" : filePath;
            this.referencedFiles = referencedFiles == null ? Collections.emptyList() : referencedFiles;
            this.noFilesNote = noFilesNote;
            this.localAnalysis = localAnalysis == null || localAnalysis.isEmpty() ? "[]" : localAnalysis;
            this.logTag = logTag;
        }
    }

    /** The LLM call failed or its reply could not be read; nothing was decided. */
    public static final class ValidationException extends Exception {
        ValidationException(String message) {
            super(message);
        }
    }

    /** The skill's document, the files shown to the model, and the prompt. */
    static final class Prepared {
        final Request request;
        final SkillDocument doc;
        final List<String> candidateLines;
        final String filesSection;
        final String prompt;

        Prepared(Request request, SkillDocument doc, List<String> candidateLines, String filesSection, String prompt) {
            this.request = request;
            this.doc = doc;
            this.candidateLines = candidateLines;
            this.filesSection = filesSection;
            this.prompt = prompt;
        }
    }

    public static Result validate(Request request) throws ValidationException {
        Prepared prepared = prepare(request);
        Map<String, Object> parsed = null;
        for (int attempt = 1; parsed == null; attempt++) {
            String rawContent;
            try {
                rawContent = callLLM(prepared.prompt);
            } catch (Exception e) {
                logger.error(request.logTag + " LLM call failed for skill=" + request.skillName + ": " + e.getMessage());
                throw new ValidationException("LLM call failed: " + e.getMessage());
            }
            try {
                parsed = requireAssessment(parseLlmJson(rawContent));
            } catch (Exception e) {
                logger.error(request.logTag + " failed to parse LLM response for skill=" + request.skillName
                        + " attempt=" + attempt + ": " + e.getMessage() + "\n" + rawContent);
                if (attempt >= LLM_ATTEMPTS) {
                    throw new ValidationException("Failed to parse LLM response: " + e.getMessage());
                }
            }
        }
        Result result = evaluate(prepared, parsed);
        logger.infoAndAddToDb(String.format(
                "%s skill=%s files=%d flagged=%b maliciousScore=%.2f riskScore=%.1f keptTags=%s",
                request.logTag, request.skillName, prepared.doc.partCount() - 1, result.flagged, result.maliciousScore,
                result.riskScore, result.findings.stream().map(f -> f.criteria.getId() + "(" + f.spans.size() + ")")
                        .collect(Collectors.joining(","))), LogDb.DB_ABS);
        return result;
    }

    /** The document (skill file first, then each script with content) and the prompt built from it. */
    static Prepared prepare(Request request) {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(SkillEvidenceV3.SKILL_FILE, request.skillContent, false));
        StringBuilder shown = new StringBuilder();
        List<String> notSent = new ArrayList<>();
        Set<String> names = new HashSet<>();
        names.add(SkillEvidenceV3.SKILL_FILE);
        int budget = MAX_FILES_CHARS;

        int index = 0;
        for (SkillReferencedFile file : request.referencedFiles) {
            if (file == null) continue;
            index++;
            String name = partName(file.getPath(), index, names);
            String via = file.getVia() == null || file.getVia().trim().isEmpty() ? "" : " (run from " + file.getVia().trim() + ")";
            String content = file.getContent() == null ? "" : file.getContent();
            if (content.trim().isEmpty()) {
                String why = file.getOmitted() == null || file.getOmitted().trim().isEmpty()
                        ? "not collected" : file.getOmitted().trim();
                notSent.add("----- FILE " + name + via + ": content not sent — " + why + " -----");
            } else if (parts.size() - 1 >= MAX_FILES) {
                notSent.add("----- FILE " + name + via + ": content not sent — too many files -----");
            } else if (content.length() > budget) {
                notSent.add("----- FILE " + name + via + ": content not sent — over the validation size budget -----");
            } else {
                budget -= content.length();
                parts.add(new Part(name, content, !isMarkdown(name)));
                shown.append("----- FILE ").append(name).append(via)
                        .append(file.isTruncated() ? " (cut short by the collector; only its start is shown)" : "")
                        .append(" -----\n")
                        .append(content).append(content.endsWith("\n") ? "" : "\n")
                        .append("----- END ").append(name).append(" -----\n");
            }
        }

        String filesSection;
        if (shown.length() == 0 && notSent.isEmpty()) {
            filesSection = request.noFilesNote;
        } else {
            filesSection = (shown.toString() + String.join("\n", notSent)).trim();
        }

        SkillDocument doc = new SkillDocument(parts);
        List<String> candidateLines = SkillTagValidatorV3.candidateLines(doc);
        String candidates = candidateLines.isEmpty()
                ? "(none — no line carries a criterion's signal; add a tag only if a line clearly meets one)"
                : String.join("\n", candidateLines)
                        + (candidateLines.size() >= SkillTagValidatorV3.MAX_CANDIDATE_LINES ? "\n(list truncated)" : "");
        String prompt = String.format(SKILL_VALIDATION_PROMPT, request.skillName, request.filePath,
                request.skillDescription, request.skillContent, filesSection, SkillTagCriteria.promptTable(),
                candidates, request.localAnalysis);
        return new Prepared(request, doc, candidateLines, filesSection, prompt);
    }

    /** The model's reply checked against the document: verified tags, verdict and risk score. */
    @SuppressWarnings("unchecked")
    static Result evaluate(Prepared prepared, Map<String, Object> parsed) {
        Request request = prepared.request;
        SkillDocument doc = prepared.doc;
        Map<String, Object> malicious = parsed.get("malicious") instanceof Map
                ? (Map<String, Object>) parsed.get("malicious") : new HashMap<>();
        boolean vendorShipped = isVendorShipped(request.filePath);
        boolean claimedMalicious = Boolean.TRUE.equals(malicious.get("isMalicious")) && !vendorShipped;
        double maliciousScore = numberOf(malicious.get("score"), 0.0);
        double matchScore = numberOf(parsed.get("toolNameDescriptionMatchScore"), 1.0);

        List<?> claimedTags = parsed.get("tags") instanceof List ? (List<?>) parsed.get("tags") : new ArrayList<>();
        List<Finding> findings = SkillTagValidatorV3.validate(claimedTags, claimedMalicious ? malicious : null, doc,
                matchScore, vendorShipped);
        Finding maliciousFinding = findings.stream().filter(f -> f.criteria.isMalicious()).findFirst().orElse(null);
        boolean flagged = claimedMalicious && maliciousFinding != null;
        maliciousScore = flagged ? Math.max(maliciousScore, 0.8) : Math.min(maliciousScore, 0.2);
        Double claimedRisk = parsed.get("riskScore") instanceof Number ? ((Number) parsed.get("riskScore")).doubleValue() : null;

        return new Result(request, doc, findings, flagged ? maliciousFinding : null, maliciousScore, matchScore,
                riskScore(claimedRisk, findings, flagged, maliciousScore),
                stringOf(malicious.get("reason")), stringOf(parsed.get("overview")),
                stringOf(parsed.get("remediation")).trim());
    }

    /**
     * The model's risk score moved into the band the verified result falls in, so it can never
     * contradict the tags or the verdict:
     *   malicious                                              4.0-5.0
     *   a HIGH/CRITICAL tag the purpose does not explain       2.0-3.9
     *   other tags                                             1.0-1.9
     *   no tags                                                0.0-0.9
     * Without a score from the model, the band's floor is used (for a malicious skill, its verdict score
     * scaled to 0-5).
     */
    static double riskScore(Double claimed, List<Finding> findings, boolean flagged, double maliciousScore) {
        double floor;
        double ceiling;
        double fallback;
        if (flagged) {
            floor = MALICIOUS_RISK_FLOOR;
            ceiling = RISK_MAX;
            fallback = maliciousScore * RISK_MAX;
        } else if (findings.stream().anyMatch(SkillValidationEngine::unexplainedSevere)) {
            floor = UNEXPLAINED_RISK_FLOOR;
            ceiling = MALICIOUS_RISK_FLOOR - BAND_GAP;
            fallback = floor;
        } else if (!findings.isEmpty()) {
            floor = TAGGED_RISK_FLOOR;
            ceiling = UNEXPLAINED_RISK_FLOOR - BAND_GAP;
            fallback = floor;
        } else {
            floor = 0.0;
            ceiling = TAGGED_RISK_FLOOR - BAND_GAP;
            fallback = floor;
        }
        double score = claimed == null || claimed.isNaN() ? fallback : claimed;
        score = Math.max(floor, Math.min(ceiling, score));
        return Math.round(score * 10) / 10.0;
    }

    private static boolean unexplainedSevere(Finding finding) {
        String severity = finding.criteria.getCategory().getSeverity();
        return !finding.purposeAligned && SEVERITY_ORDER.indexOf(severity) >= SEVERITY_ORDER.indexOf("HIGH");
    }

    /** The outcome of one validation, and every view of it the callers send on. */
    public static final class Result {
        final Request request;
        final SkillDocument doc;
        public final List<Finding> findings;
        // the AST01 finding behind the verdict; null when the skill is not malicious
        final Finding maliciousFinding;
        public final boolean flagged;
        public final double maliciousScore;
        public final double matchScore;
        public final double riskScore;
        final String reason;
        final String overview;
        final String remediation;
        final String evidence;
        final List<Map<String, String>> owaspCategories;

        Result(Request request, SkillDocument doc, List<Finding> findings, Finding maliciousFinding,
                double maliciousScore, double matchScore, double riskScore, String reason, String overview,
                String remediation) {
            this.request = request;
            this.doc = doc;
            this.findings = findings;
            this.maliciousFinding = maliciousFinding;
            this.flagged = maliciousFinding != null;
            this.maliciousScore = maliciousScore;
            this.matchScore = matchScore;
            this.riskScore = riskScore;
            this.reason = reason;
            this.overview = overview;
            this.remediation = remediation;
            this.evidence = flagged ? labelledEvidence(maliciousFinding, doc) : "";
            this.owaspCategories = buildCategoryEntries(findings, doc);
        }

        /** Distinct tag labels in precedence order — what the endpoint carries as skill-tags. */
        public List<String> tagLabels() {
            return findings.stream().map(f -> f.criteria.getLabel()).distinct().collect(Collectors.toList());
        }

        public List<Map<String, Object>> annotations() {
            return buildAnnotations(findings, doc);
        }

        /** What is stored as skill_verdict. */
        public Map<String, Object> verdict() {
            Map<String, Object> verdict = new LinkedHashMap<>();
            verdict.put("isMalicious", flagged);
            verdict.put("score", maliciousScore);
            verdict.put("riskScore", riskScore);
            verdict.put("criterion", flagged ? maliciousFinding.criteria.getId() : "");
            verdict.put("label", flagged ? maliciousFinding.criteria.getLabel() : "");
            verdict.put("reason", reason);
            verdict.put("evidence", flagged ? evidenceEntries(maliciousFinding, doc) : new ArrayList<>());
            return verdict;
        }

        /** The guardrail activity for a malicious skill; null when it is not malicious. */
        public SkillThreatReporterV3.Threat threat() {
            if (!flagged) return null;
            List<String> threatCategoryIds = findings.stream()
                    .filter(f -> !f.purposeAligned)
                    .map(f -> f.criteria.getCategory().getId())
                    .distinct()
                    .collect(Collectors.toList());
            return buildThreat(maliciousFinding, riskScore, evidence, overview, remediation, owaspCategories,
                    threatCategoryIds);
        }

        /** The skill's audit info: privileged when the description hides what it does, and the verdict. */
        public void recordAudit(String agentName) {
            try {
                String evidenceText = evidence.isEmpty() ? reason : reason + "\n\n" + evidence;
                if (!request.skillDescription.isEmpty()) {
                    evidenceText = "Description: " + request.skillDescription + "\n\n" + evidenceText;
                }
                DbLayer.updateMcpAuditInfo("AGENT_SKILL", request.skillName, agentName,
                        new ComponentRiskAnalysis(matchScore < 0.7, flagged, evidenceText, owaspCategories));
            } catch (Exception e) {
                logger.error(request.logTag + " failed to update audit DB for skill=" + request.skillName + ": " + e.getMessage());
            }
        }
    }

    /** Evidence quotes, each prefixed with its file and line: "L12: ..." or "scripts/run.sh L4: ...". */
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
     * entries never overlap. Skill file first, then each script in the order it was sent.
     *
     *   file                 "SKILL.md", or the script's path
     *   line, endLine        1-based lines in that file
     *   startColumn, endColumn  0-based, end exclusive, in UTF-16 units (JavaScript string indices)
     *   start, end           offsets into skill_content; SKILL.md only, since scripts are not ingested
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
                if (location.inSkillFile()) {
                    // the skill file is the document's first part, so its offsets are skill_content's
                    entry.put("start", span.start);
                    entry.put("end", span.end);
                }
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
                entry.put("_part", doc.partOfLine(span.firstLine));
                annotations.add(entry);
            }
        }
        annotations.sort(Comparator
                .comparing((Map<String, Object> a) -> (Integer) a.get("_part"))
                .thenComparing(a -> (Integer) a.get("line"))
                .thenComparing(a -> (Integer) a.get("startColumn")));
        annotations.forEach(a -> a.remove("_part"));
        return annotations;
    }

    /**
     * riskScore is the 0-5 score returned to the revalidation job and the agent (and saved on the skill),
     * so the guardrail activity carries the same one, never the 0-1 malicious score.
     */
    static SkillThreatReporterV3.Threat buildThreat(Finding maliciousFinding, double riskScore, String evidence,
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
                riskScore,
                maliciousFinding.reason,
                evidence,
                overview,
                remediation,
                threatCategories);
    }

    /**
     * One entry per OWASP category, flattened to strings so it fits ComponentRiskAnalysis. Evidence
     * lines are prefixed with their file and line, e.g. "L12" or "scripts/run.sh L4".
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

    /** A script's name in the document: its path, made unique and kept apart from the skill file. */
    private static String partName(String path, int index, Set<String> taken) {
        String name = path == null ? "" : path.trim().replace('\\', '/');
        if (name.isEmpty()) name = "file-" + index;
        if (name.equals(SkillEvidenceV3.SKILL_FILE)) name = "./" + name;
        String unique = name;
        for (int n = 2; !taken.add(unique); n++) unique = name + " (" + n + ")";
        return unique;
    }

    private static boolean isMarkdown(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdx");
    }

    /** The path with "." and ".." segments resolved: ~/.cursor/skills-cursor/../../evil is ~/evil. */
    public static String normalizePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";
        String path = filePath.replace('\\', '/');
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/", -1)) {
            if (segment.equals(".")) continue;
            if (segment.equals("..")) {
                if (!segments.isEmpty() && !segments.peekLast().isEmpty()) segments.pollLast();
                continue;
            }
            segments.addLast(segment);
        }
        return String.join("/", segments);
    }

    /**
     * The reply as an assessment, or an error when it is not one: an empty or partial reply ({} or one
     * without the verdict) must not pass for "no tags, not malicious" and replace a skill's findings.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> requireAssessment(Map<String, Object> parsed) {
        Object malicious = parsed.get("malicious");
        if (!(parsed.get("tags") instanceof List) || !(malicious instanceof Map)
                || !(((Map<String, Object>) malicious).get("isMalicious") instanceof Boolean)) {
            throw new IllegalStateException("incomplete assessment: tags and malicious.isMalicious are required");
        }
        return parsed;
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

    private static double numberOf(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
