import React from 'react';
import { VerticalStack } from '@shopify/polaris';
import JiraProjectCard from './JiraProjectCard';

/**
 * Component for the list of Jira projects
 * @param {Object} props - Component props
 * @returns {JSX.Element} - Rendered component
 */
function JiraProjectsList({ 
  projects, 
  loadingProjectIndex,
  handleStatusSelection,
  fetchJiraStatusMapping,
  deleteProject,
  updateProject
}) {
  if (!projects || projects.length === 0) {
    return null;
  }

  return (
    <VerticalStack gap={4}>
      {projects.map((project, index) => (
        <JiraProjectCard
          key={`project-${index}`}
          project={project}
          index={index}
          loadingProjectIndex={loadingProjectIndex}
          projectsLength={projects.length}
          handleStatusSelection={handleStatusSelection}
          fetchJiraStatusMapping={fetchJiraStatusMapping}
          deleteProject={deleteProject}
          updateProject={updateProject}
        />
      ))}
    </VerticalStack>
  );
}

export default JiraProjectsList;
