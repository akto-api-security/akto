package com.akto.action.threat_detection;

public class ThreatConfiguration {

    private Actor actor;

    public static class Actor {
        private ActorId actorId;

        public ActorId getActorId() {
            return actorId;
        }

        public void setActorId(ActorId actorId) {
            this.actorId = actorId;
        }
    }

    public static class ActorId {
        private String type;
        private String key;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public Actor getActor() {
        return actor;
    }

    public void setActor(Actor actor) {
        this.actor = actor;
    }
}
