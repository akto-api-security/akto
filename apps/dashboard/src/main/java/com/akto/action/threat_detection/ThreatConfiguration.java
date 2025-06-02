package com.akto.action.threat_detection;

import java.util.List;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class ThreatConfiguration {

    private Actor actor;
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

}
