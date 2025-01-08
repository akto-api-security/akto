import { Avatar, Box, Button, Card, InlineStack, Link, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import "../shared/style.css"
import { useNavigate } from 'react-router-dom'
import { InfoComponent } from "../../pages/dashboard/components/DashboardBanner"

function EmptyScreensLayout({iconSrc,headingText,description, buttonText, redirectUrl, docsUrl, infoTitle, infoItems, learnText, bodyComponent,onAction}) {
  const navigate = useNavigate();  
  return (
      <BlockStack gap={500}>
          <Card padding={500}>
              <Box paddingBlockStart={500} paddingBlockEnd={1600}>
                  <InlineStack align="center">
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
                                  <Text tone="subdued" variant="bodyMd" alignment="center">{description}</Text>
                                  <InlineStack align='center'>
                                      {redirectUrl ? <Box paddingBlockStart={200}>
                                          <Button  onClick={() => navigate(redirectUrl)} variant="primary">{buttonText}</Button>
                                      </Box> : null}
                                      {onAction? <Box paddingBlockStart={200}>
                                          <Button  onClick={() => onAction()} variant="primary">{buttonText}</Button>
                                      </Box> : null}
                                      {bodyComponent}
                                  </InlineStack>
                              </BlockStack>
                          </BlockStack>
                      </Box>
                  </InlineStack>
              </Box>
          </Card>
          { learnText ? 
          <InlineStack align="center">
              <InlineStack gap="100">Learn more about<Link url={docsUrl} target="_blank">{learnText}</Link>
              </InlineStack>
          </InlineStack>
          : <></>}
          {infoItems ? <InfoComponent title={infoTitle} items={infoItems} /> : null}
      </BlockStack>
  );
}

export default EmptyScreensLayout