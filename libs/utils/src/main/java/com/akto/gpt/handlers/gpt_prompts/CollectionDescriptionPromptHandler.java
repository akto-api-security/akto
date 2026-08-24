package com.akto.gpt.handlers.gpt_prompts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.ValidationException;

import com.mongodb.BasicDBObject;

/**
 * Call 2 of the two-call description pipeline: the TYPE 2 (unrecognized-platform) fallback. Only
 * reached when PlatformOnlyDescriptionPromptHandler either wasn't attempted (no platform resolved
 * at all) or came back UNKNOWN_PLATFORM. Grounded in the collection's actual endpoint/skill/tool
 * names and HTTP methods - never sample request/response traffic.
 */
public class CollectionDescriptionPromptHandler extends AzureOpenAIPromptHandler {

    public static final String COLLECTION_NAME = "collectionName";
    public static final String HOST_NAME = "hostName";
    public static final String ACCESS_TYPE = "accessType";
    public static final String COLLECTION_TYPE = "collectionType";
    public static final String SKILL_NAME = "skillName";
    // Prettified ai-agent/mcp-client platform name (e.g. "VS Code", "Cursor"), looked up via
    // KnownAiPlatforms. Independent of SKILL_NAME/ITEM_LIBRARY_SIZE - a collection can have both a
    // skill tag and a known platform at once, and the platform should never be dropped just because
    // a skill was also found.
    public static final String PLATFORM_DISPLAY_NAME = "platformDisplayName";
    // Set (>1) when the collection has many distinct items (skills, MCP tools, or plain endpoints) -
    // "Endpoints" then holds only a sample, not the full set, and the model needs to know the true count
    // to avoid describing the whole collection as if it were only about the few shown.
    public static final String ITEM_LIBRARY_SIZE = "itemLibrarySize";
    // What each entry in "Endpoints" represents when ITEM_LIBRARY_SIZE is set - "skill", "tool", or
    // "endpoint". Only used for phrasing that one instruction; irrelevant otherwise.
    public static final String ITEM_WORD = "itemWord";
    public static final String TAGS = "tags";
    public static final String ENDPOINTS = "endpoints";
    public static final String MAX_CHARS = "maxChars";

    // The one sanctioned "I can't confidently decide" answer - same text the UI already shows as its
    // empty-state button label, so it reads the same to the user either way. Storing this (rather than
    // leaving the field empty) marks the collection as "tried, gave up" so it isn't retried forever.
    public static final String CANNOT_DECIDE_PLACEHOLDER = "Add description";

    // Phrasings the model might use that clearly mean the same "can't decide" signal - normalized to
    // the exact CANNOT_DECIDE_PLACEHOLDER text rather than stored verbatim.
    private static final Set<String> GIVE_UP_PHRASES = new HashSet<>(Arrays.asList(
        "add description",
        "add a description",
        "add a brief description"
    ));

    // Any other non-answer / filler text - not the sanctioned give-up phrase, so treated as a failed
    // attempt (left empty, retried next run) rather than stored.
    private static final Set<String> REJECTED_JUNK_DESCRIPTIONS = new HashSet<>(Arrays.asList(
        "n/a",
        "na",
        "none",
        "no description",
        "no description available",
        "todo",
        "tbd",
        "unknown"
    ));

    // The prompt's static structure/instructions live in this file, not in Java string
    // concatenation, so wording tweaks don't need a recompile. Only the parts that genuinely vary
    // per call (collection info, endpoints, conditional bullets) are computed in Java and
    // substituted in.
    private static final String PROMPT_TEMPLATE = loadTemplate("/prompts/collection_description.txt");

    // Few-shot examples for this TYPE 2 (unrecognized-platform, endpoint-grounded) case - kept out of
    // Java for the same reason as PlatformOnlyDescriptionPromptHandler's TYPE 1 examples: editing or
    // adding examples should never need a recompile. Flat file, no categories yet (unlike the TYPE 1
    // examples, which are split by platform nature) - add more examples to the same file as they come.
    private static final String EXAMPLES_TEXT = loadTemplate("/prompts/collection_description_examples.txt");

