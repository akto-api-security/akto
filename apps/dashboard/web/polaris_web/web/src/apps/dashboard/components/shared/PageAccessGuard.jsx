import React from "react"
import { Box, Text, VerticalStack } from "@shopify/polaris"
import { useLocation } from "react-router-dom"
import EmptyScreensLayout from "../banners/EmptyScreensLayout"
import { usePermissions } from "@/util/permissions"
import SpinnerCentered from "../progress/SpinnerCentered"

/*
 * Waits for the user's permissions, then shows "Access restricted" instead of a page the user's role can't open (e.g. opened from a link or by URL),
 * so the page doesn't load and fail. Paths under `skip` are left to an inner guard (e.g. settings has its own).
 */
function PageAccessGuard({ children, skip }) {
    const location = useLocation()
    const { canOpen, loaded, noProductAccess } = usePermissions()
    // pages decide what to load from the permissions, so they wait for them (one small call when the dashboard opens)
    if (!loaded) {
        return <SpinnerCentered />
    }
    if ((skip && location.pathname.toLowerCase().startsWith(skip)) || canOpen(location.pathname)) {
        return children
    }
    return (
        <Box padding="8" width="100%">
            <EmptyScreensLayout
                iconSrc={"/public/upgrade.svg"}
                headingText={"Access restricted"}
                description={
                    <VerticalStack gap="2">
                        <Text variant="bodyMd" color="subdued" alignment="center">
                            {noProductAccess ? "You don't have access to this product." : "Your role doesn't include this page."}
                        </Text>
                        <Text variant="bodyMd" color="subdued" alignment="center">
                            {noProductAccess ? "Switch to another product, or ask an admin for access." : "Ask an admin if you need access."}
                        </Text>
                    </VerticalStack>
                }
            />
        </Box>
    )
}

export default PageAccessGuard
