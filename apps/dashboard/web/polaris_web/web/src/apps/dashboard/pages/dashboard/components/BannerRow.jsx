import { Box, Link, InlineStack, Icon, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'
import { ChevronRightIcon } from "@shopify/polaris-icons";

function BannerRow(props) {
    const {title, icon, description, redirectUrl, showRedirect, newTab} = props.cardObj
    const navigate = useNavigate()

    const titleComp = (
        <InlineStack gap={100}>
            <Text fontWeight="semibold" color="subdued">{title}</Text>
            {showRedirect ? <Box><Icon source={ChevronRightIcon} tone="subdued"/></Box> : null}
        </InlineStack>
    )
    
    return (
        <div style={{display: 'flex', gap: '12px'}}>
            <Box>
                <Icon source={icon} tone="base" />
            </Box>
            <BlockStack gap={200}>
                {redirectUrl ? <Link monochrome removeUnderline onClick={() => newTab ? window.open(redirectUrl, "_blank") : navigate(redirectUrl)}>
                    {titleComp}
                </Link>
                : <Box>
                    {titleComp}
                </Box>}
                <Text color="subdued">
                    {description}
                </Text>
            </BlockStack>
        </div>
    );
}

export default BannerRow