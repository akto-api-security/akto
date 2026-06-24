package com.akto.gpt.handlers.gpt_prompts;

import javax.validation.ValidationException;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;

public class GuardrailComplianceSuggestionHandler extends AzureOpenAIPromptHandler {

    // Query data keys
    public static final String INPUT_TYPE = "inputType";
    public static final String TOPIC_NAME = "topicName";
    public static final String TOPIC_DESCRIPTION = "topicDescription";
    public static final String SAMPLE_PHRASES = "samplePhrases";
    public static final String LLM_RULE = "llmRule";

    // Input type values
    public static final String TYPE_DENIED_TOPIC = "denied_topic";
    public static final String TYPE_LLM_RULE = "llm_rule";

    // Response keys
    public static final String CONTROL_TYPE = "controlType";
    public static final String COMPLIANCE = "compliance";
    public static final String MAP_COMPLIANCE_TO_LIST_CLAUSES = "mapComplianceToListClauses";

    // Input limits
    private static final int MAX_TOPIC_NAME_CHARS = 500;
    private static final int MAX_TOPIC_DESCRIPTION_CHARS = 500;
    private static final int MAX_LLM_RULE_CHARS = 1000;
    private static final int TOP_FRAMEWORKS = 4;

    private static final String FRAMEWORKS_CONTEXT =
        "COMPLIANCE FRAMEWORKS (use ONLY these exact names, do not invent or abbreviate others):\n" +
        "- GDPR: EU regulation for personal data protection — lawful processing, data subject rights, " +
            "special categories (health, biometric, financial data).\n" +
        "- HIPAA: US law protecting health information — medical records, diagnoses, treatment data.\n" +
        "- PCI DSS: Payment card industry standard — cardholder data and payment credentials.\n" +
        "- NIST AI Risk Management Framework: Governance of AI/LLM systems — bias, reliability, " +
            "transparency, robustness and overall AI safety.\n" +
        "- OWASP LLM: Top 10 risks for LLM applications — prompt injection (LLM01), " +
            "insecure output handling (LLM02), sensitive information disclosure, excessive agency.\n" +
        "- OWASP Agentic Top 10: Top 10 risks for agentic AI — agent goal hijack (ASI01), " +
            "tool misuse (ASI02), memory & context poisoning, insecure inter-agent communication.\n" +
        "- EU AI Act: EU regulation for AI systems — risk categorization, transparency, human oversight.\n" +
        "- ISO 27001: Information security management — risk management, access control, incident handling.\n";


    private static final String MAPPING_PRINCIPLES =
        "MAPPING PRINCIPLES (be strict — over-mapping is worse than under-mapping):\n" +
        "- Map to what this guardrail's mechanism ACTUALLY enforces, not to the subject it mentions. " +
            "A blocked word/topic/phrase is a content-governance control; it is NOT proof that personal, health or payment data is protected.\n" +
        "- A clause counts ONLY if this control DIRECTLY helps satisfy it. Do NOT map clauses that require extra process " +
            "this control does not perform (e.g. human oversight/review, breach notification, data-subject rights, de-identification, audit logging).\n" +
        "- Do NOT map privacy/data-protection clauses (e.g. HIPAA PHI uses & disclosures, GDPR special categories, PCI cardholder data) " +
            "unless the guardrail actually detects or blocks identifiable personal or regulated data — not merely a topic related to that domain.\n" +
        "- For generic keyword/topic/substring blocks, the honest mapping is usually policy governance and operational risk mitigation " +
            "(e.g. NIST AI RMF GOVERN/MANAGE) — and often nothing more.\n" +
        "- When unsure, OMIT. Returning {} or a single precise mapping is better than several loose ones.\n";

    private static final String CONTROL_TYPES =
        "CONTROL TYPES (pick exactly one for controlType):\n" +
        "- topic_restriction: blocks a subject/domain (finance, medicine, politics)\n" +
        "- keyword_blocklist: blocks specific configured words/phrases\n" +
        "- pii_detection: detects or blocks identifiable personal data\n" +
        "- phi_detection: detects or blocks protected health information\n" +
        "- pci_detection: detects or blocks payment card data\n" +
        "- prompt_injection_detection: detects instruction-override attempts\n" +
        "- tool_authorization: prevents unauthorized tool/API/agent execution\n" +
        "- human_review: escalates decisions to a human reviewer\n" +
        "- output_filtering: restricts unsafe model responses\n" +
        "- other\n";

