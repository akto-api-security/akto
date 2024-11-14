import { Avatar, Box, Button, Card, InlineStack, Link, Text, VerticalStack } from '@shopify/polaris'
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
                  <InlineStack align="center">
                      <Box width='400px'>
                          <VerticalStack gap={8}>
                              <InlineStack align="center">
                                  <Box width='162px' minHeight='162px' borderRadius="full" background="bg-subdued">
                                      <div className="empty-icon-container">
                                          <Avatar source={iconSrc} size=""/>
                                      </div>
                                  </Box>
                              </InlineStack>
                              <VerticalStack gap={4}>
                                  <InlineStack align='center'>
                                      <Text variant="headingLg" alignment={"center"}>{headingText}</Text>
                                  </InlineStack>
                                  
                                  <Text color="subdued" variant="bodyMd" alignment="center">{description}</Text>
                                  
                                  <InlineStack align='center'>
                                      {redirectUrl ? <Box paddingBlockStart={2}>
                                          <Button  onClick={() => navigate(redirectUrl)} variant="primary">{buttonText}</Button>
                                      </Box> : null}
                                      {bodyComponent}
                                  </InlineStack>
                              </VerticalStack>
                          </VerticalStack>
                      </Box>
                  </InlineStack>
              </Box>
          </Card>
          { learnText ? 
          <InlineStack align="center">
              <InlineStack gap="1">Learn more about<Link url={docsUrl} target="_blank">{learnText}</Link>
              </InlineStack>
          </InlineStack>
          : <></>}
          {infoItems ? <InfoComponent title={infoTitle} items={infoItems} /> : null}
      </VerticalStack>
  );
}

export default EmptyScreensLayout