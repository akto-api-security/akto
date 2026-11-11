import { Box, Card, HorizontalStack, Icon, Text, VerticalStack } from "@shopify/polaris";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { LOAD_STATE } from "../constants";
import "./discovery.css";

// Card frame with loading/error/empty states; flush drops body padding, fill pins a footer to the bottom, center middles the body; emptyIcon tops an empty message.
export default function DiscoveryCard({ title, subtitle, legend, status, empty, emptyText, emptyIcon, flush, fill, center, children }) {
    let body = children;
    let isMessage = false;
    if (status === LOAD_STATE.LOADING) {
        body = <SpinnerCentered />;
        isMessage = true;
    } else if (status === LOAD_STATE.ERROR) {
        body = <Text as="p" color="subdued">This section is unavailable right now.</Text>;
        isMessage = true;
    } else if (empty) {
        body = (
            <VerticalStack gap="2" inlineAlign={flush ? "center" : "start"}>
                {emptyIcon && <Icon source={emptyIcon} tone="success" />}
                <Text as="p" color="subdued" alignment={flush ? "center" : "start"}>{emptyText || "Nothing to show yet."}</Text>
            </VerticalStack>
        );
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
                {isMessage ? <div className="discovery-card-message"><Box padding="4">{body}</Box></div> : body}
            </Card>
        );
        return fill ? <div className="discovery-card-fill">{card}</div> : card;
    }
    if (center) {
        return (
            <Card padding="4">
                <div className="discovery-card-center">
                    <VerticalStack gap="3">
                        {header}
                        {!isMessage && legend}
                    </VerticalStack>
                    <div className="discovery-card-center__body">{body}</div>
                </div>
            </Card>
        );
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