    private static String loadTemplate(String resourcePath) {
        try (InputStream in = CollectionDescriptionPromptHandler.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing prompt template resource: " + resourcePath);
            }
            return org.apache.commons.io.IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt template: " + resourcePath, e);
        }
    }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (isBlank(queryData.getString(COLLECTION_NAME)) && isBlank(queryData.getString(HOST_NAME))) {
            throw new ValidationException("At least one of " + COLLECTION_NAME + " or " + HOST_NAME + " is required.");
        }

        // Endpoints are the usual basis for a description, but a collection with none yet can still get
        // one from its type/tags alone (e.g. "MCP server via cursor") - only reject when there's neither.
        Object endpointsObj = queryData.get(ENDPOINTS);
        boolean hasEndpoints = endpointsObj instanceof List && !((List<?>) endpointsObj).isEmpty();
        if (!hasEndpoints && isBlank(queryData.getString(COLLECTION_TYPE))) {
            throw new ValidationException("Need either a non-empty " + ENDPOINTS + " list or a " + COLLECTION_TYPE + " to go on.");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String collectionName = queryData.getString(COLLECTION_NAME);
        String hostName = queryData.getString(HOST_NAME);
        String accessType = queryData.getString(ACCESS_TYPE);
        String collectionType = queryData.getString(COLLECTION_TYPE);
        String skillName = queryData.getString(SKILL_NAME);
        String platformDisplayName = queryData.getString(PLATFORM_DISPLAY_NAME);
        int itemLibrarySize = queryData.getInt(ITEM_LIBRARY_SIZE, 0);
        String itemWord = queryData.getString(ITEM_WORD);
        List<String> tags = (List<String>) queryData.getOrDefault(TAGS, null);
        List<String> endpoints = (List<String>) queryData.get(ENDPOINTS);
        int maxChars = queryData.getInt(MAX_CHARS, 300);

        StringBuilder infoBlock = new StringBuilder();
        if (!isBlank(collectionName)) {
            infoBlock.append("Name: ").append(collectionName).append("\n");
        }
        if (!isBlank(hostName)) {
            infoBlock.append("Host: ").append(hostName).append("\n");
        }
        if (!isBlank(collectionType)) {
            infoBlock.append("Collection type: ").append(collectionType).append("\n");
        }
        if (!isBlank(platformDisplayName)) {
            infoBlock.append("Platform: ").append(platformDisplayName).append("\n");
        }
        if (!isBlank(skillName)) {
            infoBlock.append("Skill name: ").append(skillName).append("\n");
        }
        if (!isBlank(accessType) && !"unknown".equalsIgnoreCase(accessType)) {
            infoBlock.append("Access type: ").append(accessType).append("\n");
        }
        if (tags != null && !tags.isEmpty()) {
            infoBlock.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }

        boolean hasEndpoints = endpoints != null && !endpoints.isEmpty();
        String endpointsSection = "";
        if (hasEndpoints) {
            StringBuilder endpointsBlock = new StringBuilder("ENDPOINTS:\n");
            for (String endpoint : endpoints) {
                endpointsBlock.append("- ").append(endpoint).append("\n");
            }
            endpointsSection = endpointsBlock.toString();
        }

        String platformBullet = !isBlank(platformDisplayName)
            ? "- \"Platform\" (" + platformDisplayName + ") is the subject - lead with it, never a "
                + "trailing mention. Use it only as a label here; base what it does on the endpoints "
                + "below, not assumed brand knowledge (you may not know it well).\n"
            : "";

        String skillBullet = !isBlank(skillName)
            ? "- \"Skill name\" (" + skillName + ") is the point of the description: say what that "
                + "specific skill actually does (read the name itself if the endpoints don't spell it "
                + "out - e.g. \"mongodb-mcp-setup\" sets up an MCP connection to MongoDB). Do not "
                + "describe generic skill-management mechanics (listing, creating, or reading skill "
                + "definition files) instead of the skill's actual purpose.\n"
            : "";

        String libraryBullet = itemLibrarySize > 1
            ? "- This collection has " + itemLibrarySize + " distinct " + itemWord + "s - \"Endpoints\" "
                + "below only samples some of them. Generalize across them into a short functional "
                + "capability description based on what their names indicate (e.g. \"processes payments "
                + "and refunds\", \"manages files and git operations\") - do not just list them by name, "
                + "and don't assume brand knowledge beyond the name itself.\n"
            : "";

        return PROMPT_TEMPLATE
            .replace("{{EXAMPLES}}", EXAMPLES_TEXT)
            .replace("{{INFO_BLOCK}}", infoBlock.toString())
            .replace("{{ENDPOINTS_SECTION}}", endpointsSection)
            .replace("{{PLATFORM_BULLET}}", platformBullet)
            .replace("{{SKILL_BULLET}}", skillBullet)
            .replace("{{LIBRARY_BULLET}}", libraryBullet)
            .replace("{{MAX_CHARS}}", String.valueOf(maxChars))
            .replace("{{CANNOT_DECIDE}}", CANNOT_DECIDE_PLACEHOLDER);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = cleanJSON(rawResponse).trim();

        if (processed == null || processed.isEmpty() || processed.equalsIgnoreCase("NOT_FOUND")) {
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

            String normalized = description.toLowerCase();

            if (GIVE_UP_PHRASES.contains(normalized)) {
                // Sanctioned "can't decide" signal - store the exact canonical text, not verbatim.
                resp.put("description", CANNOT_DECIDE_PLACEHOLDER);
                return resp;
            }

            if (REJECTED_JUNK_DESCRIPTIONS.contains(normalized)) {
                // Non-answer, but not the sanctioned give-up phrase - treat as a failed attempt so it
                // gets retried instead of stored.
                resp.put("error", "LLM returned a non-answer: " + description);
                return resp;
            }

            resp.put("description", description);
        } catch (Exception e) {
            logger.error("Error parsing collection description response: " + processed, e);
            resp.put("error", "Error parsing response: " + e.getMessage());
        }

        return resp;
    }
}
