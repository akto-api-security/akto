import React, { useCallback } from 'react';
import {
  Button,
  Divider,
  HorizontalStack,
  LegacyCard,
  Text,
  VerticalStack
} from '@shopify/polaris';
import IntegrationsLayout from '../IntegrationsLayout';
import useJiraReducer from './hooks/useJiraReducer';
import useJiraIntegration from './hooks/useJiraIntegration';
import { transformJiraObject, isSaveButtonDisabled } from './utils/jiraHelpers';
import JiraCredentialsForm from './components/JiraCredentialsForm';
import JiraProjectsList from './components/JiraProjectsList';

/**
 * Main component for Jira integration
 * @returns {JSX.Element} - Rendered component
 */
function Jira() {
  // Use our custom hooks for state management and API integration
  const [state, actions] = useJiraReducer();
  const {
    fetchJiraInteg,
    updateProjectMap,
    fetchJiraStatusMapping,
    addJiraIntegrationV2,
    deleteProject
  } = useJiraIntegration(state, actions);

  // Destructure state for easier access
  const {
    credentials: { baseUrl, apiToken, userEmail },
    projects,
    isSaving,
    loadingProjectIndex
  } = state;

  // Destructure actions for easier access
  const {
    setCredentials,
    addProject,
    updateProject
  } = actions;

  /**
   * Handles status selection for a project
   * @param {number} index - Project index
   * @param {Object} project - Project data
   * @param {string} aktoStatus - Akto status
   * @param {Array} newValues - New values
   */
  const handleStatusSelection = useCallback((index, project, aktoStatus, newValues) => {
    const upperStatus = aktoStatus.toUpperCase();

    // Create a new copy of the project to ensure React detects the change
    const updatedProject = JSON.parse(JSON.stringify(project));

    // Update the status mapping for this Akto status
    updatedProject.aktoToJiraStatusMap[upperStatus] = newValues;

    // Update the project
    updateProject(index, { ...updatedProject });
  }, [updateProject]);

  /**
   * Handles saving the Jira integration
   */
  const handleSave = useCallback(() => {
    const data = transformJiraObject(state);
    if (data) {
      addJiraIntegrationV2(data);
    }
  }, [addJiraIntegrationV2, state]);

  // Card content for the integration layout
  const cardContent = "Seamlessly enhance your web application security with Jira integration. Create jira tickets for api vulnerability issues and view them on the tap of a button";

  // Main card component
  const JiraCard = (
    <LegacyCard
      primaryFooterAction={{
        content: isSaving ? 'Saving...' : 'Save',
        onAction: handleSave,
        disabled: isSaveButtonDisabled(state),
        loading: isSaving
      }}
    >
      <LegacyCard.Section>
        <Text variant="headingMd">Integrate Jira</Text>
      </LegacyCard.Section>

      <LegacyCard.Section>
        <VerticalStack gap="4">
          {/* Credentials Form */}
          <JiraCredentialsForm
            baseUrl={baseUrl}
            apiToken={apiToken}
            userEmail={userEmail}
            setCredentials={setCredentials}
          />

          {/* Projects Section */}
          <HorizontalStack align='space-between'>
            <Text fontWeight='semibold' variant='headingMd'>Projects</Text>
            <Button plain monochrome onClick={addProject}>Add Project</Button>
          </HorizontalStack>

          {/* Projects List */}
          {projects.length > 0 && (
            <JiraProjectsList
              projects={projects}
              loadingProjectIndex={loadingProjectIndex}
              handleStatusSelection={handleStatusSelection}
              fetchJiraStatusMapping={fetchJiraStatusMapping}
              deleteProject={deleteProject}
              updateProject={updateProject}
            />
          )}
        </VerticalStack>
      </LegacyCard.Section>
      <Divider />
      <br/>
    </LegacyCard>
  );

  return (
    <IntegrationsLayout
      title="Jira"
      cardContent={cardContent}
      component={JiraCard}
      docsUrl="https://docs.akto.io/traffic-connections/postman"
    />
  );
}

export default Jira;
