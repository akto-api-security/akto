import { Avatar, Box, Button, InlineStack, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'

const EmptySampleApi = ({ iconSrc, headingText, description, buttonText, redirectUrl }) => {
    const navigate = useNavigate()

    return (
        <Box minHeight='100%' paddingBlockStart={500} paddingBlockEnd={1600}>
            <InlineStack blockAlign='center' align="center">
                <Box width='400px'>
                    <BlockStack gap={800}>
                        <InlineStack align="center">
                            <Box width='162px' minHeight='162px' borderRadius="full" background="bg-subdued">
                                <div className="empty-icon-container">
                                    <Avatar source={iconSrc} size=""/>
                                </div>
                            </Box>
                        </InlineStack>
                        <BlockStack gap={400}>
                            <InlineStack align='center'>
                                <Text variant="headingLg" alignment={"center"}>{headingText}</Text>
                            </InlineStack>
                            
                            <Text color="subdued" variant="bodyMd" alignment="center">{description}</Text>
                            
                            <InlineStack align='center'>
                                {redirectUrl ? <Box paddingBlockStart={200}>
                                    <Button primary onClick={() => navigate(redirectUrl)}>{buttonText}</Button>
                                </Box> : null}
                            </InlineStack>
                        </BlockStack>
                    </BlockStack>
                </Box>
            </InlineStack>
        </Box>
    )
}

export default EmptySampleApi