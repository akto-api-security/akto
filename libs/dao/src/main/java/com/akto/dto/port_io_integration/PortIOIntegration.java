package com.akto.dto.port_io_integration;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortIOIntegration {

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String SEVERITY_TO_PRIORITY = "severityToPriority";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String clientId;
    private String clientSecret;
    private String workflowId;
    private Map<String, String> severityToPriority;
    private int createdTs;
    private int updatedTs;
}
