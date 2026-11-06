package com.akto.dto;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GuardrailResponseRuleRun {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String STATUS = "status";
    public static final String LAST_ERROR = "lastError";
    public static final String SENT_AT = "sentAt";

    private String id;
    private String ruleId;
    private String ruleName;
    private String eventId;
    private int actionIdx;
    private String actionType;
    private String channel;
    private Map<String, Object> actionConfig = new HashMap<>();
    private String severity;
    private String policy;
    private String filterId;
    private String host;
    private String actor;
    private String refId;
    private String eventType;
    private long detectedAt;
    private String status;
    private String lastError;
    private int sentAt;
    private Date createdAt;
}
