package com.akto.action;

import com.akto.dto.OwaspAstCategory;
import java.util.regex.Pattern;

/**
 * The only reasons a skill can carry an OWASP Agentic-Skills tag.
 *
 * A finding names exactly one criterion; the tag is derived from it, never stated separately. The
 * criterion's anchor is a deterministic pattern the quoted evidence must match, so a tag needs both
 * the model's judgement and a literal signal in the quoted line. Enum order is precedence: when two
 * findings quote the same line, the one whose criterion is declared first keeps the line.
 *
 * AST09 (governance) and AST10 (cross-platform reuse) are absent on purpose: they depend on
 * provenance and registry metadata the skill text does not carry, so tagging them from content would
 * mark nearly every skill.
 */
public enum SkillTagCriteria {

    // AST01 is the malicious verdict. Its anchors only prove the shape of the statement; the
    // validator additionally requires an untrusted host for the network-bound criteria.
    AST01_EXFILTRATION(OwaspAstCategory.AST01, "c1", "exfiltration",
            "A live directive sends local data (file contents, command output, env vars, source code) to an untrusted remote host.",
            SkillTagCriteria.SEND_PATTERN, null, true, true),
    AST01_CREDENTIAL_THEFT(OwaspAstCategory.AST01, "c2", "credential-theft",
            "Reads credentials or secrets the stated purpose never needs and sends them to an untrusted host.",
            SkillTagCriteria.CREDENTIAL_PATTERN, SkillTagCriteria.SEND_PATTERN, true, true),
    AST01_SAFETY_OVERRIDE(OwaspAstCategory.AST01, "c3", "injection",
            "Tells the agent to ignore, override or bypass its system prompt, safety rules or approvals.",
            "(ignore|disregard|forget|override)\\s+(all\\s+|any\\s+|the\\s+|your\\s+)?(previous|prior|above|earlier|system|safety|original)\\s+(instructions|rules|prompt|guidelines|constraints)"
                    + "|you\\s+are\\s+now\\s+(?!ready|able|in\\b)"
                    + "|(bypass|disable|circumvent|turn\\s+off)\\s+(the\\s+|all\\s+|any\\s+)?(safety|guardrails?|restrictions?|content\\s+polic(y|ies)|approvals?|permission\\s+prompts?)"
                    + "|jailbreak|developer\\s+mode\\s+enabled|do\\s+anything\\s+now",
            null, true, false),
    AST01_DECEPTION(OwaspAstCategory.AST01, "c4", "injection",
            "Tells the agent to hide its actions from, lie to, or mislead the user.",
            "(do\\s+not|don'?t|never)\\s+(tell|inform|mention|reveal|disclose|show|notify|alert|let)\\s+(this\\s+to\\s+)?(the\\s+)?user"
                    + "|without\\s+(the\\s+)?user'?s?\\s+(knowledge|knowing|noticing)"
                    + "|hide\\s+(this|it|these|the\\s+\\w+)\\s+from\\s+(the\\s+)?user"
                    + "|\\b(secretly|covertly|stealthily)\\b|lie\\s+to\\s+(the\\s+)?user|pretend\\s+(that\\s+)?(you|the|it)",
            null, true, false),
    AST01_BEACONING_PERSISTENCE(OwaspAstCategory.AST01, "c5", "persistence",
            "Plants persistence (cron, service, shell rc, git hook) whose payload calls out to an untrusted host.",
            SkillTagCriteria.PERSISTENCE_PATTERN, null, true, true),

