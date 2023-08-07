package com.akto.test_editor;

import org.junit.Test;

import java.util.*;

import static com.akto.test_editor.Utils.headerValuesUnchanged;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestUtils {

    @Test
    public void testHeaderValuesUnchanged() {
        Map<String, List<String>> originalRequestHeaders = new HashMap<>();
        originalRequestHeaders.put("Connection", Arrays.asList("Close", "Open"));
        originalRequestHeaders.put("Referer", Arrays.asList("a1", "a2"));
        originalRequestHeaders.put("Auth", Collections.singletonList("r@nd0m_t0k3n"));
        originalRequestHeaders.put("Accept", Collections.singletonList("application/json, text/plain, */*"));
        originalRequestHeaders.put("access-token", null);

        Map<String, List<String>> testRequestHeaders = new HashMap<>();
        testRequestHeaders.put("Connection", Collections.singletonList("Close"));
        testRequestHeaders.put("Referer", Arrays.asList("a1", "a2"));
        testRequestHeaders.put("Auth", Collections.singletonList("r@nd0m_t0k3n+changed"));
        testRequestHeaders.put("Accept", Collections.singletonList("application/json, text/plain, */*"));
        testRequestHeaders.put("access-token", Collections.singletonList(""));
        testRequestHeaders.put("Accept-Language", Collections.singletonList("en-GB"));

        Set<String> result = headerValuesUnchanged(originalRequestHeaders, testRequestHeaders);

        assertEquals(2, result.size());
        assertTrue(result.contains("Accept"));
        assertTrue(result.contains("Referer"));

    }
}
