package com.akto.testing.scripts;

import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.script.ScriptContext;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.config.ScriptDiagnostics;
import com.akto.dto.testing.config.ScriptPhaseDiagnostics;
import com.akto.dto.testing.config.TestScript;

/**
 * Records what pre/post-request scripts did on the current thread, for an Ask Akto validation replay.
 * Inactive (and ApiExecutorUtil unchanged) unless {@link #begin()} was called on this thread. Every
 * recorded text is capped. See service/scripts/validation/CLAUDE.md.
 */
public final class ScriptDiagnosticsCollector {

    public static final int MAX_LOG_CHARS = 16 * 1024;
    public static final int MAX_SNAPSHOT_CHARS = 32 * 1024;
    public static final int MAX_ERROR_CHARS = 4 * 1024;

    private static final ThreadLocal<ScriptDiagnosticsCollector> CURRENT = new ThreadLocal<>();

    private final ScriptPhaseDiagnostics pre = new ScriptPhaseDiagnostics();
    private final ScriptPhaseDiagnostics post = new ScriptPhaseDiagnostics();
    private final CappedWriter preLogs = new CappedWriter(MAX_LOG_CHARS);
    private final CappedWriter postLogs = new CappedWriter(MAX_LOG_CHARS);

    private ScriptDiagnosticsCollector() {}

    /** Starts collecting on this thread. Always pair with {@link #end()} in a finally block. */
    public static ScriptDiagnosticsCollector begin() {
        ScriptDiagnosticsCollector collector = new ScriptDiagnosticsCollector();
        CURRENT.set(collector);
        return collector;
    }

    public static void end() {
        CURRENT.remove();
    }

    /** The collector active on this thread, or null. */
    public static ScriptDiagnosticsCollector current() {
        return CURRENT.get();
    }

    public ScriptPhaseDiagnostics getPre() {
        return pre;
    }

    public ScriptPhaseDiagnostics getPost() {
        return post;
    }

    public ScriptDiagnostics toDiagnostics() {
        return new ScriptDiagnostics(pre, post);
    }

    /** Call just before eval: counts the run, records the input and routes print() into the capped log. */
    public void beforeRun(TestScript.Type type, ScriptContext context, String beforeSnapshot) {
        ScriptPhaseDiagnostics phase = phase(type);
        CappedWriter logs = logs(type);
        phase.setRuns(phase.getRuns() + 1);
        phase.setError(null);
        phase.setAfter(null);
        setBefore(phase, beforeSnapshot);
        context.setWriter(logs);
        context.setErrorWriter(logs);
    }

    public void afterRun(TestScript.Type type, String afterSnapshot) {
        ScriptPhaseDiagnostics phase = phase(type);
        String capped = cap(afterSnapshot, MAX_SNAPSHOT_CHARS);
        phase.setAfter(capped);
        if (afterSnapshot != null && capped.length() < afterSnapshot.length()) {
            phase.setSnapshotsTruncated(true);
        }
        flushLogs(type);
    }

    public void failRun(TestScript.Type type, Throwable error) {
        phase(type).setError(cap(String.valueOf(error), MAX_ERROR_CHARS));
        flushLogs(type);
    }

    private void setBefore(ScriptPhaseDiagnostics phase, String snapshot) {
        String capped = cap(snapshot, MAX_SNAPSHOT_CHARS);
        phase.setBefore(capped);
        if (snapshot != null && capped.length() < snapshot.length()) {
            phase.setSnapshotsTruncated(true);
        }
    }

    private void flushLogs(TestScript.Type type) {
        CappedWriter logs = logs(type);
        phase(type).setLogs(logs.text());
        phase(type).setLogsTruncated(logs.truncated());
    }

    private ScriptPhaseDiagnostics phase(TestScript.Type type) {
        return type == TestScript.Type.PRE_REQUEST ? pre : post;
    }

    private CappedWriter logs(TestScript.Type type) {
        return type == TestScript.Type.PRE_REQUEST ? preLogs : postLogs;
    }

    // ── Snapshots ──────────────────────────────────────────────────────────────────────

    /** "METHOD url?query", headers, blank line, body. */
    public static String describeRequest(OriginalHttpRequest request) {
        if (request == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethod()).append(' ').append(request.getUrl());
        if (request.getQueryParams() != null && !request.getQueryParams().isEmpty()) {
            sb.append('?').append(request.getQueryParams());
        }
        sb.append('\n');
        appendHeaders(sb, request.getHeaders());
        sb.append('\n').append(request.getBody() == null ? "" : request.getBody());
        return sb.toString();
    }

    /** "HTTP status", headers, blank line, body. */
    public static String describeResponse(OriginalHttpResponse response) {
        if (response == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP ").append(response.getStatusCode()).append('\n');
        appendHeaders(sb, response.getHeaders());
        sb.append('\n').append(response.getBody() == null ? "" : response.getBody());
        return sb.toString();
    }

    private static void appendHeaders(StringBuilder sb, Map<String, List<String>> headers) {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            String value = e.getValue() == null ? "" : String.join(", ", e.getValue());
            sb.append(e.getKey()).append(": ").append(value).append('\n');
        }
    }

    static String cap(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** Keeps the first {@code max} characters written and drops the rest. */
    static final class CappedWriter extends Writer {
        private final int max;
        private final StringBuilder sb = new StringBuilder();
        private boolean truncated;

        CappedWriter(int max) {
            this.max = max;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            int room = max - sb.length();
            if (len > room) {
                truncated = true;
            }
            if (room > 0) {
                sb.append(cbuf, off, Math.min(len, room));
            }
        }

        @Override
        public void flush() {
            // in memory
        }

        @Override
        public void close() {
            // in memory
        }

        String text() {
            return sb.toString();
        }

        boolean truncated() {
            return truncated;
        }
    }
}
