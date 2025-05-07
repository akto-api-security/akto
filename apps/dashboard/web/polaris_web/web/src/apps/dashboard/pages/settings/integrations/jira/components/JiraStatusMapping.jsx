import React from 'react';
import { Badge, Box, HorizontalStack, Text, VerticalStack } from '@shopify/polaris';
import { AKTO_STATUS_FOR_JIRA } from '../constants';
import { getDisabledOptions, getLabel } from '../utils/jiraHelpers';
import func from "@/util/func";
import DropdownSearchWithDisabled
  from "../../../../../components/shared/DropdownSearchWithDisabled";

/**
 * Component for Jira status mapping
 * @param {Object} props - Component props
 * @returns {JSX.Element} - Rendered component
 */
function JiraStatusMapping({ project, index, handleStatusSelection }) {
  return (
    <VerticalStack gap={3} align='start'>
      <HorizontalStack gap={12}>
        <Text fontWeight='semibold' variant='headingXs'>Akto Status</Text>
        <Text fontWeight='semibold' variant='headingXs'>Jira Status</Text>
      </HorizontalStack>
      
      {AKTO_STATUS_FOR_JIRA.map(status => {
        const upperStatus = status.toUpperCase();
        const selectedValues = project?.aktoToJiraStatusMap?.[upperStatus] || [];
        const isRequired = project?.enableBiDirIntegration && (!selectedValues || selectedValues.length === 0);
        
        return (
          <HorizontalStack gap={8} key={status}>
            <Box width='82px'>
              <Badge>{status}</Badge>
            </Box>
            
            <DropdownSearchWithDisabled
              setSelected={(value) => {
                handleStatusSelection(index, project, status, value);
              }}
              optionsList={project?.jiraStatusLabel || []}
              placeholder="Select Jira Status"
              searchDisable={true}
              showSelectedItemLabels={true}
              allowMultiple={true}
              preSelected={selectedValues}
              value={func.getSelectedItemsText(getLabel(selectedValues, project) || [])}
              disabledOptions={getDisabledOptions(project, upperStatus)} 
            />
            
            {isRequired && (
              <Text variant="bodySm" color="critical">
                <span style={{ color: 'var(--p-color-critical)', marginLeft: '8px' }}>* Required</span>
              </Text>
            )}
          </HorizontalStack>
        );
      })}
    </VerticalStack>
  );
}

export default JiraStatusMapping;
