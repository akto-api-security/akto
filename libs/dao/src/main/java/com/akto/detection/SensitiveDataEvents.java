package com.akto.detection;

import com.akto.dto.CustomDataType;
import com.akto.dto.type.SingleTypeInfo.SubType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notes which tracked data types appeared in the message currently being processed.
 *
 * The unit being counted is the message, not the value: a request carrying five customer emails is
 * one event for that type. Values are seen one at a time, deep inside payload parsing, so this
 * collects the types per thread while a message is processed, and the caller drains the set once
 * the message is done.
 *
 * {@link #record} runs for every value on the ingestion path, so it does no work unless the type is
 * tracked, and allocates nothing.
 */
public class SensitiveDataEvents {

    /** Names of the data types worth counting. Empty means the feature is off. */
    private static volatile Set<String> trackedTypes = Collections.emptySet();

    private static final ThreadLocal<Set<String>> seenInMessage = ThreadLocal.withInitial(HashSet::new);

    private SensitiveDataEvents() {
    }

    public static void setTrackedTypes(Set<String> names) {
        trackedTypes = names == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(new HashSet<>(names));
    }

    /**
     * The types to track: active custom data types marked sensitive, either always or in some
     * position. Built-in types are left out for now.
     */
    public static Set<String> customSensitiveTypeNames(List<CustomDataType> customDataTypes) {
        Set<String> names = new HashSet<>();
        if (customDataTypes == null) return names;
        for (CustomDataType type : customDataTypes) {
            if (type == null || !type.isActive() || type.getName() == null) continue;
            boolean sensitive = type.isSensitiveAlways()
                    || (type.getSensitivePosition() != null && !type.getSensitivePosition().isEmpty());
            if (sensitive) names.add(type.getName());
        }
        return names;
    }

    /** Called with the final data type of every value. */
    public static void record(SubType subType) {
        if (subType == null) return;
        Set<String> tracked = trackedTypes;
        if (tracked.isEmpty()) return;
        String name = subType.getName();
        if (name != null && tracked.contains(name)) {
            seenInMessage.get().add(name);
        }
    }

    /** Returns the types recorded since the last drain, and forgets them. Call once a message is done. */
    public static Set<String> drain() {
        Set<String> seen = seenInMessage.get();
        if (seen.isEmpty()) return Collections.emptySet();
        Set<String> copy = new HashSet<>(seen);
        seen.clear();
        return copy;
    }
}
