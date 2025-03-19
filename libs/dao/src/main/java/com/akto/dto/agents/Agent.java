package com.akto.dto.agents;

public enum Agent {

    FIND_VULNERABILITIES_FROM_SOURCE_CODE("Matt", "Vulnerability scanner",
            "Your code's guardian that identifies security weaknesses and shields against potential attacks"),
    FIND_APIS_FROM_SOURCE_CODE("Lisa", "Source code analyser",
            "An intelligent agent that analyzes your source code for API endpoints and data flow patterns"),
    FIND_SENSITIVE_DATA_TYPES("Damon", "Sensitive data type scanner",
            "The privacy sentinel that spots exposed personal information in your API ecosystem"),
//     CREATE_TEST_TEMPLATES("Edward", "Test template creator", "Your testing companion that builds customized templates tailored to your API architecture"),
    GROUP_APIS("Suzy", "API grouping tool",
            "The organization wizard that clusters your APIs into logical service groups and relationships"),
    FIND_FALSE_POSITIVE("Lizzie", "Test false positive finder",
            "Error detective that hunts down misleading test failures to improve quality assurance efficiency");

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
