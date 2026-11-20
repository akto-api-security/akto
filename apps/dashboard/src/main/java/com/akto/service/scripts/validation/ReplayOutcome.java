package com.akto.service.scripts.validation;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.config.ScriptDiagnostics;
import com.mongodb.BasicDBObject;

import lombok.Getter;

/**
 * The response of one validation replay. Built from the test result the replay template produces plus
 * the script diagnostics, whichever module ran it (dashboard today; mini-testing's playground later).
 */
@Getter
public class ReplayOutcome {

    public static final int MAX_REPLAY_MESSAGE_CHARS = 64 * 1024;

    /** {"request":{...},"response":{...}}: what was finally sent and received. Capped. */
    private final String replayMessage;
    private final boolean replayMessageTruncated;
    /** Final status after the post script; null when unknown. */
    private final Integer statusCode;
    /** Errors from the test engine plus validation notes (e.g. the role does not cover the endpoint). */
    private final List<String> warnings;
    /** False means "not captured", never "no errors"; the reason says why. */
    private final boolean diagnosticsAvailable;
    private final String diagnosticsUnavailableReason;
    private final ScriptDiagnostics diagnostics;

    private ReplayOutcome(String message, List<String> warnings, ScriptDiagnostics diagnostics, String unavailableReason) {
        boolean truncated = message != null && message.length() > MAX_REPLAY_MESSAGE_CHARS;
        this.replayMessage = truncated ? message.substring(0, MAX_REPLAY_MESSAGE_CHARS) : message;
        this.replayMessageTruncated = truncated;
        this.statusCode = statusCodeOf(message);
        this.warnings = warnings;
        this.diagnostics = diagnostics;
        this.diagnosticsAvailable = diagnostics != null;
        this.diagnosticsUnavailableReason = diagnostics != null ? null : unavailableReason;
    }

    /**
     * Reads the single test result the replay template produces. A run with no result is a failure of the
     * replay, never an empty success. diagnostics is null when the module did not capture any.
     */
    public static ReplayOutcome from(TestingRunResult result, ScriptDiagnostics diagnostics, String unavailableReason) {
        if (result == null || result.getTestResults() == null || result.getTestResults().isEmpty()) {
            throw new IllegalStateException("The replay produced no result");
        }
        GenericTestResult first = result.getTestResults().get(0);
        if (!(first instanceof TestResult)) {
            throw new IllegalStateException("The replay produced an unexpected result type");
        }
        TestResult testResult = (TestResult) first;
        List<String> warnings = new ArrayList<>();
        if (testResult.getErrors() != null) {
            for (String error : testResult.getErrors()) {
                warnings.add("Test engine: " + error);
            }
        }
        return new ReplayOutcome(testResult.getMessage(), warnings, diagnostics, unavailableReason);
    }

    /** True when the engine actually sent the request. */
    public boolean wasSent() {
        return replayMessage != null && !replayMessage.isEmpty();
    }

    static Integer statusCodeOf(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        try {
            Object response = BasicDBObject.parse(message).get("response");
            Object code = response instanceof BasicDBObject ? ((BasicDBObject) response).get("statusCode") : null;
            return code instanceof Number ? ((Number) code).intValue() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
