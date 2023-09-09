import observeApi from "../../observe/api"
import api from "./api";
import WorkflowBuilder from "./WorkflowBuilder"

function WorkflowTestBuilder(props) {

    const {
        apiCollectionId,
        endpointsList,
        originalStateFromDb,
        defaultOpenResult
    } = props;

    let defNodes = [
        {
            id: '1',
            type: 'startNode',
            position: { x: 0, y: 0 },
            draggable: false,
            selectable: false
        },
        {
            id: '2',
            type: 'blankNode',
            position: { x: 250, y: 0 },
            draggable: true,
            hidden: true
        },
        {
            id: '3',
            type: 'endNode',
            position: { x: 400, y: 400 },
            draggable: false,
            selectable: false
        }
    ]

    const defaultState = {nodes: defNodes.map(JSON.stringify), edges: [], mapNodeIdToWorkflowNodeDetails: {}, lastEdited: 1, state: "DRAFT"}

    const originalState= originalStateFromDb || defaultState

    return (
        <div>
            <WorkflowBuilder
                apiCollectionId={apiCollectionId}
                endpointsList={endpointsList}
                originalStateFromDb={originalState}
                fetchSampleDataFunc={observeApi.fetchSampleData}
                createWorkflowTest={api.createWorkflowTest}
                editWorkflowTest={api.editWorkflowTest}
                editWorkflowNodeDetails={api.editWorkflowNodeDetails}
                runWorkflowTest={api.runWorkflowTest}
                fetchWorkflowResult={api.fetchWorkflowResult}
                defaultOpenResult={defaultOpenResult}
            />
        </div>
    )

}

export default WorkflowTestBuilder