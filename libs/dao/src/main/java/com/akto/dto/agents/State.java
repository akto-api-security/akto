package com.akto.dto.agents;

public enum State {

    STOPPED, // agent/subprocess stopped
    RUNNING, // agent/subprocess running
    COMPLETED, // subprocess completed by agent, waiting on user for 
    // 1. approve
    // 2. discard => 
    // 2a. reattempt
    // 2b. provide solution
    SCHEDULED, // agent/subprocess scheduled
    FAILED, // agent/subprocess failed
    DISCARDED, // subprocess discarded by user and solution provided by user
    RE_ATTEMPT, // subprocess discarded by user and attempted again with more information 
    // => creates a new subprocess with attemptId+1
    // => preserves agent solution here.
    ACCEPTED, // subprocess accepted by user.
    PENDING, // subprocess ...
    AGENT_ACKNOWLEDGED, // agent acknowledged the user-input
}