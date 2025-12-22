package com.akto.util.enums;

public class GlobalEnums {

    public enum TestErrorSource { // Whether issue came from runtime or automated testing via dashboard
        AUTOMATED_TESTING("testing"),
        RUNTIME("runtime"),
        TEST_EDITOR("test_editor");

        private final String name;

        TestErrorSource(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /* Category of tests perfomred */
    public enum TestCategory {
        BOLA("BOLA", Severity.HIGH, "Broken Object Level Authorization (BOLA)", "BOLA"),
        NO_AUTH("NO_AUTH", Severity.HIGH, "Broken User Authentication (BUA)", "Broken Authentication"),
        BFLA("BFLA", Severity.HIGH, "Broken Function Level Authorization (BFLA)", "Broken Function Level Authorization"),
        IAM("IAM", Severity.HIGH, "Improper Assets Management (IAM)", "Improper Assets Management"),
        EDE("EDE", Severity.HIGH, "Excessive Data Exposure (EDE)", "Sensitive Data Exposure"),
        RL("RL", Severity.HIGH, "Lack of Resources & Rate Limiting (RL)", "Lack of Resources and Rate Limiting"),
        MA("MA", Severity.HIGH, "Mass Assignment (MA)", "Mass Assignment"),
        INJ("INJ", Severity.HIGH, "Injection (INJ)", "Injection"),
        ILM("ILM", Severity.HIGH, "Insufficient Logging & Monitoring (ILM)", "Insufficient Logging and Monitoring"),
        SM("SM", Severity.HIGH, "Security Misconfiguration (SM)", "Misconfiguration"),
        SSRF("SSRF", Severity.HIGH, "Server Side Request Forgery (SSRF)", "Server Side Request Forgery"),
        UC("UC", Severity.HIGH, "Uncategorized (UC)", "Uncategorized"),
        UHM("UHM", Severity.LOW, "Unnecessary HTTP Methods (UHM)", "Unnecessary HTTP Methods"),
        VEM("VEM", Severity.LOW, "Verbose Error Messages (VEM)", "Verbose Error Messages"),
        MHH("MHH", Severity.LOW, "Misconfigured HTTP Headers (MHH)", "Misconfigured HTTP Headers"),
        SVD("SVD", Severity.LOW, "Server Version Disclosure (SVD)", "Server Version Disclosure"),
        CORS("CORS", Severity.HIGH, "Cross-Origin Resource Sharing (CORS)", "CORS Misconfiguration"),
        COMMAND_INJECTION("COMMAND_INJECTION", Severity.HIGH, "Command Injection", "Command Injection"),
        CRLF("CRLF", Severity.MEDIUM, "CRLF Injection", "CRLF Injection"),
        SSTI("SSTI", Severity.HIGH, "Server Side Template Injection (SSTI)", "Server Side Template Injection"),
        LFI("LFI", Severity.HIGH, "Local File Inclusion (LFI)", "Local File Inclusion"),
        XSS("XSS", Severity.HIGH, "Cross-site scripting (XSS)", "Cross-site scripting"),
        IIM("IIM", Severity.HIGH, "Improper Inventory Management (IIM)", "Improper Inventory Management"),
        LLM("LLM",Severity.HIGH,"LLM (Large Language Models) Top 10","LLM");

        private final String name;
        private final Severity severity;
        private final String displayName;
        private final String shortName;

        TestCategory(String name, Severity severity, String displayName, String shortName) {
            this.name = name;
            this.severity = severity;
            this.displayName = displayName;
            this.shortName = shortName;
        }

        public String getName() {
            return name;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getShortName() {
                return shortName;
        }
    }

    public enum IssueTags {
        BL("Business logic"),
        OWASPTOP10("OWASP top 10"),
        HACKERONETOP10("HackerOne top 10");
        private final String name;
        IssueTags(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
    }

    public enum Severity {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        INFO
    }

    public enum TestRunIssueStatus {
        OPEN,
        IGNORED,
        FIXED
    }

    /* YamlTemplate source enum */
    public enum YamlTemplateSource {
        AKTO_TEMPLATES,
        CUSTOM
    }

    public enum TemplatePlan {
        FREE, STANDARD, PRO, ENTERPRISE
    }
    public enum TemplateFeatureAccess {
        PRO_TESTS, ENTERPRISE_TESTS
    }
    public enum TemplateNature {
        INTRUSIVE, NON_INTRUSIVE
    }
    public enum TemplateDuration {
        SLOW, FAST
    }

    public enum CONTEXT_SOURCE {
        API, MCP, GEN_AI, AGENTIC, DAST, ENDPOINT
    }
}
