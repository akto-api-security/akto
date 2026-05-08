package com.akto.action.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.agents.AgentHealthCheckDao;
import com.akto.dao.agents.AgentModelDao;
import com.akto.dao.agents.AgentRunDao;
import com.akto.dao.agents.AgentSubProcessSingleAttemptDao;
import com.akto.dao.agents.DiscoveryAgentRunDao;
import com.akto.dao.agents.DiscoverySubProcessDao;
import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;
import com.akto.dto.HttpResponseParams.Source;
import com.akto.dto.agents.*;
import com.akto.har.HAR;
import com.akto.util.Constants;
import com.akto.utils.Utils;
import com.amazonaws.util.StringUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

public class AgentAction extends UserAction {

    Map<String, Object> data;
    String agent;

    List<AgentRun> agentRuns;
    @Getter
    List<DiscoveryAgentRun> discoveryAgentRuns;
    @Getter
    List<DiscoverySubProcess> discoverySubProcesses;

    String modelName;
    String githubAccessToken;

    public String getAllAgentRuns() {
        Bson filter = Filters.eq(AgentRun._STATE, State.RUNNING);
        boolean discoveryAgent = false;
        if(!StringUtils.isNullOrEmpty(this.agent)){
            filter = Filters.and(filter, Filters.eq("agent", agent));
            if(this.agent.equals(Agent.DISCOVERY_AGENT.name())){
                discoveryAgent = true;
            }
        }
        if(discoveryAgent){
            discoveryAgentRuns = DiscoveryAgentRunDao.instance.findAll(filter, Projections.exclude(AgentRun._AGENT_MODEL, AgentRun._PRIVATE_DATA, DiscoveryAgentRun.AGENT_INIT_DOCUMENT));
        }else{
            agentRuns = AgentRunDao.instance.findAll(filter, Projections.exclude(AgentRun._AGENT_MODEL, AgentRun._PRIVATE_DATA));
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String getAllAgentRunsObject(){
        if(StringUtils.isNullOrEmpty(this.agent)){
            addActionError("Invalid agent");
            return Action.ERROR.toUpperCase();
        }
        agentRuns = AgentRunDao.instance.findAll(Filters.eq("agent", agent));
        return Action.SUCCESS.toUpperCase();
    }


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

        AgentRun existingScheduledOrRunningRuns = AgentRunDao.instance.findOne(Filters.in(
                AgentRun._STATE, Arrays.asList(State.SCHEDULED, State.RUNNING)));

        if (existingScheduledOrRunningRuns != null) {
            addActionError("An existing agent run is running or scheduled. " +
                    "Let it complete or stop it before starting another");
            return Action.ERROR.toUpperCase();
        }

        String processId = UUID.randomUUID().toString();

        Model model = null;
        if (modelName != null && !modelName.isEmpty()) {
            model = AgentModelDao.instance.findOne(Filters.eq(Model._NAME, modelName));
        }

        if (model == null) {
            // if model is not found, then check if it is using default akto model
            if(modelName.trim().equalsIgnoreCase(Constants.AKTO_AGENT_NAME)){
                model = Constants.AKTO_AGENT_MODEL;
            }else{
                addActionError("Model not found");
                return Action.ERROR.toUpperCase();
            }
        }

        // add github access token to the data when saving
        Map<String, String> privateData = new HashMap<>();
        if((agentModule.equals(Agent.FIND_VULNERABILITIES_FROM_SOURCE_CODE) || agentModule.equals(Agent.FIND_APIS_FROM_SOURCE_CODE)) && !StringUtils.isNullOrEmpty(githubAccessToken)){
            privateData.put("githubAccessToken", githubAccessToken);
        }

        agentRun = new AgentRun(processId, data, agentModule,
                Context.now(), 0, 0, State.SCHEDULED, model, privateData);
        AgentRunDao.instance.insertOne(agentRun);

        return Action.SUCCESS.toUpperCase();
    }

    public String updateAgentRun() {
        State updatedState = null;
        try {
            updatedState = State.valueOf(state);
        } catch (Exception e) {
            addActionError("Invalid state");
            return Action.ERROR.toUpperCase();
        }
        Bson processIdFilter = Filters.eq(AgentRun.PROCESS_ID, processId);

        agentRun = AgentRunDao.instance.findOne(processIdFilter);

        if (agentRun == null) {
            addActionError("No process found");
            return Action.ERROR.toUpperCase();
        }

        if (updatedState != null) {
            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set(AgentRun._STATE, updatedState));
            switch (updatedState) {
                case COMPLETED:
                    if (!State.COMPLETED.equals(agentRun.getState())) {
                        updates.add(Updates.set(AgentRun.END_TIMESTAMP, Context.now()));
                    }
                    break;
                case RUNNING:
                    if (!State.RUNNING.equals(agentRun.getState())) {
                        updates.add(Updates.set(AgentRun.START_TIMESTAMP, Context.now()));
                    }
                    break;
                default:
                    break;
            }
            agentRun = AgentRunDao.instance.updateOneNoUpsert(
                    processIdFilter,
                    Updates.combine(updates));
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteAgentRun() {
        if (processId == null || processId.isEmpty()) {
            addActionError("Process ID is required");
            return Action.ERROR.toUpperCase();
        }

        Bson processIdFilter = Filters.eq(AgentRun.PROCESS_ID, processId);
        AgentRun agentRun = AgentRunDao.instance.findOne(processIdFilter);

        if (agentRun == null) {
            addActionError("No agent run found with the given process ID");
            return Action.ERROR.toUpperCase();
        }

        // Only allow deletion of scheduled or running agents
        if (!State.SCHEDULED.equals(agentRun.getState()) && !State.RUNNING.equals(agentRun.getState()) && !State.STOPPED.equals(agentRun.getState())) {
            addActionError("Only scheduled or running agent runs can be deleted");
            return Action.ERROR.toUpperCase();
        }

        try {
            // Delete the agent run
            AgentRunDao.instance.deleteAll(processIdFilter);
            
            // Also delete associated subprocesses if they exist
            AgentSubProcessSingleAttemptDao.instance.deleteAll(Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, processId));
            
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Failed to delete agent run: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    @Setter
    Map<String, Object> results;

    public String updateStateOfDiscoveryAgentRun() {
        if(processId == null || processId.isEmpty()){
            addActionError("Process id is invalid");
            return Action.ERROR.toUpperCase();
        }
        response = new BasicDBObject();
        Bson update = Updates.set(AgentRun._STATE, State.valueOf(state));
        if(results != null && !results.isEmpty()){
            update = Updates.combine(update, Updates.set(DiscoveryAgentRun._RESULTS, results));
        }
        DiscoveryAgentRunDao.instance.updateOneNoUpsert(Filters.eq(AgentRun.PROCESS_ID, processId), update);
        response.put("status", "success");
        return Action.SUCCESS.toUpperCase();
    }

    @Setter
    String hostname;
    @Setter
    String authToken;

    public String createSubProcessNew() {
        if(processId == null || processId.isEmpty()){
            addActionError("Process id is invalid");
            return Action.ERROR.toUpperCase();
        }
        if(hostname == null || hostname.isEmpty()){
            addActionError("Hostname is invalid");
            return Action.ERROR.toUpperCase();
        }
        if(authToken == null || authToken.isEmpty()){
            addActionError("Auth token is invalid");
            return Action.ERROR.toUpperCase();
        }
        Map<String,String> userInputData = new HashMap<>();
        userInputData.put("hostName", hostname);
        userInputData.put("authToken", authToken);

        DiscoverySubProcess subProcess = new DiscoverySubProcess(
            this.processId,
            "1",
            new ArrayList<>(),  
            new HashMap<>(),
            0,
            null,
            State.RUNNING,
            -1,
            Context.now(),
            userInputData
        );
        DiscoverySubProcessDao.instance.insertOne(subProcess);
        response = new BasicDBObject();
        response.put("status", "success");
        return SUCCESS.toUpperCase();
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

    List<Model> models;

    public String getAgentModels() {
        // Do not send the api key etc params, once saved.
        models = AgentModelDao.instance.findAll(Filters.empty(), Projections.exclude(Model._PARAMS));
        models.add(Constants.AKTO_AGENT_MODEL);
        return Action.SUCCESS.toUpperCase();
    }

    List<AgentSubProcessSingleAttempt> subProcesses;

    public String getAllSubProcesses() {
        if (processId == null || processId.isEmpty()) {
            addActionError("Process id is invalid");
            return Action.ERROR.toUpperCase();
        }
        Bson processIdFilter = Filters.eq(AgentRun.PROCESS_ID, processId);
        if(this.agent.equals(Agent.DISCOVERY_AGENT.name())){
            DiscoveryAgentRun discoveryAgentRun = DiscoveryAgentRunDao.instance.findOne(processIdFilter);
            if(discoveryAgentRun != null){
                discoverySubProcesses = DiscoverySubProcessDao.instance.findAll(processIdFilter);
            }
            return Action.SUCCESS.toUpperCase();
        }

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

        if(this.agent.equals(Agent.DISCOVERY_AGENT.name())){
            DiscoveryAgentRun discoveryAgentRun = DiscoveryAgentRunDao.instance.findOne(processIdFilter);
            if(discoveryAgentRun == null){
                addActionError("No discovery agent run found");
                return Action.ERROR.toUpperCase();
            }
            Bson finalFilter = Filters.and(processIdFilter, Filters.eq(AgentSubProcessSingleAttempt.SUB_PROCESS_ID,this.subProcessId));
            DiscoverySubProcess discoverySubProcess = DiscoverySubProcessDao.instance.findOne(finalFilter);
            if(discoverySubProcess == null){
                addActionError("No discovery subprocess found");
                return Action.ERROR.toUpperCase();
            }
            String retryRequest = (String) this.data.get("retryRequest");
            if(retryRequest != null){
                Map<String,String> userInputData = discoverySubProcess.getUserInputData();
                if(userInputData == null){
                    userInputData = new HashMap<>();
                }
                userInputData.put("retryRequest", retryRequest);
                Bson update = Updates.combine(
                    Updates.set("userInputData", userInputData),
                    Updates.set(DiscoverySubProcess.BAD_ERRORS, new HashMap<>()),
                    Updates.set(AgentSubProcessSingleAttempt._STATE, State.RUNNING)
                );
                DiscoverySubProcessDao.instance.updateOneNoUpsert(finalFilter, update);
            }
            return Action.SUCCESS.toUpperCase();
        }

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
                    && (State.ACCEPTED.equals(updatedState) ||
                            State.DISCARDED.equals(updatedState) ||
                            State.RE_ATTEMPT.equals(updatedState))) {
                updates.add(Updates.set(AgentSubProcessSingleAttempt._STATE, updatedState));

            } else {
                addActionError("Invalid state");
                return Action.ERROR.toUpperCase();
            }

            // TODO: handle more state conditions
        }else {
            updates.add(Updates.setOnInsert(AgentSubProcessSingleAttempt._STATE, State.SCHEDULED));
        }

        if (subProcessHeading != null) {
            updates.add(Updates.set(AgentSubProcessSingleAttempt.SUB_PROCESS_HEADING, subProcessHeading));
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

    String subProcessHeading;

    public String getSubProcessHeading() {
        return subProcessHeading;
    }

    public void setSubProcessHeading(String subProcessHeading) {
        this.subProcessHeading = subProcessHeading;
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
                    response.put("agentData", agentRunRunning);
                }

                // else case is nothing is running or scheduled, hence empty object is sufficient for 
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
        
        return SUCCESS.toUpperCase();
    }

    public String getAllAgentRunningDetails(){
        try {
            if (this.processId == null) {
                return ERROR.toUpperCase();
            }

            AgentRun agentRun = AgentRunDao.instance.findOne(Filters.eq(AgentRun.PROCESS_ID, this.processId));
            response = new BasicDBObject();
            if(agentRun != null){
                List<AgentSubProcessSingleAttempt> subprocesses = AgentSubProcessSingleAttemptDao.instance.findAll(Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, agentRun.getProcessId()));
                response.put("type", "initForReboot");
                response.put("data", agentRun);
                response.put("allSubProcesses", subprocesses);
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

    // polling api for discover agent
    public String feedDiscoveryDataToAgent() {
        response = new BasicDBObject();
        if(StringUtils.isNullOrEmpty(this.processId)){
            DiscoveryAgentRun discoveryAgentRun = DiscoveryAgentRunDao.instance.findOne(
                Filters.eq(
                    AgentRun._STATE, State.SCHEDULED.name()
                )
            );
            if(discoveryAgentRun != null){
                response.put("type", "init");
                response.put("data", discoveryAgentRun);
                Map<String,Object> claims = new HashMap<>();
                claims.put("accountId", discoveryAgentRun.getAccountId());
                String apiToken = null;
                if(Context.accountId.get() != 1000_000){
                    try {
                        apiToken = JwtAuthenticator.createJWT(
                        claims,
                        "Akto",
                        "invite_user",
                        Calendar.HOUR,
                            3
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    response.put("apiToken", apiToken);
                }
                
            }
        }else{
            DiscoveryAgentRun discoveryAgentRun = DiscoveryAgentRunDao.instance.findOne(Filters.eq(AgentRun.PROCESS_ID, this.processId));
            DiscoverySubProcess discoverySubProcess = DiscoverySubProcessDao.instance.findLatestOne(Filters.eq("processId", discoveryAgentRun.getProcessId()));
            if(discoverySubProcess != null){
                response.put("type", "subTask");
                response.put("data", discoverySubProcess);
            }
        }
        
        return SUCCESS.toUpperCase();
    }

    List<AgentLog> logs;

    public void setLogs(List<AgentLog> logs) {
        this.logs = logs;
    }

    @Setter
    String currentProcessingUrlInDiscoveryFromAgent;

    public String receiveDataFromAgent() {
        response = new BasicDBObject();
        Agent agentModule = null;
        if(!StringUtils.isNullOrEmpty(this.agent)){
            try{
                agentModule = Agent.valueOf(this.agent);
            }catch(Exception e){
            }
        }
        Bson filter = Filters.and(
            Filters.eq(AgentSubProcessSingleAttempt.PROCESS_ID, this.processId),
            Filters.eq(AgentSubProcessSingleAttempt.SUB_PROCESS_ID, this.subProcessId)
        );
        if(!agentModule.equals(Agent.DISCOVERY_AGENT)){
            filter = Filters.and(
                filter,
                Filters.eq(AgentSubProcessSingleAttempt.ATTEMPT_ID, this.attemptId)
            );
        }
        if(!StringUtils.isNullOrEmpty(type)){
            if(type.equals("logs")){
                if(this.logs == null || this.logs.isEmpty()){
                    return SUCCESS.toUpperCase();
                }
            }else if(type.equals("batchedData")){
                if(this.data.isEmpty()){
                    return SUCCESS.toUpperCase();
                }else{
                    List<Object> outputOptions = (List<Object>) this.data.get("outputOptions");
                    if(outputOptions == null || outputOptions.isEmpty()){
                        return SUCCESS.toUpperCase();
                    }
                }
            }
        }
            
        switch (type) {
            case "logs":
                AgentSubProcessSingleAttemptDao.instance.updateOneNoUpsert(
                    filter,
                    Updates.addEachToSet(AgentSubProcessSingleAttempt._LOGS, this.logs)
                );
                break;

            case "stateChange":
                State state = State.valueOf(this.state);

                Bson update = Updates.set(AgentSubProcessSingleAttempt._STATE, state);
                if(this.data != null && !this.data.isEmpty()){
                    update = Updates.combine(
                        update,
                        Updates.set(AgentSubProcessSingleAttempt.PROCESS_OUTPUT, this.data)
                    );
                }
                try {
                    AgentSubProcessSingleAttemptDao.instance.updateOneNoUpsert(
                        filter,
                        update
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    return ERROR.toUpperCase();
                }
                break;
            case "subProcessHeading":
                AgentSubProcessSingleAttemptDao.instance.updateOneNoUpsert(
                    filter,
                    Updates.set(AgentSubProcessSingleAttempt.SUB_PROCESS_HEADING, subProcessHeading)
                );
                break;
            case "batchedData":
                List<Object> batchedList = (List<Object>) this.data.get("outputOptions");
                update = Updates.set(
                    AgentSubProcessSingleAttempt.PROCESS_OUTPUT + ".selectionType",
                    "batched"
                );
                AgentSubProcessSingleAttemptDao.instance.updateOne(
                    filter,
                    Updates.combine(
                        update,
                        Updates.addEachToSet(AgentSubProcessSingleAttempt.PROCESS_OUTPUT + ".outputOptions", batchedList)
                    ));
                break;
            case "logs_discovery":
                DiscoverySubProcessDao.instance.updateOneNoUpsert(
                    filter,
                    Updates.addEachToSet(AgentSubProcessSingleAttempt._LOGS, this.logs)
                );
                break;
            case "error_request":
                String currentProcessingUrlInDiscovery = (String) this.data.get("url");
                String currentRequestForUrl = (String) this.data.get("request");
                String errorMessage = (String) this.data.get("error");
                BasicDBObject badError = new BasicDBObject().append("url", currentProcessingUrlInDiscovery).append("request", currentRequestForUrl).append("error", errorMessage);
                DiscoverySubProcess subProcess = DiscoverySubProcessDao.instance.findOne(filter);
                subProcess.getBadErrors().put(currentProcessingUrlInDiscovery, badError);
                DiscoverySubProcessDao.instance.updateOneNoUpsert(
                    filter,
                    Updates.set(DiscoverySubProcess.BAD_ERRORS, subProcess.getBadErrors())
                );
                break;
            case "increment_processed_apis":
                DiscoverySubProcessDao.instance.updateOneNoUpsert(
                    filter,
                    Updates.combine(
                        Updates.inc(DiscoverySubProcess.PROCESSED_APIS_TILL_NOW, 1),
                        Updates.set(DiscoverySubProcess.CURRENT_URL_STRING, currentProcessingUrlInDiscoveryFromAgent)
                    )
                );
                break;
            case "metadata_discovery":
                Map<String,String> metaData = (Map<String,String>) this.data.get("metadata");
                String name = (String) this.data.get("name");
                BasicDBObject metaDataObject = new BasicDBObject().append("name", name).append("items", metaData);
                if(metaData == null){
                    return SUCCESS.toUpperCase();
                }
                DiscoveryAgentRunDao.instance.updateOneNoUpsert(
                    Filters.eq(AgentRun.PROCESS_ID, this.processId),
                    Updates.addToSet("agentStats", metaDataObject)
                );
                break;

            case "harData":
                HAR har = new HAR();
                DiscoveryAgentRun discoveryAgentRun = DiscoveryAgentRunDao.instance.findOne(Filters.eq(AgentRun.PROCESS_ID, this.processId));
                int apiCollectionId = discoveryAgentRun.getApiCollectionId();
                String harString = (String) this.data.get("harContent");
                try {
                    List<String> messages = har.getMessages(harString, apiCollectionId, Context.accountId.get(), Source.HAR);
                    List<String> harErrors = har.getErrors();
                    Utils.pushDataToKafka(apiCollectionId, "", messages, harErrors, true, true, true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
            default:
                break;
        }
        response.put("success", "200 ok");
        return SUCCESS.toUpperCase();
    }

    String instanceId;
    String version;

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

    boolean agentRunningOnModule;

    public String checkAgentRunModule() {
        if (processId == null || processId.isEmpty()) {
            addActionError("Process Id is empty");
            return Action.ERROR.toUpperCase();
        }
        agentRunningOnModule = AgentHealthCheckDao.instance.isProcessRunningSomewhere(processId);
        return Action.SUCCESS.toUpperCase();
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

    public List<Model> getModels() {
        return models;
    }

    public void setModels(List<Model> models) {
        this.models = models;
    }
    
    public BasicDBObject getResponse() {
        return response;
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
    
    public boolean getAgentRunningOnModule() {
        return agentRunningOnModule;
    }

    public void setAgentRunningOnModule(boolean agentRunningOnModule) {
        this.agentRunningOnModule = agentRunningOnModule;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setGithubAccessToken(String githubAccessToken) {
        this.githubAccessToken = githubAccessToken;
    }

}
