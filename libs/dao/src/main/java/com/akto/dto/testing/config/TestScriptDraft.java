package com.akto.dto.testing.config;

import java.util.Date;

/**
 * A script Ask Akto wrote that is being validated but is not in the library. Lives in its own
 * collection (test_script_drafts) where every document expires, so the library collection never holds
 * short-lived data. Runtimes find a draft by id through the script provider chain; see
 * service/scripts/validation/CLAUDE.md.
 */
public class TestScriptDraft extends TestScript {

    // Draft ids carry this prefix so a runtime that receives the script as JSON (mini-testing, via cyborg)
    // can still tell it is a draft and run it sandboxed.
    public static final String DRAFT_ID_PREFIX = "ai-draft-";

    public static final String EXPIRE_AT = "expireAt";
    private Date expireAt;

    public TestScriptDraft() {
    }

    public TestScriptDraft(String id, String javascript, Type type, String createdBy, int createdAt, Date expireAt) {
        super(id, id, javascript, type, createdBy, createdBy, createdAt, createdAt);
        this.expireAt = expireAt;
    }

    public static boolean isDraftId(String id) {
        return id != null && id.startsWith(DRAFT_ID_PREFIX);
    }

    /** Derived from the client's validation id, so a retried validation reuses the same draft. */
    public static String draftId(String validationId, Type type) {
        if (type == Type.PRE_REQUEST) {
            return DRAFT_ID_PREFIX + validationId + "-pre";
        }
        if (type == Type.POST_REQUEST) {
            return DRAFT_ID_PREFIX + validationId + "-post";
        }
        throw new IllegalArgumentException("No draft for type " + type);
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
    }
}
