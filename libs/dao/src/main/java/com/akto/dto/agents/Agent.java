package com.akto.dto.agents;

public enum Agent {

    FIND_APIS_FROM_SOURCE_CODE("Matt"),
    FIND_VULNERABILITIES_FROM_SOURCE_CODE("Lisa");

    private final String agentName;

    Agent(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentName() {
        return agentName;
    }

}
