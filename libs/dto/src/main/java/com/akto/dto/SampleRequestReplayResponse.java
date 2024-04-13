package com.akto.dto;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class SampleRequestReplayResponse {
    
    private ArrayList<OriginalHttpResponse> replayedResponses;
    private ArrayList<Map<String, Set<String>>> replayedResponseMap;

    public SampleRequestReplayResponse(ArrayList<OriginalHttpResponse> replayedResponses, ArrayList<Map<String, Set<String>>> replayedResponseMap) {
        this.replayedResponses = replayedResponses;
        this.replayedResponseMap = replayedResponseMap;
    }

    public SampleRequestReplayResponse() { }

    public ArrayList<OriginalHttpResponse> getReplayedResponses() {
        return replayedResponses;
    }

    public void setReplayedResponses(ArrayList<OriginalHttpResponse> replayedResponses) {
        this.replayedResponses = replayedResponses;
    }

    public ArrayList<Map<String, Set<String>>> getReplayedResponseMap() {
        return replayedResponseMap;
    }

    public void setReplayedResponseMap(ArrayList<Map<String, Set<String>>> replayedResponseMap) {
        this.replayedResponseMap = replayedResponseMap;
    }
    
}
