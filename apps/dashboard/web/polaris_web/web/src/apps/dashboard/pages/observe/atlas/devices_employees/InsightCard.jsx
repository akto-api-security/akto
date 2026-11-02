import { Box, Card, Text, VerticalStack } from "@shopify/polaris";
import SpinnerCentered from "../../../../components/progress/SpinnerCentered";

function InsightCard({ title, subtitle, status, children }) {
    let body = children;
    if (status === "loading") body = <SpinnerCentered />;
    if (status === "error") body = <Text as="p" color="subdued">This card could not be loaded. The other cards are unaffected.</Text>;

    return (
        <Card padding="0">
            <Box padding="5">
                <VerticalStack gap="4">
                    <VerticalStack gap="1">
                        <Text as="h3" variant="headingMd">{title}</Text>
                        {subtitle && <Text as="p" color="subdued">{subtitle}</Text>}
                    </VerticalStack>
                    {body}
                </VerticalStack>
            </Box>
        </Card>
    );
}

export default InsightCard;
