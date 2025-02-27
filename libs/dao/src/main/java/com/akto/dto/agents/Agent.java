package com.akto.dto.agents;

public enum Agent {

    FIND_VULNERABILITIES_FROM_SOURCE_CODE("Matt", "Vulnerability scanner",
            "An intelligent member that analyzes your source code for security vulnerabilities by examining authentication mechanisms, API endpoints, and data flow patterns"),
    FIND_APIS_FROM_SOURCE_CODE("Lisa", "Source code analyser",
            "An intelligent member that analyzes your source code for API endpoints and data flow patterns"),
    FIND_SENSITIVE_DATA_TYPES("Damon", "Sensitive data type scanner",
            "An intelligent member that analyzes your APIs for sensitive data types"),
    CREATE_TEST_TEMPLATES("Edward", "Test template creator",
            "An intelligent member that creates test templates specific to your APIs"),
    GROUP_APIS("Suzy", "API grouping tool",
            "An intelligent member that groups your APIs based on their probable services");

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
