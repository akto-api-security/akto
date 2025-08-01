import { VerticalStack, Box } from '@shopify/polaris';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import FlyLayout from '../../../components/layouts/FlyLayout';
import GridRows from '../../../components/shared/GridRows';
import JiraTicketCreationModal from '../../../components/shared/JiraTicketCreationModal';
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';
import ActionItemsTable from './ActionItemsTable';
import CriticalActionItemCard from './CriticalActionItemCard';
import issuesApi from '../../issues/api';
import { createActionItem as createActionItemUtil } from '../../../../../util/createActionItem';
import settingsModule from '../../settings/module';
import ActionItemDetails from './ActionItemDetails';
import { fetchAllActionItemsApiInfo } from './actionItemsTransform'; 

const actionItemsHeaders = [
    { title: '', value: 'priority', type: 'text' },
    { title: 'Action Item', value: 'actionItem', type: 'text', maxWidth: '180px' },
    { title: 'Description', value: 'descriptionCol', type: 'text', maxWidth: '220px' },
    { title: 'Team', value: 'team', type: 'text', maxWidth: '110px' },
    { title: 'Efforts', value: 'effort', type: 'text', maxWidth: '90px' },
    { title: 'Why It Matters', value: 'whyItMatters', type: 'text', maxWidth: '200px' },
    { title: 'Action', value: 'actions', type: 'action', maxWidth: '70px' }
];

const ACTION_ITEM_TYPES = {
    HIGH_RISK_APIS: 'HIGH_RISK_APIS',
    SENSITIVE_DATA_ENDPOINTS: 'SENSITIVE_DATA_ENDPOINTS',
    UNAUTHENTICATED_APIS: 'UNAUTHENTICATED_APIS',
    THIRD_PARTY_APIS: 'THIRD_PARTY_APIS',
    HIGH_RISK_THIRD_PARTY: 'HIGH_RISK_THIRD_PARTY',
    SHADOW_APIS: 'SHADOW_APIS',
    CRITICAL_SENSITIVE_UNAUTH: 'CRITICAL_SENSITIVE_UNAUTH'
};

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";

