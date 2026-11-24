package com.akto.dto.redteam_learnings;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A test template suggested from what a run showed about the agent. It is only a suggestion: nothing
 * here runs until a user opens it in the test editor and saves it. _id is a hash of the agent identity
 * and the prompt content, so the same suggestion from a later run is the same draft, and a draft the
 * user dismissed stays dismissed. The record is not an Akto or custom template and is never mixed with them.
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRedteamTemplateDraft extends AgentRedteamRecord {

    public static final String STATUS = "status";
    public static final String SAVED_TEMPLATE_ID = "savedTemplateId";
    public static final String UPDATED_AT_MS = "updatedAtMs";
    public static final String SOURCE_REDTEAM_LEARNING = "REDTEAM_LEARNING";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SAVED = "SAVED";
    public static final String STATUS_DISMISSED = "DISMISSED";

    public static final int MAX_BASE_PROMPT_CHARS = 1200;
    public static final int MAX_DRAFTS_PER_RUN = 5;

    private String source = SOURCE_REDTEAM_LEARNING;
    /** The profile version these were drawn from. */
    private String profileVersionId;
    private String status = STATUS_DRAFT;
    /** The id the template got when the user saved it from the test editor; null until then. */
    private String savedTemplateId;

    private String name;
    private String description;
    /** Null when the category could not be read from the run; it is then taken from the template named in basedOnTestSubType. */
    private String testCategory;
    private String basedOnTestSubType;
    /** The tailored vulnerability description that goes into the template's base_prompt wordlist. */
    private String basePrompt;
    /** Strategies, by key, chosen only from those the run actually used. */
    private List<String> strategyKeys = new ArrayList<>();
    /** single, linear, crescendo, tree, sequential or bad-likert, matching the template library's variants. */
    private String mode;
    private String rationale;
    private List<String> basedOnInsightIds = new ArrayList<>();
    private List<String> evidenceObservationIds = new ArrayList<>();

    /** The finished template, rendered and checked before it was stored. */
    private String yamlContent;

    private long updatedAtMs;
}
