package com.akto.dto.linear_integration;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LinearTeamMapping {

    private String teamName;
    private Map<String, String> severityToPriority;
}
