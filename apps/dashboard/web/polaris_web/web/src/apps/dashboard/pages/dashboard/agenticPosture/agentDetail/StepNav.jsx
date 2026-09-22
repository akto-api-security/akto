import { HorizontalStack, Link } from '@shopify/polaris'

const STEPS = [
    { id: 'agent', label: 'Agent' },
    { id: 'owner', label: 'Owner' },
    { id: 'identity', label: 'Identity' },
    { id: 'permissions', label: 'Permissions' },
    { id: 'tools', label: 'Tools' },
    { id: 'data', label: 'Data' },
    { id: 'protection', label: 'Protection' },
    { id: 'runtime', label: 'Runtime Activity' },
]

// Plain in-page anchor links — the browser handles the scroll natively (matches the original
// mockup's own <a href="#section"> behaviour), no scrollspy JS or custom CSS needed.
function StepNav() {
    return (
        <HorizontalStack gap="4" wrap>
            {STEPS.map((step) => (
                <Link key={step.id} url={`#${step.id}`} removeUnderline>
                    {step.label}
                </Link>
            ))}
        </HorizontalStack>
    )
}

export default StepNav
