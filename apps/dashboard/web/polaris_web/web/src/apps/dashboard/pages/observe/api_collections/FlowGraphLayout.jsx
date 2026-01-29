import React from 'react';
import ReactFlow, { Background, Controls, MiniMap } from 'react-flow-renderer';
import 'react-flow-renderer/dist/style.css';
import { Card, Text, VerticalStack, Box, HorizontalStack } from '@shopify/polaris';

function FlowGraphLayout({
  loading,
  loadingMessage = 'Loading...',
  nodes,
  edges,
  nodeTypes,
  edgeTypes,
  defaultEdgeType,
  emptyMessage = 'No data found',
  bannerTitle,
  bannerStats = [],
  showData,
  miniMapNodeColor = '#5c6ac4'
}) {

  const Banner = () => (
    <Card padding="6">
      <VerticalStack gap="4">
        <HorizontalStack align="space-between">
          <Text variant="headingMd" fontWeight="semibold">{bannerTitle}</Text>
        </HorizontalStack>
        <HorizontalStack gap="24">
          {bannerStats.map((stat, index) => (
            <Box key={index}>
              <Text variant="bodyMd" color="subdued">{stat.label}</Text>
              <Text variant="headingLg" fontWeight="bold">{stat.value}</Text>
            </Box>
          ))}
        </HorizontalStack>
      </VerticalStack>
    </Card>
  );

  if (loading) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <p>{loadingMessage}</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px' }}>
      <Banner />

      {!showData ? (
        <div style={{ textAlign: 'center', padding: '40px' }}>
          <p>{emptyMessage}</p>
        </div>
      ) : (
        <div style={{ height: "calc(100vh - 300px)", minHeight: "600px", marginTop: '20px', border: '1px solid #e1e3e5', borderRadius: '8px' }}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            fitView
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            defaultEdgeOptions={{
              type: defaultEdgeType,
              animated: false,
            }}
            minZoom={0.1}
            maxZoom={2}
            defaultZoom={0.8}
            attributionPosition="bottom-right"
          >
            <Background color="#aaa" gap={16} />
            <Controls
              showZoom={true}
              showFitView={true}
              showInteractive={true}
              style={{
                button: {
                  backgroundColor: '#fff',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                }
              }}
            />
            <MiniMap
              nodeColor={() => miniMapNodeColor}
              maskColor="rgba(0, 0, 0, 0.1)"
              style={{
                backgroundColor: '#f6f6f7',
                border: '1px solid #e1e3e5',
                borderRadius: '4px',
              }}
            />
          </ReactFlow>
        </div>
      )}
    </div>
  );
}

export default FlowGraphLayout;
