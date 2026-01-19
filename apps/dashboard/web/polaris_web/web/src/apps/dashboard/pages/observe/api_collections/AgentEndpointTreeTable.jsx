import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Badge, Box, HorizontalStack, Text, DataTable } from '@shopify/polaris';
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable';
import { IndexFiltersMode } from '@shopify/polaris';
import { CellType } from '../../../components/tables/rows/GithubRow';
import transform from '../transform';
import func from '@/util/func';
import { useNavigate } from 'react-router-dom';
import HeadingWithTooltip from '../../../components/shared/HeadingWithTooltip';
import TooltipText from '../../../components/shared/TooltipText';
import { FILTER_TYPES } from './useAgenticFilter';

// Headers for the parent rows (grouped by endpoint ID)
const parentHeaders = [
    {
        title: "",
        text: "",
        value: "collapsibleIcon",
        type: CellType.COLLAPSIBLE,
        boxWidth: '32px'
    },
    {
        title: "Endpoint ID",
        text: "Endpoint ID",
        value: "displayNameComp",
        filterKey: "endpointId",
        textValue: 'endpointId',
        showFilter: true,
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Risk score of collection is maximum risk score of the endpoints inside this collection</Text>} title="Risk score" />,
        value: 'riskScoreComp',
        textValue: 'riskScore',
        numericValue: 'riskScore',
        text: 'Risk Score',
        sortActive: true,
        mergeType: (a, b) => Math.max(a || 0, b || 0),
        shouldMerge: true,
        boxWidth: '80px'
    },
    {   
        title: 'Sensitive data',
        text: 'Sensitive data',
        value: 'sensitiveSubTypes',
        numericValue: 'sensitiveInRespTypes',
        textValue: 'sensitiveSubTypesVal',
        tooltipContent: (<Text variant="bodySm">Types of data type present in response of endpoint inside the collection</Text>),
        mergeType: (a, b) => [...new Set([...(a || []), ...(b || [])])],
        shouldMerge: true,
        boxWidth: '160px'
    },
    {   
        title: <HeadingWithTooltip content={<Text variant="bodySm">The most recent time an endpoint within collection was either discovered for the first time or seen again</Text>} title="Last traffic seen" />, 
        text: 'Last traffic seen', 
        value: 'lastTraffic',
        numericValue: 'detectedTimestamp',
        isText: CellType.TEXT,
        sortActive: true,
        mergeType: (a, b) => Math.max(a || 0, b || 0),
        shouldMerge: true,
        boxWidth: '80px'
    },
    {
        title: <HeadingWithTooltip content={<Text variant="bodySm">Time when collection was created</Text>} title="Discovered" />,
        text: 'Discovered',
        value: 'discovered',
        isText: CellType.TEXT,
        sortActive: true,
    },
];

// Get child column title and display field based on filter type
const getChildColumnConfig = (filterType) => {
    switch (filterType) {
        case FILTER_TYPES.MCP_SERVER:
            return { title: "MCP Server source", displayField: 'sourceId' };
        case FILTER_TYPES.BROWSER_LLM:
            return { title: "LLM source", displayField: 'sourceId' };
        case FILTER_TYPES.AI_AGENT:
        default:
            return { title: "Agentic resource name", displayField: 'serviceName' };
    }
};

// Get child headers based on filter type
const getChildHeaders = (filterType) => {
    const config = getChildColumnConfig(filterType);
    return [
        {
            title: config.title,
            text: config.title,
            value: "displayNameComp",
            textValue: config.displayField,
            boxWidth: '200px'
        },
        {
            title: "Risk score",
            text: "Risk score",
            value: "riskScoreComp",
            boxWidth: '80px'
        },
        {
            title: "Sensitive data",
            text: "Sensitive data",
            value: "sensitiveSubTypes",
            boxWidth: '160px'
        },
        {
            title: "Last traffic seen",
            text: "Last traffic seen",
            value: "lastTraffic",
            boxWidth: '80px'
        },
        {
            title: "Discovered",
            text: "Discovered",
            value: "discovered",
            boxWidth: '80px'
        },
    ];
};

