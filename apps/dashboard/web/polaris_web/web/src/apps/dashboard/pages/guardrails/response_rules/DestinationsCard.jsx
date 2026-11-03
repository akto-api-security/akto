import { Badge, Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import { useNavigate } from "react-router-dom";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";
import { DESTINATION_PAGES } from "./constants";

function DestinationTile({ destination, onOpen }) {
    return (
        <Box borderWidth="1" borderColor="border-subdued" borderRadius="2" padding="3">
            <VerticalStack gap="2">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text as="span" fontWeight="semibold">{destination.label}</Text>
                    <Badge status={destination.connected ? "success" : undefined}>{destination.connected ? "Connected" : "Not connected"}</Badge>
                </HorizontalStack>
                <Text as="p" variant="bodySm" color="subdued">{destination.detail}</Text>
                <Box>
                    <Button size="slim" onClick={() => onOpen(destination.id)}>{destination.connected ? "Configure" : "Connect"}</Button>
                </Box>
            </VerticalStack>
        </Box>
    );
}

function DestinationsCard({ destinations }) {
    const navigate = useNavigate();
    return (
        <Card>
            <VerticalStack gap="4">
                <VerticalStack gap="1">
                    <TitleWithInfo titleText="Destinations" tooltipContent="Integrations that rules can send tickets and alerts to" textProps={{ variant: "headingMd" }} />
                    <Text as="p" color="subdued">Where tickets and alerts are sent</Text>
                </VerticalStack>
                <HorizontalGrid columns={{ xs: 1, sm: 2, lg: 4 }} gap="3">
                    {destinations.map((destination) => (
                        <DestinationTile key={destination.id} destination={destination} onOpen={(id) => navigate(DESTINATION_PAGES[id])} />
                    ))}
                </HorizontalGrid>
            </VerticalStack>
        </Card>
    );
}

export default DestinationsCard;
