import { Badge, Box, Card, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import { SeverityBadge } from '../../../observe/agentic/AgenticCellRenderers'
import ChainVisual from '../ChainVisual'

// End-to-end chains where untrusted input reaches a privileged action against a sensitive
// resource with a control missing in between. Sample data for now (no graph/taint-tracing engine
// exists yet to detect these for real — see the posture plan's Phase 3); rendered plainly like
// every other section on this page until real vs. not-yet-wired sections actually diverge.
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
        <VerticalStack gap="3">
            {paths.map((path) => <PathCard key={path.id} path={path} />)}
        </VerticalStack>
    )
}

export default DangerousPathsSection