const sortOptions = [
    { label: 'Endpoint ID', value: 'endpointId asc', directionLabel: 'A-Z', sortKey: 'endpointId', columnIndex: 2 },
    { label: 'Endpoint ID', value: 'endpointId desc', directionLabel: 'Z-A', sortKey: 'endpointId', columnIndex: 2 },
    { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 3 },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore', columnIndex: 3 },
    { label: 'Activity', value: 'deactivatedScore asc', directionLabel: 'Active', sortKey: 'detectedTimestamp' },
    { label: 'Activity', value: 'deactivatedScore desc', directionLabel: 'Inactive', sortKey: 'detectedTimestamp' },
    { label: 'Last traffic seen', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'detectedTimestamp', columnIndex: 5 },
    { label: 'Last traffic seen', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'detectedTimestamp', columnIndex: 5 },
    { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 6 },
    { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs', columnIndex: 6 },
];

const resourceName = {
    singular: 'endpoint',
    plural: 'endpoints',
};

/**
 * Groups collections by endpoint ID and merges their data
 */
const groupByEndpointId = (collections) => {
    const groups = {};
    
    collections.forEach(collection => {
        const endpointId = collection.endpointId || 'unknown';
        if (!groups[endpointId]) {
            groups[endpointId] = {
                endpointId,
                children: [],
                // Initialize merge-able fields
                riskScore: 0,
                sensitiveInRespTypes: [],
                detectedTimestamp: 0,
                startTs: Infinity,
                apiCollectionIds: [],
                // First collection for icon reference
                firstCollection: null,
            };
        }
        groups[endpointId].children.push(collection);
        groups[endpointId].apiCollectionIds.push(collection.id);
        
        // Store first collection for icon lookup
        if (!groups[endpointId].firstCollection) {
            groups[endpointId].firstCollection = collection;
        }
        
        // Merge values
        groups[endpointId].riskScore = Math.max(groups[endpointId].riskScore, collection.riskScore || 0);
        groups[endpointId].sensitiveInRespTypes = [...new Set([
            ...groups[endpointId].sensitiveInRespTypes, 
            ...(collection.sensitiveInRespTypes || [])
        ])];
        groups[endpointId].detectedTimestamp = Math.max(
            groups[endpointId].detectedTimestamp, 
            collection.detectedTimestamp || 0
        );
        groups[endpointId].startTs = Math.min(
            groups[endpointId].startTs, 
            collection.startTs || Infinity
        );
    });
    
    return Object.values(groups);
};

/**
 * Prettifies the grouped endpoint data for display
 */
const prettifyGroupedData = (groupedData, filterType) => {
    return groupedData.map(group => {
        const childCount = group.children.length;
        const riskScore = group.riskScore || 0;
        
        return {
            ...group,
            // Use first collection ID as the row ID (table expects scalar, not array)
            id: group.apiCollectionIds[0] || `endpoint-${group.endpointId}`,
            allIds: group.apiCollectionIds, // Keep array for bulk actions
            name: `endpoint-${group.endpointId}`,
            displayName: group.endpointId,
            displayNameComp: (
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Box maxWidth="200px">
                        <TooltipText tooltip={group.endpointId} text={group.endpointId} textProps={{variant: 'headingSm'}} />
                    </Box>
                    <Badge size="small" status="new">{childCount}</Badge>
                </HorizontalStack>
            ),
            riskScoreComp: <Badge status={transform.getStatus(riskScore)} size="small">{riskScore}</Badge>,
            sensitiveSubTypes: transform.prettifySubtypes(group.sensitiveInRespTypes || []),
            sensitiveSubTypesVal: (group.sensitiveInRespTypes || []).join(' ') || '-',
            lastTraffic: func.prettifyEpoch(group.detectedTimestamp),
            discovered: func.prettifyEpoch(group.startTs === Infinity ? 0 : group.startTs),
            isTerminal: false,
            // Function to create expandable children row
            collapsibleRow: <ChildrenTable children={group.children} filterType={filterType} />,
        };
    });
};

/**
 * Children table component for expanded rows
 */
const ChildrenTable = ({ children, filterType }) => {
    const navigate = useNavigate();
    const childHeaders = getChildHeaders(filterType);
    const columnConfig = getChildColumnConfig(filterType);
    
    const handleChildClick = useCallback((collection) => {
        if (collection?.nextUrl) {
            navigate(collection.nextUrl);
        } else if (collection?.id) {
            navigate(`/dashboard/observe/inventory/${collection.id}`);
        }
    }, [navigate]);
    
    const rows = useMemo(() => {
        return children.map(child => {
            const childRiskScore = child.riskScore || 0;
            const prettifiedChild = {
                ...child,
                riskScoreComp: <Badge status={transform.getStatus(childRiskScore)} size="small">{childRiskScore}</Badge>,
                sensitiveSubTypes: transform.prettifySubtypes(child.sensitiveInRespTypes || []),
                lastTraffic: func.prettifyEpoch(child.detectedTimestamp || 0),
                discovered: func.prettifyEpoch(child.startTs || 0),
            };
            
            // Get the display value based on filter type
            const displayValue = child[columnConfig.displayField] || child.splitApiCollectionName;
            
            // Add spacer for collapsible icon column alignment, then map child headers
            const cells = [
                // Spacer to align with parent's collapsible icon column
                <div key={`spacer-${child.id}`} style={{ width: '32px', minWidth: '32px' }} />
            ];
            
            childHeaders.forEach((header, idx) => {
                if (header.value === 'displayNameComp') {
                    cells.push(
                        <div 
                            key={`name-${child.id}`} 
                            style={{ cursor: 'pointer', width: header.boxWidth }} 
                            onClick={() => handleChildClick(child)}
                        >
                            <Box maxWidth="200px">
                                <TooltipText tooltip={displayValue} text={displayValue} />
                            </Box>
                        </div>
                    );
                } else {
                    cells.push(
                        <div 
                            key={`${header.value}-${child.id}`} 
                            style={{ cursor: 'pointer', width: header.boxWidth }}
                            onClick={() => handleChildClick(child)}
                        >
                            {prettifiedChild[header.value] || '-'}
                        </div>
                    );
                }
            });
            
            return cells;
        });
    }, [children, handleChildClick, childHeaders, columnConfig]);
    
    return (
        <td colSpan={parentHeaders.length} style={{ padding: '0px !important' }} className="control-row">
            <DataTable
                rows={rows}
                hasZebraStripingOnData
                headings={[]}
                columnContentTypes={['text', ...childHeaders.map(() => 'text')]}
            />
        </td>
    );
};

/**
 * AgentEndpointTreeTable component
 * Displays collections grouped by endpoint ID with expandable rows showing agentic resources
 */
function AgentEndpointTreeTable({ collections, promotedBulkActions, filterType }) {
    const [groupedData, setGroupedData] = useState([]);
    
    useEffect(() => {
        if (collections && collections.length > 0) {
            const grouped = groupByEndpointId(collections);
            const prettified = prettifyGroupedData(grouped, filterType);
            // Sort by endpoint ID by default
            const sorted = func.sortFunc(prettified, 'endpointId', 1);
            setGroupedData(sorted);
        } else {
            setGroupedData([]);
        }
    }, [collections, filterType]);
    
    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);
    
    return (
        <GithubSimpleTable
            key={`agent-endpoint-tree-${groupedData.length}`}
            pageLimit={100}
            data={groupedData}
            sortOptions={sortOptions}
            resourceName={resourceName}
            filters={[]}
            disambiguateLabel={disambiguateLabel}
            headers={parentHeaders}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            mode={IndexFiltersMode.Filtering}
            headings={parentHeaders}
            useNewRow={true}
            condensedHeight={true}
            csvFileName={"AgentEndpoints"}
            filterStateUrl={"/dashboard/observe/inventory/agent-tree/"}
        />
    );
}

export default AgentEndpointTreeTable;
