package com.akto.dto.port_io_integration;

import com.akto.dto.linear_integration.LinearTeamMapping;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PortIOIntegration {

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String TEAM_MAPPINGS = "teamMappings";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String clientId;
    private String clientSecret;
    private String workflowId;
    private Map<String, LinearTeamMapping> teamMappings;
    private int createdTs;
    private int updatedTs;

    @BsonIgnore
    public Map<String, String> getTeamsMap() {
        Map<String, String> teamsMap = new LinkedHashMap<>();
        if (teamMappings != null) {
            teamMappings.forEach((teamId, mapping) -> teamsMap.put(teamId, mapping.getTeamName()));
        }
        return teamsMap;
    }
}
