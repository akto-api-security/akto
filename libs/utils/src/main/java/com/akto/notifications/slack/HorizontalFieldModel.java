package com.akto.notifications.slack;

import java.util.Map;
import java.util.stream.Collectors;

public class HorizontalFieldModel {
    private final Map<String, Integer> horizontalField;

    public HorizontalFieldModel(Map<String, Integer> horizontalField) {
        this.horizontalField = horizontalField;
    }

    @Override
    public String toString() {
        return horizontalField.entrySet()
                .stream()
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(Collectors.joining("\t|\t"));
    }

    boolean isEmpty() {
        return horizontalField.isEmpty();
    }
}
