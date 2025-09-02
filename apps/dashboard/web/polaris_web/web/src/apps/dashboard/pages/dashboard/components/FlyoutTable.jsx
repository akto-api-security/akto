import React, { useState, useEffect } from 'react';
import ApiEndpointsTable from './ApiEndpointsTable';
import issuesTransform from '@/apps/dashboard/pages/issues/transform';
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import observeApi from '../../observe/api';
import { Box, Badge, Text, Divider } from '@shopify/polaris';
import TooltipText from '@/apps/dashboard/components/shared/TooltipText';
import transform from "@/apps/dashboard/pages/observe/transform";
import func from "@/util/func";
import PersistStore from "@/apps/main/PersistStore";
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const BATCH_SIZE = 500;

const ACTION_ITEM_TYPES = {
    HIGH_RISK_APIS: 'HIGH_RISK_APIS',
    SENSITIVE_DATA_ENDPOINTS: 'SENSITIVE_DATA_ENDPOINTS',
    UNAUTHENTICATED_APIS: 'UNAUTHENTICATED_APIS',
    THIRD_PARTY_APIS: 'THIRD_PARTY_APIS',
    HIGH_RISK_THIRD_PARTY: 'HIGH_RISK_THIRD_PARTY',
    SHADOW_APIS: 'SHADOW_APIS',
    CRITICAL_SENSITIVE_UNAUTH: 'CRITICAL_SENSITIVE_UNAUTH',
    ENHANCE_TESTING_COVERAGE: 'ENHANCE_TESTING_COVERAGE',
    ENABLE_CONTINUOUS_TESTING: 'ENABLE_CONTINUOUS_TESTING',
    ADDRESS_MISCONFIGURED_TESTS: 'ADDRESS_MISCONFIGURED_TESTS',
    VULNERABLE_APIS: 'VULNERABLE_APIS',
    BROKEN_AUTHENTICATION_ISSUES: 'BROKEN_AUTHENTICATION_ISSUES',
    FREQUENTLY_VULNERABLE_ENDPOINTS: 'FREQUENTLY_VULNERABLE_ENDPOINTS',
    APIS_WITH_MULTIPLE_ISSUES: 'APIS_WITH_MULTIPLE_ISSUES',
    VERBOSE_ERROR_MESSAGES: 'VERBOSE_ERROR_MESSAGES',
    MISSING_SECURITY_HEADERS: 'MISSING_SECURITY_HEADERS',
    TOP_PUBLIC_EXPOSED_APIS: 'TOP_PUBLIC_EXPOSED_APIS',
};

const getIssueLabel = (actionItemType) => {
    switch (actionItemType) {
        case ACTION_ITEM_TYPES.HIGH_RISK_APIS:
            return 'High Risk APIs';
        case ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS:
            return 'Sensitive Data';
        case ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS:
            return 'Unauthenticated APIs';
        case ACTION_ITEM_TYPES.THIRD_PARTY_APIS:
            return 'Third-Party APIs';
        case ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY:
            return 'High-Risk Third-Party';
        case ACTION_ITEM_TYPES.SHADOW_APIS:
            return 'Shadow APIs';
        case ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH:
            return 'Sensitive, Unauthenticated';
        case ACTION_ITEM_TYPES.ENHANCE_TESTING_COVERAGE:
            return 'Not Tested APIs';
        case ACTION_ITEM_TYPES.ENABLE_CONTINUOUS_TESTING:
            return 'Only Once Tested APIs';
        case ACTION_ITEM_TYPES.ADDRESS_MISCONFIGURED_TESTS:
            return 'Misconfigured Tests';
        case ACTION_ITEM_TYPES.VULNERABLE_APIS:
            return 'Vulnerable APIs';
        case ACTION_ITEM_TYPES.BROKEN_AUTHENTICATION_ISSUES:
            return 'Broken Authentication Issues';
        case ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS:
            return 'Frequently Vulnerable Endpoints';
        case ACTION_ITEM_TYPES.VERBOSE_ERROR_MESSAGES:
            return 'Verbose Error Messages';
        case ACTION_ITEM_TYPES.MISSING_SECURITY_HEADERS:
            return 'Missing Security Headers';
        case ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS:
            return 'Sensitive Data';
        default:
            return '-';
    }
};

