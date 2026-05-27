package com.akto.action.threat_detection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardTopActorData {
    private String actor;
    private int attackCount;
    private String country;
    private String latestAttack;
}
