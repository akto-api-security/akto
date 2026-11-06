package com.akto.action.skill_validation_v3;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One file a skill runs, as the endpoint-shield agent collected it from the user's machine (its
 * ValidateSkillReferencedFile), secrets redacted. Validated with the skill, never ingested.
 */
@Getter
@Setter
@NoArgsConstructor
public class SkillReferencedFile {
    // shortened for display, home directory as ~
    private String path;
    // the file that refers to it
    private String via;
    private int depth;
    private long size;
    private String sha256;
    private String content;
    // why content is empty
    private String omitted;
    private boolean truncated;
    private int redactions;

    public SkillReferencedFile(String path, String content) {
        this.path = path;
        this.content = content;
    }
}
