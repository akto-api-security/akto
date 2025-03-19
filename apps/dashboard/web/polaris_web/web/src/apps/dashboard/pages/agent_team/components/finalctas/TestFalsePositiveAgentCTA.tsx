import React from "react"
import { intermediateStore } from "../../intermediate.store";
import func from "../../../../../../util/func";
import { Modal, Text, VerticalStack } from "@shopify/polaris";
import issueApi from "../../../../pages/issues/api"
import { useAgentsStore } from "../../agents.store";

function TestFalsePositiveAgentCTA() {
    const { finalCTAShow, setFinalCTAShow } = useAgentsStore()

    const { filteredUserInput, outputOptions, resetStore } = intermediateStore();

    async function saveFunction() {

        let filteredData = outputOptions.outputOptions.filter(x => {
            return filteredUserInput.includes(x.value)
        })

        let issueIdArray = filteredData.map((x: { issueId: any; }) => x.issueId)
        let compactIssueIdArray = [
            ...new Map(issueIdArray.map((item: any) => [JSON.stringify(item), item])).values()
        ];

        console.log(issueIdArray, compactIssueIdArray)

        let testingRunResultHexIdsMap = filteredData.reduce((y: { [x: string]: any; }, x: { value: string; severity: string; }) => {
            y[x.value] = x.severity
            return y
        }, {})

        await issueApi.bulkUpdateIssueStatus(compactIssueIdArray, "IGNORED", "False positive", testingRunResultHexIdsMap)
        func.setToast(true, false, "Tests were marked as false positive")
        setFinalCTAShow(false)
        resetStore()
    }

    return (
        <Modal
            title={"Ignore testing run results"}
            primaryAction={{
                content: 'Mark as ignored',
                onAction: () => saveFunction()
            }} open={finalCTAShow}
            onClose={() => setFinalCTAShow(false)}
        >
            <Modal.Section>
                <VerticalStack gap={"4"}>
                    <Text as={"dd"}>
                        Do you want to mark the {filteredUserInput?.length} selected testing results as false positive?
                    </Text>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default TestFalsePositiveAgentCTA