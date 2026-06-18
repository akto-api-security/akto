package com.akto.gpt.handlers.gpt_prompts;

import javax.validation.ValidationException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;

public class GuardrailComplianceSuggestionHandler extends AzureOpenAIPromptHandler {

    private static final String TYPE_DENIED_TOPIC = "denied_topic";
    private static final String TYPE_LLM_RULE = "llm_rule";

    private static final String FRAMEWORKS_CONTEXT =
        "COMPLIANCE FRAMEWORKS (use only these exact names, do not invent others):\n" +
        "- GDPR: EU regulation covering personal data protection, lawful processing, data subject rights, " +
            "and special categories (health, biometric, financial data).\n" +
        "- HIPAA: US law protecting health information — medical records, diagnoses, treatment data.\n" +
        "- PCI DSS: Payment card industry standard — protects cardholder data and payment credentials.\n" +
        "- SOC 2: Auditing standard for service organizations covering security, availability, and confidentiality.\n" +
        "- NIST 800-53: US federal security controls — access control, audit, incident response.\n" +
        "- NIST AI RMF: AI risk management framework — bias, reliability, transparency, AI safety.\n" +
        "- OWASP LLM: Top 10 vulnerabilities for LLM applications — prompt injection, data leakage, excessive agency.\n" +
        "- EU AI Act: EU regulation for AI systems — risk categorization, transparency, human oversight.\n" +
        "- ISO 27001: Information security management — risk management, access control, incident handling.\n";

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        String inputType = queryData.getString("inputType");
        if (inputType == null || (!inputType.equals(TYPE_DENIED_TOPIC) && !inputType.equals(TYPE_LLM_RULE))) {
            throw new ValidationException("inputType must be 'denied_topic' or 'llm_rule'");
        }

        if (TYPE_DENIED_TOPIC.equals(inputType)) {
            String topicName = queryData.getString("topicName");
            String topicDescription = queryData.getString("topicDescription");
            if (topicName == null || topicName.trim().isEmpty()) {
                throw new ValidationException("topicName is required for denied_topic");
            }
            if (topicDescription == null || topicDescription.trim().isEmpty()) {
                throw new ValidationException("topicDescription is required for denied_topic");
            }
            if (topicName.length() > 500) {
                throw new ValidationException("topicName exceeds 500 characters");
            }
            if (topicDescription.length() > 500) {
                throw new ValidationException("topicDescription exceeds 500 characters");
            }
        } else {
            String llmRule = queryData.getString("llmRule");
            if (llmRule == null || llmRule.trim().isEmpty()) {
                throw new ValidationException("llmRule is required for llm_rule");
            }
            if (llmRule.length() > 1000) {
                throw new ValidationException("llmRule exceeds 1000 characters");
            }
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String inputType = queryData.getString("inputType");
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a compliance mapping expert for AI security guardrails.\n\n");
        prompt.append(FRAMEWORKS_CONTEXT);
        prompt.append("\n");

        if (TYPE_DENIED_TOPIC.equals(inputType)) {
            prompt.append("INPUT_TYPE: denied_topic\n");
            prompt.append("TOPIC_NAME: ").append(queryData.getString("topicName")).append("\n");
            prompt.append("TOPIC_DESCRIPTION: ").append(queryData.getString("topicDescription")).append("\n");

            Object samplePhrases = queryData.get("samplePhrases");
            if (samplePhrases != null) {
                prompt.append("SAMPLE_PHRASES: ").append(samplePhrases.toString()).append("\n");
            }
        } else {
            prompt.append("INPUT_TYPE: llm_rule\n");
            prompt.append("LLM_RULE: ").append(queryData.getString("llmRule")).append("\n");
        }

        prompt.append("\n");
        prompt.append("Return ONLY a JSON object (no prose, no markdown, no explanation):\n");
        prompt.append("{\"frameworks\": [\"<framework_name>\", ...]}\n\n");
        prompt.append("Rules:\n");
        prompt.append("- Include only frameworks from the list above where this guardrail clearly helps satisfy that framework's requirements\n");
        prompt.append("- Use the exact framework names as listed above\n");
        prompt.append("- Max 4 frameworks\n");
        prompt.append("- Return {\"frameworks\": []} if no compliance clearly applies\n");

        return prompt.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject result = new BasicDBObject();
        String cleaned = cleanJSON(rawResponse);

        if (cleaned == null || cleaned.equals("NOT_FOUND") || cleaned.isEmpty()) {
            result.put("mapComplianceToListClauses", new BasicDBObject());
            return result;
        }

        try {
            JSONObject json = new JSONObject(cleaned);
            BasicDBObject complianceMap = new BasicDBObject();
            if (json.has("frameworks")) {
                JSONArray frameworks = json.getJSONArray("frameworks");
                for (int i = 0; i < frameworks.length(); i++) {
                    String framework = frameworks.getString(i).trim();
                    if (!framework.isEmpty()) {
                        complianceMap.put(framework, new java.util.ArrayList<String>());
                    }
                }
            }
            result.put("mapComplianceToListClauses", complianceMap);
        } catch (Exception e) {
            logger.error("Failed to parse compliance suggestion response: " + cleaned);
            result.put("mapComplianceToListClauses", new BasicDBObject());
        }

        return result;
    }
}
