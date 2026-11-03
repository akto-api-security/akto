import { Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import "./discovery.css";

// Card frame with loading/error/empty states; flush drops body padding, fill pins a footer to the bottom.
export default function DiscoveryCard({ title, subtitle, legend, status, empty, emptyText, flush, fill, children }) {
    let body = children;
    let isMessage = false;
    if (status === "loading") {
        body = <SpinnerCentered />;
        isMessage = true;
    } else if (status === "error") {
        body = <Text as="p" color="subdued">This section is unavailable right now.</Text>;
        isMessage = true;
    } else if (empty) {
        body = <Text as="p" color="subdued">{emptyText || "Nothing to show yet."}</Text>;
        isMessage = true;
    }

    const header = (
        <HorizontalStack align="space-between" blockAlign="start" gap="2" wrap={false}>
            <VerticalStack gap="0">
                <Text variant="headingMd" as="h2">{title}</Text>
                {subtitle && <Text as="p" color="subdued">{subtitle}</Text>}
            </VerticalStack>
        </HorizontalStack>
    );

    if (flush) {
        const card = (
            <Card padding="0">
                <Box padding="4" paddingBlockEnd="2">{header}</Box>
                {isMessage ? <Box padding="4">{body}</Box> : body}
            </Card>
        );
        return fill ? <div className="discovery-card-fill">{card}</div> : card;
    }
    return (
        <Card padding="4">
            <VerticalStack gap="3">
                {header}
                {!isMessage && legend}
                {body}
            </VerticalStack>
        </Card>
    );
}
