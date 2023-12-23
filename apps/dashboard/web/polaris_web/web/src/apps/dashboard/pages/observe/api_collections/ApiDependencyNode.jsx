import { Box, Card, HorizontalStack, Icon, Text, VerticalStack } from '@shopify/polaris';
import React, { useState, useEffect, useRef, useCallback } from 'react'
import ReactFlow, {
    Background,
    getRectOfNodes
} from 'react-flow-renderer';
import { Handle, Position } from 'react-flow-renderer';
import PersistStore from '../../../../main/PersistStore';
import TooltipText from '../../../components/shared/TooltipText';
import StyledEndpoint from './component/StyledEndpoint';
import { ArrowUpMinor } from "@shopify/polaris-icons"


function ApiDependencyNode({ data }) {

    const { apiCollectionId, endpoint, method, isCurrentNode, isFirstNode} = data

    const methodPlusUrl = method + " " + endpoint
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const apiCollectionName = collectionsMap[apiCollectionId]

    return (
        <>
            {!isFirstNode ? <Handle type="target" position={Position.Top} /> : null}
            <div onClick={() => { !isCurrentNode && openTargetUrl(apiCollectionId, endpoint, method) }} style={isCurrentNode ? { "cursor": "default" } : { "cursor": "pointer" }}>
                <VerticalStack gap={2}>
                    <Card padding={0}>
                        <Box padding={3}>
                            <VerticalStack gap={1}>
                                <Box width='250px'>
                                    <TooltipText tooltip={apiCollectionName} text={apiCollectionName} textProps={{ color: "subdued", variant: "bodySm" }} />
                                </Box>
                                <HorizontalStack gap={1}>
                                    <Box width='230px'>
                                        {StyledEndpoint(methodPlusUrl, "12px", "bodySm")}
                                    </Box>
                                    <div style={{ transform: 'rotate(45deg)', width: "20px" }}>
                                        {!isCurrentNode && <Icon source={ArrowUpMinor} color="subdued" />}
                                    </div>

                                </HorizontalStack>
                            </VerticalStack>
                        </Box>
                    </Card>
                </VerticalStack>
            </div>
            {!isCurrentNode ? <Handle type="source" position={Position.Bottom} id="b" /> : null}
        </>
    );
}

function openTargetUrl(apiCollectionId, url, method) {
    const encodedUrl = encodeURIComponent(url);
    const baseUrl = `${window.location.protocol}//${window.location.host}`;

    const finalUrl = `${baseUrl}/dashboard/observe/inventory/${apiCollectionId}?selected_url=${encodedUrl}&selected_method=${method}`;
    window.open(finalUrl, '_blank');
}

export default ApiDependencyNode