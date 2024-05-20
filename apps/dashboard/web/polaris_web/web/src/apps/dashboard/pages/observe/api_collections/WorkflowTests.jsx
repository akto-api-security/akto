import { useEffect, useState } from "react"
import api from "../api"
import { Button, ButtonGroup, Card, LegacyCard, Page, Text } from "@shopify/polaris"
import {  MobileBackArrowMajor } from "@shopify/polaris-icons"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import func from "../../../../../util/func"
import WorkflowTestBuilder from "../../testing/workflow_test/WorkflowTestBuilder"
import UploadFile from "../../../components/shared/UploadFile"

function WorkflowTests({ apiCollectionId, endpointsList }) {
    const [workflowTests, setWorkflowTests] = useState([])
    const [showWorkflowBuilder, setShowWorkflowbuilder] = useState(false)
    const [workflowTest, setWorkflowTest] = useState(null)
    const [loading, setLoading] = useState(false)

    async function fetchWorkflowTests() {
        setLoading(true)

        const workflowTestsResponse = await api.fetchWorkflowTests()
        const workFlowTestsCollection = workflowTestsResponse.workflowTests
            .filter(workflowTest => workflowTest.apiCollectionId === parseInt(apiCollectionId))
            .map(workflowTest => ({
                ...workflowTest,
                createdTimestamp: func.prettifyEpoch(workflowTest.createdTimestamp),
                lastEdited: func.prettifyEpoch(workflowTest.lastEdited)
            }))
        setWorkflowTests(workFlowTestsCollection)
        setLoading(false)
    }

    useEffect(() => {
        fetchWorkflowTests()
    }, [showWorkflowBuilder])

    const resourceName = {
        singular: 'Workflow test',
        plural: 'Workflow tests',
    };

    const headers = [
        {
            text: "ID",
            value: "id",
            showFilter: false,
            itemOrder: 1,
        },
        {
            text: "Author",
            value: "author",
            showFilter: true,
            itemCell: 2,
            sortActive: true,
        },
        {
            text: "Created",
            value: "createdTimestamp",
            showFilter: false,
            itemCell: 2,
        },
        {
            text: "Last edited",
            value: "lastEdited",
            showFilter: false,
            itemCell: 2,
        }
    ]

    const sortOptions = [
        { label: 'Author', value: 'author asc', directionLabel: 'A-Z', sortKey: 'author', columnIndex: 2 },
        { label: 'Author', value: 'author desc', directionLabel: 'Z-A', sortKey: 'author', columnIndex: 2 },
    ];


    const getActions = (item) => {
        
        return [{
            items: [{
                content: 'Edit',
                onAction: () => {
                    setWorkflowTest(item)
                    setShowWorkflowbuilder(true)
                },
            }]
        }]
    }

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 3)
     }

    function handleFileChange(file) {
        if (file) {
            const reader = new FileReader();
            let isJson = file.name.endsWith(".json")
            if (isJson) {
                reader.readAsText(file)
            } 
            reader.onload = async () => {
                const uploadWorkflowJsonResponse = await api.uploadWorkflowJson(reader.result, parseInt(apiCollectionId))
                if (uploadWorkflowJsonResponse 
                        && uploadWorkflowJsonResponse.workflowTests 
                        && uploadWorkflowJsonResponse.workflowTests.length > 0) {
                    func.setToast(true, false, "Workflow uploaded successfully")
                    setWorkflowTest(uploadWorkflowJsonResponse.workflowTests[0])
                }
            }
        }
    }

    const header = (
        showWorkflowBuilder ?
            <LegacyCard.Header 
            title={
                <ButtonGroup>
                    <Button icon={MobileBackArrowMajor} plain onClick={() => {
                            setShowWorkflowbuilder(false)
                            setWorkflowTest(null)
                        }}
                    />
                    <Text variant="headingMd">
                        { workflowTest ? workflowTest.id : "Create workflow" }
                    </Text>
                </ButtonGroup>
            }
            >
                <UploadFile
                        fileFormat=".json"
                        fileChanged={file => handleFileChange(file)}
                        tooltipText="Upload workflow(.json)"
                        label="Upload workflow"
                        primary={false} />
            </LegacyCard.Header> :
            <LegacyCard.Header title="Workflow tests">
                <Button
                    onClick={() => setShowWorkflowbuilder(true)}
                >
                    Create workflow
                </Button>
            </LegacyCard.Header>
    )

    return (
        <LegacyCard>
            {header}
            <LegacyCard.Section>
                {showWorkflowBuilder ?
                    <WorkflowTestBuilder
                        key="workflow-test"
                        endpointsList={endpointsList}
                        apiCollectionId={parseInt(apiCollectionId)}
                        originalStateFromDb={workflowTest}
                        defaultOpenResult={false}
                        class={"white-background"}
                    /> :
                    <GithubSimpleTable
                        key="table"
                        data={workflowTests}
                        sortOptions={sortOptions}
                        resourceName={resourceName}
                        filters={[]}
                        disambiguateLabel={disambiguateLabel}
                        headers={headers}
                        hasRowActions={true}
                        getActions={getActions}
                    />
                }

            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default WorkflowTests