package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DeviceTag {

    public static final String SOURCE_MANUAL = "manual";

    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String SOURCE = "source";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";

    private String key;
    private String value;
    // Free-form, not an enum — e.g. "manual", "okta", and any future identity provider,
    // added with no schema change.
    private String source;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
}
