package com.akto.util.enums;

public class GlobalEnums {
    /* * * * * * * *  Enums for Testing run issues * * * * * * * * * * * *  */

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

        // whenever adding a new test category, please ensure that if mcp or LLM is used, update the list of categories in TestTemplateUtils.java which will be used to fetch templates in fetchAllSubCategories

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
        INJECT("INJECT", Severity.MEDIUM, "Injection Attacks (INJECT)", "Injection Attacks"),
        INPUT("INPUT", Severity.MEDIUM, "Input Validation (INPUT)", "Input Validation"),

        BOLA_AGENTIC("BOLA_AGENTIC", Severity.HIGH, "Broken Object Level Authorization (BOLA)", "BOLA"),
        NO_AUTH_AGENTIC("NO_AUTH_AGENTIC", Severity.HIGH, "Broken User Authentication (BUA)", "Broken Authentication"),
        BFLA_AGENTIC("BFLA_AGENTIC", Severity.HIGH, "Broken Function Level Authorization (BFLA)", "Broken Function Level Authorization"),
        IAM_AGENTIC("IAM_AGENTIC", Severity.HIGH, "Improper Assets Management (IAM)", "Improper Assets Management"),
        EDE_AGENTIC("EDE_AGENTIC", Severity.HIGH, "Excessive Data Exposure (EDE)", "Sensitive Data Exposure"),
        RL_AGENTIC("RL_AGENTIC", Severity.HIGH, "Lack of Resources & Rate Limiting (RL)", "Lack of Resources and Rate Limiting"),
        MA_AGENTIC("MA_AGENTIC", Severity.HIGH, "Mass Assignment (MA)", "Mass Assignment"),
        INJ_AGENTIC("INJ_AGENTIC", Severity.HIGH, "Injection (INJ)", "Injection"),
        ILM_AGENTIC("ILM_AGENTIC", Severity.HIGH, "Insufficient Logging & Monitoring (ILM)", "Insufficient Logging and Monitoring"),
        SM_AGENTIC("SM_AGENTIC", Severity.HIGH, "Security Misconfiguration (SM)", "Misconfiguration"),
        SSRF_AGENTIC("SSRF_AGENTIC", Severity.HIGH, "Server Side Request Forgery (SSRF)", "Server Side Request Forgery"),
        UC_AGENTIC("UC_AGENTIC", Severity.HIGH, "Uncategorized (UC)", "Uncategorized"),
        UHM_AGENTIC("UHM_AGENTIC", Severity.LOW, "Unnecessary HTTP Methods (UHM)", "Unnecessary HTTP Methods"),
        VEM_AGENTIC("VEM_AGENTIC", Severity.LOW, "Verbose Error Messages (VEM)", "Verbose Error Messages"),
        MHH_AGENTIC("MHH_AGENTIC", Severity.LOW, "Misconfigured HTTP Headers (MHH)", "Misconfigured HTTP Headers"),
        SVD_AGENTIC("SVD_AGENTIC", Severity.LOW, "Server Version Disclosure (SVD)", "Server Version Disclosure"),
        CORS_AGENTIC("CORS_AGENTIC", Severity.HIGH, "Cross-Origin Resource Sharing (CORS)", "CORS Misconfiguration"),
        COMMAND_INJECTION_AGENTIC("COMMAND_INJECTION_AGENTIC", Severity.HIGH, "Command Injection", "Command Injection"),
        CRLF_AGENTIC("CRLF_AGENTIC", Severity.MEDIUM, "CRLF Injection", "CRLF Injection"),
        SSTI_AGENTIC("SSTI_AGENTIC", Severity.HIGH, "Server Side Template Injection (SSTI)", "Server Side Template Injection"),
        LFI_AGENTIC("LFI_AGENTIC", Severity.HIGH, "Local File Inclusion (LFI)", "Local File Inclusion"),
        XSS_AGENTIC("XSS_AGENTIC", Severity.HIGH, "Cross-site scripting (XSS)", "Cross-site scripting"),
        IIM_AGENTIC("IIM_AGENTIC", Severity.HIGH, "Improper Inventory Management (IIM)", "Improper Inventory Management"),
        INJECT_AGENTIC("INJECT_AGENTIC", Severity.MEDIUM, "Injection Attacks (INJECT)", "Injection Attacks"),
        INPUT_AGENTIC("INPUT_AGENTIC", Severity.MEDIUM, "Input Validation (INPUT)", "Input Validation"),

