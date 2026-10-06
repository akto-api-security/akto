import { Badge, Card, DataTable, HorizontalStack, Text } from '@shopify/polaris'
import './AgentDetail.css'

const MAX_TAGS = 5

function Heading({ children }) {
    return (
        <span className="ad-label">
            <Text as="span" variant="bodySm" fontWeight="semibold" color="subdued">{children}</Text>
        </span>
    )
}

// A rule nobody enabled is a gap, so it reads critical rather than muted.
function Status({ enabled }) {
    if (!enabled) {
        return <Text as="span" variant="bodySm" fontWeight="semibold" color="critical">Not enabled</Text>
    }
    return (
        <span className="ad-text-success">
            <Text as="span" variant="bodySm" fontWeight="semibold">Enabled</Text>
        </span>
    )
}

function Details({ rule }) {
    const details = rule.details || []
    if (details.length === 0) return <Text as="span" color="subdued">—</Text>

    const shown = details.slice(0, MAX_TAGS)
    const rest = (rule.detailsTotal || details.length) - shown.length

    return (
        <HorizontalStack gap="1">
            {shown.map((value) => <Badge key={value}>{value}</Badge>)}
            {rest > 0 && <Badge>{`+${rest}`}</Badge>}
        </HorizontalStack>
    )
}

function ProtectionSection({ protection }) {
    const rows = protection || []
    if (rows.length === 0) return null

    return (
        <Card padding="0">
            <DataTable
                columnContentTypes={['text', 'text', 'text', 'text']}
                headings={[
                    <Heading key="rule">Rule</Heading>,
                    <Heading key="status">Status</Heading>,
                    <Heading key="details">Details</Heading>,
                    <Heading key="appliesOn">Applies on</Heading>,
                ]}
                rows={rows.map((rule) => [
                    <Text as="span" variant="bodyMd" fontWeight="medium">{rule.name}</Text>,
                    <Status enabled={rule.enabled} />,
                    <Details rule={rule} />,
                    <Text as="span" variant="bodyMd" color="subdued">{rule.appliesOn || '—'}</Text>,
                ])}
                verticalAlign="middle"
                increasedTableDensity
                hideScrollIndicator
            />
        </Card>
    )
}

export default ProtectionSection
