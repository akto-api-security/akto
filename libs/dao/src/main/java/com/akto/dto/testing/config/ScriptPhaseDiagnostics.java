package com.akto.dto.testing.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What one script phase did during a validation replay. Captured only in the dashboard module (see
 * service/scripts/validation/CLAUDE.md); every text field is capped where it is recorded.
 */
@Getter
@Setter
@NoArgsConstructor
public class ScriptPhaseDiagnostics {

    /** Times the script ran in this replay (the SSE path can run the post script twice). 0 = never ran. */
    private int runs;
    /** The exception the script threw, or null when it finished. */
    private String error;
    /** print() output. */
    private String logs;
    private boolean logsTruncated;
    /** Pre: the request before the script. Post: the status code and body before the script. */
    private String before;
    /** Same shape as before, after the script ran. Null when the script threw. */
    private String after;
    /** True when before or after was cut to fit. */
    private boolean snapshotsTruncated;
}
