package com.akto.dao;

import com.akto.dto.linear_integration.LinearIssueMapping;

public class LinearIssueMappingDao extends AccountsContextDao<LinearIssueMapping> {
    public static final LinearIssueMappingDao instance = new LinearIssueMappingDao();

    @Override
    public String getCollName() {
        return "linear_issue_mapping";
    }

    @Override
    public Class<LinearIssueMapping> getClassT() {
        return LinearIssueMapping.class;
    }
}
