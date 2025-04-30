package com.akto.dto.jira_integration;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BiDirectionalSyncSettings {
    private boolean enabled;
    private Map<String, List<String>> aktoStatusMappings;
}
