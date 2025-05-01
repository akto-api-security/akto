package com.akto.dto.jira_integration;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProjectMapping {
    private Set<JiraStatus> statuses;
    private BiDirectionalSyncSettings biDirectionalSyncSettings;
}
