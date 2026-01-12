import { Text, Card, VerticalStack, Box, Badge } from '@shopify/polaris'
import FlyLayout from '@/apps/dashboard/components/layouts/FlyLayout'
import SampleData from '@/apps/dashboard/components/shared/SampleData'
import GridRows from '@/apps/dashboard/components/shared/GridRows'

const AuditLogDetails = ({ showDetails, setShowDetails, auditLog }) => {

    const { metadata, actionDescription, ...details } = auditLog || {}

    const { apiEndpoint, resource, operation, userEmail, rawUserAgent, timestamp, userIpAddress } = details 
    const detailsGridItems = [
        { title: "Operation", value: apiEndpoint || "-" },
        { title: "Resource", value: resource && resource !== "NOT_SPECIFIED" ? <Box><Badge status='info'>{resource}</Badge></Box> : "-"  },
        { title: "Operation type", value: operation && operation !== "NOT_SPECIFIED" ? <Box><Badge status='info'>{operation}</Badge></Box> : "-" },
        { title: "User email", value: userEmail || "-" },
        { title: "User agent", value: rawUserAgent ? <Box><Badge status='info'>{rawUserAgent}</Badge></Box> : "-" },
        { title: "Time", value: timestamp || "-" },
        { title: "User IP address", value: userIpAddress || "-" },
    ]

    const DetailsGridComp = ({ cardObj }) => {
        const { title, value } = cardObj
        return (
            value ? <Box width="224px">
                <VerticalStack gap={"2"}>
                    <Text variant="headingSm">{title}</Text>
                    {value}
                </VerticalStack>
            </Box> : null
        )
    }

    let metadataStr = ""
    if (metadata && typeof metadata === 'object' && Object.keys(metadata).length > 0) {
        metadataStr = JSON.stringify(metadata, null, 2)
    }

    const components = [
       (
            <Box paddingBlockEnd={2}>
                <VerticalStack gap="2">
                    <Text variant="headingSm" alignment="start">Description</Text>
                    <Text variant="bodyMd">{actionDescription ? actionDescription : ""}</Text>
                </VerticalStack>
            </Box>
        ),
        (
            <Box paddingBlockStart={2} paddingBlockEnd={2}>
                <GridRows columns={3} items={detailsGridItems} CardComponent={DetailsGridComp} />
            </Box>
        ),
        metadataStr ? (
            <Box paddingBlockStart={2}>
                <VerticalStack gap="2">
                    <Text variant="headingSm" alignment="start">More details</Text>
                    <Card padding="2">
                        <SampleData data={{ message: metadataStr }} editorLanguage="json" minHeight="75px" />
                    </Card>
                </VerticalStack>
            </Box>
        ) : null
    ]

    return (
        <FlyLayout
            title="Details"
            show={showDetails}
            setShow={setShowDetails}
            components={components}
            showDivider={true}
        />
    )
}

export default AuditLogDetails;