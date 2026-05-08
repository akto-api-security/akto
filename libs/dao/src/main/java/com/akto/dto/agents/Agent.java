package com.akto.dto.agents;

public enum Agent {

    FIND_VULNERABILITIES_FROM_SOURCE_CODE("Argus", "Vulnerability scanner",
            "Your code's guardian that identifies security weaknesses and shields against potential attacks"),
    FIND_APIS_FROM_SOURCE_CODE("Sentinel", "Source code analyser",
            "An intelligent agent that analyzes your source code for API endpoints and data flow patterns"),
    FIND_SENSITIVE_DATA_TYPES("Hunter", "Sensitive data type scanner",
            "The privacy sentinel that spots exposed personal information in your API ecosystem"),
//     CREATE_TEST_TEMPLATES("Edward", "Test template creator", "Your testing companion that builds customized templates tailored to your API architecture"),
    GROUP_APIS("Moriarty", "API grouping tool",
            "The organization wizard that clusters your APIs into logical service groups and relationships"),
    FIND_FALSE_POSITIVE("Sage", "Test false positive finder",
            "Error detective that hunts down misleading test failures to improve quality assurance efficiency"),
    DISCOVERY_AGENT("Sherlock", "Smart discovery agent",
            "A smart agent that discovers APIs and sensitive data types in your API ecosystem");

    private final String agentEnglishName;

    public String getAgentEnglishName() {
        return agentEnglishName;
    }

    private final String agentFunctionalName;

    public String getAgentFunctionalName() {
        return agentFunctionalName;
    }

    private final String description;

    public String getDescription() {
        return description;
    }

    Agent(String agentEnglishName, String agentFunctionalName, String description) {
        this.agentEnglishName = agentEnglishName;
        this.agentFunctionalName = agentFunctionalName;
        this.description = description;
    }

}
