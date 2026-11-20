package com.akto.service.atlas;

import lombok.AllArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Who the discovered devices belong to: device -> username, and username -> team. */
@AllArgsConstructor
public class AtlasPeople {

    private static final String USERNAME_KEY = "username";
    /** The bucket for users and devices with no team tag; a real team tag spelled "Untagged" joins it. */
    public static final String UNTAGGED_TEAM = "Untagged";

    private final Map<String, String> usernameByDevice;
    private final Map<String, String> teamByUser;

    /** From ModuleInfoDao's device metadata map (device -> username, os, browserName). Usernames match ignoring case and spaces, like Devices & employees. */
    public static AtlasPeople of(Map<String, Map<String, String>> deviceMetadata, Map<String, String> teamByUser) {
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
        Map<String, String> teams = new HashMap<>();
        teamByUser.forEach((user, team) -> teams.put(spellingByKey.getOrDefault(identityKey(user), user), team));
        return new AtlasPeople(usernameByDevice, teams);
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
            String username = usernameByDevice.get(deviceId);
            if (AtlasPeopleService.isRealUsername(username)) devices.merge(username, 1, Integer::sum);
        }
        return devices;
    }

    /** Known user -> the newest of the given per-device times (epoch seconds) on their devices. */
    public Map<String, Integer> lastSeenPerUser(Map<String, Integer> lastSeenByDevice) {
        Map<String, Integer> lastSeen = new HashMap<>();
        for (Map.Entry<String, Integer> e : lastSeenByDevice.entrySet()) {
            String username = usernameByDevice.get(e.getKey());
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
        return teamOrUntagged(usernameByDevice.get(deviceId));
    }

    /** Team -> its tagged users; known device users with no tag go under Untagged. */
    public Map<String, Set<String>> membersByTeam() {
        Map<String, Set<String>> members = new HashMap<>();
        for (Map.Entry<String, String> e : teamByUser.entrySet()) {
            members.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
        for (String username : usernameByDevice.values()) {
            if (AtlasPeopleService.isRealUsername(username) && !teamByUser.containsKey(username)) members.computeIfAbsent(UNTAGGED_TEAM, k -> new HashSet<>()).add(username);
        }
        return members;
    }
}
