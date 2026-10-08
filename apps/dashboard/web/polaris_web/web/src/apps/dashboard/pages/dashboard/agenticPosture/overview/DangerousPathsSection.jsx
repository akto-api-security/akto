import { Badge, Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import { ComingSoonOverlay } from '../../agenticPostureShared'
import ChainVisual from '../ChainVisual'

// Illustrative chains, blurred behind "Coming soon" until the path-tracing engine exists.
function PathCard({ path }) {
    return (
        <Card>
            <Box padding="4">
                <VerticalStack gap="3">
                    <HorizontalStack gap="2" blockAlign="center">
                        <SeverityBadge severity={path.severity} />
                        <Text variant="bodyMd" fontWeight="semibold">{path.title}</Text>
                    </HorizontalStack>
                    <ChainVisual chain={path.chain} />
                    <VerticalStack gap="2">
                        <div>
                            {(path.tags || []).map((tag) => (
                                <span key={tag} style={{ display: 'inline-block', marginRight: 6, marginBottom: 6 }}>
                                    <Badge>{tag}</Badge>
                                </span>
                            ))}
                        </div>
                        <Text variant="bodySm" color="subdued">{path.explanation}</Text>
                    </VerticalStack>
                </VerticalStack>
            </Box>
        </Card>
    )
}

function DangerousPathsSection({ dangerousPaths }) {
    if (!dangerousPaths) return null
    const paths = dangerousPaths.illustrative || []
    return (
        <ComingSoonOverlay panelId="dangerousPaths">
            <VerticalStack gap="3">
                {paths.map((path) => <PathCard key={path.id} path={path} />)}
            </VerticalStack>
        </ComingSoonOverlay>
    )
}

export default DangerousPathsSection
