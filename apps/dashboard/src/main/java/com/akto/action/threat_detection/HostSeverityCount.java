package com.akto.action.threat_detection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HostSeverityCount {
    private String host;
    private int critical;
    private int high;
    private int medium;
    private int low;
}
