import React, { useState, useEffect } from 'react';
import ApiEndpointsTable from './ApiEndpointsTable';
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import observeApi from '../../observe/api';
import { Box, Badge } from '@shopify/polaris';
import TooltipText from '@/apps/dashboard/components/shared/TooltipText';
import transform from "@/apps/dashboard/pages/observe/transform";
import func from "@/util/func";
import PersistStore from "@/apps/main/PersistStore";

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
        default:
            return '-';
    }
};

function FlyoutTable({ actionItemType, count, allApiInfo, apiInfoLoading }) {
    const [apiData, setApiData] = useState([]);
    const [loading, setLoading] = useState(true);

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
                switch (actionItemType) {
                    case ACTION_ITEM_TYPES.HIGH_RISK_APIS:
                        relevantData = allApiInfo.highRiskApis || [];
                        break;
                    case ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS:
                        relevantData = allApiInfo.sensitiveDataEndpoints || [];
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
                    default:
                        relevantData = [];
                }

                const endpointUrls = relevantData.map(api => api.id?.url || api.url);
                let sensitiveParamsMap = {};
                try {
                    if (endpointUrls.length > 0) {
                        const resp = await observeApi.fetchSensitiveParamsForEndpoints(endpointUrls);
                        if (resp?.data?.endpoints) {
                            resp.data.endpoints.forEach(item => {
                                if (!sensitiveParamsMap[item.url]) sensitiveParamsMap[item.url] = [];
                                sensitiveParamsMap[item.url].push(item.subTypeString || item.subType?.name);
                            });
                        }
                    }
                } catch (e) {
                    console.error('Error fetching sensitive params:', e);
                }

                const transformedData = relevantData.map((api, index) => {
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

                    return {
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
                });

                setApiData(transformedData);
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
        if (actionItemType === ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS || actionItemType === ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH) {
            return [
                { text: 'Endpoint', title: 'Endpoint', value: 'endpoint', maxWidth: '300px' },
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