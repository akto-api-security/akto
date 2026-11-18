package com.akto.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LoginRedirectUtilsTest {

    @Test
    public void keepsDashboardPathsWithQuery() {
        String path = "/dashboard/mcp-oauth/consent?request_id=abc123";
        assertEquals(path, LoginRedirectUtils.safeReturnPath(path));
    }

    @Test
    public void dropsAnythingOutsideTheDashboard() {
        assertNull(LoginRedirectUtils.safeReturnPath(null));
        assertNull(LoginRedirectUtils.safeReturnPath(""));
        assertNull(LoginRedirectUtils.safeReturnPath("https://example.com/dashboard/home"));
        assertNull(LoginRedirectUtils.safeReturnPath("//example.com/dashboard/"));
        assertNull(LoginRedirectUtils.safeReturnPath("/login"));
        assertNull(LoginRedirectUtils.safeReturnPath("/dashboard"));
    }

    @Test
    public void dropsHeaderInjectionAndBackslashes() {
        assertNull(LoginRedirectUtils.safeReturnPath("/dashboard/x\r\nSet-Cookie: a=b"));
        assertNull(LoginRedirectUtils.safeReturnPath("/dashboard/\\evil.example.com"));
    }

    @Test
    public void dropsOverlongPaths() {
        StringBuilder sb = new StringBuilder("/dashboard/");
        for (int i = 0; i < 2100; i++) sb.append('a');
        assertNull(LoginRedirectUtils.safeReturnPath(sb.toString()));
    }

    @Test
    public void landingPathFallsBackToInventory() {
        assertEquals("/dashboard/observe/inventory", LoginRedirectUtils.landingPath(null));
        assertEquals("/dashboard/home", LoginRedirectUtils.landingPath("/dashboard/home"));
    }
}
