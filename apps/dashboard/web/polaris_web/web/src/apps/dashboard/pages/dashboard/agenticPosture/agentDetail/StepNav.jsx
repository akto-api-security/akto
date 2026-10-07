import { useState } from 'react'
import { HorizontalStack } from '@shopify/polaris'
import './AgentDetail.css'

// hidden: the section has no data source in the Argus context yet, so the page does not render it
// and this step stays out of the nav. The entry is kept so turning the section back on is one flag.
const STEPS = [
    { id: 'agent', label: 'Agent' },
    { id: 'owner', label: 'Owner', hidden: true },
    { id: 'identity', label: 'Identity', hidden: true },
    { id: 'permissions', label: 'Permissions', hidden: true },
    { id: 'tools', label: 'Tools & Capabilities' },
    { id: 'redTeam', label: 'Red Teaming' },
    { id: 'data', label: 'Data' },
    { id: 'protection', label: 'Protection' },
    { id: 'runtime', label: 'Runtime Activity', hidden: true },
]

function StepNav() {
    const [current, setCurrent] = useState('agent')

    return (
        <HorizontalStack gap="1">
            {STEPS.filter((step) => !step.hidden).map((step) => (
                <a
                    key={step.id}
                    href={`#${step.id}`}
                    onClick={() => setCurrent(step.id)}
                    className={step.id === current ? 'ad-steplink ad-steplink--current' : 'ad-steplink'}
                >
                    {step.label}
                </a>
            ))}
        </HorizontalStack>
    )
}

export default StepNav
