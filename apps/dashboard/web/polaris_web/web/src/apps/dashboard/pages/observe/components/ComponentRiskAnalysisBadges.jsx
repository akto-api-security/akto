import { Text, VerticalStack, Badge, Tooltip, Box } from "@shopify/polaris";

/**
 * Renders Component Risk Analysis tags: "Malicious" (High severity style) and/or "Excessive Access" (Low severity style) with optional evidence tooltip.
 * Reuses dashboard.css badge-wrapper-HIGH and badge-wrapper-LOW for consistent severity styling.
 */
function ComponentRiskAnalysisBadges({ componentRiskAnalysis }) {
    if (!componentRiskAnalysis) return <Text as="span">-</Text>;
    const hasPrivilegedAccess = componentRiskAnalysis.hasPrivilegedAccess;
    const isComponentMalicious = componentRiskAnalysis.isComponentMalicious;
    const evidence = componentRiskAnalysis.evidence;
    const tags = [];
    if (isComponentMalicious) {
        tags.push(<div key="malicious" className="badge-wrapper-HIGH"><Badge size="small">Malicious</Badge></div>);
    }
    if (hasPrivilegedAccess) {
        tags.push(<div key="privileged" className="badge-wrapper-MEDIUM"><Badge size="small">Privileged Access</Badge></div>);
    }
    const content = tags.length > 0 ? (
        <VerticalStack gap="2">
            {tags}
        </VerticalStack>
    ) : (
        <Text as="span">-</Text>
    );
    return evidence ? (
        <Tooltip
            content={<Box maxWidth="280px">{evidence}</Box>}
            dismissOnMouseOut
        >
            {content}
        </Tooltip>
    ) : content;
}

export default ComponentRiskAnalysisBadges;
