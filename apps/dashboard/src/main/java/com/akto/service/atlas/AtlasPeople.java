package com.akto.service.atlas;

import lombok.AllArgsConstructor;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Who the discovered devices belong to: device -> username, and username -> team. */
@AllArgsConstructor
public class AtlasPeople {

    private static final String USERNAME_KEY = "username";

    private final Map<String, String> usernameByDevice;
    private final Map<String, String> teamByUser;

    /** From ModuleInfoDao's device metadata map (device -> username, os, browserName). */
    public static AtlasPeople of(Map<String, Map<String, String>> deviceMetadata, Map<String, String> teamByUser) {
        Map<String, String> usernameByDevice = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> e : deviceMetadata.entrySet()) {
            usernameByDevice.put(e.getKey(), e.getValue().get(USERNAME_KEY));
        }
        return new AtlasPeople(usernameByDevice, teamByUser);
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

    /** Null when the device's user has no team tag. */
    public String teamOfDevice(String deviceId) {
        return teamByUser.get(usernameByDevice.get(deviceId));
    }

    /** Team -> its tagged users. */
    public Map<String, Set<String>> membersByTeam() {
        Map<String, Set<String>> members = new HashMap<>();
        for (Map.Entry<String, String> e : teamByUser.entrySet()) {
            members.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
        }
        return members;
    }
}