    private static final String DECISION_RULES =
        "DECISION RULES (hard constraints — these override any loose association):\n" +
        "- topic_restriction / keyword_blocklist: map ONLY to NIST AI Risk Management Framework (GOVERN, MANAGE); ISO 27001 A.5 is allowed. Nothing else.\n" +
        "- Map OWASP LLM ONLY if controlType is prompt_injection_detection (LLM01) or output_filtering (LLM02).\n" +
        "- Map OWASP Agentic Top 10 ONLY if controlType is prompt_injection_detection (ASI01) or tool_authorization (ASI02).\n" +
        "- Map HIPAA ONLY if controlType is phi_detection (identifiable PHI is detected/blocked).\n" +
        "- Map GDPR ONLY if controlType is pii_detection (identifiable personal data is detected/blocked).\n" +
        "- Map PCI DSS ONLY if controlType is pci_detection.\n" +
        "- Map EU AI Act ONLY if controlType is human_review.\n" +
        "- When uncertain, omit the framework.\n";

    private static final String EXAMPLES =
        "EXAMPLES:\n" +
        "denied_topic 'finance' -> {\"controlType\":\"topic_restriction\",\"compliance\":[\"NIST AI Risk Management Framework\",\"ISO 27001\"]}\n" +
        "llm_rule 'block prompts that say ignore previous instructions' -> {\"controlType\":\"prompt_injection_detection\",\"compliance\":[\"OWASP LLM\",\"OWASP Agentic Top 10\"]}\n" +
        "llm_rule 'detect SSNs and customer names' -> {\"controlType\":\"pii_detection\",\"compliance\":[\"GDPR\"]}\n" +
        "llm_rule 'detect medical records and diagnoses in responses' -> {\"controlType\":\"phi_detection\",\"compliance\":[\"HIPAA\"]}\n" +
        "llm_rule 'block model responses containing harmful or unsafe content' -> {\"controlType\":\"output_filtering\",\"compliance\":[\"OWASP LLM\"]}\n" +
        "llm_rule 'prevent agent from calling tools not in the approved list' -> {\"controlType\":\"tool_authorization\",\"compliance\":[\"OWASP Agentic Top 10\"]}\n" +
        "llm_rule 'escalate high-risk decisions to a human reviewer' -> {\"controlType\":\"human_review\",\"compliance\":[\"EU AI Act\"]}\n";

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        String inputType = queryData.getString(INPUT_TYPE);
        if (inputType == null || (!inputType.equals(TYPE_DENIED_TOPIC) && !inputType.equals(TYPE_LLM_RULE))) {
            throw new ValidationException(INPUT_TYPE + " must be '" + TYPE_DENIED_TOPIC + "' or '" + TYPE_LLM_RULE + "'");
        }

