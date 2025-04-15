import { Modal } from '@shopify/polaris'
import React, { useState } from 'react'
import { useAgentsStore } from '../../agents.store'
import { intermediateStore } from '../../intermediate.store';
import func from '../../../../../../util/func';
import apiCollectionApi from "../../../../pages/observe/api"

function SourceCodeAnalyserCTA() {
    const { finalCTAShow, setFinalCTAShow } = useAgentsStore()

    //outputOptions is processOutput
    const { filteredUserInput, outputOptions, agentInitDocument } = intermediateStore();

    function getApiCollectionName() {
        let apiCollectionName = "source-code-ai-agent"
        if (agentInitDocument?.sourceCodeType) {
            apiCollectionName = `${agentInitDocument?.repository}/${agentInitDocument?.project}`
        }
        return apiCollectionName
    }

    async function saveApis() {
        let apiCollectionName = getApiCollectionName()
        let projectDir = apiCollectionName
        let codeAnalysisApisList: any[] = []
        outputOptions?.outputOptions?.forEach((element: any) => {
            if (filteredUserInput.includes(element.textValue)) {
                codeAnalysisApisList.push(element?.valueObj)
            }
        })

        await apiCollectionApi.syncExtractedAPIs(apiCollectionName, projectDir, codeAnalysisApisList)
        func.setToast(true, false, `All the api's are now saved in ${apiCollectionName} collection`)
        setFinalCTAShow(false)
    }

    return (
       <Modal
            title={`Save all extracted apis`}
            open={finalCTAShow}
            onClose={() => setFinalCTAShow(false)}
            primaryAction={{
                content: 'Save apis',
                onAction: () => {saveApis()} /* setCurrentAgent as source code agent here */
            }}
            secondaryActions={[{
                content: 'Cancel',
                onAction: () => setFinalCTAShow(false)
            }]}
        >
            <Modal.Section>
                <p>
                    Save all { filteredUserInput ? filteredUserInput.length : ""} apis with schema into "{getApiCollectionName()}" collection
                </p>
            </Modal.Section>
        </Modal>
    )
}

export default SourceCodeAnalyserCTA