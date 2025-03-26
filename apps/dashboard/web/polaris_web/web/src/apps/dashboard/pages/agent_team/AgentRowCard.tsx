// @ts-nocheck
/* 
needed to ignore typescript check because 
button only accepts string | string[] | undefined
and we need a react element
*/
import { Avatar, Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import React from "react";

function AgentRowCard(props) {

  const { cardObj, onButtonClick } = props

  return <Card>

    <Button onClick={() => onButtonClick(cardObj)} plain monochrome removeUnderline>
      <HorizontalStack gap="3" align="start" wrap={false} blockAlign="start">
        <div style={{ alignSelf: 'start' }}>
          <Box>
            <Avatar customer size="medium" name={cardObj.id} source={cardObj.image} shape='square' />
          </Box>
        </div>
        <VerticalStack gap="1" align="start" inlineAlign="start">
          <Text variant="headingSm">{cardObj.name}</Text>
          <Text variant="bodyMd" color="subdued" alignment="start">{cardObj.description}</Text>
        </VerticalStack>
      </HorizontalStack>
    </Button>

  </Card>
}

export default AgentRowCard;