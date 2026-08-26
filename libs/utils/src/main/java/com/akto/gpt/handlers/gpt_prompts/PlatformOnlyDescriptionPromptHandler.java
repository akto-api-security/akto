package com.akto.gpt.handlers.gpt_prompts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.ValidationException;

import com.mongodb.BasicDBObject;

/**
 * Call 1 of the two-call collection-description pipeline: given only the collection's identity -
 * platform name and type, no endpoints or traffic at all - ask the model whether it genuinely
 * recognizes the named platform. If yes, it writes the description straight from its own knowledge
 * (cheap, and skips the endpoint/library scan entirely). If no, it returns UNKNOWN_PLATFORM_FLAG so
 * the caller falls through to CollectionDescriptionPromptHandler's endpoint-grounded call instead of
 * guessing at a product it doesn't actually know.
 */
public class PlatformOnlyDescriptionPromptHandler extends AzureOpenAIPromptHandler {

    public static final String PLATFORM_DISPLAY_NAME = "platformDisplayName";
    public static final String COLLECTION_TYPE = "collectionType";
    public static final String MAX_CHARS = "maxChars";

    // Distinct from CollectionDescriptionPromptHandler.CANNOT_DECIDE_PLACEHOLDER: this specifically
    // means "I don't recognize the platform, retry with endpoint evidence" - not "there's nothing to
    // go on at all," which is what the other placeholder means in the fallback call.
    public static final String UNKNOWN_PLATFORM_FLAG = "UNKNOWN_PLATFORM";

    // Backstop against the model echoing its internal TYPE 1/TYPE 2 classification into the actual
    // description text (seen in practice despite the prompt telling it not to) - never trust wording
    // alone for this, catch it in code so a leaked classification can never end up stored as if it
    // were a real description.
    private static final java.util.regex.Pattern LEAKED_CLASSIFICATION_PATTERN =
        java.util.regex.Pattern.compile("\\bTYPE\\s*[12]\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    // The prompt's static structure/instructions live in this file, not in Java string
    // concatenation, so wording tweaks don't need a recompile. Only the parts that genuinely vary
    // per call (platform name, examples, type phrase) are computed in Java and substituted in.
    private static final String PROMPT_TEMPLATE = loadTemplate("/prompts/platform_only_description.txt");

    private static String loadTemplate(String resourcePath) {
        try (InputStream in = PlatformOnlyDescriptionPromptHandler.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt template resource: " + resourcePath);
            }
            return org.apache.commons.io.IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt template: " + resourcePath, e);
        }
    }

    // Hand-curated reference descriptions, one set per platform nature - kept out of Java entirely so
    // adding/editing examples never needs a recompile. Shown to the model as few-shot examples rather
    // than described in prose, since a smaller model matches a concrete example's length/tone/structure
    // far more reliably than it follows an abstract instruction saying the same thing. Deliberately
    // worded generically (understands X, reasons Y, acts through Z) - none of them name a specific
    // skill/tool/endpoint, which is exactly the style this call is meant to produce.
    private static final Map<String, List<String>> EXAMPLES_BY_CATEGORY =
        loadExamples("/prompts/platform_only_examples.txt");

