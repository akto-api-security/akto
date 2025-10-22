import React, { useEffect, useState } from 'react';
import { Box, EmptySearchResult } from '@shopify/polaris';
import { DeleteMinor } from '@shopify/polaris-icons';
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import api from '../api';
import { AgentRun, State } from '../types';
import func from '../../../../../util/func';
import ShowListInBadge from '../../../components/shared/ShowListInBadge';
import transform from '../transform';
import { CellType } from '../../../components/tables/rows/GithubRow';


interface TableData {
    id: string;
    start_time: string;
    targetName: any;
    action: string;
    duration: string;
    details: string;
    createdTimeStamp: number|string;
    processId: string;
    state: State;
}

const sortOptions = [
    { label: 'Start time', value: 'start_time asc', directionLabel: 'Highest', sortKey: 'createdTimeStamp', columnIndex: 1 },
    { label: 'Start time', value: 'start_time desc', directionLabel: 'Lowest', sortKey: 'createdTimeStamp', columnIndex: 1 },
];

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
            maxWidth: "300px",
            type:CellType.TEXT
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
        },
        {
            title: "",
            value: "",
            type: CellType.ACTION
        }
    ]

    const resourceName = {
        singular: 'agent activity',
        plural: 'agent activities',
    };


    const getDetails = async (runData: AgentRun) => {
        let details = "";
        if (runData.state === State.SCHEDULED) {
            details = "Agent is scheduled";
        } else if ([State.RUNNING, State.COMPLETED, State.STOPPED].includes(runData.state)) {
            const subprocesses = await transform.getAllSubProcesses(runData.processId);
            // extracting subProcessHeading and subProcess.processOutput, if it exists for running and completed state of agentRun
            let lastSubprocess = subprocesses.find(subprocess => subprocess.state === State.ACCEPTED);
            details = `${lastSubprocess?.subProcessHeading || ""} => ${lastSubprocess?.userInput?.length > 0
                    ? lastSubprocess?.userInput
                    : lastSubprocess?.processOutput?.outputMessage ?? ""
                }`;

        } else if (runData.state === State.FAILED) {
            const subprocesses = await transform.getAllSubProcesses(runData.processId);
            let lastSubprocess = subprocesses.find(subprocess => subprocess.state === State.FAILED);

            details = `Agent failed ${lastSubprocess?.processOutput?.outputMessage ?? ""}`;
        }

        return details;
    };


    const getTargetNames = (runData: AgentRun) => {
        if (!runData?.agentInitDocument) {
            return "-";
        }
        const targetNames = Object.values(runData.agentInitDocument).flat().filter(Boolean);
        
        return <ShowListInBadge
            itemsArr={[...targetNames]}
            maxItems={1}
            maxWidth={"250px"}
            status={"new"}
            itemWidth={"200px"}
            useBadge={false}
            useTooltip={false}
        />
    }

    const handleDeleteClick = (agentData: TableData) => {
        const deleteConfirmationMessage = `Are you sure you want to delete this agent run?\n\nStatus: ${func.capitalizeFirstLetter(agentData.state.toLowerCase())}\nDuration: ${agentData.duration}\nDetails: ${agentData.details}\n\nThis action cannot be undone.`;
        func.showConfirmationModal(deleteConfirmationMessage, "Delete", () => handleDeleteConfirm(agentData));
    };

    const handleDeleteConfirm = async (agentData: TableData) => {
        try {
            await api.deleteAgentRun({ processId: agentData.processId });
            // Refresh the table data
            await fetchTable();
            func.setToast(true, false, "Agent run deleted successfully");
        } catch (error: any) {
            console.error('Failed to delete agent run:', error);
            //  Check if it's a 422 error (agent not found or already deleted)
            if (error?.response?.status === 422) {
                // Treat 422 as success - agent is already deleted or not found
                // The request interceptor already showed an error toast, so we override it with success
                await fetchTable();
                func.setToast(true, false, "Agent run deleted successfully");
            } else if (error?.response?.status !== 403) {
                // Don't show error for 403 (forbidden) as it's already handled by interceptor
                func.setToast(true, true, "Failed to delete agent run. Please try again.");
            }
            // For 403 and other interceptor-handled errors, don't show duplicate toast
        }
    };

    const getActions = (agentData: TableData) => {
        // Only show delete action for scheduled, running, or stopped agents
        if (agentData.state === State.SCHEDULED || agentData.state === State.RUNNING || agentData.state === State.STOPPED) {
            return [
                {
                    title: 'Actions',
                    items: [
                        {
                            content: 'Delete',
                            icon: DeleteMinor,
                            destructive: true,
                            onAction: () => handleDeleteClick(agentData),
                        },
                    ]
                }
            ];
        }
        return [];
    };


    const fetchTable = async () => {
        let agentRuns: AgentRun[] = [];
        try {
            const response = (await api.getAllAgentRunsObject(agentId));
            agentRuns = response as AgentRun[];
        } catch (error) {
        }
        const allPromises = agentRuns.map(async (runData) => {
            let duration = "";
            if(runData.state !== State.COMPLETED && (runData.endTimestamp === undefined || runData.endTimestamp === 0)){
                switch(runData.state){
                    case State.SCHEDULED:
                        duration = "Agent is scheduled";
                        break;
                    case State.STOPPED:
                        duration = "Agent is stopped";
                        break;
                    case State.FAILED:
                        duration = "Agent failed";
                        break;
                }   
            }else{
                duration = func.prettifyEpochDuration(runData.endTimestamp - runData.startTimestamp);
            }
            return {
                id: runData.processId, // Add id field for row actions
                start_time: runData?.startTimestamp ? new Date(runData?.startTimestamp * 1000).toUTCString() : "-",
                targetName: getTargetNames(runData),
                action: func.capitalizeFirstLetter(runData.state.toLowerCase()),
                duration: duration,
                details: await getDetails(runData),
                createdTimeStamp: runData.startTimestamp,
                processId: runData.processId,
                state: runData.state,
            };
        });
        
        const subprocesses = await Promise.allSettled(allPromises);
        const successfulResults = subprocesses.filter((res) => res.status === "fulfilled").map(res => res.value);
        setData(successfulResults);
    }



    useEffect(() => {
        if(!agentId || agentId.trim() === "") {
            setData([]);
            return;
        }
        fetchTable();

    }, [agentId]);

    const emptyStateMarkup = (
        <EmptySearchResult
          title={'No agent activity found'}
          withIllustration
        />
      );

    const table = (
        <GithubSimpleTable
            key={data.length}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={data}
            hideQueryField={true}
            hidePagination={true}
            showFooter={false}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}
            hasRowActions={true}
            getActions={getActions}
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
