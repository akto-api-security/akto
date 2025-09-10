package com.akto.action.threat_detection;

import java.util.List;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class ThreatConfiguration {

    private Actor actor;
    private RatelimitConfig ratelimitConfig;
    
    @lombok.Getter
    @lombok.Setter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class Actor {
        private List<ActorId> actorId;
    }
    
    @lombok.Getter
    @lombok.Setter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ActorId {
        private String type;
        private String key;
        private String kind;
        private String pattern;
    }
    
    @lombok.Getter
    @lombok.Setter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class RatelimitConfig {
        private List<RatelimitConfigItem> rules;
        
        @lombok.Getter
        @lombok.Setter
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class AutomatedThreshold {
            private String percentile;
            private Integer overflowPercentage;
            private Integer baselinePeriod;
        }
        
        @lombok.Getter
        @lombok.Setter
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class RatelimitConfigItem {
            private String name;
            private Integer period;
            private Integer maxRequests;
            private Integer mitigationPeriod;
            private String action;
            private String type;
            private AutomatedThreshold autoThreshold;
            private String behaviour;
            private float rateLimitConfidence;
        }
    }

}