    AST08_ENCODED_EXECUTION(OwaspAstCategory.AST08, "c1", "obfuscation",
            "An encoded payload (base64/hex/escape sequences) that is decoded and then executed.",
            "base64\\s+(-d|--decode|-D)|atob\\s*\\(|b64decode|frombase64string|buffer\\.from\\([^)]*base64|fromhex|xxd\\s+-r|(\\\\x[0-9a-f]{2}){4,}",
            "\\|\\s*(ba|z|da)?sh\\b|\\beval\\b|\\bexec\\b|\\biex\\b|invoke-expression|python3?\\s+-c|node\\s+-e|bash\\s+-c|\\bsh\\s+-c",
            false, false),
    AST08_INVISIBLE_UNICODE(OwaspAstCategory.AST08, "c2", "obfuscation",
            "Zero-width, bidi-override or tag characters hiding text from human review (detected by the scanner, not the model).",
            "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]|\\uDB40[\\uDC00-\\uDC7F]",
            null, false, false),
    AST08_DETECTION_EVASION(OwaspAstCategory.AST08, "c3", "obfuscation",
            "Instructs evading scanners or erasing logs, shell history or audit trails.",
            "(avoid|evade|bypass|disable|clear|delete|wipe|erase|suppress)\\s+(the\\s+|any\\s+|all\\s+)?(detection|scanners?|scanning|logging|logs|audit|history|antivirus|edr|monitoring|telemetry)"
                    + "|history\\s+-c|unset\\s+histfile|histfile=/dev/null|rm\\s+[^\\n]*_history",
            null, false, false),
    AST08_HIDDEN_COMMENT(OwaspAstCategory.AST08, "c4", "obfuscation",
            "Agent instructions placed inside an HTML comment so they do not render for a human reader.",
            "<!--", null, false, false),

    AST05_UNSAFE_DESERIALIZATION(OwaspAstCategory.AST05, "c1", "system-tampering",
            "Live directive runs eval/exec/unsafe deserialization on config, memory, fetched or user-supplied data.",
            "\\beval\\s*\\(|\\bexec\\s*\\(|pickle\\.loads?\\s*\\(|cpickle|yaml\\.load\\s*\\((?![^)]*safeloader)|yaml\\.unsafe_load|!!python/|new\\s+function\\s*\\(|marshal\\.loads|jsonpickle\\.decode|objectinputstream|\\bunserialize\\s*\\(",
            null, false, false),

    AST02_PIPE_TO_SHELL(OwaspAstCategory.AST02, "c1", "system-tampering",
            "Remote code is piped straight into an interpreter (curl ... | sh, iwr ... | iex).",
            "(curl|wget|invoke-webrequest|\\biwr\\b|invoke-restmethod|\\birm\\b)\\b[^\\n]*\\|\\s*(sudo\\s+)?((ba|z|da)?sh|python3?|node|perl|ruby|iex|invoke-expression)\\b",
            null, false, false),
    AST02_REMOTE_EXECUTABLE(OwaspAstCategory.AST02, "c2", "system-tampering",
            "Downloads an executable or script from a non-registry host and runs it.",
            "(chmod\\s+\\+x|\\./[\\w.-]+|\\b(ba)?sh\\s+[\\w./-]+|python3?\\s+[\\w./-]+\\.py|node\\s+[\\w./-]+\\.js|start-process|msiexec)",
            null, false, true),

    AST06_ROOT(OwaspAstCategory.AST06, "c1", "privilege-escalation",
            "Runs commands as root or elevates privileges (sudo, su, runas).",
            "\\bsudo\\b|\\bsu\\s+-|\\brunas\\b|--user[= ]root|\\bdoas\\b", null, false, false),
    AST06_CONTAINER_ESCAPE(OwaspAstCategory.AST06, "c2", "privilege-escalation",
            "Breaks container isolation: Docker socket, --privileged, host namespaces, added capabilities.",
            "docker\\.sock|--privileged|--(net|network|pid|ipc)[= ]host|--cap-add|/var/run/docker",
            null, false, false),
    AST06_SYSTEM_TAMPERING(OwaspAstCategory.AST06, "c3", "system-tampering",
            "Modifies system paths or permissions: /etc, /proc, /sys, chmod 777, setuid, chown root.",
            "/etc/|/proc/|/sys/|chmod\\s+(-r\\s+)?(0?777|[ugoa]*\\+s)|setuid|chown\\s+(-r\\s+)?root",
            null, false, false),
    AST06_PERSISTENCE(OwaspAstCategory.AST06, "c4", "persistence",
            "Installs persistence: cron, systemd, launchd, shell rc/profile files, git hooks, Windows Run keys.",
            SkillTagCriteria.PERSISTENCE_PATTERN, null, false, false),
    AST06_SANDBOX_DISABLED(OwaspAstCategory.AST06, "c5", "privilege-escalation",
            "Disables the agent sandbox or permission prompts.",
            "--dangerously-skip-permissions|bypasspermissions|dangerouslydisablesandbox|--no-sandbox|--disable-sandbox|--yolo|--full-auto"
                    + "|approval[_-]?policy\\s*[:=]\\s*\"?never|sandbox(_mode)?\\s*[:=]\\s*\"?(off|false|none|danger-full-access)",
            null, false, false),

