package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GuardrailRecommendationSeen {

    public static final String ID_VALUE = "default";
    public static final String LAST_SEEN_TIMESTAMP = "lastSeenTimestamp";

    @BsonId
    private String id = ID_VALUE;
    private int lastSeenTimestamp;
}
