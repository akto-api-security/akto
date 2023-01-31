package com.akto.dto.testing.info;

public class BFLATestInfo extends TestInfo {

    private String attackerRole;
    private String victimRole;

    public BFLATestInfo() {
        super();
    }

    public BFLATestInfo(String attackerRole, String victimRole) {
        super();
        this.attackerRole = attackerRole;
        this.victimRole = victimRole;
    }

    public String getAttackerRole() {
        return attackerRole;
    }

    public void setAttackerRole(String attackerRole) {
        this.attackerRole = attackerRole;
    }

    public String getVictimRole() {
        return victimRole;
    }

    public void setVictimRole(String victimRole) {
        this.victimRole = victimRole;
    }
}