        LLM("LLM",Severity.HIGH,"LLM (Large Language Models) Top 10","LLM"),
        PROMPT_INJECTION("PROMPT_INJECTION", Severity.HIGH, "Prompt Injection", "PromptInjection"),
        SENSITIVE_INFORMATION_DISCLOSURE("SENSITIVE_INFORMATION_DISCLOSURE", Severity.HIGH, "Sensitive Information Disclosure", "SensitiveInformationDisclosure"),
        SUPPLY_CHAIN("SUPPLY_CHAIN", Severity.HIGH, "Supply Chain", "SupplyChain"),
        DATA_AND_MODEL_POISONING("DATA_AND_MODEL_POISONING", Severity.HIGH, "Data and model poisoning", "DataAndModelPoisoning"),
        IMPROPER_OUTPUT_HANDLING("IMPROPER_OUTPUT_HANDLING", Severity.HIGH, "Improper output handling", "ImproperOutputHandling"),
        EXCESSIVE_AGENCY("EXCESSIVE_AGENCY", Severity.HIGH, "Excessive Agency", "ExcessiveAgency"),
        SYSTEM_PROMPT_LEAKAGE("SYSTEM_PROMPT_LEAKAGE", Severity.HIGH, "System Prompt Leakage", "SystemPromptLeakage"),
        VECTOR_AND_EMBEDDING_WEAKNESSES("VECTOR_AND_EMBEDDING_WEAKNESSES", Severity.HIGH, "Vector and Embedding Weaknesses", "VectorAndEmbeddingWeaknesses"),
        MISINFORMATION("MISINFORMATION", Severity.HIGH, "Misinformation", "Misinformation"),
        UNBOUNDED_CONSUMPTION("UNBOUNDED_CONSUMPTION", Severity.HIGH, "Unbounded Consumption", "UnboundedConsumption"),
        MCP_PROMPT_INJECTION("MCP_PROMPT_INJECTION", Severity.HIGH, "MCP - Prompt Injection", "MCP_PROMPT_INJECTION"),
        MCP_TOOL_POISONING("MCP_TOOL_POISONING", Severity.HIGH, "MCP - Tool Poisoning", "MCP_TOOL_POISONING"),
        MCP_PRIVILEGE_ABUSE("MCP_PRIVILEGE_ABUSE", Severity.HIGH, "MCP - Privilege Abuse", "MCP_PRIVILEGE_ABUSE"),
        MCP_INDIRECT_PROMPT_INJECTION("MCP_INDIRECT_PROMPT_INJECTION", Severity.HIGH, "MCP - Indirect Prompt Injection", "MCP_INDIRECT_PROMPT_INJECTION"),
        MCP_SENSITIVE_DATA_LEAKAGE("MCP_SENSITIVE_DATA_LEAKAGE", Severity.HIGH, "MCP - Data Leak", "MCP_SENSITIVE_DATA_LEAKAGE"),
        MCP_DOS("MCP_DOS", Severity.HIGH, "MCP - Denial of Service", "MCP_DOS"),
        MCP_AUTH("MCP_AUTH", Severity.HIGH, "MCP - Broken Authentication", "MCP_AUTH"),
        MCP_MALICIOUS_CODE_EXECUTION("MCP_MALICIOUS_CODE_EXECUTION", Severity.HIGH, "MCP - Malicious Code Execution", "MCP_MALICIOUS_CODE_EXECUTION"),
        MCP("MCP", Severity.HIGH, "Model Context Protocol (MCP) Security", "MCP"),
        MCP_INPUT_VALIDATION("MCP_INPUT_VALIDATION", Severity.HIGH, "MCP - Input Validation", "MCP_INPUT_VALIDATION"),
        MCP_FUNCTION_MANIPULATION("MCP_FUNCTION_MANIPULATION", Severity.CRITICAL, "Model Context Protocol (MCP) Security - Function Call Manipulation", "MCP_FUNC_MANIP"),
        MCP_SECURITY("MCP_SECURITY", Severity.HIGH, "Model Context Protocol (MCP) Security", "MCP_SEC"),
        AGENTIC_BUSINESS_ALIGNMENT("AGENTIC_BUSINESS_ALIGNMENT", Severity.HIGH, "Agent Business Alignment", "AGENTIC_BUSINESS_ALIGNMENT"),
        AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS("AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS", Severity.HIGH, "Agent Hallucination and Trustworthiness", "AGENTIC_HALLUCINATION_AND_TRUSTWORTHINESS"),
        AGENTIC_SAFETY("AGENTIC_SAFETY", Severity.HIGH, "Agent Safety", "AGENTIC_SAFETY"),
        AGENTIC_SECURITY("AGENTIC_SECURITY", Severity.HIGH, "Agent Security", "AGENTIC_SECURITY"),
        AGENTIC_SECURITY_PROMPT_INJECTION("AGENTIC_SECURITY_PROMPT_INJECTION", Severity.HIGH, "Agent Security - Prompt Injection", "AGENTIC_SECURITY_PROMPT_INJECTION"),
        AGENTIC_SECURITY_AGENT_EXPLOITATION("AGENTIC_SECURITY_AGENT_EXPLOITATION", Severity.HIGH, "Agent Security - Agent Exploitation", "AGENTIC_SECURITY_AGENT_EXPLOITATION"),
        AGENTIC_SECURITY_INFRASTRUCTURE("AGENTIC_SECURITY_INFRASTRUCTURE", Severity.HIGH, "Agent Security - Infrastructure", "AGENTIC_SECURITY_INFRASTRUCTURE"),
        AGENTIC_SECURITY_DATA_EXPOSURE("AGENTIC_SECURITY_DATA_EXPOSURE", Severity.HIGH, "Agent Security - Data Exposure", "AGENTIC_SECURITY_DATA_EXPOSURE"),
        AGENTIC_SECURITY_CODE_EXECUTION("AGENTIC_SECURITY_CODE_EXECUTION", Severity.HIGH, "Agent Security - Code Execution", "AGENTIC_SECURITY_CODE_EXECUTION"),
        // Agentic OWASP top 10 - 2026
        AGENT_GOAL_HIJACK("AGENT_GOAL_HIJACK", Severity.HIGH, "Agent Goal Hijack", "AGENT_GOAL_HIJACK"),
        TOOL_MISUSE_AND_EXPLOITATION("TOOL_MISUSE_AND_EXPLOITATION", Severity.HIGH, "Tool Misuse and Exploitation", "TOOL_MISUSE_AND_EXPLOITATION"),
        IDENTITY_AND_PRIVILEGE_ABUSE("IDENTITY_AND_PRIVILEGE_ABUSE", Severity.HIGH, "Identity and Privilege Abuse", "IDENTITY_AND_PRIVILEGE_ABUSE"),
        AGENTIC_SUPPLY_CHAIN("AGENTIC_SUPPLY_CHAIN", Severity.HIGH, "Agentic Supply Chain", "AGENTIC_SUPPLY_CHAIN"),
        UNEXPECTED_CODE_EXECUTION("UNEXPECTED_CODE_EXECUTION", Severity.HIGH, "Unexpected Code Execution", "UNEXPECTED_CODE_EXECUTION"),
        MEMORY_AND_CONTEXT_POISONING("MEMORY_AND_CONTEXT_POISONING", Severity.HIGH, "Memory and Context Poisoning", "MEMORY_AND_CONTEXT_POISONING"),
        INSECURE_INTER_AGENT_COMMUNICATION("INSECURE_INTER_AGENT_COMMUNICATION", Severity.HIGH, "Insecure Inter-Agent Communication", "INSECURE_INTER_AGENT_COMMUNICATION"),
        CASCADING_FAILURES("CASCADING_FAILURES", Severity.HIGH, "Cascading Failures", "CASCADING_FAILURES"),
        HUMAN_AGENT_TRUST_EXPLOITATION("HUMAN_AGENT_TRUST_EXPLOITATION", Severity.HIGH, "Human-Agent Trust Exploitation", "HUMAN_AGENT_TRUST_EXPLOITATION"),
        ROGUE_AGENTS("ROGUE_AGENTS", Severity.HIGH, "Rogue Agents", "ROGUE_AGENTS");

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

    public enum ENCODING_TYPE{
        BASE_64_ENCODED, JWT
    }

    public enum TicketSource {
        JIRA, AZURE_BOARDS, SERVICENOW, DEVREV
    }

    public enum CONTEXT_SOURCE {
        API, MCP, GEN_AI, AGENTIC, DAST, ENDPOINT
    }

    /* ********************************************************************** */

    public enum DashboardCategory {
        // FeatureLabel(DashboardCategory)
        API_SECURITY("API Security"),
        SECURITY_TYPE_AGENTIC("Agentic Security"),
        ENDPOINT_SECURITY("Endpoint Security"),
        AKTO_DAST("DAST");

        private final String dashboardCategory;

        DashboardCategory(String dashboardCategory) {
            this.dashboardCategory = dashboardCategory;
        }

        public String getDashboardCategory() {
            return dashboardCategory;
        }
    }
}
