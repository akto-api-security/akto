package com.akto.service.scripts;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.akto.dto.testing.config.TestScript;

/** Input rules for library scripts. Pure: no database access. */
public class TestScriptValidator {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_JAVASCRIPT_BYTES = 256 * 1024;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _.\\-]*$");

    public TestScript.Type validateType(String rawType) throws TestScriptException {
        if (rawType == null || rawType.isEmpty()) {
            throw new TestScriptException("Script type is required");
        }
        TestScript.Type type;
        try {
            type = TestScript.Type.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            throw new TestScriptException("Invalid script type");
        }
        if (type != TestScript.Type.PRE_REQUEST && type != TestScript.Type.POST_REQUEST) {
            throw new TestScriptException("Script type must be PRE_REQUEST or POST_REQUEST");
        }
        return type;
    }

    /** Returns the trimmed name. A default name is only valid for the type it stands in for. */
    public String validateName(String rawName, TestScript.Type type) throws TestScriptException {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new TestScriptException("Script name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new TestScriptException("Script name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new TestScriptException("Script name may contain letters, digits, spaces, '.', '_' and '-' and must start with a letter or digit");
        }
        if (TestScript.isDefaultName(name) && !name.equals(TestScript.defaultNameFor(type))) {
            throw new TestScriptException("'" + name + "' is reserved for the default " + (type == TestScript.Type.PRE_REQUEST ? "post" : "pre") + "-request script");
        }
        return name;
    }

    public String validateJavascript(String javascript) throws TestScriptException {
        if (javascript == null || javascript.trim().isEmpty()) {
            throw new TestScriptException("Script body is required");
        }
        if (javascript.getBytes(StandardCharsets.UTF_8).length > MAX_JAVASCRIPT_BYTES) {
            throw new TestScriptException("Script body must be at most " + (MAX_JAVASCRIPT_BYTES / 1024) + " KB");
        }
        return javascript;
    }
}
