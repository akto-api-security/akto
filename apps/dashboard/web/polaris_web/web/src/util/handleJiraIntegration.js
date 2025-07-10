import settingFunctions from '../apps/dashboard/pages/settings/module';

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";

export async function handleJiraIntegration(actionItem, navigate, setSelectedActionItem, setJiraProjectMaps, setProjId, setIssueType, setModalActive) {
    const integrated = Boolean(window?.JIRA_INTEGRATED);
    if (!integrated) {
        navigate(JIRA_INTEGRATION_URL);
        return;
    }
    setSelectedActionItem(actionItem);
    try {
        const jirIntegration = await settingFunctions.fetchJiraIntegration();
        if (jirIntegration.projectIdsMap && Object.keys(jirIntegration.projectIdsMap).length > 0) {
            setJiraProjectMaps(jirIntegration.projectIdsMap);
            setProjId(Object.keys(jirIntegration.projectIdsMap)[0]);
        } else {
            setProjId(jirIntegration.projId);
            setIssueType(jirIntegration.issueType);
        }
        setModalActive(true);
    } catch (e) {
        navigate(JIRA_INTEGRATION_URL);
    }
} 