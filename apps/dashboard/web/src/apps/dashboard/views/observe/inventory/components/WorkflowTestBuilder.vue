<template>
    <div>
        <workflow-builder 
            :endpointsList="endpointsList" 
            :originalStateFromDb="originalState" 
            :fetchSampleDataFunc="fetchSampleDataFunc"
            :createWorkflowTest="createWorkflowTest"
            :editWorkflowTest="editWorkflowTest"
            :editWorkflowNodeDetails="editWorkflowNodeDetails"
            style="height: 500px" 
        />
    </div>
</template>

<script>
import WorkflowBuilder from '../../../testing/components/react/WorkflowBuilder.jsx'
import api from '../api'
import obj from '@/util/obj'

export default {
    name: "WorkflowTestBuilder",
    components: {
        'workflow-builder': WorkflowBuilder
    },
    props: {
        endpointsList: obj.arrR,
        originalStateFromDb: obj.objN
    },
    data() {

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

        let defEdges = [

        ]

        let defaultState = {nodes: defNodes.map(JSON.stringify), edges: defEdges.map(JSON.stringify), lastEdited: 1, mapNodeIdToWorkflowNodeDetails: {}}

        return {
            fetchSampleDataFunc: api.fetchSampleData,
            createWorkflowTest: api.createWorkflowTest,
            editWorkflowTest: api.editWorkflowTest,
            editWorkflowNodeDetails: api.editWorkflowNodeDetails,
            originalState: this.originalState || defaultState
        }
    }
}
</script>

<style lang="sass" scoped>

</style>