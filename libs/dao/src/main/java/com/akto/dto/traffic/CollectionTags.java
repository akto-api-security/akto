package com.akto.dto.traffic;


import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CollectionTags {
    int lastUpdatedTs;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";

    String keyName;
    public static final String KEY_NAME = "keyName";

    String value;
    public static final String VALUE = "value";

    @Override
    public int hashCode() {
        return Objects.hash(lastUpdatedTs, keyName, value);
    }

}
