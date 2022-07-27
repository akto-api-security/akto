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
                type: 'input',
                data: {
                    label: 'Welcome to React Flow'
                },
                position: { x: 250, y: 0 },
            },
            {
                id: '2',
                data: {
                    label: 'This is a default node'
                },
                position: { x: 100, y: 100 },
            },
            {
                id: '3',
                data: {
                    label: 'This one has a custom style'
                },
                position: { x: 400, y: 100 },
                style: {
                background: '#D6D5E6',
                color: '#333',
                border: '1px solid #222138',
                width: 180,
                },
            },
            {
                id: '4',
                position: { x: 250, y: 200 },
                data: {
                label: 'Another default node',
                },
            },
            {
                id: '5',
                data: {
                label: 'Node id: 5',
                },
                position: { x: 250, y: 325 },
            },
            {
                id: '6',
                type: 'output',
                data: {
                    label: 'An output node'
                },
                position: { x: 100, y: 480 },
            },
            {
                id: '7',
                type: 'output',
                data: { label: 'Another output node' },
                position: { x: 400, y: 450 },
            },
        ]

        let defEdges = [
            { id: 'e1-2', source: '1', target: '2', label: 'this is an edge label' },
            { id: 'e1-3', source: '1', target: '3' },
            {
                id: 'e3-4',
                source: '3',
                target: '4',
                animated: true,
                label: 'animated edge',
            },
            {
                id: 'e4-5',
                source: '4',
                target: '5',
                label: 'edge with arrow head'
            },
            {
                id: 'e5-6',
                source: '5',
                target: '6',
                type: 'smoothstep',
                label: 'smooth step edge',
            },
            {
                id: 'e5-7',
                source: '5',
                target: '7',
                type: 'step',
                style: { stroke: '#f6ab6c' },
                label: 'a step edge',
                animated: true,
                labelStyle: { fill: '#f6ab6c', fontWeight: 700 },
            },
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