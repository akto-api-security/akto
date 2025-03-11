package com.akto.action.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.agents.AgentRunDao;
import com.akto.dao.agents.AgentSubProcessSingleAttemptDao;
import com.akto.dao.context.Context;
import com.akto.dto.agents.*;
import com.amazonaws.util.StringUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class AgentAction extends UserAction {

    Map<String, Object> data;
    String agent;

    List<AgentRun> agentRuns;

    public String getAllAgentRuns() {
        Bson filter = Filters.eq(AgentRun._STATE, State.RUNNING);
        if(!StringUtils.isNullOrEmpty(this.agent)){
            filter = Filters.and(filter, Filters.eq("agent", agent));
        }
        agentRuns = AgentRunDao.instance.findAll(filter);
        return Action.SUCCESS.toUpperCase();
    }

    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

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

        // TODO: Use health check for dashboard to trigger the agent module.
        int accountId = Context.accountId.get();
        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                try {
                    AgentRunDao.instance.updateOne(
                        Filters.eq(AgentRun.PROCESS_ID, processId),
                        Updates.combine(
                            Updates.setOnInsert(AgentRun.START_TIMESTAMP, Context.now()),
                            Updates.set(AgentRun._STATE, State.RUNNING)
                        )
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 5 , TimeUnit.SECONDS);

        return Action.SUCCESS.toUpperCase();
    }

    Agent[] agents;

    AgentRun agentRun;

    String processId;
    String subProcessId;
    int attemptId;
    String state;

    AgentSubProcessSingleAttempt subprocess;

    public String getMemberAgents() {
        agents = Agent.values();
        return Action.SUCCESS.toUpperCase();
    }

    Model[] models;

    public String getAgentModels() {
        models = Model.values();
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

        Bson filter = Filters.and(
            Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, this.processId.toString()),
            Filters.eq(AgentSubProcessSingleAttempt.SUB_PROCESS_ID, this.subProcessId),
            Filters.eq(AgentSubProcessSingleAttempt.ATTEMPT_ID, this.attemptId)
        );
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

        Bson filter = Filters.and(
            Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, this.processId),
            Filters.eq(AgentSubProcessSingleAttempt.SUB_PROCESS_ID, this.subProcessId),
            Filters.eq(AgentSubProcessSingleAttempt.ATTEMPT_ID, this.attemptId)
        );

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

    BasicDBObject response;
    public String feedDataToAgent(){
        try {
            AgentRun agentRun = AgentRunDao.instance.findOne(Filters.eq(AgentRun._STATE, State.SCHEDULED.toString()));
            response = new BasicDBObject();
            if(agentRun != null){
                // case 1: info about the agent as the agent is just triggered
                response.put("type", "init");
                response.put("data", agentRun);
            }else{
                // case 2: info about the agent as the agent is running
                AgentRun agentRunRunning = AgentRunDao.instance.findOne(Filters.eq(AgentRun._STATE, State.RUNNING.toString()), Projections.include(AgentRun.PROCESS_ID));
                if(agentRunRunning != null){
                    response.put("type", "subTask");
                    // get subprocess data of the latest one only, i am assuming that if chosen retry attempt, the data of later will get cleaned up from mongo, the new one would be inserted
                    AgentSubProcessSingleAttempt subprocess = AgentSubProcessSingleAttemptDao.instance.findLatestOne(Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, agentRunRunning.getProcessId()));
                    response.put("data", subprocess);
                }

                // else case is nothing is running or scheduled, hence empty object is sufficient for 
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
        
        return SUCCESS.toUpperCase();
    }

    String type;
    public void setType(String type) {
        this.type = type;
    }

    List<AgentLog> logs;

    public void setLogs(List<AgentLog> logs) {
        this.logs = logs;
    }

    public String receiveDataFromAgent(){
        response = new BasicDBObject();
        Bson filter = Filters.and(
            Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, this.processId),
            Filters.eq(AgentSubProcessSingleAttempt.SUB_PROCESS_ID, this.subProcessId),
            Filters.eq(AgentSubProcessSingleAttempt.ATTEMPT_ID, this.attemptId)
        );
        switch (type) {
            case "logs":
                AgentSubProcessSingleAttemptDao.instance.updateOne(
                    filter,
                    Updates.addEachToSet(AgentSubProcessSingleAttempt._LOGS, this.logs)
                );
                break;

            case "stateChange":
                State state = State.valueOf(this.state);
                try {
                    AgentSubProcessSingleAttemptDao.instance.updateOne(
                        filter,
                        Updates.combine(
                            Updates.set(AgentSubProcessSingleAttempt._STATE, state),
                            Updates.set(AgentSubProcessSingleAttempt.PROCESS_OUTPUT, this.data)
                        )
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    return ERROR.toUpperCase();
                }
                break;
        
            default:
                break;
        }
        response.put("success", "200 ok");
        return SUCCESS.toUpperCase();
    }

    public List<AgentRun> getAgentRuns() {
        return agentRuns;
    }

    public void setAgentRuns(List<AgentRun> agentRuns) {
        this.agentRuns = agentRuns;
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

    public Agent[] getAgents() {
        return agents;
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

    public Model[] getModels() {
        return models;
    }

    public void setModels(Model[] models) {
        this.models = models;
    }
    
    public BasicDBObject getResponse() {
        return response;
    }

}
