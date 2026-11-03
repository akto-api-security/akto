package com.akto.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GuardrailResponseRule {

    public static final String CONDITION_SEVERITY = "severity";
    public static final String CONDITION_POLICY = "policy";
    public static final String CONDITION_ACCOUNT_TYPE = "accountType";
    public static final String CONDITION_SURFACE = "surface";
    public static final String CONDITION_AGENT = "agent";
    public static final String CONDITION_UNASSIGNED_HOURS = "unassignedHours";

    public static final String ACTION_CREATE_TICKET = "CREATE_TICKET";
    public static final String ACTION_ASSIGN = "ASSIGN";
    public static final String ACTION_ALERT = "ALERT";
    public static final String ACTION_IGNORE = "IGNORE";
    public static final String ACTION_COACH_EMPLOYEE = "COACH_EMPLOYEE";
    public static final String ACTION_NOTIFY_MANAGER = "NOTIFY_MANAGER";

    private ObjectId id;
    @BsonIgnore
    private String hexId;
    private String name;
    private boolean active;
    private CONTEXT_SOURCE contextSource;
    private List<RuleCondition> conditions = new ArrayList<>();
    private List<RuleAction> actions = new ArrayList<>();
    private long runCount;
    private int lastRunAt;
    private String createdBy;
    private int createdTimestamp;
    private String updatedBy;
    private int updatedTimestamp;

    public String getHexId() {
        return id != null ? id.toHexString() : null;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuleCondition {
        private String field;
        private String operator;
        private List<String> values = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuleAction {
        private String type;
        private Map<String, Object> config = new HashMap<>();
    }
}
