import React from 'react';
import { Box } from '@shopify/polaris';
import ActionItemCard from './ActionItemCard';

const CriticalActionItemCard = ({ cardObj, onButtonClick, jiraTicketUrlMap }) => {
  if (!cardObj) return null;
  return (
    <Box maxWidth="300px">
      <div style={{ cursor: 'pointer' }}>
        <ActionItemCard
          cardObj={cardObj}
          onButtonClick={() => onButtonClick(cardObj)}
          jiraTicketUrlMap={jiraTicketUrlMap}
        />
      </div>
    </Box>
  );
};

export default CriticalActionItemCard; 