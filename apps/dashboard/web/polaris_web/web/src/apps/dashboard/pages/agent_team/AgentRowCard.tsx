import { Avatar, Box, Card, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import React from "react";
import "./agentRowCard.css"

function AgentRowCard(props) {

  const { cardObj, onButtonClick } = props

  // 
  return(
  <Card>
    <div style={{cursor:"pointer"}} onClick={() => onButtonClick(cardObj)} >
      <HorizontalStack gap="3" align="start" wrap={false} blockAlign="start">
        <div style={{ alignSelf: 'start' }}>
          <Box>
            <Avatar customer size="medium" name={cardObj.id} source={cardObj.image} shape='square' />
          </Box>
        </div>
        <VerticalStack gap="1" align="start" inlineAlign="start">
          <HorizontalStack gap={"2"} align="start" wrap={false} blockAlign="start">
          <Text as="span" variant="headingSm">{cardObj.name}</Text> 
          {/* {transform.getStatusBadge(cardObj.id,getCurrentAgentState)} */}
          </HorizontalStack>
          <Text as="span" variant="bodyMd" color="subdued" alignment="start">{cardObj.description}</Text>
        </VerticalStack>
      </HorizontalStack>
    </div>
  </Card>
  )
}

export default AgentRowCard;