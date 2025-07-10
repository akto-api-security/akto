import React from 'react';
import { Box } from '@shopify/polaris';
import ActionItemCard from './ActionItemCard';

const CriticalActionItemCard = ({ cardObj, onButtonClick, jiraTicketUrlMap, onCardClick }) => {
  if (!cardObj) return null;
  return (
    <Box maxWidth="300px">
      <div style={{ cursor: 'pointer' }} onClick={() => onCardClick?.(cardObj)}>
        <ActionItemCard
          cardObj={cardObj}
          onButtonClick={(e, obj) => {
            if (e && e.stopPropagation) e.stopPropagation();
            onButtonClick(obj);
          }}
          jiraTicketUrlMap={jiraTicketUrlMap}
        />
      </div>
    </Box>
  );
};

export default CriticalActionItemCard; 