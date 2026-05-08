package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Recursive data type structure that represents the nested dataTypes in Imperva schema.
 * This is the core recursive class that handles all nesting levels.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTypeDto {
    private String type; // String, Number, Boolean, Object, Array
    private ParameterDrillDown[] children; // Recursive reference for nested structures

    /**
     * Recursive parameter structure that can contain nested children
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParameterDrillDown {
        private DataTypeDto[] dataTypes;
        private Long id;
        private LabelDto[] labels;
        private String name;
        private Boolean required;
    }
}