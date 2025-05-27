import { VerticalStack, } from "@shopify/polaris"
import React, { useState, useEffect, useRef } from 'react'
import ReactFlow, {
    Background,
} from 'react-flow-renderer';
import ApiDependencyNode from "./ApiDependencyNode";
import api from "../api";
import ApiDependencyEdge from "./ApiDependencyEdge";
import func from "../../../../../util/func";

const initialNodes = [];

const initialEdges = [];

const nodeTypes = { apiDependencyNode: ApiDependencyNode };
const edgeTypes = { apiDependencyEdge: ApiDependencyEdge };

function ApiDependency(props) {
    const { apiCollectionId, endpoint, method } = props
    const [nodes, setNodes] = useState(initialNodes);
    const [edges, setEdges] = useState(initialEdges);
    const [loading, setLoading] = useState(false)

    const fetchData = async () => {
        setLoading(true)

        let resp = await api.fetchApiDependencies(apiCollectionId, endpoint, method)
        if (!resp.result) return

        if (resp.result && resp.result.length > 0) {
            let result = formatRawNodeData(resp.result, apiCollectionId, endpoint, method)
            setNodes(result["nodes"])
            setEdges(result["edges"])
        }

        setLoading(false)
    }

    useEffect(() => {
        fetchData()
    }, [endpoint])

    return (
        <VerticalStack gap="2">
            <div style={{ height: "800px" }}>
                <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes}>
                    <Background color="#aaa" gap={6} />
                </ReactFlow>;
            </div>
        </VerticalStack>
    )

}


const midPoint = 210

function formatRawNodeData(nodes, currentApiCollectionId, currentEndpoint, currentMethod) {
    let result = {}
    let finalNodes = []
    let finalEdges = []
    let currentNodeId = calculateNodeId(currentApiCollectionId, currentEndpoint, currentMethod)

    let nodesLength = nodes.length;
    if (nodesLength == 0) return finalNodes;

    let maxDepth = 0
    nodes.forEach((x) => {
        maxDepth = Math.max(maxDepth, findMaxDepth(x))
    })

    for (let i = 0; i < maxDepth + 1; i++) {
        finalNodes.push([])
    }

    let nodesAdded = new Set()

    for (let index = 0; index < nodesLength; index++) {
        let node = nodes[index]

        let id = calculateNodeId(node["apiCollectionId"], node["url"], node["method"])

        if (!nodesAdded.has(id)) {
            let isCurrentNode = id === currentNodeId
            let depth = findMaxDepth(node)
            finalNodes[depth].push({
                "id": id,
                "type": 'apiDependencyNode',
                "data": { "apiCollectionId": node["apiCollectionId"], "endpoint": node["url"], "method": node["method"], "isCurrentNode": isCurrentNode, "isFirstNode": false },
                "position": { x: 0, y: depth * 200 }
            })
            nodesAdded.add(id)
        }

        let connections = node["connections"]
        let edgesMap = new Map()
        Object.values(connections).forEach(connection => {
            let edge = connection["edges"][0]

            if (!edge) return

            let source = calculateNodeId(edge["apiCollectionId"], edge["url"], edge["method"]);
            let edgeId = source + "-" + id;

            let parameters = prepareParams(edge["param"], connection["param"])

            if (edge["depth"] === 1) {
                let parentId = calculateNodeId(edge["apiCollectionId"], edge["url"], edge["method"])
                if (!nodesAdded.has(parentId)) {
                    finalNodes[0].push({
                        "id": parentId,
                        "type": 'apiDependencyNode',
                        "data": { "apiCollectionId": edge["apiCollectionId"], "endpoint": edge["url"], "method": edge["method"], "isCurrentNode": false, "isFirstNode": true },
                        "position": { x: 100, y: 0 }
                    })
                    nodesAdded.add(parentId)
                }
            }

            let edgeFromMap = edgesMap.get(edgeId)
            if (edgeFromMap) {
                edgeFromMap["data"]["parameters"].push(parameters)
            } else {
                edgesMap.set(edgeId, {id: edgeId, source: source, target: id, animated: false, type: 'apiDependencyEdge', data: { "parameters": [parameters] }})
            }
        })

        for (let [key, value] of edgesMap) {
            finalEdges.push(value)
        }
    }

    result["nodes"] = []
    finalNodes.forEach((x) => {
        fillFinalNodes(x, result["nodes"])
    })

    result["edges"] = finalEdges

    return result;
}

function fillFinalNodes(nodes, finalNodes) {
    let nodesLength = nodes.length

    let pattern = [];
    let start = midPoint - 150 * (nodesLength - 1);
    for (let i = 0; i < nodesLength; i++) {
        pattern.push(start + 300 * i);
    }

    let idx = 0
    for (const n of nodes) {
        n["position"]["x"] = pattern[idx];
        finalNodes.push(n)
        idx += 1
    }
}

function prepareParams(responseParam, requestParam) {
    let finalResponseParam = func.findLastParamField(responseParam)
    let finalRequestParam = func.findLastParamField(requestParam)

    return finalResponseParam + " \u2192 " + finalRequestParam
}

function calculateNodeId(apiCollectionId, endpoint, method) {
    return apiCollectionId + "#" + endpoint + "#" + method
}

function findMaxDepth(node) {
    let connections = node["connections"]
    let maxDepth = 0
    Object.values(connections).forEach(x => {
        let edges = x["edges"]
        if (edges.length > 0) {
            maxDepth = Math.max(maxDepth, edges[0].depth)
        }
    })

    return maxDepth
}

export default ApiDependency