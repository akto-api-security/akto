package com.akto.imperva.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeaderDto {
    private DataTypeDto[] dataTypes;
    private ErrorDto error;
    private Boolean headerWithValue;
    private String key;
    private LabelDto[] labels;
    private Boolean required;
    private String type; // HEADER, COOKIE
    private String value;
    // For COOKIE type
    private CookieDto[] cookies;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CookieDto {
        private DataTypeDto[] dataTypes;
        private String key;
        private LabelDto[] labels;
        private Boolean required;
    }
}