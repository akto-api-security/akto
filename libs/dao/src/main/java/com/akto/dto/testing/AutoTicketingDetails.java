package com.akto.dto.testing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.akto.util.enums.GlobalEnums.TicketSource;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutoTicketingDetails {

    public static final String PORT_IO_TICKET_SOURCE = "PORT_IO";

    @JsonProperty("shouldCreateTickets")
    private boolean shouldCreateTickets;
    private List<String> severities;
    private String projectId;
    private String issueType;
    private String ticketSource = TicketSource.JIRA.name();

    /** The plan feature that gates auto-creating tickets in the given tool. */
    public static String featureLabelFor(String ticketSource) {
        // Port.io creates Linear tickets, so it shares Linear's feature
        if (TicketSource.LINEAR.name().equals(ticketSource) || PORT_IO_TICKET_SOURCE.equals(ticketSource)) {
            return "LINEAR_INTEGRATION";
        }
        if (TicketSource.WIZ.name().equals(ticketSource)) {
            return "WIZ_INTEGRATION";
        }
        return "JIRA_INTEGRATION";
    }
}
