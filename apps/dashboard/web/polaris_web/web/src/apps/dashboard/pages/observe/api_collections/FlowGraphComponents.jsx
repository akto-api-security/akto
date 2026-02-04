import React from 'react';
import { getBezierPath, Handle, Position } from 'react-flow-renderer';
import GetPrettifyEndpoint from '../GetPrettifyEndpoint';

export const createCustomEdge = (markerId, strokeColor = 'grey', useColorVariation = false) => {
  return ({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, style = {}, data }) => {
    const edgePath = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
    const { label, order, param } = data || {};

    // Use light grey for swagger dependencies edge lines, grey for sequences
    let edgeColor = strokeColor;
    let textColor = '#666';

    if (useColorVariation) {
      edgeColor = '#D3D3D3'; // Very light grey for edge line
      textColor = '#333'; // Very dark grey for param text
    }

    // Only show param name, not order
    let displayLabel = '';
    if (param) {
      displayLabel = param;
    } else if (label) {
      displayLabel = label;
    }

    return (
      <g>
        <svg>
          <defs>
            <marker id={`${markerId}-${order || 0}`} markerWidth="14" markerHeight="14" refX="9" refY="7" orient="auto" markerUnits="strokeWidth">
              <path d="M0,0 L9,7 L0,14" fill='none' stroke={edgeColor} />
            </marker>
          </defs>
        </svg>
        <path
          id={id}
          style={{ ...style, stroke: edgeColor, strokeWidth: useColorVariation ? 2 : 1 }}
          className="react-flow__edge-path"
          d={edgePath}
          markerEnd={`url(#${markerId}-${order || 0})`}
        />
        {displayLabel && (
          <text>
            <textPath href={`#${id}`} startOffset="50%" textAnchor="middle">
              <tspan style={{
                fontSize: '11px',
                fill: textColor,
                fontWeight: useColorVariation ? '600' : 'normal'
              }}>
                {displayLabel}
              </tspan>
            </textPath>
          </text>
        )}
      </g>
    );
  };
};

export const createCustomNode = (borderColor = '#ccc', borderWidth = '1px', handleColor = '#555', boxShadow = '0 1px 3px rgba(0,0,0,0.1)') => {
  return ({ data }) => {
    const { method, url, incomingCount, outgoingCount, showCounts } = data;
    return (
      <div style={{
        background: '#ffffff',
        border: `${borderWidth} solid ${borderColor}`,
        borderRadius: '4px',
        padding: '10px',
        minWidth: '200px',
        boxShadow: boxShadow
      }}>
        <Handle type="target" position={Position.Top} id="t" style={{ background: handleColor }} />
        <GetPrettifyEndpoint
          method={method}
          url={url}
          isNew={false}
          maxWidth="180px"
          methodBoxWidth="54px"
        />
        {showCounts && (incomingCount > 0 || outgoingCount > 0) && (
          <div style={{
            marginTop: '8px',
            fontSize: '11px',
            color: '#666',
            display: 'flex',
            justifyContent: 'space-between',
            borderTop: '1px solid #e1e3e5',
            paddingTop: '6px'
          }}>
            {incomingCount > 0 && <span>↓ {incomingCount} in</span>}
            {outgoingCount > 0 && <span>↑ {outgoingCount} out</span>}
          </div>
        )}
        <Handle type="source" position={Position.Bottom} id="b" style={{ background: handleColor }} />
      </div>
    );
  };
};
