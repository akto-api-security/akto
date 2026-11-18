package com.akto.service.scripts.validation;

import java.util.List;

import com.akto.dto.testing.config.ScriptDiagnostics;

import lombok.Getter;
import lombok.Setter;

/**
 * An "Ask Akto to fix" turn: the validation outcome the user got back (ReplayOutcome's fields) plus the
 * scripts and endpoint it ran with. Sent by the client in chatAndStore metaData; it is the user's own
 * result going to the user's own conversation, and TestScriptValidationContextBuilder caps, redacts and
 * fences all of it before the agent sees it.
 */
@Getter
@Setter
public class ScriptValidationFeedback {
    private String method;
    private String url;
    private String preJavascript;
    private String postJavascript;
    private Integer statusCode;
    private String replayMessage;
    private boolean replayMessageTruncated;
    private List<String> warnings;
    private boolean diagnosticsAvailable;
    private String diagnosticsUnavailableReason;
    private ScriptDiagnostics diagnostics;
}
