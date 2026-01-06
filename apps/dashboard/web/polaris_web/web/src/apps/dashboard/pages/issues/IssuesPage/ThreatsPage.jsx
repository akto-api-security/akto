import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState, useEffect, useCallback } from "react";
import { useLocation } from "react-router-dom";
import func from "@/util/func";
import PersistStore from "../../../../main/PersistStore";
import { IndexFiltersMode, Badge, Avatar, Box, HorizontalStack, Text, Button } from "@shopify/polaris";
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter.jsx";
import { produce } from "immer";
import "./style.css"
import values from "@/util/values";
import threatDetectionApi from "../../threat_detection/api.js"
import SessionStore from "../../../../main/SessionStore"
import SampleDetails from "../../threat_detection/components/SampleDetails";
import useTable from "../../../components/tables/TableContext.js";
import TableStore from "../../../components/tables/TableStore.js";
import { CellType } from "../../../components/tables/rows/GithubRow.js";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import dayjs from "dayjs";
import { formatActorId } from "../../threat_detection/utils/formatUtils";
import useThreatReportDownload from "../../../hooks/useThreatReportDownload";

const sortOptions = [
    { label: 'Discovered time', value: 'detectedAt asc', directionLabel: 'Newest', sortKey: 'detectedAt', columnIndex: 8 },
    { label: 'Discovered time', value: 'detectedAt desc', directionLabel: 'Oldest', sortKey: 'detectedAt', columnIndex: 8 },
];

const resourceName = {
    singular: 'threat',
    plural: 'threats',
};

const initialEventState = {
    currentRefId: '',
    rowDataList: [],
    moreInfoData: {},
    currentEventId: '',
    currentEventStatus: '',
    currentJiraTicketUrl: ''
};

