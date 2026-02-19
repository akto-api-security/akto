import { Text } from '@shopify/polaris';
import React, { useState } from 'react';
import { getBezierPath } from 'react-flow-renderer';

export default function TraceSpanEdge({
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    style = {},
    data,
}) {
    const [show, setShow] = useState(false);

    const edgePath = getBezierPath({
        sourceX,
        sourceY,
        sourcePosition,
        targetX,
        targetY,
        targetPosition,
    });

    const { input } = data;

    // Format input for display (first 2 lines)
    const formatInput = (inputData) => {
        if (!inputData || Object.keys(inputData).length === 0) {
            return ['No input'];
        }

        const lines = [];
        let count = 0;
        for (const [key, value] of Object.entries(inputData)) {
            if (count >= 2) break;
            const displayValue = typeof value === 'string' ? value : JSON.stringify(value);
            const truncatedValue = displayValue.length > 40
                ? `${displayValue.substring(0, 40)}...`
                : displayValue;
            lines.push(`${key}: ${truncatedValue}`);
            count++;
        }
        return lines;
    };

    const inputLines = formatInput(input);

    return (
        <>
            <g
                onMouseEnter={() => setShow(true)}
                onMouseLeave={() => setShow(false)}
            >
                <defs>
                    <marker
                        id={`trace-arrow-${id}`}
                        markerWidth="10"
                        markerHeight="10"
                        refX="8"
                        refY="5"
                        orient="auto"
                        markerUnits="strokeWidth"
                    >
                        <path d="M0,0 L10,5 L0,10" fill='none' stroke='#7c3aed' strokeWidth="1.5" />
                    </marker>
                </defs>
                <path
                    id={id}
                    style={{ ...style, stroke: '#7c3aed', strokeWidth: 2 }}
                    className="react-flow__edge-path"
                    d={edgePath}
                    markerEnd={`url(#trace-arrow-${id})`}
                />
                <g>
                    {show && (
                        <foreignObject
                            x={sourceX - 80}
                            y={sourceY + (targetY - sourceY) / 2 - 30}
                            width={160}
                            height={60}
                        >
                            <div
                                xmlns="http://www.w3.org/1999/xhtml"
                                style={{
                                    display: 'flex',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    height: '100%',
                                    width: '100%'
                                }}
                            >
                                <div style={{
                                    backgroundColor: 'white',
                                    border: '1px solid #e1e5e9',
                                    borderRadius: '4px',
                                    padding: '6px 8px',
                                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
                                    maxWidth: '150px'
                                }}>
                                    <Text
                                        variant="bodySm"
                                        as="div"
                                        fontFamily="monospace"
                                        color="subdued"
                                    >
                                        {inputLines.join('\n')}
                                    </Text>
                                </div>
                            </div>
                        </foreignObject>
                    )}
                </g>
            </g>
        </>
    );
}
