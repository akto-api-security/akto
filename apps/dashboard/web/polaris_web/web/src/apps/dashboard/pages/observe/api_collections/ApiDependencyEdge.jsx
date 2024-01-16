import { Box, Card, Text, VerticalStack } from '@shopify/polaris';
import { useState } from 'react';
import {  getBezierPath } from 'react-flow-renderer';


export default function ApiDependencyEdge({ id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    style = {},
    data,
    arrowHeadType,
    markerEndId,
    borderRadius = 5, }) {
    const edgePath = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
    const [show, setShow] = useState(false);

    const { parameters } = data

    return (
        <>
            <g
                onMouseEnter={() => setShow(true)}
                onMouseLeave={() => setShow(false)}
            >
                <svg>
                    <defs>
                        <marker id="arrow" markerWidth="14" markerHeight="14" refX="9" refY="7" orient="auto" markerUnits="strokeWidth">
                            <path d="M0,0 L9,7 L0,14" fill='none' stroke='grey' />
                        </marker>
                    </defs>
                </svg>
                <path id={id} style={{ ...style }} className="react-flow__edge-path" d={edgePath} markerEnd="url(#arrow)" />
                <g>
                    {show && (
                        <foreignObject
                            x={sourceX - 75}
                            y={sourceY}
                            width={150}
                            height={targetY - sourceY}
                        >
                            <div xmlns="http://www.w3.org/1999/xhtml" style={{
                                display: 'flex',
                                justifyContent: 'center',
                                alignItems: 'center',
                                height: '100%',
                                width: '100%'
                            }}>
                                <Card padding={3}>
                                    <VerticalStack gap={1}>
                                        <Box width='150px'>
                                            <Text color='subdued' variant='bodySm'>
                                                Dependencies
                                            </Text>
                                        </Box>
                                        <Box width='150px'>
                                            <Text variant='bodySm'>
                                                {parameters.map((item, index) => (
                                                    <li key={index}>{item}</li>
                                                ))}
                                            </Text>
                                        </Box>
                                    </VerticalStack>
                                </Card>
                            </div>
                        </foreignObject>
                    )}
                </g>
            </g>
        </>
    );
}