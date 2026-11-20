package com.akto.service.atlas;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Who the discovered devices belong to: device -> username, and username -> team. */
public class AtlasPeople {

    private static final String USERNAME_KEY = "username";
    private static final String BROWSER_NAME_KEY = "browserName";
    /** The bucket for users and devices with no team tag; a real team tag spelled "Untagged" joins it. */
    public static final String UNTAGGED_TEAM = "Untagged";

    private final Map<String, String> usernameByDevice;
    private final Map<String, String> teamByUser;
    // Fallbacks for a device id that is not an exact device name, matched as Devices & employees does.
    private final Map<String, String> usernameByLowerDevice = new HashMap<>();
    private final Map<String, String> usernameByBrowserIdentity = new HashMap<>();

    public AtlasPeople(Map<String, String> usernameByDevice, Map<String, String> teamByUser) {
        this.usernameByDevice = usernameByDevice;
        this.teamByUser = teamByUser;
        usernameByDevice.forEach((device, username) -> usernameByLowerDevice.putIfAbsent(device.toLowerCase(Locale.ROOT), username));
    }

    /**
     * From ModuleInfoDao's device metadata map (device -> username, os, browserName) and the inference-hooks devices
     * (device -> username, from AtlasPeopleService.hookUsername). Usernames match ignoring case and spaces, like Devices & employees.
     */
    public static AtlasPeople of(Map<String, Map<String, String>> deviceMetadata, Map<String, String> hookUsernameByDevice, Map<String, String> teamByUser) {
        Map<String, String> spellingByKey = new HashMap<>();
        Map<String, String> usernameByDevice = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> e : deviceMetadata.entrySet()) {
            String username = e.getValue().get(USERNAME_KEY);
            if (AtlasPeopleService.isRealUsername(username)) {
                String trimmed = username.trim();
                username = spellingByKey.computeIfAbsent(identityKey(trimmed), k -> trimmed);
            }
            usernameByDevice.put(e.getKey(), username);
        }
        // People seen only through inference hooks, added as Devices & employees adds them: not when they are already an employee through a managed device.
        Set<String> managedPeople = new HashSet<>();
        for (String username : usernameByDevice.values()) {
            if (AtlasPeopleService.isRealUsername(username)) managedPeople.add(AtlasPeopleService.personKey(username));
        }
        hookUsernameByDevice.forEach((device, username) -> {
            if (usernameByDevice.containsKey(device) || managedPeople.contains(AtlasPeopleService.personKey(username))) return;
            usernameByDevice.put(device, spellingByKey.computeIfAbsent(identityKey(username), k -> username));
        });
        Map<String, String> teams = new HashMap<>();
        teamByUser.forEach((user, team) -> teams.put(spellingByKey.getOrDefault(identityKey(user), user), team));
        AtlasPeople people = new AtlasPeople(usernameByDevice, teams);
        // A browser host's prefix is its extension's identity (name or email) without punctuation, not the device name.
        deviceMetadata.forEach((device, meta) -> {
            String username = usernameByDevice.get(device);
            if (meta.get(BROWSER_NAME_KEY) == null || !AtlasPeopleService.isRealUsername(username)) return;
            people.usernameByBrowserIdentity.putIfAbsent(AtlasPeopleService.identityKey(device), username);
            people.usernameByBrowserIdentity.putIfAbsent(AtlasPeopleService.identityKey(username), username);
        });
        return people;
    }

    // The device's user: by exact name, then by its browser identity, then by name ignoring case.
    private String usernameOf(String deviceId) {
        String username = usernameByDevice.get(deviceId);
        if (username == null && deviceId != null) username = usernameByBrowserIdentity.get(AtlasPeopleService.browserIdentity(deviceId));
        if (username == null && deviceId != null) username = usernameByLowerDevice.get(deviceId.toLowerCase(Locale.ROOT));
        return username;
    }

    private static String identityKey(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    /** The distinct known users of these devices. */
    public Set<String> usersOf(Collection<String> deviceIds) {
        return devicesPerUser(deviceIds).keySet();
    }

    /** Known user -> how many of these devices are theirs. */
    public Map<String, Integer> devicesPerUser(Collection<String> deviceIds) {
        Map<String, Integer> devices = new HashMap<>();
        for (String deviceId : deviceIds) {
            String username = usernameOf(deviceId);
            if (AtlasPeopleService.isRealUsername(username)) devices.merge(username, 1, Integer::sum);
        }
        return devices;
    }

    /** Known user -> the newest of the given per-device times (epoch seconds) on their devices. */
    public Map<String, Integer> lastSeenPerUser(Map<String, Integer> lastSeenByDevice) {
        Map<String, Integer> lastSeen = new HashMap<>();
        for (Map.Entry<String, Integer> e : lastSeenByDevice.entrySet()) {
            String username = usernameOf(e.getKey());
            if (AtlasPeopleService.isRealUsername(username)) lastSeen.merge(username, e.getValue(), Math::max);
        }
        return lastSeen;
    }

    /** Null when the user has no team tag. */
    public String teamOfUser(String username) {
        return teamByUser.get(username);
    }

    /** Untagged when the user has no team tag. */
    public String teamOrUntagged(String username) {
        return teamByUser.getOrDefault(username, UNTAGGED_TEAM);
    }

    /** Untagged when the device has no user or its user has no team tag. */
    public String teamOfDevice(String deviceId) {
        return teamOrUntagged(usernameOf(deviceId));
    }

    /** Team -> the known device users in it; users with no tag go under Untagged. A tagged user with no device is not counted, as on Devices & employees. */
    public Map<String, Set<String>> membersByTeam() {
        Map<String, Set<String>> members = new HashMap<>();
        for (String username : usernameByDevice.values()) {
            if (AtlasPeopleService.isRealUsername(username)) members.computeIfAbsent(teamOrUntagged(username), k -> new HashSet<>()).add(username);
        }
        return members;
    }
}