        if (TYPE_DENIED_TOPIC.equals(inputType)) {
            String topicName = queryData.getString(TOPIC_NAME);
            String topicDescription = queryData.getString(TOPIC_DESCRIPTION);
            if (topicName == null || topicName.trim().isEmpty()) {
                throw new ValidationException(TOPIC_NAME + " is required for " + TYPE_DENIED_TOPIC);
            }
            if (topicDescription == null || topicDescription.trim().isEmpty()) {
                throw new ValidationException(TOPIC_DESCRIPTION + " is required for " + TYPE_DENIED_TOPIC);
            }
            if (topicName.length() > MAX_TOPIC_NAME_CHARS) {
                throw new ValidationException(TOPIC_NAME + " exceeds " + MAX_TOPIC_NAME_CHARS + " characters");
            }
            if (topicDescription.length() > MAX_TOPIC_DESCRIPTION_CHARS) {
                throw new ValidationException(TOPIC_DESCRIPTION + " exceeds " + MAX_TOPIC_DESCRIPTION_CHARS + " characters");
            }
        } else {
            String llmRule = queryData.getString(LLM_RULE);
            if (llmRule == null || llmRule.trim().isEmpty()) {
                throw new ValidationException(LLM_RULE + " is required for " + TYPE_LLM_RULE);
            }
            if (llmRule.length() > MAX_LLM_RULE_CHARS) {
                throw new ValidationException(LLM_RULE + " exceeds " + MAX_LLM_RULE_CHARS + " characters");
            }
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String inputType = queryData.getString(INPUT_TYPE);
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a compliance mapping expert for AI, LLM and agentic (MCP/agent/skill) security guardrails.\n\n")
              .append(FRAMEWORKS_CONTEXT)
              .append("\n")
              .append(CONTROL_TYPES)
              .append("\n")
              .append(MAPPING_PRINCIPLES)
              .append("\n")
              .append(DECISION_RULES)
              .append("\n")
              .append(EXAMPLES)
              .append("\n");

        if (TYPE_DENIED_TOPIC.equals(inputType)) {
            prompt.append("INPUT_TYPE: ").append(TYPE_DENIED_TOPIC).append("\n")
                  .append("TOPIC_NAME: ").append(queryData.getString(TOPIC_NAME)).append("\n")
                  .append("TOPIC_DESCRIPTION: ").append(queryData.getString(TOPIC_DESCRIPTION)).append("\n");

            Object samplePhrases = queryData.get(SAMPLE_PHRASES);
            if (samplePhrases != null) {
                prompt.append("SAMPLE_PHRASES: ").append(samplePhrases.toString()).append("\n");
            }
        } else {
            prompt.append("INPUT_TYPE: ").append(TYPE_LLM_RULE).append("\n")
                  .append("LLM_RULE: ").append(queryData.getString(LLM_RULE)).append("\n");
        }

        prompt.append("\n")
              .append("Reason internally in this order, then output ONLY the final JSON:\n")
              .append("1. Identify the single controlType.\n")
              .append("2. Determine what the control DIRECTLY enforces.\n")
              .append("3. Eliminate frameworks that need capabilities this control does not provide (apply DECISION RULES).\n")
              .append("4. Output the JSON below.\n\n")
              .append("Return ONLY a JSON object (no prose, no markdown, no explanation):\n")
              .append("{\"").append(CONTROL_TYPE).append("\": \"<control_type>\", \"")
              .append(COMPLIANCE).append("\": [\"<framework_name>\", ...]}\n\n")
              .append("Rules:\n")
              .append("- Include a framework ONLY if this guardrail DIRECTLY and concretely helps satisfy a requirement of it (apply mapping principles and decision rules above).\n")
              .append("- Use the EXACT framework names from the frameworks list; never invent, abbreviate or rename them.\n")
              .append("- Return only the top ").append(TOP_FRAMEWORKS).append(" most relevant frameworks. If fewer apply, return fewer — NEVER pad the list.\n")
              .append("- Always set controlType. Use an empty compliance array if no framework clearly applies.\n");

        return prompt.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject result = new BasicDBObject();
        String cleaned = cleanJSON(rawResponse);

        if (cleaned == null || cleaned.equals("NOT_FOUND") || cleaned.isEmpty()) {
            result.put(MAP_COMPLIANCE_TO_LIST_CLAUSES, new BasicDBObject());
            return result;
        }

        try {
            JSONObject json = new JSONObject(cleaned);
            logger.info("Guardrail compliance controlType: " + json.optString(CONTROL_TYPE, "unknown"));
            BasicDBObject complianceMap = new BasicDBObject();
            if (json.has(COMPLIANCE)) {
                JSONArray complianceArray = json.optJSONArray(COMPLIANCE);
                if (complianceArray != null) {
                    for (int i = 0; i < complianceArray.length(); i++) {
                        String frameworkName = complianceArray.optString(i, "").trim();
                        if (!frameworkName.isEmpty()) {
                            complianceMap.put(frameworkName, new ArrayList<>());
                        }
                    }
                }
            }
            result.put(MAP_COMPLIANCE_TO_LIST_CLAUSES, complianceMap);
        } catch (Exception e) {
            logger.error("Failed to parse compliance suggestion response: " + cleaned);
            result.put(MAP_COMPLIANCE_TO_LIST_CLAUSES, new BasicDBObject());
        }

        return result;
    }
}
