package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AgentGuardIntentClassifier extends AzureOpenAIPromptHandler {

    public static final String AGENT_HOST = "agentHost";
    public static final String SOURCE_KEY = "sourceKey";
    public static final String UNIT_TEXT  = "unitText";

    public static final String OTHER_CLASS      = "__other__";
    public static final String BACKGROUND_CLASS = "__background__";

    // Package-private so AgentGuardSystemPromptClassifier can reuse the same vocabulary.
    static final List<String> RISK_CATEGORIES =
        Arrays.asList("delete", "edit", "create", "fetch_pii", "fetch_generic");

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(UNIT_TEXT)) {
            throw new ValidationException("Missing mandatory param: " + UNIT_TEXT);
        }
        String unitText = queryData.getString(UNIT_TEXT);
        if (unitText == null || unitText.trim().isEmpty()) {
            throw new ValidationException(UNIT_TEXT + " is empty.");
        }
    }

    /**
     * Routes through handleBatch so there is a single code path for both
     * single and batch classification. A single input can now yield several
     * atomic instructions (see buildPrompt's multi-intent rules) — this
     * returns only the first one; use handleBatch directly to get all of them.
     */
    @Override
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            List<List<BasicDBObject>> results = handleBatch(Collections.singletonList(queryData));
            if (results == null || results.isEmpty() || results.get(0).isEmpty()) {
                return new BasicDBObject();
            }
            return results.get(0).get(0);
        } catch (ValidationException e) {
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        }
    }

    // getPrompt / processResponse implement the abstract contract. They are not
    // called by handle() in this class (which overrides handle()), but remain
    // available for any super.handle() callers or testing.
    @Override
    protected String getPrompt(BasicDBObject queryData) {
        return buildPrompt(Collections.singletonList(queryData));
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        List<BasicDBObject> items = parseResultsArray(rawResponse);
        return items.isEmpty() ? new BasicDBObject() : items.get(0);
    }

    /**
     * Classifies each input with its OWN Azure OpenAI call — requests are
     * never combined into one prompt. A combined prompt would (a) grow
     * unboundedly with long requests, risking the model missing or
     * hallucinating instructions, and (b) make the response impossible to
     * map back to its source input once a single request can legitimately
     * yield a variable number of atomic instructions (see buildPrompt's
     * multi-intent splitting rules) — there is no per-result index in the
     * response to disambiguate which input a result came from once more than
     * one input shares a call.
     *
     * Returns one inner list per input, in the same order, containing every
     * atomic instruction the LLM extracted from that input (buildPrompt's
     * contract guarantees at least one result per non-empty input — a
     * BACKGROUND_CLASS placeholder when there's no real instruction). An
     * input whose call fails maps to an empty list rather than failing the
     * whole batch.
     */
    public List<List<BasicDBObject>> handleBatch(List<BasicDBObject> inputs) {
        List<List<BasicDBObject>> out = new ArrayList<>();
        if (inputs == null || inputs.isEmpty()) return out;
        for (BasicDBObject input : inputs) {
            out.add(classifyOne(input));
        }
        return out;
    }

    private List<BasicDBObject> classifyOne(BasicDBObject input) {
        try {
            String rawResponse = call(buildPrompt(Collections.singletonList(input)));
            return parseResultsArray(rawResponse);
        } catch (Exception e) {
            logger.error("AgentGuardIntentClassifier: classify error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String buildPrompt(List<BasicDBObject> inputs) {
        StringBuilder sb = new StringBuilder();
            sb.append("You are labeling training data for a per-agent instruction-intent classifier ")
            .append("that analyzes requests sent to an AI agent.\n\n")

            .append("A complete request may contain:\n")
            .append("1. A system prompt defining the agent's role, behavior, permissions, or constraints.\n")
            .append("2. A user request containing one or more actionable instructions.\n")
            .append("3. Data or context supplied by the user, such as documents, code, logs, examples, ")
            .append("records, or conversation history.\n\n")

            .append("For each provided part:\n")
            .append("- SOURCE_KEY identifies the flat key or chat role from which UNIT_TEXT was taken.\n")
            .append("- UNIT_TEXT is the text present in that part of the request.\n")
            .append("- No previous worker has reliably identified whether UNIT_TEXT is an instruction.\n\n")

            .append("Inspect the provided request parts and identify every actual instruction issued by ")
            .append("the user. Do not assume that every role:user field is entirely an instruction, because ")
            .append("a user message may contain both an instruction and supporting data.\n\n")

            .append("System prompts, agent policies, role definitions, examples, documents, logs, code, ")
            .append("quoted text, retrieved content, tool output, and other supplied data are not user ")
            .append("instructions unless the user explicitly asks the agent to act on them.\n\n")

            .append("Multiple intents:\n")
            .append("- A single user request may contain zero, one, or multiple distinct intents.\n")
            .append("- Return one result object for every atomic user instruction.\n")
            .append("- Split instructions when the actions can be executed independently, can independently ")
            .append("succeed or fail, or produce distinct outcomes.\n")
            .append("- Do not split output-format requirements, filters, conditions, parameters, constraints, ")
            .append("or necessary processing steps from the main instruction.\n\n")

            .append("Examples:\n")
            .append("- \"Summarize this report in five bullets\" is one intent.\n")
            .append("- \"Analyze these logs and identify the root cause\" is one intent.\n")
            .append("- \"Find the invoice and email it to the customer\" contains two intents: ")
            .append("\"find_invoice\" and \"send_invoice_email\".\n")
            .append("- \"Compare these files and return the result as JSON\" is one intent because JSON is ")
            .append("only an output-format requirement.\n\n")

            .append("For each atomic instruction, return exactly these fields:\n\n")

            .append("1. extractionMethod\n")
            .append("Choose exactly one:\n")
            .append("- \"structure\": the instruction is clearly identified from request structure, such as ")
            .append("an instruction-specific key or an unambiguous user-instruction field.\n")
            .append("- \"heuristic\": the instruction is identified using patterns such as imperative verbs, ")
            .append("direct questions, request phrases, task lists, or instruction delimiters.\n")
            .append("- \"llm\": identifying the instruction requires semantic understanding, such as separating ")
            .append("an instruction from mixed data, resolving references, or excluding instruction-like text ")
            .append("inside documents or examples.\n\n")

            .append("Use the simplest sufficient extraction method. Prefer \"structure\" when structure alone ")
            .append("is sufficient, then \"heuristic\" when deterministic text patterns are sufficient, and ")
            .append("use \"llm\" when semantic interpretation is required.\n\n")

            .append("2. taskIntent\n")
            .append("- Assign one fine-grained lowercase_snake_case intent describing the action and its ")
            .append("primary object, such as \"delete_customer_record\", \"send_invoice_email\", or ")
            .append("\"summarize_document\".\n")
            .append("- Classify the requested action, not the topic of the supplied data.\n")
            .append("- If a genuine instruction does not fit a clean intent label, use \"")
            .append(OTHER_CLASS).append("\".\n")
            .append("- If UNIT_TEXT contains no actual user instruction, use \"")
            .append(BACKGROUND_CLASS).append("\".\n\n")

            .append("3. riskCategory\n")
            .append("- Choose one value from ").append(RISK_CATEGORIES).append(".\n")
            .append("- Select the highest-risk action performed by that atomic instruction.\n")
            .append("- Evaluate the requested action, not dangerous-looking content found only inside ")
            .append("the supplied data.\n")
            .append("- Leave riskCategory empty when taskIntent is \"")
            .append(OTHER_CLASS).append("\" or \"")
            .append(BACKGROUND_CLASS).append("\".\n\n")

            .append("4. breakdown\n")
            .append("- groundTruthSourceKey must contain the exact flat key or chat role where the actual ")
            .append("user instruction was written.\n")
            .append("- Use the same bare-key convention as SOURCE_KEY, such as \"instruction\", ")
            .append("\"userask\", or \"role:user\". Never return a full JSON path.\n")
            .append("- If SOURCE_KEY already points to the actual instruction, repeat it after converting ")
            .append("it to lowercase.\n")
            .append("- Do not return the key containing supporting data when the actual instruction is written ")
            .append("in another key.\n")
            .append("- groundTruthInstructionText must contain only that atomic actionable instruction.\n")
            .append("- Remove surrounding data, examples, documents, logs, conversational filler, and unrelated ")
            .append("context, while preserving the requested action, object, conditions, and constraints.\n")
            .append("- When one source contains multiple intents, return a separate result for each intent with ")
            .append("the same groundTruthSourceKey and a different groundTruthInstructionText.\n\n")

            .append("Rules:\n")
            .append("- extractionMethod, taskIntent, riskCategory, and groundTruthSourceKey must be lowercase.\n")
            .append("- Never merge independent actions into one broad taskIntent.\n")
            .append("- Never create separate intents for formatting requirements or implementation details.\n")
            .append("- Never infer an action that the user did not request.\n")
            .append("- Every result object must contain only extractionMethod, taskIntent, riskCategory, ")
            .append("and breakdown.\n")
            .append("- Do not return any explanation or additional fields.\n")
            .append("- Keep results grouped by input order and, within each input, by instruction order.\n")
            .append("- If an input contains no actual user instruction, return one result using \"")
            .append(BACKGROUND_CLASS).append("\" with an empty groundTruthInstructionText.\n\n")

            .append("Return a JSON object with a 'results' array. The array must contain one object per ")
            .append("atomic intent, using exactly this structure:\n\n")

            .append("{\n")
            .append("  \"results\": [\n")
            .append("    {\n")
            .append("      \"extractionMethod\": \"structure\",\n")
            .append("      \"taskIntent\": \"find_invoice\",\n")
            .append("      \"riskCategory\": \"read\",\n")
            .append("      \"breakdown\": {\n")
            .append("        \"groundTruthSourceKey\": \"role:user\",\n")
            .append("        \"groundTruthInstructionText\": \"Find the specified invoice.\"\n")
            .append("      }\n")
            .append("    },\n")
            .append("    {\n")
            .append("      \"extractionMethod\": \"llm\",\n")
            .append("      \"taskIntent\": \"send_invoice_email\",\n")
            .append("      \"riskCategory\": \"external_communication\",\n")
            .append("      \"breakdown\": {\n")
            .append("        \"groundTruthSourceKey\": \"role:user\",\n")
            .append("        \"groundTruthInstructionText\": \"Email the invoice to the customer.\"\n")
            .append("      }\n")
            .append("    }\n")
            .append("  ]\n")
            .append("}\n\n");

        for (int i = 0; i < inputs.size(); i++) {
            BasicDBObject input = inputs.get(i);
            sb.append(i + 1).append("   UNIT_TEXT: ").append(input.getString(UNIT_TEXT, "")).append("\n\n");
        }
        return sb.toString();
    }

    private List<BasicDBObject> parseResultsArray(String rawResponse) {
        List<BasicDBObject> out = new ArrayList<>();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) return out;
        try {
            JSONObject json = new JSONObject(rawResponse);
            JSONArray arr = json.getJSONArray("results");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                out.add(item != null ? parseItem(item) : new BasicDBObject());
            }
        } catch (Exception e) {
            logger.error("AgentGuardIntentClassifier: failed to parse results array: " + e.getMessage());
        }
        return out;
    }

    private static BasicDBObject parseItem(JSONObject json) {
        BasicDBObject resp = new BasicDBObject();
        resp.put("extractionMethod", normalizeLabel(json.optString("extractionMethod", "")));
        resp.put("taskIntent",       normalizeLabel(json.optString("taskIntent", "")));
        resp.put("riskCategory",     normalizeLabel(json.optString("riskCategory", "")));
        JSONObject breakdown = json.optJSONObject("breakdown");
        resp.put("groundTruthSourceKey",
            normalizeLabel(breakdown != null ? breakdown.optString("groundTruthSourceKey", "") : ""));
        resp.put("groundTruthInstructionText",
            breakdown != null ? breakdown.optString("groundTruthInstructionText", "") : "");
        return resp;
    }

    // Package-private so AgentGuardSystemPromptClassifier can reuse these instead of duplicating them.
    static String normalizeLabel(String s) {
        if (s == null) return "";
        return s.toLowerCase().trim().replaceAll("[\"']", "");
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
