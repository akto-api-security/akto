package com.akto.testing.scripts;

import java.util.HashMap;
import java.util.Map;

import com.akto.dto.testing.config.TestScript;

/** In-memory provider that counts calls and can be told to fail. */
class StubTestScriptProvider implements TestScriptProvider {

    final Map<String, TestScript> byId = new HashMap<>();
    final Map<TestScript.Type, TestScript> defaults = new HashMap<>();
    int byIdCalls = 0;
    int defaultCalls = 0;
    RuntimeException failWith = null;

    static TestScript script(String id, TestScript.Type type, String js) {
        return new TestScript(id, id, js, type, "a@x.com", "a@x.com", 1, 1);
    }

    @Override
    public TestScript findById(String id) {
        byIdCalls++;
        if (failWith != null) throw failWith;
        return byId.get(id);
    }

    @Override
    public TestScript findDefault(TestScript.Type type) {
        defaultCalls++;
        if (failWith != null) throw failWith;
        return defaults.get(type);
    }
}
