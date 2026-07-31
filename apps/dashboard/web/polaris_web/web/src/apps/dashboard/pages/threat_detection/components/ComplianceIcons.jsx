import { Avatar, Badge, Box, HorizontalStack, Text } from "@shopify/polaris";
import func from "@/util/func";

// Renders a compliance-framework icon list with a "+N" overflow badge.
// Shared by the Guardrail Activity table (SusDataTable) and the Violations page.
export default function ComplianceIcons({ complianceList = [], max = 2 }) {
    if (!complianceList.length) return <Text color="subdued">-</Text>;
    return (
        <HorizontalStack wrap={false} gap={1}>
            {complianceList.slice(0, max).map((complianceName, idx) => (
                <Avatar
                    key={idx}
                    source={func.getComplianceIcon(complianceName)}
                    shape="square"
                    size="extraSmall"
                />
            ))}
            {complianceList.length > max && (
                <Box>
                    <Badge size="extraSmall">+{complianceList.length - max}</Badge>
                </Box>
            )}
        </HorizontalStack>
    );
}
