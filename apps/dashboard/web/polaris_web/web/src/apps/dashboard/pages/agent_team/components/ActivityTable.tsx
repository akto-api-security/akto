import React, { useEffect, useState } from 'react';
import { Box } from '@shopify/polaris';
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import api from '../api';
import { useAgentsStore } from '../agents.store';
import { AgentRun, AgentSubprocess, State } from '../types';
import { useAgentsStateStore } from '../agents.state.store';
import func from '../../../../../util/func';
import testEditorRequests from '../../test_editor/api';
import ShowListInBadge from '../../../components/shared/ShowListInBadge';
import transform from '../transform';


interface TableData {
    start_time: string;
    targetName: any;
    action: string;
    duration: string;
    details: string;
}

function ActivityTable({ agentId }) {

    const [data, setData] = useState<TableData[]>([]);



    const headings = [
        {
            title: "Start time",
            value: "start_time",
            textValue: "name",
            sortActive: true,
        },
        {
            title: transform.getTargetNames(agentId),
            value: "targetName",
        },
        {
            title: "Action",
            value: "action",
        },
        {
            title: "Duration",
            value: "duration",
        },
        {
            title: "Details",
            value: "details",
        }
    ]

    const resourceName = {
        singular: 'agent activity',
        plural: 'agent activities',
    };
    
    const { setCurrentProcessId, resetStore } = useAgentsStore();
    const { setCurrentAgentProcessId, resetAgentState } = useAgentsStateStore();


    const getAllSubProcesses = async (processId: string) => {
        const response = await api.getAllSubProcesses({
            processId: processId
        });
        const subprocesses = (response.subProcesses as AgentSubprocess[]).sort(
            (a, b) => b.createdTimestamp - a.createdTimestamp
        );
        return subprocesses;
    }

    const getDetails = async (runData: AgentRun) => {
        let details = "";
    
        if (runData.state === State.SCHEDULED) {
            details = "Agent is scheduled";
        } else if ([State.RUNNING, State.COMPLETED].includes(runData.state)) {
            const subprocesses = await getAllSubProcesses(runData.processId);
            let lastSubprocess = subprocesses.find(subprocess => subprocess.state === "ACCEPTED");
    
            details = `${lastSubprocess?.subProcessHeading ?? ""} ${
                lastSubprocess?.userInput?.length > 0
                    ? lastSubprocess?.userInput
                    : lastSubprocess?.processOutput?.outputMessage ?? ""
            }`;
    
        } else if (runData.state === State.FAILED) {
            const subprocesses = await getAllSubProcesses(runData.processId);
            let lastSubprocess = subprocesses.find(subprocess => subprocess.state === State.FAILED);
    
            details = `Agent failed ${lastSubprocess?.processOutput?.outputMessage ?? ""}`;
        }
    
        return details;
    };
    

    const getTargetNames = (runData: AgentRun) => {
        if (!runData?.agentInitDocument) {
            return "-";
        }
        const targetNames = Object.values(runData.agentInitDocument)
        .flat() 
        .filter(Boolean); 
        return <ShowListInBadge
            itemsArr={[...targetNames]}
            maxItems={1}
            maxWidth={"250px"}
            status={"new"}
            itemWidth={"200px"}
            useBadge={false}
        />
    }


    const fetchTable = async () => {
        let agentRuns: AgentRun[] = [];
        try {
            const response = (await api.getAllAgentRunsObject(agentId));
            agentRuns = response as AgentRun[];
            if (agentRuns.length > 0 && agentRuns[0]?.processId) {
                setCurrentProcessId(agentRuns[0]?.processId)
                setCurrentAgentProcessId(agentId, agentRuns[0]?.processId)
            } else {
                // TODO: handle cases here, because the above API only gets "RUNNING" Agents.
                // setCurrentProcessId("")
                resetStore();
                resetAgentState(agentId);
            }
        } catch (error) {
            resetStore();
            resetAgentState(agentId);
        }
        if (agentRuns.length === 0) return;
        const fetchedData: TableData[] = await Promise.all(
            agentRuns.map(async (runData) => {
                console.log(runData);
                return {
                    start_time: func.prettifyEpoch(runData.startTimestamp),
                    targetName: getTargetNames(runData),
                    action: func.capitalizeFirstLetter(runData.state.toLowerCase()),
                    duration: func.prettifyEpoch(runData.endTimestamp - runData.startTimestamp),
                    details: await getDetails(runData),
                } as TableData;
            })
        );
        setData(fetchedData);
    }



    useEffect(() => {
        fetchTable();

    }, [agentId]);


    const table = (
        <GithubSimpleTable
            key={"agent-activity-table"}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={data}
            hideQueryField={true}
            hidePagination={true}
            showFooter={false}
        />
    )

    return (
        <div >
            <Box borderRadius="2" borderColor="border-subdued" paddingBlockEnd={"5"} paddingBlockStart={"5"} paddingInlineStart={"4"} paddingInlineEnd={"4"} >
                {table}
            </Box>
        </div>
    )
}

export default ActivityTable;