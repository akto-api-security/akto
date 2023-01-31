<template>
    <div>
        <workflow-builder 
            v-if="originalState"
            :apiCollectionId="apiCollectionId"
            :endpointsList="endpointsList" 
            :originalStateFromDb="originalState" 
            :fetchSampleDataFunc="fetchSampleDataFunc"
            :createWorkflowTest="createWorkflowTest"
            :editWorkflowTest="editWorkflowTest"
            :editWorkflowNodeDetails="editWorkflowNodeDetails"
            :runWorkflowTest="runWorkflowTest"
            :fetchWorkflowResult="fetchWorkflowResult"
            :defaultOpenResult="defaultOpenResult"
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
        apiCollectionId: obj.numR,
        endpointsList: obj.arrR,
        originalStateFromDb: obj.objN,
        defaultOpenResult: obj.ObjN
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

        let defaultState = {nodes: defNodes.map(JSON.stringify), edges: [], mapNodeIdToWorkflowNodeDetails: {}, lastEdited: 1, state: "DRAFT"}

        return {
            fetchSampleDataFunc: api.fetchSampleData,
            createWorkflowTest: api.createWorkflowTest,
            editWorkflowTest: api.editWorkflowTest,
            editWorkflowNodeDetails: api.editWorkflowNodeDetails,
            originalState: this.originalStateFromDb || defaultState,
            runWorkflowTest: api.runWorkflowTest,
            fetchWorkflowResult: api.fetchWorkflowResult
        }
    }
}
</script>

<style lang="sass" scoped>

</style>