    AST03_WILDCARD_GRANT(OwaspAstCategory.AST03, "c1", "privilege-escalation",
            "Frontmatter grants unrestricted tools (allowed-tools: *, bare Bash with no command scope).",
            "(allowed[-_]?tools|tools|permissions?)\\s*:[^\\n]*(\\*|\\bbash\\b(?!\\s*\\())", null, false, false),
    AST03_CREDENTIAL_ACCESS(OwaspAstCategory.AST03, "c2", "secrets",
            "Reads credential stores or dumps the whole environment.",
            SkillTagCriteria.CREDENTIAL_PATTERN, null, false, false),
    AST03_OUTSIDE_WORKSPACE(OwaspAstCategory.AST03, "c3", "privilege-escalation",
            "Reads or writes the user's home directory or other users' directories, outside the project.",
            "(~/|\\$home\\b|\\$\\{home\\}|%userprofile%|/users/[^/\\s]+|/home/[^/\\s]+|/root/)", null, false, false),

    AST07_UNPINNED(OwaspAstCategory.AST07, "c1", "system-tampering",
            "Installs or executes a dependency with no pinned version (@latest, git URL without a ref).",
            "@latest\\b|git\\+https?://[^\\s#@]+(\\s|$)", null, false, false),
    AST07_SELF_UPDATE(OwaspAstCategory.AST07, "c2", "system-tampering",
            "Overwrites the skill's own files from a remote source at runtime.",
            "(curl|wget|fetch|download|git\\s+pull)[^\\n]*(skill\\.md|skills?/)|(skill\\.md|skills?/)[^\\n]*(curl|wget|fetch|download|git\\s+pull)",
            null, false, false),

    AST04_UNDISCLOSED_BEHAVIOR(OwaspAstCategory.AST04, "c1", "injection",
            "Performs a material action (network send, credential read, deletion) the name/description never discloses.",
            "https?://|curl|wget|fetch\\(|upload|\\.ssh|credentials|(?<!\\w)\\.env\\b|rm\\s+-[rf]+|delete|drop\\s+table",
            null, false, false);

    private static final String CREDENTIAL_PATTERN =
            "\\.ssh/|id_rsa|id_ed25519|id_ecdsa|\\.aws/credentials|\\.aws/config|\\.npmrc|\\.pypirc|\\.netrc|\\.git-credentials"
                    + "|\\.docker/config\\.json|\\.kube/config|application_default_credentials|\\.config/gh/hosts|(?<!\\w)\\.env\\b"
                    + "|keychain|security\\s+find-(generic|internet)-password|login\\s+data|cookies\\.sqlite|logins\\.json|key4\\.db"
                    + "|/etc/shadow|/etc/passwd|\\bprintenv\\b|\\benv\\s*(\\||>|$)|process\\.env\\s*\\)|os\\.environ\\s*\\)";

    private static final String PERSISTENCE_PATTERN =
            "crontab|/etc/cron|systemctl\\s+(--user\\s+)?enable|\\.service\\b|launchctl|launchagents|launchdaemons"
                    + "|\\.bashrc|\\.zshrc|\\.bash_profile|\\.zprofile|\\.profile\\b|config\\.fish|\\.git/hooks|core\\.hookspath"
                    + "|currentversion\\\\run|schtasks|\\\\startup\\\\";

