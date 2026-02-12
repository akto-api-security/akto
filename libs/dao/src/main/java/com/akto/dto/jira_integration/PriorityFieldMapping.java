package com.akto.dto.jira_integration;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PriorityFieldMapping {

    /**
     * The Jira field ID (e.g., "priority" for standard field or "customfield_10001" for custom field)
     */
    private String fieldId;

    /**
     * The display name of the field (e.g., "Priority", "Severity Level")
     */
    private String fieldName;

    /**
     * The type of field (e.g., "priority", "select", "custom")
     */
    private String fieldType;

    /**
     * Maps Akto severity to Jira field values (IDs)
     * Key: Akto Severity (CRITICAL, HIGH, MEDIUM, LOW)
     * Value: Jira field value ID (e.g., "1", "10021", etc.)
     */
    private Map<String, String> severityToValueMap;

    /**
     * Maps Akto severity to Jira field value display names
     * Key: Akto Severity (CRITICAL, HIGH, MEDIUM, LOW)
     * Value: Jira field value name (e.g., "High", "Medium", "p0", etc.)
     */
    private Map<String, String> severityToDisplayNameMap;
}
