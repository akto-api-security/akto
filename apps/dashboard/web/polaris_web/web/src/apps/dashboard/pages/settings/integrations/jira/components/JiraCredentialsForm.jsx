import React from 'react';
import { TextField, VerticalStack } from '@shopify/polaris';
import PasswordTextField from "../../../../../components/layouts/PasswordTextField";

/**
 * Component for Jira credentials form
 * @param {Object} props - Component props
 * @returns {JSX.Element} - Rendered component
 */
function JiraCredentialsForm({ baseUrl, apiToken, userEmail, setCredentials }) {
  return (
    <VerticalStack gap="4">
      <TextField 
        label="Base Url" 
        value={baseUrl} 
        helpText="Specify the base url of your jira project(for ex - https://jiraintegloc.atlassian.net)" 
        placeholder='Base Url' 
        requiredIndicator 
        onChange={(value) => setCredentials('baseUrl', value)} 
      />
      
      <TextField 
        label="Email" 
        value={userEmail} 
        helpText="Specify your email id for which api token will be generated" 
        placeholder='Email' 
        requiredIndicator 
        onChange={(value) => setCredentials('userEmail', value)} 
      />
      
      <PasswordTextField
        label="Api Token" 
        helpText="Specify the api token created for your user email" 
        field={apiToken} 
        onFunc={true} 
        setField={(value) => setCredentials('apiToken', value)} 
      />
    </VerticalStack>
  );
}

export default JiraCredentialsForm;
