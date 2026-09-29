import { Badge, Card, DataTable, Text } from '@shopify/polaris'

function ProtectionSection({ protection }) {
    const rows = (protection || []).map((p) => [
        <Text variant="bodyMd" fontWeight="medium">{p.control}</Text>,
        <Badge status={p.statusTone}>{p.status}</Badge>,
    ])
    return (
        <Card>
            <DataTable
                columnContentTypes={['text', 'text']}
                headings={['Control', 'Status']}
                rows={rows}
                hideScrollIndicator
                increasedTableDensity
            />
        </Card>
    )
}

export default ProtectionSection
