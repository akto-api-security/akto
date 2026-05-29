package com.akto.action.threat_detection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DashboardTopApiData {
    private String endpoint;
    private String method;
    private String host;
    private int requestsCount;
    private int actorsCount;
}
