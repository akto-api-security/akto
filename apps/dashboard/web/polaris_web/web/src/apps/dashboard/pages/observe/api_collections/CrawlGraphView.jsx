import { useEffect, useState } from "react"
import { Box, Button, Card, HorizontalStack, Spinner, Text, VerticalStack } from "@shopify/polaris"
import ReactFlow, { Controls, Background } from 'react-flow-renderer'
import 'react-flow-renderer/dist/style.css'
import 'react-flow-renderer/dist/theme-default.css'
import TooltipText from "../../../components/shared/TooltipText"
import api from "./api"

const NODE_W = 180
const CONTAINER_HEIGHT = 480

const NODE_STYLE = {
    background: 'var(--p-color-bg)',
    border: '1px solid var(--p-color-border)',
    borderRadius: '6px',
    fontSize: '12px',
    padding: '8px 12px',
    width: NODE_W,
}

const EDGE_STYLE = { stroke: 'var(--p-color-border-strong)' }

function parseMermaidToFlow(source) {
    const nodes = {}
    const edges = []
    let direction = 'TD'

    for (const rawLine of source.split('\n')) {
        const line = rawLine.trim()
        if (!line || line.startsWith('%%')) continue

        const dirMatch = line.match(/^(?:graph|flowchart)\s+(LR|RL|TD|TB|BT)/)
        if (dirMatch) { direction = dirMatch[1]; continue }
        if (/^(subgraph|end|style|classDef|class)\b/.test(line)) continue

        const nodeDef = line.match(/^([A-Za-z0-9_]+)\s*\["([^"]*)"\]$/)
        if (nodeDef) {
            const [, id, label] = nodeDef
            if (!nodes[id]) nodes[id] = { id, label: label.trim() }
            continue
        }

        const edgeDef = line.match(/^([A-Za-z0-9_]+)\s*-->\s*([A-Za-z0-9_]+)$/)
        if (edgeDef) {
            const [, src, tgt] = edgeDef
            if (!nodes[src]) nodes[src] = { id: src, label: src }
            if (!nodes[tgt]) nodes[tgt] = { id: tgt, label: tgt }
            edges.push({ id: `e${edges.length}`, source: src, target: tgt })
        }
    }

    return { nodes: Object.values(nodes), edges, direction }
}

function computeLayout(nodes, edges, direction) {
    if (!nodes.length) return {}

    const isH = ['LR', 'RL'].includes(direction)
    const children = {}
    const inDegree = {}

    nodes.forEach(n => { children[n.id] = []; inDegree[n.id] = 0 })
    edges.forEach(e => {
        children[e.source]?.push(e.target)
        if (e.target in inDegree) inDegree[e.target]++
    })

    const level = {}
    const queue = nodes.filter(n => inDegree[n.id] === 0).map(n => n.id)
    if (!queue.length) queue.push(nodes[0].id)
    queue.forEach(id => { level[id] = 0 })

    for (let i = 0; i < queue.length; i++) {
        const id = queue[i];
        (children[id] || []).forEach(child => {
            if (level[child] === undefined) {
                level[child] = level[id] + 1
                queue.push(child)
            }
        })
    }

    const byLevel = {}
    nodes.forEach(n => {
        const l = level[n.id] ?? 0;
        (byLevel[l] = byLevel[l] || []).push(n.id)
    })

    const positions = {}
    Object.entries(byLevel).forEach(([lvl, ids]) => {
        const l = Number(lvl)
        ids.forEach((id, i) => {
            positions[id] = isH
                ? { x: l * 280, y: i * 120 }
                : { x: i * 280, y: l * 120 }
        })
    })

    return positions
}

function CrawlGraphView({ crawlId }) {
    const [graph, setGraph] = useState("")
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        if (!crawlId) return
        let mounted = true
        api.getCrawlerGraph(crawlId)
            .then((g) => { if (mounted) { setGraph(g || ""); setLoading(false) } })
            .catch(() => { if (mounted) setLoading(false) })
        return () => { mounted = false }
    }, [crawlId])

    if (loading) {
        return (
            <Card>
                <Box padding="6">
                    <HorizontalStack align="center" blockAlign="center" gap="2">
                        <Spinner size="small" />
                        <Text>Loading navigation graph...</Text>
                    </HorizontalStack>
                </Box>
            </Card>
        )
    }

    if (!graph) {
        return (
            <Card>
                <Box padding="4">
                    <Text color="subdued">No navigation graph available for this DAST scan.</Text>
                </Box>
            </Card>
        )
    }

    const { nodes: parsedNodes, edges: parsedEdges, direction } = parseMermaidToFlow(graph)
    const positions = computeLayout(parsedNodes, parsedEdges, direction)

    const rfNodes = parsedNodes.map(n => ({
        id: n.id,
        data: { label: <TooltipText text={n.label} tooltip={n.label} /> },
        position: positions[n.id] || { x: 0, y: 0 },
        style: NODE_STYLE,
    }))

    const rfEdges = parsedEdges.map(e => ({ ...e, type: 'smoothstep', style: EDGE_STYLE }))

    const download = () => {
        const url = URL.createObjectURL(new Blob([graph], { type: "text/plain" }))
        Object.assign(document.createElement("a"), { href: url, download: `navigation-${crawlId}.mmd` }).click()
        URL.revokeObjectURL(url)
    }

    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <Text variant="headingSm" fontWeight="semibold">Navigation graph</Text>
                        <HorizontalStack gap="2">
                            <Button size="slim" onClick={() => navigator.clipboard?.writeText(graph)}>Copy source</Button>
                            <Button size="slim" onClick={download}>Download .mmd</Button>
                        </HorizontalStack>
                    </HorizontalStack>

                    <div style={{ height: `${CONTAINER_HEIGHT}px`, borderRadius: '8px', overflow: 'hidden', background: 'var(--p-color-bg-subdued)' }}>
                        <ReactFlow
                            nodes={rfNodes}
                            edges={rfEdges}
                            fitView
                            nodesDraggable
                            nodesConnectable={false}
                            elementsSelectable={false}
                        >
                            <Controls />
                            <Background />
                        </ReactFlow>
                    </div>
                </VerticalStack>
            </Box>
        </Card>
    )
}

export default CrawlGraphView