    /**
     * Parses a flat "[CATEGORY]\nexample\nexample\n\n[CATEGORY]\n..." file - one bracketed header per
     * category, one example per line underneath, blank lines ignored. No JSON/YAML needed for
     * something this simple, and it stays trivially readable/editable as plain text.
     */
    private static Map<String, List<String>> loadExamples(String resourcePath) {
        String raw;
        try (InputStream in = PlatformOnlyDescriptionPromptHandler.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing examples resource: " + resourcePath);
            }
            raw = org.apache.commons.io.IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load examples: " + resourcePath, e);
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        String currentCategory = null;
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentCategory = trimmed.substring(1, trimmed.length() - 1);
                result.put(currentCategory, new ArrayList<>());
                continue;
            }
            if (currentCategory != null) {
                result.get(currentCategory).add(trimmed);
            }
        }
        return result;
    }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (isBlank(queryData.getString(PLATFORM_DISPLAY_NAME))) {
            throw new ValidationException(PLATFORM_DISPLAY_NAME + " is required.");
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String platformDisplayName = queryData.getString(PLATFORM_DISPLAY_NAME);
        String collectionType = queryData.getString(COLLECTION_TYPE);
        int maxChars = queryData.getInt(MAX_CHARS, 300);
        // "Collection type" describes what this collection's traffic represents (one skill
        // invocation, a general agent session, an MCP server's tool surface, raw LLM calls) - it is
        // NOT what the platform itself fundamentally is. Claude isn't "a skill" just because this
        // particular collection happens to track one of its skills; it's an AI agent/product that
        // hosts skills. Only "AI agent"/"MCP server"/"LLM" describe the platform's own nature -
        // "Skill" never does, so it maps to the generic "product" phrasing instead, with a separate
        // context clause below explaining the skill angle without mischaracterizing the platform.
        boolean isSkillType = "Skill".equals(collectionType);
        String typePhrase = typePhrase(collectionType);
        List<String> examples = examplesFor(typePhrase);

        StringBuilder examplesBlock = new StringBuilder();
        for (String example : examples) {
            examplesBlock.append("- ").append(example).append("\n");
        }

        // A bare "TYPE: Skill" line stacked right under "PRODUCT: Claude" reads as "Claude's type is
        // Skill" no matter what the instructions say elsewhere - the data block itself would
        // contradict the clarification. So for the skill case this is a CONTEXT line about the
        // traffic, never a TYPE line about the product itself.
        String identityLine = isSkillType
            ? "CONTEXT: This traffic is from one of " + platformDisplayName + "'s custom skills - not a "
                + "description of what " + platformDisplayName + " itself is.\n"
            : (isBlank(collectionType) ? "" : "TYPE: " + collectionType + "\n");

        String skillClarification = isSkillType
            ? "- Answer about " + platformDisplayName + " the product/agent itself, never as if "
                + platformDisplayName + " itself were a single skill.\n"
            : "";

        return PROMPT_TEMPLATE
            .replace("{{PLATFORM}}", platformDisplayName)
            .replace("{{IDENTITY_LINE}}", identityLine)
            .replace("{{EXAMPLES}}", examplesBlock.toString())
            .replace("{{SKILL_CLARIFICATION}}", skillClarification)
            .replace("{{TYPE_PHRASE}}", typePhrase)
            .replace("{{MAX_CHARS}}", String.valueOf(maxChars))
            .replace("{{UNKNOWN_FLAG}}", UNKNOWN_PLATFORM_FLAG);
    }

    /**
     * The phrase used for both the recognition question and the output framing, and the key used to
     * pick which example set to show. Only "AI agent", "MCP server", and "LLM" describe what the
     * platform itself fundamentally is - "Skill" describes the collection, not the platform (see
     * getPrompt()), so it maps to generic "product" instead of literally asking whether the platform
     * is "a real, known skill." "product" reuses the AI-agent examples in examplesFor() below, since
     * Skill-tagged collections are always hosted by an agent-shaped platform (Claude, VS Code, etc).
     */
    private static String typePhrase(String collectionType) {
        if ("AI agent".equals(collectionType) || "MCP server".equals(collectionType) || "LLM".equals(collectionType)) {
            return collectionType;
        }
        return "product";
    }

    private static List<String> examplesFor(String typePhrase) {
        String category = "MCP server".equals(typePhrase) ? "MCP_SERVER"
            : "LLM".equals(typePhrase) ? "LLM"
            : "AI_AGENT";
        return EXAMPLES_BY_CATEGORY.getOrDefault(category, Collections.emptyList());
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = cleanJSON(rawResponse).trim();

        if (processed.isEmpty() || processed.equalsIgnoreCase("NOT_FOUND")) {
            resp.put("error", "Unable to generate description - invalid response");
            return resp;
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(processed);
            String description = json.optString("description", "").trim();

            if (description.isEmpty()) {
                resp.put("error", "Empty description in LLM response");
                return resp;
            }

            if (UNKNOWN_PLATFORM_FLAG.equalsIgnoreCase(description)) {
                resp.put("description", UNKNOWN_PLATFORM_FLAG);
                return resp;
            }

            if (LEAKED_CLASSIFICATION_PATTERN.matcher(description).find()) {
                // Never store this - treat it the same as any other failed Call 1 attempt, which
                // falls through to the endpoint-grounded call instead of leaving bad text behind.
                resp.put("error", "LLM leaked its TYPE 1/TYPE 2 classification instead of a description: " + description);
                return resp;
            }

            resp.put("description", description);
        } catch (Exception e) {
            logger.error("Error parsing platform-only description response: " + processed, e);
            resp.put("error", "Error parsing response: " + e.getMessage());
        }

        return resp;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
