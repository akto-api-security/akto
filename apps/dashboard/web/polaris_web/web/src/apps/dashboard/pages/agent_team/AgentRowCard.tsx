// @ts-nocheck
/* 
needed to ignore typescript check because 
button only accepts string | string[] | undefined
and we need a react element
*/
import { Avatar, Box, Button, Card, HorizontalGrid, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import React from "react";
import {useAgentsStateStore} from "./agents.state.store";
import "./agentRowCard.css"

import transform from "./transform";

function AgentRowCard(props) {

  const { cardObj, onButtonClick } = props
  
  const { getCurrentAgentState} = useAgentsStateStore();

  return<Card background={transform.getAgentStatusColor(cardObj.id,getCurrentAgentState)}>
    <div style={{cursor:"pointer"}} onClick={() => onButtonClick(cardObj)} >
      <HorizontalStack gap="3" align="start" wrap={false} blockAlign="start">
        <div style={{ alignSelf: 'start' }}>
          <Box>
            <Avatar customer size="medium" name={cardObj.id} source={cardObj.image} shape='square' />
          </Box>
        </div>
        <VerticalStack gap="1" align="start" inlineAlign="start">
          <HorizontalStack gap={2} align="start" wrap={false} blockAlign="start">
          <Text variant="headingSm">{cardObj.name}</Text> 
          {transform.getStatusBadge(cardObj.id,getCurrentAgentState)}
          </HorizontalStack>
          <Text variant="bodyMd" color="subdued" alignment="start">{cardObj.description}</Text>
        </VerticalStack>
      </HorizontalStack>
    </div>
  </Card>
}

export default AgentRowCard;