import { Link, Text, Box, Button, HorizontalStack, VerticalStack, Avatar } from "@shopify/polaris";
import EmptyScreensLayout from "../dashboard/components/banners/EmptyScreensLayout";
import {
    createBrowserRouter,
    RouterProvider
} from "react-router-dom";

function FreeApp() {
    const contactSalesUrl = "https://www.akto.io/demo";
    const signInUrl = "https://app.akto.io/";

    const description = (
        <Text>
            Free access is not available. Reach out to our sales team to discuss your organization's requirements and next steps.
        </Text>
    );

    const bodyComponent = (
        <Box paddingBlockStart={2}>
            <Button url={contactSalesUrl} outline>
                Contact Sales
            </Button>
        </Box>
    );

    const router = createBrowserRouter([
        {
            path: "*",
            element: (
                <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', justifyContent: 'center', alignItems: 'center' }}>
                    <HorizontalStack align="center" blockAlign="center">
                        <img src={"/public/akto_name_with_logo.svg"} alt="Akto Logo" style={{ maxWidth: '116px' }} />
                    </HorizontalStack>
                    <Box padding="8" width="80%">
                        <EmptyScreensLayout
                            key={"freeAccessScreen"}
                            iconSrc={"/public/upgrade.svg"}
                            headingText={"Access Restricted"}
                            description={description}
                            bodyComponent={bodyComponent}
                        />
                        <Box paddingBlockStart={4}>
                            <VerticalStack align="center" gap={2} inlineAlign="center">
                                <Text variant="bodySm" color="subdued">Already a customer?</Text>
                                <Link url={signInUrl}>Sign in with your organization account</Link>
                            </VerticalStack>
                        </Box>
                    </Box>
                </div>
            )
        }
    ]);

    return (
        <RouterProvider router={router} />
    );
}

export default FreeApp;