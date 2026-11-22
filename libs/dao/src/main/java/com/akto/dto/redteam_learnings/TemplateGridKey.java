package com.akto.dto.redteam_learnings;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where a test sits in the template library's grid: its base prompt (the vulnerability) x its strategy (the attack
 * style), each as a hash of the template's wordlist text plus a short label for display. Either side is null for a
 * template that has no such wordlist.
 */
@Getter
@Setter
@NoArgsConstructor
public class TemplateGridKey {

    public static final String BASE_PROMPT_KEY = "basePromptKey";
    public static final String STRATEGY_KEY = "strategyKey";
    /** Labels are for display only (the key identifies the text); a library base_prompt runs up to ~900 chars. */
    public static final int MAX_LABEL_CHARS = 200;

    private String basePromptKey;
    private String basePromptLabel;
    private String strategyKey;
    private String strategyLabel;
}
