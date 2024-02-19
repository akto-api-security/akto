import { Avatar, Box, Button, Card, HorizontalStack, Link, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import "../shared/style.css"
import { useNavigate } from 'react-router-dom'
import { InfoComponent } from "../../pages/dashboard/components/DashboardBanner"

function EmptyScreensLayout({iconSrc,headingText,description, buttonText, redirectUrl, docsUrl, infoTitle, infoItems, learnText, bodyComponent}) {
  const navigate = useNavigate();  
  return (
    <VerticalStack gap={5}>
        <Card padding={5}>
            <Box paddingBlockStart={5} paddingBlockEnd={16}>
                <HorizontalStack align="center">
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
                                    {bodyComponent}
                                </HorizontalStack>
                            </VerticalStack>
                        </VerticalStack>
                    </Box>
                
                </HorizontalStack>
            </Box>
        </Card>
        { learnText ? 
        <HorizontalStack align="center">
            <HorizontalStack gap="1">
                Learn more about
                <Link url={docsUrl} target="_blank">{learnText}</Link>
            </HorizontalStack>
        </HorizontalStack>
        : <></>}
        {infoItems ? <InfoComponent title={infoTitle} items={infoItems} /> : null}
    </VerticalStack>
  )
}

export default EmptyScreensLayout