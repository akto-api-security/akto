package com.akto.dto.agents;

import java.util.Map;

public class AgentRun {

    /*
     * This is a random generated UUID
     * The process id for the entire agent. Created once per agent.
     * process e.g. finding vulnerabilities from source code
     * Same as AgentSubProcessSingleAttempt.processId
     */
    String processId;
    final public static String PROCESS_ID = "processId";
    /*
     * Contains agent init information
     * e.g. for vulnerability finding agent may contain the repository to run the
     * agent on.
     * for sensitive data finding agent may contain the collection to run the agent
     * on.
     * 
     * Generic Map data type because the data may vary according to agent.
     */
    Map<String, Object> agentInitDocument;
    Agent agent;
    int createdTimestamp;
    final public static String CREATED_TIMESTAMP = "createdTimestamp";
    final public static String START_TIMESTAMP = "startTimestamp";
    int startTimestamp;
    final public static String END_TIMESTAMP = "endTimestamp";
    int endTimestamp;
    State state;
    final public static String _STATE = "state";

    Model model;

    public AgentRun(String processId, Map<String, Object> agentInitDocument, Agent agent, int createdTimestamp,
            int startTimestamp, int endTimestamp, State state, Model model) {
        this.processId = processId;
        this.agentInitDocument = agentInitDocument;
        this.agent = agent;
        this.createdTimestamp = createdTimestamp;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.state = state;
        this.model = model;
    }

    public AgentRun() {
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public Map<String, Object> getAgentInitDocument() {
        return agentInitDocument;
    }

    public void setAgentInitDocument(Map<String, Object> agentInitDocument) {
        this.agentInitDocument = agentInitDocument;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public int getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(int createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

}