function FlyoutTable({ actionItemType, count, allApiInfo, apiInfoLoading }) {
    const [apiData, setApiData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [expandedRowIndex, setExpandedRowIndex] = useState(null);

    const allCollections = PersistStore(state => state.allCollections);
    const collectionIdToName = func.mapCollectionIdToName(allCollections || []);

    useEffect(() => {
        const processData = async () => {
            if (apiInfoLoading || !allApiInfo) {
                setLoading(true);
                return;
            }

            try {
                setLoading(true);
                let relevantData = [];
                let presetSensitiveParamsMap = null;
                switch (actionItemType) {
                    case ACTION_ITEM_TYPES.HIGH_RISK_APIS:
                        relevantData = allApiInfo.highRiskApis || [];
                        break;
                    case ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS:
                        relevantData = allApiInfo.sensitiveDataEndpoints || [];
                        break;
                    case ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS:
                        {
                            const items = allApiInfo.sensitiveSubtypesInUrl || [];
                            relevantData = items.map(x => x.apiInfo || {});
                            presetSensitiveParamsMap = items.reduce((acc, item) => {
                                const url = item?.apiInfo?.id?.url || item?.apiInfo?.url;
                                if (url) {
                                    const rawSubTypes = Array.isArray(item?.subTypes) ? item.subTypes : [];
                                    const subTypes = rawSubTypes.length > 0 ? Array.from(new Set(rawSubTypes)) : [];
                                    if (subTypes.length > 0) acc[url] = subTypes;
                                }
                                return acc;
                            }, {});
                        }
                        break;
                    case ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS:
                        relevantData = allApiInfo.unauthenticatedApis || [];
                        break;
                    case ACTION_ITEM_TYPES.THIRD_PARTY_APIS:
                        relevantData = allApiInfo.thirdPartyApis || [];
                        break;
                    case ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY:
                        relevantData = allApiInfo.highRiskThirdParty || [];
                        break;
                    case ACTION_ITEM_TYPES.SHADOW_APIS:
                        relevantData = allApiInfo.shadowApis || [];
                        break;
                    case ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH:
                        relevantData = allApiInfo.sensitiveAndUnauthenticated || [];
                        break;
                    case ACTION_ITEM_TYPES.ENHANCE_TESTING_COVERAGE:
                        relevantData = allApiInfo.notTestedEndpointsApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.ENABLE_CONTINUOUS_TESTING:
                        relevantData = allApiInfo.onlyOnceTestedEndpointsApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.ADDRESS_MISCONFIGURED_TESTS:
                        relevantData = allApiInfo.misConfiguredTestsApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.VULNERABLE_APIS:
                        relevantData = allApiInfo.vulnerableApiCountApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.BROKEN_AUTHENTICATION_ISSUES:
                        relevantData = allApiInfo.brokenAuthIssuesApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS:
                        relevantData = allApiInfo.multipleIssuesApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.APIS_WITH_MULTIPLE_ISSUES:
                        relevantData = allApiInfo.multipleIssuesApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.VERBOSE_ERROR_MESSAGES:
                        relevantData = allApiInfo.vemVulnerableApisApiInfo || [];
                        break;
                    case ACTION_ITEM_TYPES.MISSING_SECURITY_HEADERS:
                        relevantData = allApiInfo.mhhVulnerableApisApiInfo || [];
                        break;
                    default:
                        relevantData = [];
                }

                const transformApiRow = (api, index, sensitiveMap) => {
                    const url = api.id?.url || api.url || '-';
                    const method = api.id?.method || api.method || '-';

                    const issuesText = getIssueLabel(actionItemType);
                    const hostnameText = extractHostname(url);

                    const accessTypeText = (Array.isArray(api.apiAccessTypes) && api.apiAccessTypes.length > 0)
                        ? api.apiAccessTypes.join(', ')
                        : 'No access type';
                    const authTypeText = getAuthTypeDisplay(api.allAuthTypesFound || api.actualAuthType);
                    const sensitiveTypes = (sensitiveParamsMap[url] && sensitiveParamsMap[url].length > 0)
                        ? Array.from(new Set(sensitiveParamsMap[url]))
                        : [];
                    const sensitiveParamsIcons = transform.prettifySubtypes(sensitiveTypes, false);
                    const lastSeenFormatted = func.prettifyEpoch(api.lastSeen);
                    const discoveredAtFormatted = func.prettifyEpoch(api.discoveredTimestamp);
                    const lastTestedFormatted = func.prettifyEpoch(api.lastTested);
                    let collectionName = '-';
                    if (Array.isArray(api.collectionIds) && api.collectionIds.length > 0) {
                        collectionName = collectionIdToName[api.collectionIds[0]] || api.collectionIds[0];
                    } else if (api.collectionIds) {
                        collectionName = collectionIdToName[api.collectionIds] || api.collectionIds;
                    }

                    const baseRow = {
                        id: `${actionItemType}-${index}`,
                        endpoint: <Box maxWidth="250px">
                                <GetPrettifyEndpoint url={url} method={method} />
                            </Box>,
                        riskScore: api.riskScore !== null && api.riskScore !== undefined ? 
                            <Badge status={transform.getStatus(api.riskScore)} size="small">{api.riskScore}</Badge> : '-',
                        issues: <Box maxWidth="150px"><TooltipText tooltip={issuesText} text={issuesText} /></Box>,
                        hostname: <Box maxWidth="120px"><TooltipText tooltip={hostnameText} text={hostnameText} /></Box>,
                        accessType: <Box maxWidth="120px"><TooltipText tooltip={accessTypeText} text={accessTypeText} /></Box>,
                        authType: <Box maxWidth="120px"><TooltipText tooltip={authTypeText} text={authTypeText} /></Box>,
                        sensitiveParams: <Box maxWidth="200px">{sensitiveParamsIcons}</Box>,
                        lastSeen: lastSeenFormatted,
                        discoveredAt: discoveredAtFormatted,
                        lastTested: lastTestedFormatted,
                        collection: <Box maxWidth="150px"><TooltipText tooltip={collectionName} text={collectionName} /></Box>,
                    };

                    if (actionItemType === ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS) {
                        // Add collapsible icon on the left and show issues (transformed) inside
                        return {
                            ...baseRow,
                            name: `${method} ${url}`,
                            collapsibleRow: (
                                <tr>
                                    <td colSpan={'100%'}>
                                        <Box padding="3">
                                            {Array.isArray(api.issueLabels) && api.issueLabels.length > 0 ? (
                                                <Box>
                                                    {api.issueLabels.map((label, i) => (
                                                        <React.Fragment key={i}>
                                                            {i > 0 && <Divider />}
                                                            <Box padding="2">
                                                                <Text variant="bodyMd">{label}</Text>
                                                            </Box>
                                                        </React.Fragment>
                                                    ))}
                                                </Box>
                                            ) : (
                                                <Text variant="bodyMd" tone="subdued">No issues found</Text>
                                            )}
                                        </Box>
                                    </td>
                                </tr>
                            )
                        };
                    }

                    return baseRow;
                };

                // Initial Render 
                const initialBatch = relevantData.slice(0, BATCH_SIZE);
                const initialEndpointUrls = initialBatch.map(api => api.id?.url || api.url);
                let sensitiveParamsMap = presetSensitiveParamsMap || {};

                if (!presetSensitiveParamsMap && initialEndpointUrls.length > 0) {
                    try {
                        const resp = await observeApi.fetchSensitiveParamsForEndpoints(initialEndpointUrls);
                        if (resp?.data?.endpoints) {
                            resp.data.endpoints.forEach(item => {
                                if (!sensitiveParamsMap[item.url]) sensitiveParamsMap[item.url] = [];
                                sensitiveParamsMap[item.url].push(item.subTypeString || item.subType?.name);
                            });
                        }
                    } catch (e) { console.error('Error fetching sensitive params for initial batch:', e); }
                }

                const transformedInitialData = initialBatch.map((api, index) => transformApiRow(api, index, sensitiveParamsMap));
                setApiData(transformedInitialData);
                setLoading(false);

                // Process remaining data in the background 
                if (relevantData.length > BATCH_SIZE) {
                    setTimeout(async () => {
                        const remainingBatch = relevantData.slice(BATCH_SIZE);
                        const remainingEndpointUrls = remainingBatch.map(api => api.id?.url || api.url);
                        
                        if (!presetSensitiveParamsMap && remainingEndpointUrls.length > 0) {
                            try {
                                const resp = await observeApi.fetchSensitiveParamsForEndpoints(remainingEndpointUrls);
                                if (resp?.data?.endpoints) {
                                    resp.data.endpoints.forEach(item => {
                                        if (!sensitiveParamsMap[item.url]) sensitiveParamsMap[item.url] = [];
                                        sensitiveParamsMap[item.url].push(item.subTypeString || item.subType?.name);
                                    });
                                }
                            } catch (e) { console.error('Error fetching sensitive params for remaining batch:', e); }
                        }
                        
                        const transformedRemainingData = remainingBatch.map((api, index) => transformApiRow(api, BATCH_SIZE + index, sensitiveParamsMap));
                        setApiData(prevData => [...prevData, ...transformedRemainingData]);
                    }, 0);
                }

            } catch (error) {
                console.error('Error processing API data for flyout:', error);
                setApiData([]);
            } finally {
                setLoading(false);
            }
        };

        if (actionItemType) {
            processData();
        }
    }, [actionItemType, allApiInfo, apiInfoLoading]);

    const extractHostname = (url) => {
        if (!url || url === '-') return '-';
        try {
            return new URL(url).hostname;
        } catch {
            return url.split('/')[0] || '-';
        }
    };

    const getAuthTypeDisplay = (authTypes) => {
        if (!authTypes) return '-';
        if (Array.isArray(authTypes)) {
            if (authTypes.length > 0 && Array.isArray(authTypes[0])) {
                return authTypes[0][0] || '-';
            }
            return authTypes[0] || '-';
        }
        return authTypes;
    };

    const getHeaders = () => {
        if (actionItemType === ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS) {
            return [
                { type: CellType.COLLAPSIBLE, value: 'name', text: '', title: '' },
                { text: 'Endpoint', title: 'Endpoint', value: 'endpoint', maxWidth: '300px' },
                { text: 'Risk Score', title: 'Risk Score', value: 'riskScore', isText: CellType.TEXT },
                { text: 'Issues', title: 'Issues', value: 'issues', isText: CellType.TEXT, maxWidth: '150px' },
                { text: 'Hostname', title: 'Hostname', value: 'hostname', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Access Type', title: 'Access Type', value: 'accessType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Auth Type', title: 'Auth Type', value: 'authType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Sensitive Params', title: 'Sensitive Params', value: 'sensitiveParams', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Last Seen', title: 'Last Seen', value: 'lastSeen', isText: CellType.TEXT },
                { text: 'Discovered At', title: 'Discovered At', value: 'discoveredAt', isText: CellType.TEXT },
                { text: 'Last Tested', title: 'Last Tested', value: 'lastTested', isText: CellType.TEXT },
                { text: 'Collection', title: 'Collection', value: 'collection', isText: CellType.TEXT, maxWidth: '150px' },
            ];
        }
        if (actionItemType === ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS || actionItemType === ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH || actionItemType === ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS) {
            return [
                { text: mapLabel("Endpoint", getDashboardCategory()), title: mapLabel("Endpoint", getDashboardCategory()), value: 'endpoint', maxWidth: '300px' },
                { text: 'Sensitive Params', title: 'Sensitive Params', value: 'sensitiveParams', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Risk Score', title: 'Risk Score', value: 'riskScore', isText: CellType.TEXT },
                { text: 'Issues', title: 'Issues', value: 'issues', isText: CellType.TEXT, maxWidth: '150px' },
                { text: 'Hostname', title: 'Hostname', value: 'hostname', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Access Type', title: 'Access Type', value: 'accessType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Auth Type', title: 'Auth Type', value: 'authType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Last Seen', title: 'Last Seen', value: 'lastSeen', isText: CellType.TEXT },
                { text: 'Discovered At', title: 'Discovered At', value: 'discoveredAt', isText: CellType.TEXT },
                { text: 'Last Tested', title: 'Last Tested', value: 'lastTested', isText: CellType.TEXT },
                { text: 'Collection', title: 'Collection', value: 'collection', isText: CellType.TEXT, maxWidth: '150px' },
            ];
        } else {
            return [
                { text: mapLabel("Endpoint", getDashboardCategory()), title: mapLabel("Endpoint", getDashboardCategory()), value: 'endpoint', maxWidth: '300px' },
                { text: 'Risk Score', title: 'Risk Score', value: 'riskScore', isText: CellType.TEXT },
                { text: 'Issues', title: 'Issues', value: 'issues', isText: CellType.TEXT, maxWidth: '150px' },
                { text: 'Hostname', title: 'Hostname', value: 'hostname', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Access Type', title: 'Access Type', value: 'accessType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Auth Type', title: 'Auth Type', value: 'authType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Sensitive Params', title: 'Sensitive Params', value: 'sensitiveParams', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Last Seen', title: 'Last Seen', value: 'lastSeen', isText: CellType.TEXT },
                { text: 'Discovered At', title: 'Discovered At', value: 'discoveredAt', isText: CellType.TEXT },
                { text: 'Last Tested', title: 'Last Tested', value: 'lastTested', isText: CellType.TEXT },
                { text: 'Collection', title: 'Collection', value: 'collection', isText: CellType.TEXT, maxWidth: '150px' },
            ];
        }
    };

    const headers = getHeaders();

    return (
        <ApiEndpointsTable
            data={apiData}
            headers={headers}
            loading={loading}
            pageLimit={10}
            showFooter={true}
            emptyStateTitle={'No APIs found'}
            emptyStateDescription={'No APIs found for this action item type.'}
        />
    );
}

export default FlyoutTable;