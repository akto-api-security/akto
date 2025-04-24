package com.akto.dto.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutoTicketingDetails {

    @JsonProperty("shouldCreateTickets")
    private boolean shouldCreateTickets;
    private List<String> severities;
    private String projectId;
    private String issueType;
}
