package com.akto.dto.agents;

import java.util.List;
import java.util.Map;

import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverySubProcess {

    public static final String BAD_ERRORS = "badErrors";
    public static final String PROCESSED_APIS_TILL_NOW = "processedApisTillNow";
    public static final String CURRENT_URL_STRING = "currentUrlString";

    String processId;
    String subProcessId;
    List<AgentLog> logs;
    Map<String,BasicDBObject> badErrors;
    int processedApisTillNow;
    String currentUrlString;
    State state;
    int endTimestamp;
    int startTimestamp;
    Map<String,String> userInputData;
}
