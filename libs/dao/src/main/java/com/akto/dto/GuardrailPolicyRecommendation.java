package com.akto.dto;

import com.mongodb.BasicDBObject;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GuardrailPolicyRecommendation {

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    private String vulnerabilityHeadline;
    private String vulnerabilityNewsUrl;
    private String vulnerabilitySummary;
    private String aktoTacklingDescription;
    private BasicDBObject suggestedPolicyPayload;
    private String guardrailSectionRef;
    private String policySummary;
    private int createdTimestamp;
    private String source;

    public String getHexId() {
        if (this.id != null) {
            return this.id.toHexString();
        }
        return null;
    }
}
