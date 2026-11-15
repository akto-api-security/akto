package com.akto.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.testing.TestingRunConfig;
import com.akto.dto.testing.config.TestScript;

/** The run's script ids decide whether the account's default script runs (see ScriptSelection). */
public class ApiExecutorUtilScriptSelectionTest {

    private static final String PRE_SETS_METHOD = "method = 'PATCH';\n";

    @BeforeEach
    public void setUp() {
        ApiExecutorUtil.resetForTest();
        ApiExecutorUtil.installScriptsForTest(PRE_SETS_METHOD, null);
    }

    @AfterEach
    public void tearDown() {
        ApiExecutorUtil.resetForTest();
    }

    private static OriginalHttpRequest request() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList("application/json"));
        return new OriginalHttpRequest("https://example.com/api", "", "POST", "{}", headers, "");
    }

    @Test
    public void configWithoutIdsRunsDefaultScript() {
        OriginalHttpRequest request = request();
        ApiExecutorUtil.calculateHashAndAddAuth(request, true, new TestingRunConfig());
        assertEquals("PATCH", request.getMethod());
    }

    @Test
    public void explicitNoneSkipsDefaultScript() {
        TestingRunConfig config = new TestingRunConfig();
        config.setPreRequestScriptId(TestScript.NO_SCRIPT);
        OriginalHttpRequest request = request();
        ApiExecutorUtil.calculateHashAndAddAuth(request, true, config);
        assertEquals("POST", request.getMethod());
    }

    @Test
    public void unknownIdRunsNothingInsteadOfDefault() {
        TestingRunConfig config = new TestingRunConfig();
        config.setPreRequestScriptId("deleted-script");
        OriginalHttpRequest request = request();
        ApiExecutorUtil.calculateHashAndAddAuth(request, true, config);
        assertEquals("POST", request.getMethod());
    }

    @Test
    public void noConfigRunsNothing() {
        OriginalHttpRequest request = request();
        ApiExecutorUtil.calculateHashAndAddAuth(request, true, null);
        assertEquals("POST", request.getMethod());
    }
}
