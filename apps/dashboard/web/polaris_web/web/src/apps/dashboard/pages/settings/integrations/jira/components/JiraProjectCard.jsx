import React from 'react';
import { Button, Card, Checkbox, HorizontalStack, Spinner, Text, TextField, VerticalStack } from '@shopify/polaris';
import JiraStatusMapping from './JiraStatusMapping';
import func from "@/util/func";
import { INITIAL_EMPTY_MAPPING } from '../constants';

/**
 * Component for a single Jira project card
 * @param {Object} props - Component props
 * @returns {JSX.Element} - Rendered component
 */
function JiraProjectCard({
  project,
  index,
  loadingProjectIndex,
  projectsLength,
  handleStatusSelection,
  fetchJiraStatusMapping,
  deleteProject,
  updateProject
}) {
  const handleProjectKeyChange = (val) => {
    if (val && !/^[A-Z0-9]+$/.test(val)) {
      func.setToast(true, true, "Project key must contain only capital letters and numbers");
      return;
    }

    updateProject(index, {
      projectId: val,
      statuses: [],
      jiraStatusLabel: [],
      aktoToJiraStatusMap: JSON.parse(JSON.stringify(INITIAL_EMPTY_MAPPING)),
      enableBiDirIntegration: false
    });
  };

  return (
    <Card roundedAbove="sm">
      <VerticalStack gap={4}>
        <HorizontalStack align='space-between'>
          <Text fontWeight='semibold' variant='headingSm'>{`Project ${index + 1}`}</Text>
          <Button
            plain
            removeUnderline
            destructive
            size='slim'
            disabled={projectsLength <= 1}
            onClick={() => deleteProject(index)}
          >
            Delete Project
          </Button>
        </HorizontalStack>

        <TextField
          maxLength={10}
          showCharacterCount
          value={project?.projectId || ""}
          label="Project key"
          placeholder={"Project Key"}
          requiredIndicator
          onChange={(val) => handleProjectKeyChange(val)}
        />

        {loadingProjectIndex === index ? (
          <div style={{ display: 'flex', alignItems: 'center', margin: '8px 0' }}>
            <Spinner size="small" />
            <Text variant="bodyMd" as="span" style={{ marginLeft: '8px' }}>&nbsp;&nbsp;Loading status mappings...</Text>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Checkbox
              disabled={!project?.projectId?.trim()}
              checked={project.enableBiDirIntegration}
              onChange={() => {
                if (project?.projectId?.trim()) {
                  fetchJiraStatusMapping(project.projectId, index);
                }
              }}
              label=""
            />
            <span style={{ marginLeft: '4px', opacity: project?.projectId?.trim() ? 1 : 0.5 }}>
              Enable bi-directional integration
            </span>
          </div>
        )}

        {project.enableBiDirIntegration && (
          <JiraStatusMapping
            project={project}
            index={index}
            handleStatusSelection={handleStatusSelection}
          />
        )}
      </VerticalStack>
    </Card>
  );
}

export default JiraProjectCard;
