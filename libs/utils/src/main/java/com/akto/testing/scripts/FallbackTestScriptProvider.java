package com.akto.testing.scripts;

import com.akto.dto.testing.config.TestScript;

/**
 * Asks the primary provider first and the fallback only when the primary has nothing.
 * Exceptions from the primary propagate: an outage must not silently serve a different script.
 */
public class FallbackTestScriptProvider implements TestScriptProvider {

    private final TestScriptProvider primary;
    private final TestScriptProvider fallback;

    public FallbackTestScriptProvider(TestScriptProvider primary, TestScriptProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public TestScript findById(String id) {
        TestScript script = primary.findById(id);
        return script != null ? script : fallback.findById(id);
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        TestScript script = primary.findDefault(type);
        return script != null ? script : fallback.findDefault(type);
    }
}
