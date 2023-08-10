import React from 'react';

import { Handle, Position } from 'react-flow-renderer';

import './start-node.css';

const StartNode = () => {
    return (
        <div className={"start-node"}>
            <div>Start</div>
            <Handle
                type="source"
                position={Position.Bottom}
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

export default StartNode