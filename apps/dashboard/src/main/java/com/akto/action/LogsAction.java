package com.akto.action;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.FilterLogEventsRequest;
import com.amazonaws.services.logs.model.FilterLogEventsResult;
import com.amazonaws.services.logs.model.FilteredLogEvent;


public class LogsAction extends UserAction {

    private String logGroupName;
    private long startTime;
    private long endTime;
    private String filterPattern;
    private String nextToken;
    private int limit;
    private String output;

    public String fetchLogs() {

        if (!logGroupName.startsWith("akto-")) {
            return ERROR.toUpperCase();
        }

        this.output = "";

        try {
            AWSLogs awsLogs = AWSLogsClientBuilder.defaultClient();
            
            FilterLogEventsRequest filterLogEventsRequest = 
                new FilterLogEventsRequest()
                .withLogGroupName(logGroupName)
                .withLogStreamNames(logGroupName);

            if (endTime != 0) {
                filterLogEventsRequest.setEndTime(endTime);
            }
            
            if (startTime != 0) {
                filterLogEventsRequest.setStartTime(startTime);
            }

            if (limit != 0) {
                filterLogEventsRequest.setLimit(limit);
            }
            
            if (filterPattern != null) {
                filterLogEventsRequest.setFilterPattern(filterPattern);
            }
            
            int counter = 0;
            
            do {
                filterLogEventsRequest.setNextToken(nextToken);
                FilterLogEventsResult filterLogEventsResult = awsLogs.filterLogEvents(filterLogEventsRequest);
                for(FilteredLogEvent filteredLogEvent: filterLogEventsResult.getEvents()) {
                    this.output += filteredLogEvent.getTimestamp() +  ": " + filteredLogEvent.getMessage() + "\n";
                    counter++;
                }
                this.nextToken = filterLogEventsResult.getNextToken();
            } while (nextToken != null && counter < limit);
        } catch (Exception e) {
            this.output += e.getMessage() + "\n";
        }

        return SUCCESS.toUpperCase();
    }


    public String getLogGroupName() {
        return this.logGroupName;
    }

    public void setLogGroupName(String logGroupName) {
        this.logGroupName = logGroupName;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return this.endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getFilterPattern() {
        return this.filterPattern;
    }

    public void setFilterPattern(String filterPattern) {
        this.filterPattern = filterPattern;
    }

    public String getNextToken() {
        return this.nextToken;
    }

    public void setNextToken(String nextToken) {
        this.nextToken = nextToken;
    }

    public int getLimit() {
        return this.limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getOutput() {
        return this.output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
