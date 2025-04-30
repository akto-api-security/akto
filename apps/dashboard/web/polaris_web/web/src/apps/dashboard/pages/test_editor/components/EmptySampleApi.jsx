import { Avatar, Box, Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import { useNavigate } from 'react-router-dom'

const EmptySampleApi = ({ iconSrc, headingText, description, buttonText, redirectUrl }) => {
    const navigate = useNavigate()

    return (
        <Box minHeight='100%' paddingBlockStart={5} paddingBlockEnd={16}>
            <HorizontalStack blockAlign='center' align="center">
                <Box width='400px'>
                    <VerticalStack gap={8}>
                        <HorizontalStack align="center">
                            <Box width='162px' minHeight='162px' borderRadius="full" background="bg-subdued">
                                <div className="empty-icon-container">
                                    <Avatar source={iconSrc} size=""/>
                                </div>
                            </Box>
                        </HorizontalStack>
                        <VerticalStack gap={4}>
                            <HorizontalStack align='center'>
                                <Text variant="headingLg" alignment={"center"}>{headingText}</Text>
                            </HorizontalStack>
                            
                            <Text color="subdued" variant="bodyMd" alignment="center">{description}</Text>
                            
                            <HorizontalStack align='center'>
                                {redirectUrl ? <Box paddingBlockStart={2}>
                                    <Button primary onClick={() => navigate(redirectUrl)}>{buttonText}</Button>
                                </Box> : null}
                            </HorizontalStack>
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </HorizontalStack>
        </Box>
    )
}

export default EmptySampleApi