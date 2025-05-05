import React from "react";
import { LegacyCard, HorizontalGrid, TextField } from "@shopify/polaris";
import ScopeRoleDropdown from './ScopeRoleDropdown';

const DescriptionCard = ({
  roleName,
  systemRole,
  isNew,
  handleTextChange,
  dispatch,
  initialItems
}) => {
  return (
    <LegacyCard title="Details" key="desc">
      <LegacyCard.Section>
        <HorizontalGrid gap="4" columns={2}>
          <TextField
            label="Name"
            value={roleName}
            disabled={systemRole}
            placeholder="New test role name"
            onChange={isNew ? handleTextChange : () => {}}
            requiredIndicator
          />
          <ScopeRoleDropdown dispatch={dispatch} initialItems={initialItems}/>
        </HorizontalGrid>
      </LegacyCard.Section>
    </LegacyCard>
  );
};

export default DescriptionCard;
