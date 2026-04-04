package com.akto.dto.testing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Verbatim excerpt from the conversation supporting a vulnerability flag.
 * Expected JSON from agent /chat (when validation is true):
 * { "source": "user_prompt" | "assistant_response", "excerpt": "...", "start": optional, "end": optional }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlaggingEvidenceItem {

    /** user_prompt or assistant_response */
    private String source;
    private String excerpt;
    private Integer start;
    private Integer end;
}
