package com.akto.jobs.utils;

public class Utils {

    public static float getRiskScoreValueFromSeverityScore(float severityScore) {
        if (severityScore >= 100) {
            return 2;
        } else if (severityScore >= 10) {
            return 1;
        } else if (severityScore > 0) {
            return (float) 0.5;
        } else {
            return 0;
        }
    }
    
}
