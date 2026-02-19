import { Box, Badge, Text, VerticalStack } from '@shopify/polaris';
import React from 'react';
import { Handle, Position } from 'react-flow-renderer';

function TraceSpanNode({ data }) {
    const { name, spanKind, output, isRootNode, hasChildren } = data;

    // Format output for display (first 4 lines)
    const formatOutput = (outputData) => {
        if (!outputData || Object.keys(outputData).length === 0) {
            return 'No output';
        }

        const lines = [];
        let count = 0;
        for (const [key, value] of Object.entries(outputData)) {
            if (count >= 4) break;
            const displayValue = typeof value === 'string' ? value : JSON.stringify(value);
            const truncatedValue = displayValue.length > 50
                ? `${displayValue.substring(0, 50)}...`
                : displayValue;
            lines.push(`${key}: ${truncatedValue}`);
            count++;
        }
        return lines.join('\n');
    };

    // Get span kind color
    const getSpanKindTone = (kind) => {
        switch (kind?.toLowerCase()) {
            case 'llm':
                return 'info';
            case 'tool':
                return 'attention';
            case 'agent':
            case 'workflow':
                return 'success';
            case 'api':
            case 'http':
                return 'magic';
            case 'database':
                return 'warning';
            default:
                return undefined;
        }
    };

    // Get border color based on span kind
    const getBorderColor = (kind) => {
        switch (kind?.toLowerCase()) {
            case 'llm':
                return '#3b82f6';
            case 'tool':
                return '#f59e0b';
            case 'agent':
            case 'workflow':
                return '#10b981';
            case 'http':
                return '#8b5cf6';
            default:
                return '#6b7280';
        }
    };

    const borderColor = getBorderColor(spanKind);

    return (
        <>
            {!isRootNode && <Handle type="target" position={Position.Top} style={{ background: borderColor, width: '8px', height: '8px' }} />}
            <div style={{
                border: `2px solid ${borderColor}`,
                borderRadius: '6px',
                backgroundColor: isRootNode ? 'rgba(124, 58, 237, 0.05)' : '#ffffff',
                padding: '12px',
                width: '200px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)'
            }}>
                <VerticalStack gap="2">
                    {/* Span Name */}
                    <Text variant="bodyMd" fontWeight="semibold" as="div" truncate>
                        {name}
                    </Text>

                    {/* Span Kind Badge */}
                    <Box>
                        <Badge status={getSpanKindTone(spanKind)} size="small">
                            {spanKind.toUpperCase() || 'unknown'}
                        </Badge>
                    </Box>

                    {/* Output Section - Compact */}
                    <Box
                        background="bg-surface-secondary"
                        padding="1"
                        borderRadius="100"
                        style={{ maxHeight: '60px', overflowY: 'hidden' }}
                    >
                        <Text
                            variant="bodySm"
                            as="div"
                            fontFamily="monospace"
                            color="subdued"
                            breakWord
                        >
                            {formatOutput(output)}
                        </Text>
                    </Box>
                </VerticalStack>
            </div>
            {hasChildren && <Handle type="source" position={Position.Bottom} id="b" style={{ background: borderColor, width: '8px', height: '8px' }} />}
        </>
    );
}

export default TraceSpanNode;
