package com.akto.dto.threat_detection;

import java.util.Map;

import org.bson.types.ObjectId;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IpReputationScore {

    public static final String _IP = "ip";
    public static final String _TIMESTAMP = "timestamp";
    public static final String _SCORE = "score";
    public static final String _SOURCE = "source";
    public static final String _METADATA = "metadata";

    private ObjectId id;
    private String ip;
    private int timestamp;
    private ReputationScore score;
    private ReputationSource source;
    private Map<String, Object> metadata;

    public enum ReputationSource {
        ABUSEIPDB,
        OTHER
    }

    public enum ReputationScore {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        NONE
    }

}