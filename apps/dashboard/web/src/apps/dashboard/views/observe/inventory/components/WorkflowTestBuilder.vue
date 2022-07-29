<template>
    <div>
        <workflow-builder style="height: 500px" :endpointsList="endpointsList" :initialNodes="defaultNodes" :initialEdges="defaultEdges" :fetchSampleDataFunc="fetchSampleDataFunc"/>
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
        initialNodes: obj.arrN,
        initialEdges: obj.arrN
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

        return {
            defaultNodes: this.initialNodes || defNodes,
            defaultEdges: this.initialEdges || defEdges,
            fetchSampleDataFunc: api.fetchSampleData
        }
    }
}
</script>

<style lang="sass" scoped>

</style>