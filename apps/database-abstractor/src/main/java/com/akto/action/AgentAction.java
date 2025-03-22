package com.akto.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.conversions.Bson;

import com.akto.dao.agents.AgentHealthCheckDao;
import com.akto.dao.agents.AgentRunDao;
import com.akto.dao.agents.AgentSubProcessSingleAttemptDao;
import com.akto.dao.context.Context;
import com.akto.dto.agents.AgentLog;
import com.akto.dto.agents.AgentRun;
import com.akto.dto.agents.AgentSubProcessSingleAttempt;
import com.akto.dto.agents.State;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionSupport;

public class AgentAction extends ActionSupport {

    String instanceId;
    String version;
    String processId;

    public String agentHealth() {

        if (instanceId == null || instanceId.isEmpty()) {
            addActionError("InstanceId is empty");
            return Action.ERROR.toUpperCase();
        }
        if (version == null || version.isEmpty()) {
            addActionError("Agent Version is missing");
            return Action.ERROR.toUpperCase();
        }

        AgentHealthCheckDao.instance.saveHealth(instanceId, version, processId);
        return Action.SUCCESS.toUpperCase();
    }

    AgentRun agentRun;

    public String findEarliestPendingAgentRun() {

        List<AgentRun> oldestRunList = AgentRunDao.instance.findAll(Filters.eq(AgentRun._STATE, State.SCHEDULED), 0, 1,
                Sorts.ascending(AgentRun.CREATED_TIMESTAMP));

        if (oldestRunList.isEmpty()) {
            return Action.SUCCESS.toUpperCase();
        }

        agentRun = oldestRunList.get(0);
        return Action.SUCCESS.toUpperCase();
    }

    String state;

    public String updateAgentProcessState() {

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

        State updatedState = null;
        try {
            updatedState = State.valueOf(state);
        } catch (Exception e) {
            addActionError("Invalid process state");
            return Action.ERROR.toUpperCase();
        }

        List<Bson> updates = new ArrayList<>();

        /*
         * TODO: Add all validations for state filters based
         * on current filters and updated filters
         */
        if (State.RUNNING.equals(updatedState)) {
            if (State.SCHEDULED.equals(agentRun.getState())) {
                updates.add(Updates.set(AgentRun._STATE, State.RUNNING));
                updates.add(Updates.set(AgentRun.START_TIMESTAMP, Context.now()));
            } else {
                addActionError("Only scheduled process can be started");
                return Action.ERROR.toUpperCase();
            }
        }

        if (State.COMPLETED.equals(updatedState)) {
            if (State.RUNNING.equals(agentRun.getState())) {
                updates.add(Updates.set(AgentRun._STATE, State.COMPLETED));
                updates.add(Updates.set(AgentRun.END_TIMESTAMP, Context.now()));
            } else {
                addActionError("Only running process can be completed");
                return Action.ERROR.toUpperCase();
            }
        }

        AgentRunDao.instance.updateOne(processIdFilter, Updates.combine(updates));

        return Action.SUCCESS.toUpperCase();

    }

    int attemptId;
    String subProcessId;
    String subProcessHeading;
    String log;
    Map<String, Object> processOutput;
    AgentSubProcessSingleAttempt subprocess;

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

        Bson filter = AgentSubProcessSingleAttemptDao.instance.getFiltersForAgentSubProcess(processId, subProcessId, attemptId);

        AgentSubProcessSingleAttempt subProcess = AgentSubProcessSingleAttemptDao.instance.findOne(filter);

        if (subProcess == null) {
            addActionError("No subprocess found");
            return Action.ERROR.toUpperCase();
        }

        State updatedState = null;
        try {
            updatedState = State.valueOf(state);
        } catch (Exception e) {
        }

        List<Bson> updates = new ArrayList<>();

        if (updatedState!=null && State.RUNNING.equals(updatedState) && State.SCHEDULED.equals(subProcess.getState())) {
            updates.add(Updates.set(AgentSubProcessSingleAttempt.START_TIMESTAMP, Context.now()));
            updates.add(Updates.set(AgentSubProcessSingleAttempt._STATE, State.RUNNING));
        }

        if (subProcessHeading != null) {
            updates.add(Updates.set(AgentSubProcessSingleAttempt.SUB_PROCESS_HEADING, subProcessHeading));
        }

        if (log != null) {
            updates.add(Updates.addToSet(AgentSubProcessSingleAttempt._LOGS, new AgentLog(log, Context.now())));
        }

        if (updatedState!=null && processOutput != null && State.COMPLETED.equals(updatedState)) {
            updates.add(Updates.set(AgentSubProcessSingleAttempt.PROCESS_OUTPUT, processOutput));
            updates.add(Updates.set(AgentSubProcessSingleAttempt._STATE, State.COMPLETED));
            updates.add(Updates.set(AgentSubProcessSingleAttempt.END_TIMESTAMP, Context.now()));
        }

        /*
         * Upsert: false 
         * because the state is controlled by user inputs.
         */
        subprocess = AgentSubProcessSingleAttemptDao.instance.updateOneNoUpsert(filter, Updates.combine(updates));

        return Action.SUCCESS.toUpperCase();
    }

    /*
     * 
     * flow for subprocess:
     * 
     * subprocess complete
     * |-> user accepts -> accept state marked from dashboard
     * and agent moves to next subprocess, which is also created from dashboard.
     * |-> user declines -> declined state marked from dashboard
     * and new subprocess single attempt created from dashboard with user input.
     * 
     */

    public String getSubProcess() {

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

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public AgentRun getAgentRun() {
        return agentRun;
    }

    public void setAgentRun(AgentRun agentRun) {
        this.agentRun = agentRun;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(int attemptId) {
        this.attemptId = attemptId;
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

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public Map<String, Object> getProcessOutput() {
        return processOutput;
    }

    public void setProcessOutput(Map<String, Object> processOutput) {
        this.processOutput = processOutput;
    }

    public AgentSubProcessSingleAttempt getSubprocess() {
        return subprocess;
    }

    public void setSubprocess(AgentSubProcessSingleAttempt subprocess) {
        this.subprocess = subprocess;
    }

}