    private static final String SEND_PATTERN =
            "curl|wget|fetch|axios|requests\\.|httpx|http\\.|urllib|invoke-webrequest|invoke-restmethod|\\biwr\\b|\\birm\\b"
                    + "|\\bnc\\b|ncat|socat|\\bscp\\b|rsync|sftp|webhook|upload|\\bpost\\b|--data|\\s-d\\s|\\s-F\\s|--upload-file"
                    + "|sendmail|\\bmail\\b|smtp|\\bdig\\b|nslookup|send|transmit|forward";

    private final OwaspAstCategory category;
    private final String id;
    private final String rule;
    private final String description;
    private final Pattern anchor;
    private final Pattern companion;
    private final boolean malicious;
    private final boolean requiresUntrustedHost;

    /**
     * @param anchor    at least one quote of the finding must match it
     * @param companion when set, some quote of the finding must also match it (the second half of a
     *                  two-step behaviour such as decode-then-execute, which may span two lines)
     */
    SkillTagCriteria(OwaspAstCategory category, String suffix, String rule, String description, String anchor,
            String companion, boolean malicious, boolean requiresUntrustedHost) {
        this.category = category;
        this.id = category.getId() + "." + suffix;
        this.rule = rule;
        this.description = description;
        this.anchor = Pattern.compile(anchor, Pattern.CASE_INSENSITIVE);
        this.companion = companion == null ? null : Pattern.compile(companion, Pattern.CASE_INSENSITIVE);
        this.malicious = malicious;
        this.requiresUntrustedHost = requiresUntrustedHost;
    }

    public OwaspAstCategory getCategory() { return category; }
    public String getId() { return id; }
    /** Rule token the FE maps to a display category; only used for the malicious verdict event. */
    public String getRule() { return rule; }
    public String getDescription() { return description; }
    public boolean isMalicious() { return malicious; }
    public boolean requiresUntrustedHost() { return requiresUntrustedHost; }

    public boolean anchors(String evidence) {
        return evidence != null && anchor.matcher(evidence).find();
    }

    public boolean hasCompanion() {
        return companion != null;
    }

    public boolean companions(String evidence) {
        return companion != null && evidence != null && companion.matcher(evidence).find();
    }

    /**
     * The non-malicious criterion a malicious finding falls back to when it does not clear the bar
     * (the verdict is negative, or the behaviour is explained by the skill's purpose). Null means the
     * behaviour has no factual tag of its own and the finding is dropped.
     */
    public SkillTagCriteria demoted() {
        switch (this) {
            case AST01_CREDENTIAL_THEFT: return AST03_CREDENTIAL_ACCESS;
            case AST01_BEACONING_PERSISTENCE: return AST06_PERSISTENCE;
            default: return null;
        }
    }

    public static SkillTagCriteria fromId(String id) {
        if (id == null) return null;
        String wanted = id.trim();
        for (SkillTagCriteria criteria : values()) {
            if (criteria.id.equalsIgnoreCase(wanted)) return criteria;
        }
        return null;
    }

    /** Criteria table rendered for the prompt, grouped by tag in precedence order. */
    public static String promptTable() {
        StringBuilder sb = new StringBuilder();
        OwaspAstCategory current = null;
        for (SkillTagCriteria criteria : values()) {
            if (criteria == AST08_INVISIBLE_UNICODE) continue; // detected in Java, never by the model
            if (criteria.category != current) {
                current = criteria.category;
                sb.append("  ").append(current.getId()).append(" ").append(current.getDisplayName())
                        .append(criteria.malicious ? "  [MALICIOUS VERDICT — only when THE BAR is cleared]" : "")
                        .append("\n");
            }
            sb.append("    ").append(criteria.id).append("  ").append(criteria.description).append("\n");
        }
        return sb.toString();
    }
}
