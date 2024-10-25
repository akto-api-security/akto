package com.akto.dao;

import com.akto.dto.jira_integration.JiraIntegration;

public class JiraIntegrationDao extends AccountsContextDao<JiraIntegration> {
    
    public static final JiraIntegrationDao instance = new JiraIntegrationDao();

    @Override
    public String getCollName() {
        return "jira_integration";
    }

    @Override
    public Class<JiraIntegration> getClassT() {
        return JiraIntegration.class;
    }
    
}
