package com.akto.utils.api_audit_logs;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActionAuditGeneratorUtils {
    public static int getItemsCount(List<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    public static <T> String getItemsCS(
            List<T> list,
            Function<T, String> propertyMapper) {
        return Optional.ofNullable(list)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(Objects::nonNull) // 1. Filter out null list elements.
                .map(propertyMapper) // 2. Transform the stream by applying the property extraction function.
                .filter(Objects::nonNull) // 3. Filter out null strings.
                .filter(s -> !s.isEmpty()) // 4. Filter out empty strings ("").
                .collect(Collectors.joining(", ")); // 5. Collect the remaining valid strings.
    }

    public static <T, E extends Enum<E>> String getItemsCSFromEnum(
            List<T> list,
            Function<T, E> propertyMapper) {
        return getItemsCS(list, item -> {
            E enumValue = propertyMapper.apply(item);
            return enumValue != null ? enumValue.name() : null; // Convert enum to its name string
        });     
    }
}
