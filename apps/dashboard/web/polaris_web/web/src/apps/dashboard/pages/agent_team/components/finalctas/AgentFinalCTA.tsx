import React from 'react';
import { useAgentsStore } from '../../agents.store';
import { intermediateStore } from '../../intermediate.store';
import AgentCoreCTA from './AgentCoreCTA';
import func from '../../../../../../util/func';
import issueApi from "../../../../pages/issues/api"
import api from "./api";
import apiCollectionApi from "../../../../pages/observe/api"
import APISRequiredCTA from './APISRequiredCTA';
import SourceCodeAnalyserCTA from "./SourceCodeAnalyserCTA"

function AgentFinalCTA() {
    const { PRstate, currentAgent, currentProcessId} = useAgentsStore()
    const { filteredUserInput, outputOptions } = intermediateStore();

    async function falsePositiveSave() {
        let filteredData = outputOptions.outputOptions.filter(x => {
            return filteredUserInput.includes(x.value)
        })
        let issueIdArray = filteredData.map((x: { issueId: any; }) => x.issueId)
        let compactIssueIdArray = [
            ...new Map(issueIdArray.map((item: any) => [JSON.stringify(item), item])).values()
        ];
        let testingRunResultHexIdsMap = filteredData.reduce((y: { [x: string]: any; }, x: { value: string; severity: string; }) => {
            y[x.value] = x.severity
            return y
        }, {})
        await issueApi.bulkUpdateIssueStatus(compactIssueIdArray, "IGNORED", "False positive", testingRunResultHexIdsMap)
        func.setToast(true, false, "Tests were marked as false positive")
    }

    async function sensitiveDataTypeSave() {
        await api.createSensitiveResponseDataTypes({ dataTypeKeys: filteredUserInput })
        func.setToast(true, false, "Sensitive data types are being created")

    }

    async function apiGroupSave() {
        let filteredCollections = outputOptions.outputOptions.filter(x => {
            return filteredUserInput.includes(x.value)
        })
        let interval = 1000;
        for (let index in filteredCollections) {
            let collectionName = filteredCollections[index].value
            let apis = filteredCollections[index].apis
            // this because they take up the same timestamp
            // and since the timestamp is _id, it gives an error.
            setTimeout(async () => {
                await apiCollectionApi.addApisToCustomCollection(apis, collectionName)
            }, interval)
            interval += 1000
        }
        func.setToast(true, false, "API groups are being created")
    }

    const atLeastOneVuln = (obj: any) => {
        if(obj === undefined || obj === null) {
            return false
        }
        return Object.values(obj).some((x: any) => x === true)
    }

    const handleSaveVuln = async () => {
        const filteredData = outputOptions.outputOptions.filter(x => {
            return filteredUserInput.includes(x.value)
        })

        const totalApisScanned = outputOptions.outputOptions.length;
        const agentProcessId = currentProcessId;
        let finalVuln : any[] = [];
        filteredData.forEach((data: any) => {
            let vulnObj = {} as any;
            const id = data?.id;
            vulnObj["id"] = id;
            vulnObj["output"] = data?.output;
            if(atLeastOneVuln(data?.output)) {
                finalVuln.push(vulnObj);
            }
        })
        await api.saveVulnerabilities({
            agentProcessId: agentProcessId, 
            vulnerabilities: finalVuln, 
            totalApisScanned: totalApisScanned
        })      
    }

    return (() => {
        switch (currentAgent?.id) {
            case 'FIND_VULNERABILITIES_FROM_SOURCE_CODE':
                return (PRstate === "1" ? <APISRequiredCTA />: 
                    <AgentCoreCTA
                        onSave={() => handleSaveVuln()}
                        modalTitle={`Save vulnerabilities`}
                        actionText={'Save'}
                        contentString={`Saving the shown vulnerabilities to the database`} 
                    />
                )
            case 'FIND_SENSITIVE_DATA_TYPES':
                return <AgentCoreCTA
                    onSave={() => sensitiveDataTypeSave()}
                    modalTitle={`Save sensitive data type${filteredUserInput?.length === 1 ? "" : "s"}`}
                    actionText={'Save'}
                    contentString={`Do you want to add the ${filteredUserInput?.length} selected sensitive data type${filteredUserInput?.length === 1 ? "" : "s"} to Akto ?`} 
                />
            case 'FIND_APIS_FROM_SOURCE_CODE':
                return (<SourceCodeAnalyserCTA />)
            case 'GROUP_APIS':
                return <AgentCoreCTA
                    onSave={() => apiGroupSave()}
                    modalTitle={`Save API group${filteredUserInput?.length === 1 ? "" : "s"}`}
                    actionText={'Save'}
                    contentString={`Do you want to add the ${filteredUserInput?.length} selected API group${filteredUserInput?.length === 1 ? "" : "s"} to Akto ?`} 
                    />
            case 'FIND_FALSE_POSITIVE':
                return <AgentCoreCTA
                    onSave={() => falsePositiveSave()}
                    modalTitle={`Ignore testing run result${filteredUserInput?.length === 1 ? "" : "s"}`}
                    actionText={'Mark as ignored'}
                    contentString={`Do you want to mark the ${filteredUserInput?.length} selected testing result${filteredUserInput?.length === 1 ? "" : "s"} as false positive?`} 
                    />
            default:
                return (<></>)
        }
    }
    )()
}

export default AgentFinalCTA