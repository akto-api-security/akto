import {Spinner, Icon, Tooltip, Button} from '@shopify/polaris';
import {
    PauseCircleMajor, StopMajor, StatusActiveMajor
  } from '@shopify/polaris-icons';
import { State } from './types';
import api from './api';
  

const transform = {
    getStatusBadge:(id,getCurrentAgentState)=>{
        const agentState = getCurrentAgentState(id);
        if(agentState === "thinking"){
            return <span className='agentStatusColor' style={{backgroundColor:"#E4E5E7", borderRadius:"8px",fill:"#5C5F62",color:"#5C5F62"}}>
                <Tooltip dismissOnMouseOut content="Agent is currently working.">
                <Spinner size="small"/>
                </Tooltip> </span>;             
        }
        else if (agentState === "paused"){
            return <span className='agentStatusColor' style={{backgroundColor:"#FFD79D", borderRadius:"8px"}}> <Tooltip dismissOnMouseOut content="Agent is currently paused, waiting for your approval to proceed."> <Icon  color="warning" source={PauseCircleMajor} /></Tooltip> </span>; 
        }
        else if(agentState === "stopped"){
            return <span className='agentStatusColor' style={{backgroundColor:"#FED3D1", borderRadius:"8px"}}><Tooltip dismissOnMouseOut content="Agent is currently stopped."> <Icon color="critical" source={StopMajor} /></Tooltip></span>; 
        }
        else if(agentState === "completed"){
            return <span className='agentStatusColor' style={{backgroundColor:"#AEE9D1", borderRadius:"8px"}}><Tooltip dismissOnMouseOut content="Agent has completed the task"> <Icon color="success" source={StatusActiveMajor} /></Tooltip></span>; 
        }
        else return null;
    },
    getAgentStatusColor:(id,getCurrentAgentState)=>{
        const agentState = getCurrentAgentState(id);
        if(agentState === "thinking"){
            return "bg-subdued";
        }
        else if (agentState === "paused"){
            return "bg-caution-subdued";
        }
        else if(agentState === "stopped"){
            return "bg-critical-subdued";
        }
        else if(agentState === "completed"){
            return "bg-success-subdued";
        }
        else return "bg";
    },
    getStateToAgentState:(state)=>{
        if(state === State.RUNNING || state === State.RE_ATTEMPT){
            return "thinking";
        }
        else if(state === State.COMPLETED){
            return "paused";
        }
        else if(state === State.STOPPED){
            return "stopped";
        }
        else return "idle";
    },
    getTargetNames:(agentId)=>{
        if(["FIND_VULNERABILITIES_FROM_SOURCE_CODE","FIND_APIS_FROM_SOURCE_CODE"].includes(agentId)){
            return "Repository";
        }
        else if (["FIND_SENSITIVE_DATA_TYPES","GROUP_APIS"].includes(agentId)){
            return "Collection";
        }
        else if(["FIND_FALSE_POSITIVE"].includes(agentId)){
            return "Test name"
        }
        else return "Target name";
    },
    getAllSubProcesses: async (processId) => {
        const response = await api.getAllSubProcesses({
            processId: processId
        });
        const subprocesses = (response.subProcesses).sort(
            (a, b) => b.createdTimestamp - a.createdTimestamp
        );
        return subprocesses;
    },
    updateAgentState: (state,agentId="",setAgentState) => {
        setAgentState(state)
    },

}

export default transform;