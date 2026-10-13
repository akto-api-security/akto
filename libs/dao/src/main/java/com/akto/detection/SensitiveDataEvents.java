package com.akto.detection;

import com.akto.dto.CustomDataType;
import com.akto.dto.type.SingleTypeInfo.SubType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects which tracked data types were seen, per API collection, while a message is processed.
 *
 * The unit being counted is the message, not the value: a request carrying five customer emails is
 * one event for that type. Values are seen one at a time, deep inside payload parsing, so they are
 * collected per thread - keyed by the collection they were detected in - and the caller drains them
 * once the message is done.
 *
 * {@link #record} runs for every value on the ingestion path, so it does no work unless the type is
 * tracked.
 */
public class SensitiveDataEvents {

    /** Names of the data types worth counting. Empty means the feature is off. */
    private static volatile Set<String> trackedTypes = Collections.emptySet();

    /** apiCollectionId -> tracked types seen on this thread since the last drain. */
    private static final ThreadLocal<Map<Integer, Set<String>>> seen = ThreadLocal.withInitial(HashMap::new);

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

    /** Called with the final data type of every value, and the collection it was detected in. */
    public static void record(int apiCollectionId, SubType subType) {
        if (subType == null) return;
        Set<String> tracked = trackedTypes;
        if (tracked.isEmpty()) return;
        String name = subType.getName();
        if (name != null && tracked.contains(name)) {
            seen.get().computeIfAbsent(apiCollectionId, k -> new HashSet<>()).add(name);
        }
    }

    /** Returns the types seen per collection since the last drain, and forgets them. */
    public static Map<Integer, Set<String>> drain() {
        Map<Integer, Set<String>> current = seen.get();
        if (current.isEmpty()) return Collections.emptyMap();
        Map<Integer, Set<String>> copy = new HashMap<>(current);
        current.clear();
        return copy;
    }
}
