package com.akto.dto.testing.config;

import java.util.Objects;

/**
 * The single place that interprets TestingRunConfig.preRequestScriptId / postRequestScriptId.
 * The raw value crosses Java, JSON and JS, so its meaning is fixed here:
 * <ul>
 *   <li>null  -> DEFAULT: use the default-test-{type} script if one exists</li>
 *   <li>""    -> NONE: explicitly run no script</li>
 *   <li>other -> BY_ID: run that script; the id is used exactly as given (not trimmed)</li>
 * </ul>
 * Callers must not re-implement these checks; they switch on {@link #getKind()}.
 */
public final class ScriptSelection {

    public enum Kind { DEFAULT, NONE, BY_ID }

    private final Kind kind;
    private final TestScript.Type type;
    private final String scriptId;

    private ScriptSelection(Kind kind, TestScript.Type type, String scriptId) {
        this.kind = kind;
        this.type = type;
        this.scriptId = scriptId;
    }

    public static ScriptSelection from(String rawId, TestScript.Type type) {
        if (type != TestScript.Type.PRE_REQUEST && type != TestScript.Type.POST_REQUEST) {
            throw new IllegalArgumentException("Unsupported script type " + type);
        }
        if (rawId == null) {
            return new ScriptSelection(Kind.DEFAULT, type, null);
        }
        if (rawId.equals(TestScript.NO_SCRIPT)) {
            return new ScriptSelection(Kind.NONE, type, null);
        }
        return new ScriptSelection(Kind.BY_ID, type, rawId);
    }

    public Kind getKind() {
        return kind;
    }

    public TestScript.Type getType() {
        return type;
    }

    /** Non-null only when the kind is BY_ID. */
    public String getScriptId() {
        return scriptId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScriptSelection)) return false;
        ScriptSelection that = (ScriptSelection) o;
        return kind == that.kind && type == that.type && Objects.equals(scriptId, that.scriptId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, type, scriptId);
    }

    @Override
    public String toString() {
        return "ScriptSelection{kind=" + kind + ", type=" + type + ", scriptId=" + scriptId + "}";
    }
}
