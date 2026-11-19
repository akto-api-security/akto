import { Badge, Card, DataTable, Text } from '@shopify/polaris'

// "Scope" is the tool's own endpoint identity (URL/method or MCP resource+host), not a real
// OAuth-scope string — no such field exists anywhere (verified) — labeled honestly rather than
// implying a granted-permission model that doesn't exist yet.
function PermissionsSection({ permissions }) {
    const rows = (permissions || []).map((p) => [
        <Text variant="bodyMd" fontWeight="medium">{p.scope}</Text>,
        p.resource,
        p.usedIn30d ? `Yes · ${p.callCount} calls` : 'No',
        <Badge status={p.statusTone}>{p.status}</Badge>,
    ])
    return (
        <Card>
            <DataTable
                columnContentTypes={['text', 'text', 'text', 'text']}
                headings={['Tool', 'Resource', 'Used, 30d', 'Status']}
                rows={rows}
                hideScrollIndicator
                increasedTableDensity
            />
        </Card>
    )
}

export default PermissionsSection
