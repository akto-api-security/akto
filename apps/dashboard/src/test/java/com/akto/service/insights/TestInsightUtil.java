package com.akto.service.insights;

import com.akto.dto.ApiCollection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestInsightUtil {

    private static ApiCollection host(int id, String hostName) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setHostName(hostName);
        return c;
    }

    // ── assetIdentity: connector-created collections have a name but no hostName ──────

    @Test
    public void assetIdentity_prefersHostNameWhenPresent() {
        ApiCollection c = host(1, "a.akto.io");
        c.setName("ignored");

        assertEquals("a.akto.io", InsightUtil.assetIdentity(c));
    }

    @Test
    public void assetIdentity_fallsBackToNameWhenHostNameMissing() {
        ApiCollection c = host(1, null);
        c.setName("aria-agentic");

        assertEquals("aria-agentic", InsightUtil.assetIdentity(c));
    }

    @Test
    public void assetIdentity_nullSafe() {
        assertNull(InsightUtil.assetIdentity(null));
    }
}
