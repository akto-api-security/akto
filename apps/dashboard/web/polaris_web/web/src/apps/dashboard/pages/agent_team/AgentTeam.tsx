import React from 'react';
import FlyLayout from '../../components/layouts/FlyLayout';

function AgentTeam() {
    return (
        <div>
             <FlyLayout
                title="Member Details"
                show={true}
                setShow={() => {}}
                components={[<div>To implelemt</div>]}
                loading={false}
            />
        </div>
    )
}

export default AgentTeam;