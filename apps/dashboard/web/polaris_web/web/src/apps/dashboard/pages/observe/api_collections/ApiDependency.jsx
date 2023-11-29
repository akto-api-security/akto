import { VerticalStack, } from "@shopify/polaris"
import React, { useState, useEffect, useRef } from 'react'
import ReactFlow, {
    Background,
} from 'react-flow-renderer';
import ApiDependencyNode from "./ApiDependencyNode";
import api from "../api";
import ApiDependencyEdge from "./ApiDependencyEdge";



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
    result["nodes"] = finalNodes
    let finalEdges = []
    result["edges"] = finalEdges

    let nodesLength = nodes.length;
    if (nodesLength == 0) return finalNodes;

    let currentNodeId = calculateNodeId(currentApiCollectionId, currentEndpoint, currentMethod)


    // add current node
    finalNodes.push({
        "id": currentNodeId,
        "type": 'apiDependencyNode',
        "data": { "apiCollectionId": currentApiCollectionId, "endpoint": currentEndpoint, "method": currentMethod },
        "position": { x: midPoint, y: 200 },
    })

    let parentNodes = new Map()
    let childrenNodes = new Map()

    for (let index = 0; index < nodesLength; index++) {
        let node = nodes[index]["node"]
        let nodeInfo = nodes[index]["nodeInfo"]

        if (isParent(node, currentApiCollectionId, currentEndpoint, currentMethod)) {
            let id = calculateNodeId(node["apiCollectionIdResp"], node["urlResp"], node["methodResp"])
            parentNodes.set(id, {
                "id": id,
                "type": 'apiDependencyNode',
                "data": { "apiCollectionId": node["apiCollectionIdResp"], "endpoint": node["urlResp"], "method": node["methodResp"], "type": "input", "dependents": nodeInfo["dependents"] },
                "position": { x: 0, y: 0 }
            })

            let edgeId = node["hexId"];
            let source = id;
            let target = currentNodeId;

            let paramInfos = node["paramInfos"]
            let parameters = []
            paramInfos.forEach(paramInfo => {
                parameters.push(paramInfo["responseParam"] + " \u2192 " + paramInfo["requestParam"])
            })


            finalEdges.push({
                id: edgeId, source: source, target: target, animated: false, type: 'apiDependencyEdge', data: { "parameters": parameters }
            })

        } else {
            let id = calculateNodeId(node["apiCollectionIdReq"], node["urlReq"], node["methodReq"])
            childrenNodes.set(id, {
                "id": id,
                "type": 'apiDependencyNode',
                "data": { "apiCollectionId": node["apiCollectionIdReq"], "endpoint": node["urlReq"], "method": node["methodReq"], "type": "output", "dependents": nodeInfo["dependents"] },
                "position": { x: 0, y: 380 }
            })

            let edgeId = node["hexId"];
            let source = currentNodeId;
            let target = id;

            let paramInfos = node["paramInfos"]
            let parameters = []
            paramInfos.forEach(paramInfo => {
                parameters.push(paramInfo["responseParam"] + " \u2192 " + paramInfo["requestParam"])
            })

            finalEdges.push({
                id: edgeId, source: source, target: target, animated: false, type: 'apiDependencyEdge', data: { "parameters": parameters }
            })
        }

    }

    fillFinalNodes(parentNodes, finalNodes)
    fillFinalNodes(childrenNodes, finalNodes)

    return result;

}

function fillFinalNodes(nodes, finalNodes) {
    let n = nodes.size

    let pattern = [];
    let start = midPoint - 150 * (n - 1);
    for (let i = 0; i < n; i++) {
        pattern.push(start + 300 * i);
    }

    let idx = 0
    for (const [id, node] of nodes) {
        node["position"]["x"] = pattern[idx]
        finalNodes.push(node)
        idx += 1
    }
}

function calculateNodeId(apiCollectionId, endpoint, method) {
    return apiCollectionId + "#" + endpoint + "#" + method
}

function isParent(node, currentApiCollectionId, currentEndpoint, currentMethod) {
    return calculateNodeId(node["apiCollectionIdReq"], node["urlReq"], node["methodReq"]) === calculateNodeId(currentApiCollectionId, currentEndpoint, currentMethod)
}

export default ApiDependency