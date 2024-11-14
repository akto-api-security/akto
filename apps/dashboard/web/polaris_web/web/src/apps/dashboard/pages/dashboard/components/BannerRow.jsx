import { Box, Link, InlineStack, Icon, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronRightMinor } from "@shopify/polaris-icons"

function BannerRow(props) {
    const {title, icon, description, redirectUrl, showRedirect, newTab} = props.cardObj
    const navigate = useNavigate()

    const titleComp = (
        <InlineStack gap={1}>
            <Text fontWeight="semibold" color="subdued">{title}</Text>
            {showRedirect ? <Box><Icon source={ChevronRightMinor} tone="subdued"/></Box> : null}
        </InlineStack>
    )
    
    return (
        <div style={{display: 'flex', gap: '12px'}}>
            <Box>
                <Icon source={icon} tone="base" />
            </Box>
            <VerticalStack gap={2}>
                {redirectUrl ? <Link monochrome removeUnderline onClick={() => newTab ? window.open(redirectUrl, "_blank") : navigate(redirectUrl)}>
                    {titleComp}
                </Link>
                : <Box>
                    {titleComp}
                </Box>}
                <Text color="subdued">
                    {description}
                </Text>
            </VerticalStack>
        </div>
    );
}

export default BannerRow