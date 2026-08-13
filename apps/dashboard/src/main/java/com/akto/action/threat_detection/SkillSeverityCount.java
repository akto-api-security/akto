package com.akto.action.threat_detection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SkillSeverityCount {
    private String skillName;
    private int critical;
    private int high;
    private int medium;
    private int low;
}
