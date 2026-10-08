import { Tabs } from '@shopify/polaris'

// Plain Polaris Tabs — no custom pill styling. `environments` is the mock/real
// [{id,label,count}] list; the "All environments" tab has a null count.
function EnvironmentTabs({ environments, selected, onSelect }) {
    const tabs = (environments || []).map((env) => ({
        id: env.id,
        content: env.count === null || env.count === undefined ? env.label : `${env.label} · ${env.count}`,
    }))
    const selectedIndex = Math.max(0, tabs.findIndex((t) => t.id === selected))
    return (
        <Tabs
            tabs={tabs}
            selected={selectedIndex}
            onSelect={(index) => onSelect(environments[index]?.id)}
        />
    )
}

export default EnvironmentTabs
