import { Banner, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function ForbiddenRole() {
    const currentRole = window.USER_ROLE
    return (
        <Banner
            status="warning"
            title="Unauthorized Access"
        >
            <VerticalStack gap={"2"}>
                <HorizontalStack gap={"1"}>
                    <Text variant="bodyMd">Your current role</Text>
                    <Text variant="bodyMd" fontWeight="medium">{currentRole}</Text>
                    <Text> does not have access to this page. </Text>
                </HorizontalStack>
                <Text variant="bodyMd" color="subdued" fontWeight="medium">Please contact your administrator to get access.</Text>
            </VerticalStack>
        </Banner>
    )
}

export default ForbiddenRole