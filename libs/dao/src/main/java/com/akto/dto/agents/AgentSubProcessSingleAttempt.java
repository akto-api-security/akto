package com.akto.dto.agents;

import java.util.List;
import java.util.Map;

public class AgentSubProcessSingleAttempt {

    /*
     * This is a random generated UUID
     * The process id for the entire agent. Created once per agent.
     * process e.g. finding vulnerabilities from source code
     * Same as AgentRun.processId
     */
    String processId;
    final public static String PROCESS_ID = "processId";
    /*
     * The sub process id for the subprocess in the agent.
     * subprocess e.g. find authentication mechanisms subprocess
     * inside finding vulnerabilities from source code process.
     * 
     * Cardinal number for subProcess
     * 
     * This ID is per subProcess.
     * So for multiple attempts remains same.
     */
    String subProcessId;
    final public static String SUB_PROCESS_ID = "subProcessId";
    String subProcessHeading;
    final public static String SUB_PROCESS_HEADING = "subProcessHeading";

    /*
     * By default user input is empty.
     * User can provide input if they choose to,
     * to help the agent.
     * 
     * Using a generic type here as the user input
     * could be in a variety of formats
     * 
     * TODO: make abstract classes for userInput, agentOutput and apiInitDocument
     * and implement them based on feedback on what to expect to/from agent.
     * 
     */
    Map<String, Object> userInput;
    final public static String USER_INPUT = "userInput";
    int createdTimestamp;
    final public static String CREATED_TIMESTAMP = "createdTimestamp";
    int startTimestamp;
    final public static String START_TIMESTAMP = "startTimestamp";
    int endTimestamp;
    final public static String END_TIMESTAMP = "endTimestamp";
    State state;
    final public static String _STATE = "state";
    /*
     * Cardinal number for attempt
     */
    int attemptId;
    final public static String ATTEMPT_ID = "attemptId";
    List<AgentLog> logs;
    final public static String _LOGS = "logs";
    /*
     * Using a generic type here as the agent output
     * could be in a variety of formats
     * 
     * e.g.
     * 
     * backendDirectoriesFound: [ '/app/dir1', '/app/dir2' ]
     * backendFrameworksFound: ['struts2', 'mux']
     * vulnerabilities: "The source code contains no vulnerabilities"
     * vulnerabilitiesFound: [ {vulnerability: 'BOLA', severity: 'HIGH',
     * listOfAPIs:['/api/testing', '/api/finalTesting', '/api/finalFinalTesting']} ]
     * 
     */
    Map<String, Object> processOutput;
    final public static String PROCESS_OUTPUT = "processOutput";

    public class CurrentProcessState {
        String processId;
        String subProcessId;
        int attemptId;

        public CurrentProcessState(String processId, String subProcessId, int attemptId) {
            this.processId = processId;
            this.subProcessId = subProcessId;
            this.attemptId = attemptId;
        }

        public CurrentProcessState() {
        }

        public String getProcessId() {
            return processId;
        }

        public void setProcessId(String processId) {
            this.processId = processId;
        }

        public String getSubProcessId() {
            return subProcessId;
        }

        public void setSubProcessId(String subProcessId) {
            this.subProcessId = subProcessId;
        }

        public int getAttemptId() {
            return attemptId;
        }

        public void setAttemptId(int attemptId) {
            this.attemptId = attemptId;
        }
        
    }

    public AgentSubProcessSingleAttempt(String processId, String subProcessId, String subProcessHeading,
            Map<String, Object> userInput, int createdTimestamp, int startTimestamp, int endTimestamp, State state,
            int attemptId, List<AgentLog> logs, Map<String, Object> processOutput) {
        this.processId = processId;
        this.subProcessId = subProcessId;
        this.subProcessHeading = subProcessHeading;
        this.userInput = userInput;
        this.createdTimestamp = createdTimestamp;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.state = state;
        this.attemptId = attemptId;
        this.logs = logs;
        this.processOutput = processOutput;
    }

    public AgentSubProcessSingleAttempt() {
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getSubProcessId() {
        return subProcessId;
    }

    public void setSubProcessId(String subProcessId) {
        this.subProcessId = subProcessId;
    }

    public String getSubProcessHeading() {
        return subProcessHeading;
    }

    public void setSubProcessHeading(String subProcessHeading) {
        this.subProcessHeading = subProcessHeading;
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

    public int getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(int attemptId) {
        this.attemptId = attemptId;
    }

    public List<AgentLog> getLogs() {
        return logs;
    }

    public void setLogs(List<AgentLog> logs) {
        this.logs = logs;
    }

    public Map<String, Object> getProcessOutput() {
        return processOutput;
    }

    public void setProcessOutput(Map<String, Object> processOutput) {
        this.processOutput = processOutput;
    }

    public Map<String, Object> getUserInput() {
        return userInput;
    }

    public void setUserInput(Map<String, Object> userInput) {
        this.userInput = userInput;
    }

    public int getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(int createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

}
