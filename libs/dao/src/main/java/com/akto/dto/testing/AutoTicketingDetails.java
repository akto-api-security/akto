package com.akto.dto.testing;

import com.akto.util.enums.GlobalEnums.Severity;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutoTicketingDetails {

    private boolean shouldCreateTickets;
    private Set<Severity> severities;
    private String projectId;
}
