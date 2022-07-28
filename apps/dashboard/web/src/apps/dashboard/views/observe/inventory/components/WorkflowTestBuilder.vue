<template>
    <div>
        <workflow-builder style="height: 500px" :endpointsList="endpointsList" :initialNodes="defaultNodes" :initialEdges="defaultEdges" />
    </div>
</template>

<script>
import WorkflowBuilder from '../../../testing/components/react/WorkflowBuilder.jsx'

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
                position: { x: 250, y: 100 },
                draggable: false
            },
            {
                id: '2',
                type: 'blankNode',
                position: { x: 250, y: 0 },
                draggable: true,
                hidden: true
            }

        ]

        let defEdges = [

        ]

        return {
            defaultNodes: this.initialNodes || defNodes,
            defaultEdges: this.initialEdges || defEdges
        }
    }
}
</script>

<style lang="sass" scoped>

</style>