package com.akto.action.threat_detection;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopHostData {
    private String host;
    private int attacks;
}


