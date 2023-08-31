import React from 'react';

import { Handle, Position } from 'react-flow-renderer';

import './start-node.css';

const EndNode = () => {
    return (
        <div className={"start-node"}>
            <div>End</div>
            <Handle
                type="target"
                position={Position.Top}
                style={{
                    height: '13px',
                    width: '13px',
                    bottom: '-6px',
                    cursor: 'pointer'
                }}
                isConnectable={true}
            >
                
            </Handle>

        </div>
    )
}

export default EndNode