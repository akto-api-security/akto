package com.akto.action.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.agents.AgentRunDao;
import com.akto.dao.agents.AgentSubProcessSingleAttemptDao;
import com.akto.dao.context.Context;
import com.akto.dto.agents.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class AgentAction extends UserAction {

    Map<String, Object> data;
    String agent;

    public String createAgentRun() {

        if (data == null || data.isEmpty()) {
            addActionError("Agent Init document is empty");
            return Action.ERROR.toUpperCase();
        }

        if (agent == null || agent.isEmpty()) {
            addActionError("Agent is empty");
            return Action.ERROR.toUpperCase();
        }

        Agent agentModule;
        try {
            agentModule = Agent.valueOf(agent);
        } catch (Exception e) {
            addActionError("Invalid agent");
            return Action.ERROR.toUpperCase();
        }

        AgentRun existingScheduledOrRunningRuns = AgentRunDao.instance.findOne(Filters.nin(
                AgentRun._STATE, Arrays.asList(State.SCHEDULED, State.RUNNING)));

        if (existingScheduledOrRunningRuns != null) {
            addActionError("An existing agent run is running or scheduled. " +
                    "Let it complete or stop it before starting another");
            return Action.ERROR.toUpperCase();
        }

        String processId = UUID.randomUUID().toString();

        agentRun = new AgentRun(processId, data, agentModule,
                Context.now(), 0, 0, State.SCHEDULED);

        AgentRunDao.instance.insertOne(agentRun);

        return Action.SUCCESS.toUpperCase();
    }

    Agent[] agents;

    AgentRun agentRun;

    String processId;
    int subProcessId;
    int attemptId;
    String state;

    AgentSubProcessSingleAttempt subprocess;

    public String getAgents() {
        agents = Agent.values();
        return Action.SUCCESS.toUpperCase();
    }

    List<AgentSubProcessSingleAttempt> subProcesses;

    public String getAllSubProcesses() {
        if (processId == null || processId.isEmpty()) {
            addActionError("Process id is invalid");
            return Action.ERROR.toUpperCase();
        }
        Bson processIdFilter = Filters.eq(AgentRun.PROCESS_ID, processId);

        agentRun = AgentRunDao.instance.findOne(processIdFilter);

        if (agentRun == null) {
            addActionError("No process found");
            return Action.ERROR.toUpperCase();
        }

        subProcesses = AgentSubProcessSingleAttemptDao.instance.findAll(processIdFilter);
        return Action.SUCCESS.toUpperCase();
    }

    public String getSubProcess(){

        if (processId == null || processId.isEmpty()) {
            addActionError("Process id is invalid");
            return Action.ERROR.toUpperCase();
        }

        Bson processIdFilter = Filters.eq(AgentRun.PROCESS_ID, processId);

        agentRun = AgentRunDao.instance.findOne(processIdFilter);

        if (agentRun == null) {
            addActionError("No process found");
            return Action.ERROR.toUpperCase();
        }

        Bson filter = AgentSubProcessSingleAttemptDao.instance.getFiltersForAgentSubProcess(processId, subProcessId, attemptId);
        subprocess = AgentSubProcessSingleAttemptDao.instance.findOne(filter);

        return Action.SUCCESS.toUpperCase();

    }

    public String updateAgentSubprocess() {

        if (processId == null || processId.isEmpty()) {
            addActionError("Process id is invalid");
            return Action.ERROR.toUpperCase();
        }

        Bson processIdFilter = Filters.eq(AgentRun.PROCESS_ID, processId);

        agentRun = AgentRunDao.instance.findOne(processIdFilter);

        if (agentRun == null) {
            addActionError("No process found");
            return Action.ERROR.toUpperCase();
        }

        Bson filter = AgentSubProcessSingleAttemptDao.instance.getFiltersForAgentSubProcess(processId, subProcessId,
                attemptId);

        List<Bson> updates = new ArrayList<>();

        updates.add(Updates.setOnInsert(AgentSubProcessSingleAttempt.CREATED_TIMESTAMP, Context.now()));
        updates.add(Updates.setOnInsert(AgentSubProcessSingleAttempt._STATE, State.SCHEDULED));

        State updatedState = null;
        try {
            updatedState = State.valueOf(state);
        } catch (Exception e) {
        }

        if (updatedState != null) {
            AgentSubProcessSingleAttempt subProcess = AgentSubProcessSingleAttemptDao.instance.findOne(filter);
            if (subProcess == null) {
                addActionError("No subprocess found");
                return Action.ERROR.toUpperCase();
            }

            if (State.COMPLETED.equals(subProcess.getState())
                    && (State.ACCEPTED.equals(updatedState) || State.DISCARDED.equals(updatedState))) {
                updates.add(Updates.set(AgentSubProcessSingleAttempt._STATE, updatedState));
            } else {
                addActionError("Invalid state");
                return Action.ERROR.toUpperCase();
            }
        }

        /*
         * For a new subprocess.
         */
        if (data != null) {
            updates.add(Updates.set(AgentSubProcessSingleAttempt.USER_INPUT, data));
        }

        /*
         * Upsert: true.
         * Since state management is through dashboard,
         * all subprocess' are created here and only modified using the agent-module
         */
        subprocess = AgentSubProcessSingleAttemptDao.instance.updateOne(filter, Updates.combine(updates));

        return Action.SUCCESS.toUpperCase();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public void setAgents(Agent[] agents) {
        this.agents = agents;
    }

    public AgentRun getAgentRun() {
        return agentRun;
    }

    public void setAgentRun(AgentRun agentRun) {
        this.agentRun = agentRun;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public int getSubProcessId() {
        return subProcessId;
    }

    public void setSubProcessId(int subProcessId) {
        this.subProcessId = subProcessId;
    }

    public int getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(int attemptId) {
        this.attemptId = attemptId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public AgentSubProcessSingleAttempt getSubprocess() {
        return subprocess;
    }

    public void setSubprocess(AgentSubProcessSingleAttempt subprocess) {
        this.subprocess = subprocess;
    }

    public List<AgentSubProcessSingleAttempt> getSubProcesses() {
        return subProcesses;
    }

    public void setSubProcesses(List<AgentSubProcessSingleAttempt> subProcesses) {
        this.subProcesses = subProcesses;
    }

}