function ThreatsPage() {
    const [loading, setLoading] = useState(true);
    const [currentTab, setCurrentTab] = useState('active');
    const [selected, setSelected] = useState(0);
    const [showDetails, setShowDetails] = useState(false);
    const [eventState, setEventState] = useState(initialEventState);
    const [detailsLoading, setDetailsLoading] = useState(false);
    const [tableKey, setTableKey] = useState(false);
    const [currentAppliedFilters, setCurrentAppliedFilters] = useState({});

    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);
    const location = useLocation();

    const { tabsInfo, selectItems } = useTable();

    const resetResourcesSelected = () => {
        TableStore.getState().setSelectedItems([])
        selectItems([])
        setTableKey(!tableKey)
    };

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    };

    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    const { downloadThreatReport } = useThreatReportDownload({
        startTimestamp,
        endTimestamp,
        additionalFilters: currentAppliedFilters
    });

    const headers = [
        {
            text: "Severity",
            value: "severityComp",
            title: "Severity",
        },
        {
            text: "API endpoint",
            value: "endpointComp",
            title: "API endpoint",
        },
        {
            text: "Host",
            value: "host",
            title: "Host",
        },
        {
            text: "Threat Actor",
            value: "actorComp",
            title: "Actor",
            filterKey: 'actor'
        },
        {
            text: "Filter",
            value: "filterId",
            title: "Attack type",
        },
        {
            text: "Compliance",
            value: "compliance",
            title: "Compliance",
            maxWidth: "200px",
        },
        {
            text: "successfulExploit",
            value: "successfulComp",
            title: "Successful Exploit",
            maxWidth: "90px",
        },
        {
            text: "Collection",
            value: "apiCollectionName",
            title: "Collection",
            maxWidth: "95px",
            type: CellType.TEXT,
        },
        {
            text: "Discovered",
            title: "Detected",
            value: "discoveredTs",
            type: CellType.TEXT,
            sortActive: true,
        },
    ];

    const tableTabs = [
        {
            content: 'Active',
            onAction: () => { setCurrentTab('active') },
            id: 'active',
            index: 0
        },
        {
            content: 'Under Review',
            onAction: () => { setCurrentTab('under_review') },
            id: 'under_review',
            index: 1
        },
        {
            content: 'Ignored',
            onAction: () => { setCurrentTab('ignored') },
            id: 'ignored',
            index: 2
        }
    ];

    const handleSelectedTab = (selectedIndex) => {
        setLoading(true);
        setSelected(selectedIndex);
        setTimeout(() => {
            setLoading(false);
        }, 200);
    };

    const handleDetailsVisibility = useCallback((visible) => {
        setShowDetails(visible);
        if (!visible) {
            setEventState(initialEventState);
            setDetailsLoading(false);
        }
    }, []);

    const handleThreatClick = useCallback(async (threatData) => {
        if (!threatData?.refId || !threatData?.eventType || !threatData?.actor || !threatData?.filterId) {
            return;
        }

        setShowDetails(true);
        setDetailsLoading(true);

        try {
            const payloadResponse = await threatDetectionApi.fetchMaliciousRequest(
                threatData.refId,
                threatData.eventType,
                threatData.actor,
                threatData.filterId
            );

            const maliciousPayloads = payloadResponse?.maliciousPayloadsResponses || [];

            setEventState({
                currentRefId: threatData.refId,
                rowDataList: maliciousPayloads,
                moreInfoData: {
                    url: threatData.url || '',
                    method: threatData.method || '',
                    apiCollectionId: threatData.apiCollectionId,
                    templateId: threatData.filterId,
                },
                currentEventId: threatData.eventId || '',
                currentEventStatus: threatData.status || '',
                currentJiraTicketUrl: threatData.jiraTicketUrl || ''
            });
        } catch (error) {
            console.error('Error fetching threat details:', error);
            func.setToast(true, true, 'Failed to load threat details. Please try again.');
            handleDetailsVisibility(false);
        } finally {
            setDetailsLoading(false);
        }
    }, [handleDetailsVisibility]);

    const handleStatusUpdate = (newStatus) => {
        setEventState(prev => ({ ...prev, currentEventStatus: newStatus }));
    };

    const createThreatDataObject = (item) => ({
        refId: item?.refId,
        eventType: item?.eventType,
        actor: item?.actor,
        filterId: item?.filterId,
        url: item?.url,
        method: item?.method,
        apiCollectionId: item?.apiCollectionId,
        status: item?.status,
        eventId: item?.id,
        jiraTicketUrl: item?.jiraTicketUrl
    });

    async function fetchData(
        sortKey,
        sortOrder,
        skip,
        limit,
        filters,
        filterOperators,
        queryValue
    ) {
        setLoading(true);
        try {
            const sourceIpsFilter = filters?.actor || [];
            const matchingUrlFilter = filters?.url || [];
            const hostFilter = filters?.host || [];
            const typeFilter = filters?.type || [];
            const latestAttack = filters?.latestAttack || [];
            const severityFilter = filters?.severity || [];
            const apiCollectionIdsFilter = [];
            const latestApiOrigRegex = queryValue?.length > 3 ? queryValue : "";

            // Update current applied filters for report export
            const appliedFilters = {};
            if (sourceIpsFilter.length > 0) appliedFilters.actor = sourceIpsFilter;
            if (matchingUrlFilter.length > 0) appliedFilters.url = matchingUrlFilter;
            if (hostFilter.length > 0) appliedFilters.host = hostFilter;
            if (typeFilter.length > 0) appliedFilters.type = typeFilter;
            if (latestAttack.length > 0) appliedFilters.latestAttack = latestAttack;
            if (severityFilter.length > 0) appliedFilters.severity = severityFilter;
            setCurrentAppliedFilters(appliedFilters);

            const sort = { [sortKey || 'detectedAt']: sortOrder === 'asc' ? 1 : -1 };

            const res = await threatDetectionApi.fetchSuspectSampleData(
                skip,
                sourceIpsFilter,
                apiCollectionIdsFilter,
                matchingUrlFilter,
                typeFilter,
                sort,
                startTimestamp,
                endTimestamp,
                latestAttack,
                50,
                currentTab.toUpperCase(),
                undefined,
                'THREAT',
                hostFilter,
                latestApiOrigRegex,
                [],
                true
            );

            const total = res?.total || 0;
            const ret = (res?.maliciousEvents || [])
                .filter((x) => {
                    // Apply client-side severity filtering
                    if (severityFilter.length > 0) {
                        const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH";
                        if (!severityFilter.includes(severity)) {
                            return false;
                        }
                    }
                    return true;
                })
                .map((x) => {
                const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH";

                const filterTemplate = threatFiltersMap[x?.filterId];
                const complianceMap = filterTemplate?.compliance?.mapComplianceToListClauses || {};
                const complianceList = Object.keys(complianceMap);

                // Build nextUrl for row navigation
                let nextUrl = null;
                if (x.refId && x.eventType && x.actor && x.filterId) {
                    const params = new URLSearchParams();
                    params.set("refId", x.refId);
                    params.set("eventType", x.eventType);
                    params.set("actor", x.actor);
                    params.set("filterId", x.filterId);
                    if (x.status) {
                        params.set("eventStatus", x.status.toUpperCase());
                    }
                    nextUrl = `${location.pathname}?${params.toString()}`;
                }

                return {
                    ...x,
                    id: x.id,
                    actorComp: formatActorId(x.actor),
                    host: x.host || "-",
                    endpointComp: (
                        <GetPrettifyEndpoint
                            maxWidth="300px"
                            method={x.method}
                            url={x.url}
                            isNew={false}
                        />
                    ),
                    apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
                    discoveredTs: dayjs(x.timestamp * 1000).format("DD-MM-YYYY HH:mm:ss"),
                    successfulComp: (
                        <Badge size="small">{x?.successfulExploit ? "True" : "False"}</Badge>
                    ),
                    severityComp: (
                        <div className={`badge-wrapper-${severity}`}>
                            <Badge size="small">{func.toSentenceCase(severity)}</Badge>
                        </div>
                    ),
                    compliance: complianceList.length > 0 ? (
                        <HorizontalStack wrap={false} gap={1}>
                            {complianceList.slice(0, 2).map((complianceName, idx) =>
                                <Avatar
                                    key={idx}
                                    source={func.getComplianceIcon(complianceName)}
                                    shape="square"
                                    size="extraSmall"
                                />
                            )}
                            {complianceList.length > 2 && (
                                <Box>
                                    <Badge size="extraSmall">+{complianceList.length - 2}</Badge>
                                </Box>
                            )}
                        </HorizontalStack>
                    ) : <Text color="subdued">-</Text>,
                    nextUrl: nextUrl
                };
            });

            setLoading(false);
            // Use filtered result count for total when severity filter is applied
            const filteredTotal = severityFilter.length > 0 ? ret.length : total;
            return { value: ret, total: filteredTotal };
        } catch (error) {
            console.error("Error fetching threat data:", error);
            setLoading(false);
            return { value: [], total: 0 };
        }
    }

    async function fillFilters() {
        const res = await threatDetectionApi.fetchFiltersThreatTable();
        let urlChoices = (res?.urls || []).map((x) => {
            const url = x || "/";
            return { label: url, value: x };
        });
        let ipChoices = (res?.ips || []).map((x) => {
            return { label: x, value: x };
        });

        let hostChoices = [];
        if (res?.hosts && Array.isArray(res.hosts) && res.hosts.length > 0) {
            hostChoices = res.hosts
                .filter(host => host && host.trim() !== '' && host !== '-')
                .map(x => ({ label: x, value: x }));
        }

        const attackTypeChoices = Object.keys(threatFiltersMap).length === 0 ? [] : Object.entries(threatFiltersMap).map(([key, value]) => {
            return {
                label: value?._id || key,
                value: value?._id || key
            }
        });

        return [
            {
                key: 'severity',
                label: 'Severity',
                title: 'Severity',
                choices: [
                    { label: 'Critical', value: 'CRITICAL' },
                    { label: 'High', value: 'HIGH' },
                    { label: 'Medium', value: 'MEDIUM' },
                    { label: 'Low', value: 'LOW' }
                ]
            },
            {
                key: "actor",
                label: "Actor",
                title: "Actor",
                choices: ipChoices,
            },
            {
                key: "url",
                label: "URL",
                title: "URL",
                choices: urlChoices,
            },
            {
                key: 'host',
                label: "Host",
                title: "Host",
                choices: hostChoices,
            },
            {
                key: 'type',
                label: "Type",
                title: "Type",
                choices: [
                    { label: 'Rule based', value: 'Rule-Based' },
                    { label: 'Anomaly', value: 'Anomaly' },
                ],
            },
            {
                key: 'latestAttack',
                label: 'Latest attack sub-category',
                type: 'select',
                choices: attackTypeChoices,
                multiple: true
            }
        ];
    }

    const [filters, setFilters] = useState([]);

    useEffect(() => {
        async function loadFilters() {
            const f = await fillFilters();
            setFilters(f);
        }
        loadFilters();
    }, [threatFiltersMap]);

    function disambiguateLabel(key, value) {
        switch (key) {
            case "apiCollectionId":
                return func.convertToDisambiguateLabelObj(value, collectionsMap, 2);
            default:
                return func.convertToDisambiguateLabelObj(value, null, 2);
        }
    }

    const handleBulkOperation = async (selectedIds, operation, newState = null) => {
        const actionLabels = {
            ignore: { ing: 'ignoring', ed: 'ignored' },
            delete: { ing: 'deleting', ed: 'deleted' },
            markForReview: { ing: 'marking for review', ed: 'marked for review' },
            removeFromReview: { ing: 'removing from review', ed: 'removed from review' }
        };

        const label = actionLabels[operation];

        if (!selectedIds || selectedIds.length === 0) {
            func.setToast(true, true, 'No events selected');
            return;
        }

        let eventIds = [];
        selectedIds.forEach(id => {
            try {
                const parsed = JSON.parse(id);
                if (parsed.eventId) {
                    eventIds.push(parsed.eventId);
                } else if (Array.isArray(parsed)) {
                    parsed.forEach(item => {
                        if (item.eventId) eventIds.push(item.eventId);
                    });
                }
            } catch (e) {
                console.error('Error parsing ID:', e);
            }
        });

        if (eventIds.length === 0) {
            func.setToast(true, true, 'No valid events selected');
            return;
        }

        try {
            let response;
            if (operation === 'delete') {
                response = await threatDetectionApi.deleteMaliciousEvents({ eventIds });
            } else {
                response = await threatDetectionApi.updateMaliciousEventStatus({ eventIds, status: newState });
            }

            const isSuccess = operation === 'delete' ? response?.deleteSuccess : response?.updateSuccess;
            const count = operation === 'delete' ? response?.deletedCount : response?.updatedCount;

            if (isSuccess) {
                func.setToast(true, false, `${count || eventIds.length} event${eventIds.length === 1 ? '' : 's'} ${label.ed} successfully`);
                resetResourcesSelected();
                setTableKey(prev => !prev);
            } else {
                func.setToast(true, true, `Failed to ${operation} events`);
            }
        } catch (error) {
            func.setToast(true, true, `Error ${label.ing} events`);
        }
    };

    const promotedBulkActions = (selectedResources) => {
        if (!selectedResources || selectedResources.length === 0) return [];

        let items = [];
        if (Array.isArray(selectedResources)) {
            selectedResources.forEach(resource => {
                if (typeof resource === 'string') {
                    items.push(resource);
                } else if (Array.isArray(resource)) {
                    items.push(...resource);
                }
            });
        }

        const eventCount = items.length;
        const eventText = `${eventCount} selected event${eventCount === 1 ? '' : 's'}`;

        const createAction = (label, actionType, includeWarning = false) => {
            const warningText = includeWarning
                ? '\n\nNote: Future events matching these URL and Attack Type combinations will be automatically blocked.'
                : '';

            return {
                content: `${label} ${eventText}`,
                onAction: () => {
                    const message = actionType === 'delete'
                        ? `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`
                        : `Are you sure you want to ${label.toLowerCase()} ${eventText}?${warningText}`;

                    const handlers = {
                        markForReview: () => handleBulkOperation(items, 'markForReview', 'UNDER_REVIEW'),
                        ignore: () => handleBulkOperation(items, 'ignore', 'IGNORED'),
                        removeFromReview: () => handleBulkOperation(items, 'removeFromReview', 'ACTIVE'),
                        reactivate: () => handleBulkOperation(items, 'removeFromReview', 'ACTIVE'),
                        delete: () => handleBulkOperation(items, 'delete')
                    };

                    func.showConfirmationModal(message, label, handlers[actionType]);
                }
            };
        };

        const actions = [];
        const tabActions = {
            'active': [
                { label: 'Mark for Review', type: 'markForReview' },
                { label: 'Ignore', type: 'ignore', warning: true }
            ],
            'under_review': [
                { label: 'Remove from Review', type: 'removeFromReview' },
                { label: 'Ignore', type: 'ignore', warning: true }
            ],
            'ignored': [
                { label: 'Reactivate', type: 'reactivate' }
            ]
        };

        const currentTabActions = tabActions[currentTab] || [];
        currentTabActions.forEach(({ label, type, warning }) => {
            actions.push(createAction(label, type, warning));
        });

        actions.push(createAction('Delete', 'delete'));

        return actions;
    }

    const getTitle = () => {
        return <TitleWithInfo
            titleText={"Threats"}
            docsUrl={"https://docs.akto.io/threat-detection"}
            tooltipContent={"View and manage security threats grouped by threat category"}
        />
    }

    const emptyStateComponent = (
        <EmptyScreensLayout
            iconSrc={"/public/cyber_threats.svg"}
            headingText={"No threats detected"}
            description={"Threats will appear here once detected in your API traffic"}
        />
    )

    return (
        <>
            <PageWithMultipleCards
                title={getTitle()}
                isFirstPage={true}
                primaryAction={<DateRangeFilter
                        key="date-range"
                        initialDispatch={currDateRange}
                        dispatch={(dateObj) => dispatchCurrDateRange({
                            type: "update",
                            period: dateObj.period,
                            title: dateObj.title,
                            alias: dateObj.alias
                        })}
                    />}
                secondaryActions={
                    <Button primary onClick={downloadThreatReport}>
                        Export Threat Report
                    </Button>
                }
                components={[
                    <GithubServerTable
                        key={`threats-table-${selected}-${tableKey}-${startTimestamp}-${endTimestamp}`}
                        pageLimit={50}
                        fetchData={fetchData}
                        sortOptions={sortOptions}
                        resourceName={resourceName}
                        filters={filters}
                        disambiguateLabel={disambiguateLabel}
                        headers={headers}
                        headings={headers}
                        selectable={true}
                        promotedBulkActions={promotedBulkActions}
                        isMultipleItemsSelected={true}
                        mode={IndexFiltersMode.Default}
                        useNewRow={true}
                        condensedHeight={true}
                        tableTabs={tableTabs}
                        onSelect={handleSelectedTab}
                        selected={selected}
                        loading={loading}
                        getStatus={() => 'default'}
                        emptyStateComponent={emptyStateComponent}
                        onRowClick={(data) => handleThreatClick(createThreatDataObject(data))}
                    />
                ]}
            />
            {showDetails && (
                <SampleDetails
                    key={`threat-sample-details-${eventState.currentRefId || 'default'}`}
                    title={"Attacker payload"}
                    showDetails={showDetails}
                    setShowDetails={handleDetailsVisibility}
                    data={eventState.rowDataList}
                    moreInfoData={eventState.moreInfoData}
                    threatFiltersMap={threatFiltersMap}
                    eventId={eventState.currentEventId}
                    eventStatus={eventState.currentEventStatus}
                    onStatusUpdate={handleStatusUpdate}
                    jiraTicketUrl={eventState.currentJiraTicketUrl}
                    loading={detailsLoading}
                />
            )}
        </>
    )
}

export default ThreatsPage
