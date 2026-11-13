package com.akto.dto.linear_integration;

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
public class LinearIntegration {

    public static final String API_TOKEN = "apiToken";
    public static final String TEAM_MAPPINGS = "teamMappings";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    private String apiToken;
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
