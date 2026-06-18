import { Box, Button, Text, VerticalStack } from "@shopify/polaris"
import { useNavigate } from "react-router-dom"

function WizSource() {
    const navigate = useNavigate()

    return (
        <Box paddingBlockStart="4">
            <VerticalStack gap="4">
                <Text variant="bodyMd">
                    Once you connect Wiz, Akto will periodically import your API endpoints to keep your inventory up to date.
                </Text>
                <Button primary onClick={() => navigate("/dashboard/settings/integrations/wiz")}>
                    Go to Wiz settings
                </Button>
            </VerticalStack>
        </Box>
    )
}

export default WizSource;
