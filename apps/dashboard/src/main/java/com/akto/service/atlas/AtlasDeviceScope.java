package com.akto.service.atlas;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Narrows discovery to desktop agents or browser extensions; a browser device reports a browser name. */
public final class AtlasDeviceScope {

    private AtlasDeviceScope() {}

    public static final String ALL = "all";
    public static final String DESKTOP = "desktop";
    public static final String BROWSER = "browser";

    private static final String BROWSER_NAME_KEY = "browserName";

    /** Lower-cased device names whose module reports a browser, from ModuleInfoDao's device metadata map. */
    public static Set<String> browserDevices(Map<String, Map<String, String>> metadata) {
        Set<String> browsers = new HashSet<>();
        for (Map.Entry<String, Map<String, String>> e : metadata.entrySet()) {
            if (StringUtils.isNotBlank(e.getValue().get(BROWSER_NAME_KEY))) browsers.add(e.getKey().toLowerCase(Locale.ROOT));
        }
        return browsers;
    }

    /** Managed devices inside the scope, from the Endpoint Shield row counts (all rows, and the browser ones). */
    public static int countDevices(int all, int browsers, String scope) {
        if (BROWSER.equals(scope)) return browsers;
        return DESKTOP.equals(scope) ? all - browsers : all;
    }

    /** Rows restricted to in-scope devices; a tool left on no device is dropped. */
    public static List<AtlasToolRow> apply(List<AtlasToolRow> rows, String scope, Set<String> browserDevices) {
        if (!DESKTOP.equals(scope) && !BROWSER.equals(scope)) return rows;
        boolean wantBrowser = BROWSER.equals(scope);
        List<AtlasToolRow> scoped = new ArrayList<>();
        for (AtlasToolRow row : rows) {
            Set<String> devices = inScope(row.getDeviceIds(), wantBrowser, browserDevices);
            if (devices.isEmpty()) continue;
            AtlasToolRow copy = row.toBuilder().build();
            copy.setDeviceIds(devices);
            copy.setDeviceLastSeen(lastSeenIn(row.getDeviceLastSeen(), devices));
            Map<String, Set<String>> mcpDevices = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : row.getMcpDevices().entrySet()) {
                Set<String> serverDevices = inScope(e.getValue(), wantBrowser, browserDevices);
                if (!serverDevices.isEmpty()) mcpDevices.put(e.getKey(), serverDevices);
            }
            copy.setMcpDevices(mcpDevices);
            narrowFlags(row, copy, wantBrowser, browserDevices);
            scoped.add(copy);
        }
        return scoped;
    }

    // Misconfigured and personal-account flags count only in-scope devices; a tool with no device ids keeps its flags.
    private static void narrowFlags(AtlasToolRow row, AtlasToolRow copy, boolean wantBrowser, Set<String> browserDevices) {
        if (!row.getMisconfiguredDeviceIds().isEmpty()) {
            Set<String> devices = inScope(row.getMisconfiguredDeviceIds(), wantBrowser, browserDevices);
            copy.setMisconfiguredDeviceIds(devices);
            copy.setMisconfiguredDevices(devices.size());
            copy.setMisconfigured(row.isMisconfigured() && !devices.isEmpty());
        }
        if (!row.getPersonalDeviceIds().isEmpty()) {
            Set<String> devices = inScope(row.getPersonalDeviceIds(), wantBrowser, browserDevices);
            copy.setPersonalDeviceIds(devices);
            copy.setPersonalDevices(devices.size());
            copy.setPersonal(row.isPersonal() && !devices.isEmpty());
        }
    }

    private static Map<String, Integer> lastSeenIn(Map<String, Integer> lastSeen, Set<String> devices) {
        Map<String, Integer> kept = new HashMap<>();
        for (String device : devices) {
            Integer seen = lastSeen.get(device);
            if (seen != null) kept.put(device, seen);
        }
        return kept;
    }

    private static Set<String> inScope(Set<String> devices, boolean wantBrowser, Set<String> browserDevices) {
        Set<String> kept = new HashSet<>();
        for (String device : devices) {
            if (browserDevices.contains(device.toLowerCase(Locale.ROOT)) == wantBrowser) kept.add(device);
        }
        return kept;
    }
}