export const ActionItemsContent = ({ actionItemsData, onCountChange }) => {
    const navigate = useNavigate();
    const [actionItems, setActionItems] = useState([]);
    const [criticalCardData, setCriticalCardData] = useState(null);
    const [modalActive, setModalActive] = useState(false);
    const [projId, setProjId] = useState('');
    const [issueType, setIssueType] = useState('');
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});
    const [selectedActionItem, setSelectedActionItem] = useState(null);
    const [jiraTicketUrlMap, setJiraTicketUrlMap] = useState({});
    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [allApiInfo, setAllApiInfo] = useState(null);
    const [apiInfoLoading, setApiInfoLoading] = useState(false);

    useEffect(() => {
        const fetchApiInfo = async () => {
            setApiInfoLoading(true);
            try {
                const apiData = await fetchAllActionItemsApiInfo();
                setAllApiInfo(apiData);
            } catch (error) {
                console.error('Error fetching API info:', error);
                setAllApiInfo(null);
            } finally {
                setApiInfoLoading(false);
            }
        };

        fetchApiInfo();
    }, []); 

    const handleJiraIntegration = async (actionItem) => {
        const integrated = window.JIRA_INTEGRATED === 'true'
        if (!integrated) {
            navigate(JIRA_INTEGRATION_URL);
            return;
        }
        setSelectedActionItem(actionItem);
        try {
            const jirIntegration = await settingsModule.fetchJiraIntegration();
            if (jirIntegration.projectIdsMap && Object.keys(jirIntegration.projectIdsMap).length > 0) {
                setJiraProjectMaps(jirIntegration.projectIdsMap);
                setProjId(Object.keys(jirIntegration.projectIdsMap)[0]);
            } else {
                setProjId(jirIntegration.projId);
                setIssueType(jirIntegration.issueType);
            }
            setModalActive(true);
        } catch (e) {
            console.error("Error fetching Jira integration settings:", e);
        }
    };

    const createActionItem = (
        id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration, whyItMatters
    ) => {
        return createActionItemUtil(
            id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration, whyItMatters
        );
    };

    const getActions = (item) => {
        const actionSource = item?.actionItemObj || item;
        const jiraTicketUrl = jiraTicketUrlMap[actionSource?.actionItemType];
        const jiraKey = jiraTicketUrl?.split('/').pop() || "";

        return [{
            items: [{
                content: (
                    <JiraTicketDisplay
                        jiraTicketUrl={jiraTicketUrl}
                        jiraKey={jiraKey}
                        onButtonClick={() => handleJiraIntegration(actionSource)}
                        ariaLabel={jiraTicketUrl ? `View Jira ticket ${jiraKey}` : 'Create Jira ticket'}
                    />
                ),
                onAction: jiraTicketUrl
                    ? () => window.open(jiraTicketUrl, '_blank')
                    : () => handleJiraIntegration(actionSource),
                accessibilityLabel: jiraTicketUrl ? `View Jira ticket ${jiraKey}` : 'Create Jira ticket',
            }]
        }];
    };

    const handleCardClick = (item) => {
        setSelectedItem(item);
        setShowFlyout(true);
    };
    
    const handleRowClick = (item) => {
        setSelectedItem(item);
        setShowFlyout(true);
    };

    useEffect(() => {
        if (!actionItemsData) return;
        const {
            highRiskCount,
            sensitiveDataCount,
            unauthenticatedCount,
            thirdPartyDiff,
            highRiskThirdPartyCount,
            shadowApisCount,
            sensitiveAndUnauthenticatedCount,
            jiraTicketUrlMap: ticketMap
        } = actionItemsData;
        setJiraTicketUrlMap(ticketMap || {});

        const items = [
            createActionItem('1', 'P1', 'High-risk APIs', `${highRiskCount} APIs with risk score more than 3`, "Security Team", "Medium", highRiskCount, ACTION_ITEM_TYPES.HIGH_RISK_APIS, jiraTicketUrlMap, handleJiraIntegration, "Creates multiple attack vectors for malicious actors"),

            createActionItem('2', 'P1', 'APIs returning sensitive data', `${sensitiveDataCount} Endpoints exposing PII or confidential information`, "Development", "Medium", sensitiveDataCount, ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS, jiraTicketUrlMap, handleJiraIntegration, "Violates data privacy regulations (GDPR, CCPA) and risks customer trust"),

            createActionItem('3', 'P1', 'Missing authentication methods', `${unauthenticatedCount} APIs lacking proper authentication controls`, "Security Team", "Medium", unauthenticatedCount, ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS, jiraTicketUrlMap, handleJiraIntegration, "Easy target for unauthorized access and data exfiltration"),

            createActionItem('4', 'P1', 'Recently active third-party APIs', `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`, "Integration Team", "Low", Math.max(0, thirdPartyDiff), ACTION_ITEM_TYPES.THIRD_PARTY_APIS, jiraTicketUrlMap, handleJiraIntegration, "New integrations may introduce unvetted security risks"),

            createActionItem('5', 'P1', 'High-risk third-party APIs', `${highRiskThirdPartyCount} External APIs with high risk scores requiring attention`, "Security Team", "High", highRiskThirdPartyCount, ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY, jiraTicketUrlMap, handleJiraIntegration, "Supply chain vulnerabilities that can compromise entire systems"),

            createActionItem('6', 'P1', 'Shadow APIs', `${shadowApisCount} Undocumented APIs discovered in the system`, "API Governance", "High", shadowApisCount, ACTION_ITEM_TYPES.SHADOW_APIS, jiraTicketUrlMap, handleJiraIntegration, "Unmonitored attack surface with unknown security posture")
        ];

        const filteredItems = items.filter(item => item.count > 0);
        setActionItems(filteredItems);
        let totalCount = filteredItems.length;
        if (sensitiveAndUnauthenticatedCount > 0) {
            totalCount += 1;
            setCriticalCardData({
                id: 'p0-critical',
                priority: 'P0',
                staticTitle: 'Sensitive data without proper controls',
                title: 'Sensitive data without proper controls',
                description: `${sensitiveAndUnauthenticatedCount} APIs returning sensitive data without encryption or proper authorization`,
                team: 'Security & Development',
                effort: 'High',
                count: sensitiveAndUnauthenticatedCount,
                actionItemType: ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH
            });
        } else {
            setCriticalCardData(null);
        }
        if (onCountChange) {
            onCountChange(totalCount);
        }
    }, [actionItemsData, jiraTicketUrlMap, onCountChange]);

    const handleSaveJiraAction = () => {
        if (!selectedActionItem) {
            navigate(JIRA_INTEGRATION_URL);
            return;
        }

        setModalActive(false);
        const { title, displayName, description, actionItemType } = selectedActionItem;

        issuesApi.createGeneralJiraTicket({
            title: title || displayName || 'Action Item',
            description: description || '',
            projId,
            issueType,
            actionItemType: actionItemType || ''
        }).then((res) => {
            if (res?.errorMessage) {
                navigate(JIRA_INTEGRATION_URL);
            } else {
                // No need to refetch data here as it's passed as a prop
            }
        }).catch((error) => {
            console.error("Error creating Jira ticket:", error);
            navigate(JIRA_INTEGRATION_URL);
        });
    };

    return (
        <VerticalStack gap="5">
            {criticalCardData && (
                <CriticalActionItemCard
                    cardObj={criticalCardData}
                    onButtonClick={handleJiraIntegration}
                    jiraTicketUrlMap={jiraTicketUrlMap}
                    onCardClick={handleCardClick}
                />
            )}

            <Box maxWidth="100%" style={{ overflowX: 'hidden' }}>
                <ActionItemsTable
                    data={actionItems}
                    headers={actionItemsHeaders}
                    getActions={getActions}
                    jiraTicketUrlMap={jiraTicketUrlMap}
                    onRowClick={handleRowClick}
                />
            </Box>

            <FlyLayout
                show={showFlyout}
                setShow={setShowFlyout}
                title="Action item details"
                components={[
                    <ActionItemDetails
                        item={selectedItem}
                        jiraTicketUrlMap={jiraTicketUrlMap}
                        onJiraButtonClick={handleJiraIntegration}
                        allApiInfo={allApiInfo} 
                        apiInfoLoading={apiInfoLoading} 
                    />
                ]}
            />

            <JiraTicketCreationModal
                activator={null}
                modalActive={modalActive}
                setModalActive={setModalActive}
                handleSaveAction={handleSaveJiraAction}
                jiraProjectMaps={jiraProjectMaps}
                setProjId={setProjId}
                setIssueType={setIssueType}
                projId={projId}
                issueType={issueType}
                isAzureModal={false}
            />
        </VerticalStack>
    );
};