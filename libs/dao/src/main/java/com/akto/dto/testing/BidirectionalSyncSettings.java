package com.akto.dto.testing;

import com.akto.util.enums.GlobalEnums.TicketSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BidirectionalSyncSettings {

    public static final String ID = "_id";
    public static final String SOURCE = "source";
    public static final String PROJECT_KEY = "projectKey";
    public static final String ACTIVE = "active";
    public static final String JOB_ID = "jobId";
    public static final String LAST_SYNCED_AT = "lastSyncedAt";

    private ObjectId id;
    private TicketSource source;
    private String projectKey;
    private boolean active;
    private ObjectId jobId;
    private int lastSyncedAt;
}
