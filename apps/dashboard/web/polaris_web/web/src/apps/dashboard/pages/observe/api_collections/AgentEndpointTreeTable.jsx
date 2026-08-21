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
import { getAgenticCategoryLabel, hasPersonalAccountTag, hasLocalMcpServerTag, hasMisconfiguredConfigTag, getPluginNameForCollection, findAssetTag, CLIENT_TYPES } from '../agentic/mcpClientHelper';
import { skillCollectionKey } from '../agentic/constants';
import PersistStore from '../../../../main/PersistStore';

/** IndexTable adds a leading selection column when `selectable` is true (see AgentEndpointTreeTable). */
const INDEX_TABLE_SELECTION_COLUMN_COUNT = 1;

// Parent rows (grouped by endpoint ID). Users & devices adds Type before Username (same column count +1 when scoped).
const parentHeadersBase = [
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
        title: "Username",
        text: "Username",
        value: "usernameComp",
        filterKey: "username",
        textValue: 'username',
        showFilter: true,
        boxWidth: '100px',
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
    {
        title: 'Endpoint tags',
        text: 'Endpoint tags',
        value: 'endpointTagsComp',
        filterKey: 'endpointTags',
        textValue: 'endpointTags',
        showFilter: true,
        tooltipContent: (<Text variant="bodySm">Risk tags associated with this endpoint (personal account, malicious skills, etc.)</Text>),
    },
];

const parentHeadersScoped = [...parentHeadersBase.slice(0, 2), { title: "Type", text: "Type", value: "parentTypeComp", textValue: "parentTypeComp", isText: CellType.TEXT, boxWidth: "120px" }, ...parentHeadersBase.slice(2)];

const sortOptions = [
    { label: 'Endpoint ID', value: 'endpointId asc', directionLabel: 'A-Z', sortKey: 'endpointId', columnIndex: 2 },
    { label: 'Endpoint ID', value: 'endpointId desc', directionLabel: 'Z-A', sortKey: 'endpointId', columnIndex: 2 },
    { label: 'Username', value: 'username asc', directionLabel: 'A-Z', sortKey: 'username', columnIndex: 3 },
    { label: 'Username', value: 'username desc', directionLabel: 'Z-A', sortKey: 'username', columnIndex: 3 },
    { label: 'Risk Score', value: 'score asc', directionLabel: 'High risk', sortKey: 'riskScore', columnIndex: 4 },
    { label: 'Risk Score', value: 'score desc', directionLabel: 'Low risk', sortKey: 'riskScore', columnIndex: 4 },
    { label: 'Activity', value: 'deactivatedScore asc', directionLabel: 'Active', sortKey: 'detectedTimestamp' },
    { label: 'Activity', value: 'deactivatedScore desc', directionLabel: 'Inactive', sortKey: 'detectedTimestamp' },
    { label: 'Last traffic seen', value: 'detected asc', directionLabel: 'Recent first', sortKey: 'detectedTimestamp', columnIndex: 6 },
    { label: 'Last traffic seen', value: 'detected desc', directionLabel: 'Oldest first', sortKey: 'detectedTimestamp', columnIndex: 6 },
    { label: 'Discovered', value: 'discovered asc', directionLabel: 'Recent first', sortKey: 'startTs', columnIndex: 7 },
    { label: 'Discovered', value: 'discovered desc', directionLabel: 'Oldest first', sortKey: 'startTs', columnIndex: 7 },
];

// Get child column title and display field based on filter type
const getChildColumnConfig = (filterType) => {
    switch (filterType) {
        case FILTER_TYPES.MCP_SERVER:
            return { title: "MCP Server source", displayField: 'sourceId' };
        case FILTER_TYPES.BROWSER_LLM:
            return { title: "LLM source", displayField: 'sourceId' };
        case FILTER_TYPES.SKILL:
        case FILTER_TYPES.PLUGIN:
        case FILTER_TYPES.AI_AGENT:
        default:
            return { title: "Agentic resource name", displayField: 'serviceName' };
    }
};

// Get child headers based on filter type
const getChildHeaders = (filterType, showCategoryColumn) => {
    const config = getChildColumnConfig(filterType);
    const nameCol = {
        title: config.title,
        text: config.title,
        value: "displayNameComp",
        textValue: config.displayField,
        boxWidth: '200px'
    };
    const categoryCol = {
        title: "Type",
        text: "Type",
        value: "agenticCategory",
        textValue: "agenticCategory",
        boxWidth: "120px"
    };
    const rest = [
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
    return showCategoryColumn ? [nameCol, categoryCol, ...rest] : [nameCol, ...rest];
};

const resourceName = {
    singular: 'endpoint',
    plural: 'endpoints',
};

/**
 * Groups collections by endpoint ID and merges their data
 */
const groupByEndpointId = (collections) => {
    // Collection-scoped keys: only the collection that actually owns the tagged skill is flagged,
    // so a same-named skill under another user/agent doesn't inherit the badge.
    const maliciousSkillKeys = new Set(
        (PersistStore.getState().skillRiskScoreCache)?.maliciousSkillKeys || []
    );
    const groups = {};

    collections.forEach(collection => {
        const endpointId = collection.endpointId || 'unknown';
        if (!groups[endpointId]) {
            groups[endpointId] = {
                endpointId,
                children: [],
                riskScore: 0,
                sensitiveInRespTypes: [],
                detectedTimestamp: 0,
                startTs: Infinity,
                apiCollectionIds: [],
                firstCollection: null,
                username: '-',
                hasPersonalAccount: false,
                hasLocalMcpServer: false,
                hasMisconfiguredConfig: false,
                hasMaliciousSkill: false,
                pluginNames: new Set(),
            };
        }
        groups[endpointId].children.push(collection);
        groups[endpointId].apiCollectionIds.push(collection.id);
        if (!groups[endpointId].firstCollection) {
            groups[endpointId].firstCollection = collection;
            groups[endpointId].username = collection.username || '-';
        }
        if (hasPersonalAccountTag(collection.envTypeOriginal)) {
            groups[endpointId].hasPersonalAccount = true;
        }
        if (hasLocalMcpServerTag(collection.envTypeOriginal)) {
            groups[endpointId].hasLocalMcpServer = true;
        }
        if (hasMisconfiguredConfigTag(collection.envTypeOriginal)) {
            groups[endpointId].hasMisconfiguredConfig = true;
        }
        const collPluginName = getPluginNameForCollection(collection);
        if (collPluginName) groups[endpointId].pluginNames.add(collPluginName);
        if (!groups[endpointId].hasMaliciousSkill && Array.isArray(collection.skills)) {
            groups[endpointId].hasMaliciousSkill = collection.skills.some(s => maliciousSkillKeys.has(skillCollectionKey(collection.id, s)));
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
 * Counts agentic assets the badge should advertise: each non-Skill child collection plus the
 * unique skill names from c.skills[] across all children (deduped against sibling Skill
 * collections). Matches the per-user "Agentic assets" semantic on the Users and devices page —
 * skills bundled inside AI Agent / MCP Server collections still count even though we don't
 * explode them into separate rows.
 */
const countAgenticAssets = (children, showCategoryColumn) => {
    const skillNames = new Set();
    const pluginNames = new Set();
    let nonSkillCount = 0;
    children.forEach(child => {
        const category = getAgenticCategoryLabel(child);
        if (category !== CLIENT_TYPES.SKILL) nonSkillCount += 1;
        if (Array.isArray(child.skills)) {
            child.skills.forEach(s => { if (s) skillNames.add(String(s).toLowerCase()); });
        }
        const childPluginName = getPluginNameForCollection(child);
        if (childPluginName) pluginNames.add(String(childPluginName).toLowerCase());
    });
    // When not in the "All" category view, skill collections themselves appear as children.
    // In that case fall back to children.length so standalone Skill rows count correctly.
    if (!showCategoryColumn && skillNames.size === 0 && pluginNames.size === 0) return children.length;
    return nonSkillCount + skillNames.size + pluginNames.size;
};

/**
 * Prettifies the grouped endpoint data for display
 */
const prettifyGroupedData = (groupedData, filterType, showCategoryColumn, expandedColSpan) => {
    // Scoped to one plugin: the matched collections are AGENT collections, so their skill/misconfig
    // flags belong to the agent, not the plugin. Plugins are siblings of skills, not containers.
    const isPluginScope = filterType === FILTER_TYPES.PLUGIN;
    return groupedData.map(group => {
        const pluginCount = group.pluginNames?.size || 0;
        const childCount = isPluginScope ? pluginCount : countAgenticAssets(group.children, showCategoryColumn);
        const riskScore = isPluginScope ? 0 : (group.riskScore || 0);
        const hasMisconfiguredConfig = !isPluginScope && (group.hasMisconfiguredConfig || false);
        const hasMaliciousSkill = !isPluginScope && (group.hasMaliciousSkill || false);

        const endpointTags = [
            ...(!isPluginScope && group.hasPersonalAccount ? ['Contains personal account'] : []),
            ...(!isPluginScope && group.hasLocalMcpServer ? ['Local MCP Server'] : []),
            ...(hasMisconfiguredConfig ? ['Misconfigured'] : []),
            ...(hasMaliciousSkill ? ['Malicious Skills'] : []),
            ...(!isPluginScope && pluginCount > 0 ? ['Contains Plugins'] : []),
        ];

        return {
            // Use first collection ID as the row ID (table expects scalar, not array)
            id: group.apiCollectionIds[0] || `endpoint-${group.endpointId}`,
            allIds: group.apiCollectionIds, // Keep array for bulk actions
            name: `endpoint-${group.endpointId}`,
            endpointId: group.endpointId,
            displayName: group.endpointId,
            username: group.username || '-',
            usernameComp: (
                <Box maxWidth="100px">
                    <TooltipText tooltip={group.username || '-'} text={group.username || '-'} />
                </Box>
            ),
            riskScore: group.riskScore || 0,
            sensitiveInRespTypes: group.sensitiveInRespTypes || [],
            detectedTimestamp: group.detectedTimestamp || 0,
            startTs: group.startTs === Infinity ? 0 : group.startTs,
            endpointTags,
            displayNameComp: (
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Box maxWidth="200px">
                        <TooltipText tooltip={group.endpointId} text={group.endpointId} textProps={{variant: 'headingSm'}} />
                    </Box>
                    <Badge size="small" status="new">{childCount}</Badge>
                    {!isPluginScope && group.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                    {!isPluginScope && group.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                    {hasMisconfiguredConfig && <Badge size="small" status="attention">Misconfigured</Badge>}
                    {hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                    {!isPluginScope && pluginCount > 0 && <Badge size="small" status="info">{`${pluginCount} ${pluginCount === 1 ? 'plugin' : 'plugins'}`}</Badge>}
                </HorizontalStack>
            ),
            ...(showCategoryColumn ? { parentTypeComp: "-" } : {}),
            riskScoreComp: isPluginScope ? '-' : <Badge status={transform.getStatus(riskScore)} size="small">{riskScore}</Badge>,
            sensitiveSubTypes: isPluginScope ? '-' : transform.prettifySubtypes(group.sensitiveInRespTypes || []),
            sensitiveSubTypesVal: isPluginScope ? '-' : ((group.sensitiveInRespTypes || []).join(' ') || '-'),
            lastTraffic: func.prettifyEpoch(group.detectedTimestamp),
            discovered: func.prettifyEpoch(group.startTs === Infinity ? 0 : group.startTs),
            endpointTagsComp: endpointTags.length > 0 ? endpointTags.join(', ') : '-',
            isTerminal: false,
            // Function to create expandable children row
            collapsibleRow: (
                <ChildrenTable
                    children={group.children}
                    filterType={filterType}
                    showCategoryColumn={showCategoryColumn}
                    expandedColSpan={expandedColSpan}
                    pluginNames={isPluginScope ? [...(group.pluginNames || [])] : null}
                    misconfiguredCollectionId={hasMisconfiguredConfig
                        ? (group.children.find(c => hasMisconfiguredConfigTag(c.envTypeOriginal))?.id || null)
                        : null}
                />
            ),
        };
    });
};

/**
 * Children table component for expanded rows
 */
const ChildrenTable = ({ children, filterType, showCategoryColumn, expandedColSpan, misconfiguredCollectionId, pluginNames }) => {
    const navigate = useNavigate();
    const childHeaders = getChildHeaders(filterType, showCategoryColumn);
    const columnConfig = getChildColumnConfig(filterType);
    // Plugin scope: one row per plugin, not per agent collection (which would list the agent's skills).
    const isPluginScope = Array.isArray(pluginNames);
    const maliciousSkillKeys = useMemo(() => {
        const cached = PersistStore.getState().skillRiskScoreCache;
        return new Set(cached?.maliciousSkillKeys || []);
    }, []);

    // Config endpoints (/<tool>/config/*) and skill endpoints (/skills/*) coexist in one collection.
    // Scope the inventory view so the config row shows only config endpoints and a skill-bearing
    // row shows only its skills, instead of mixing both.
    const handleChildClick = useCallback((collection) => {
        const childCategory = getAgenticCategoryLabel(collection);
        const bundlesComponents = childCategory === CLIENT_TYPES.AI_AGENT || childCategory === CLIENT_TYPES.MCP_SERVER || childCategory === CLIENT_TYPES.SKILL;
        const bundlesSkills = bundlesComponents && Array.isArray(collection?.skills) && collection.skills.length > 0;
        const scope = bundlesSkills ? '?agentic_view=skills' : '';
        if (collection?.nextUrl) {
            navigate(`${collection.nextUrl}${scope}`);
        } else if (collection?.id) {
            navigate(`/dashboard/observe/inventory/${collection.id}${scope}`);
        }
    }, [navigate]);

    const handleConfigClick = useCallback(() => {
        if (!misconfiguredCollectionId) return;
        navigate(`/dashboard/observe/inventory/${misconfiguredCollectionId}?agentic_view=config`);
    }, [navigate, misconfiguredCollectionId]);

    const configRow = useMemo(() => {
        if (!misconfiguredCollectionId) return null;
        const cells = [
            <div key="spacer-config" style={{ width: '32px', minWidth: '32px' }} />
        ];
        childHeaders.forEach((header, idx) => {
            if (header.value === 'displayNameComp') {
                cells.push(
                    <div key="name-config" style={{ cursor: 'pointer', width: header.boxWidth }} onClick={handleConfigClick}>
                        <HorizontalStack gap="1" align="start" wrap={false}>
                            <Text variant="bodyMd" as="span">config</Text>
                            <Badge size="small" status="attention">Misconfigured</Badge>
                        </HorizontalStack>
                    </div>
                );
            } else {
                cells.push(
                    <div key={`config-empty-${idx}`} style={{ cursor: 'pointer', width: header.boxWidth }} onClick={handleConfigClick} />
                );
            }
        });
        return cells;
    }, [misconfiguredCollectionId, childHeaders, handleConfigClick]);

    const pluginRows = useMemo(() => {
        if (!isPluginScope) return null;
        const target = children[0];
        return pluginNames.map((pluginName) => {
            const cells = [<div key={`spacer-${pluginName}`} style={{ width: '32px', minWidth: '32px' }} />];
            childHeaders.forEach((header) => {
                const isName = header.value === 'displayNameComp';
                cells.push(
                    <div
                        key={`${header.value}-${pluginName}`}
                        style={{ cursor: 'pointer', width: header.boxWidth }}
                        onClick={() => target?.id && navigate(`/dashboard/observe/inventory/${target.id}?agentic_view=plugins`)}
                    >
                        {isName ? (
                            <HorizontalStack gap="1" align="start" wrap={false}>
                                <Box maxWidth="200px"><TooltipText tooltip={pluginName} text={pluginName} /></Box>
                                <Badge size="small">Plugin</Badge>
                            </HorizontalStack>
                        ) : (header.value === 'agenticCategory' ? (
                            <Text variant="bodyMd" as="span">Plugin</Text>
                        ) : '-')}
                    </div>
                );
            });
            return cells;
        });
    }, [isPluginScope, pluginNames, children, childHeaders, navigate]);

    // Plugins live in their own collection (sibling to the agent, not embedded in it), but they still
    // share the agent's own owner tag (mcp-client/ai-agent) and endpointId — so "how many plugins does
    // this agent have" is answered by matching siblings in this same device's `children` list, the
    // same continuity the skills badge below already gives for embedded skills.
    const pluginCountByAgentTag = useMemo(() => {
        const counts = {};
        children.forEach(c => {
            const pluginName = getPluginNameForCollection(c);
            if (!pluginName) return;
            const owner = findAssetTag(c.envTypeOriginal)?.value;
            if (owner) counts[owner] = (counts[owner] || 0) + 1;
        });
        return counts;
    }, [children]);

    const rows = useMemo(() => {
        if (isPluginScope) return [];
        return children.map(child => {
            const childRiskScore = child.riskScore || 0;
            const prettifiedChild = {
                ...child,
                agenticCategory: showCategoryColumn ? getAgenticCategoryLabel(child) : undefined,
                riskScoreComp: transform.wrapRiskScoreTooltip(
                    <Badge status={transform.getStatus(childRiskScore)} size="small">{childRiskScore}</Badge>,
                    childRiskScore, child.baseRiskScore, child.baseRiskScoreReason
                ),
                sensitiveSubTypes: transform.prettifySubtypes(child.sensitiveInRespTypes || []),
                lastTraffic: func.prettifyEpoch(child.detectedTimestamp || 0),
                discovered: func.prettifyEpoch(child.startTs || 0),
            };

            const displayValue = child[columnConfig.displayField] || child.splitApiCollectionName;

            const cells = [
                <div key={`spacer-${child.id}`} style={{ width: '32px', minWidth: '32px' }} />
            ];

            const childHasLocalMcp = hasLocalMcpServerTag(child.envTypeOriginal);
            const childHasPersonalAccount = hasPersonalAccountTag(child.envTypeOriginal);
            const childCategory = getAgenticCategoryLabel(child);
            const showsBundledSkills = childCategory === CLIENT_TYPES.AI_AGENT || childCategory === CLIENT_TYPES.MCP_SERVER;
            const bundledSkillsCount = showsBundledSkills && Array.isArray(child.skills) ? child.skills.length : 0;
            // Only agents (not MCP servers) have plugins under them.
            const childOwnerTag = childCategory === CLIENT_TYPES.AI_AGENT ? findAssetTag(child.envTypeOriginal)?.value : null;
            const bundledPluginsCount = childOwnerTag ? (pluginCountByAgentTag[childOwnerTag] || 0) : 0;
            const childHasMaliciousSkill = Array.isArray(child.skills) && child.skills.some(s => maliciousSkillKeys.has(skillCollectionKey(child.id, s)));
            childHeaders.forEach(header => {
                if (header.value === 'displayNameComp') {
                    cells.push(
                        <div
                            key={`name-${child.id}`}
                            style={{ cursor: 'pointer', width: header.boxWidth }}
                            onClick={() => handleChildClick(child)}
                        >
                            <HorizontalStack gap="1" align="start" wrap={false}>
                                <Box maxWidth="200px">
                                    <TooltipText tooltip={displayValue} text={displayValue} />
                                </Box>
                                {bundledSkillsCount > 0 && (
                                    <Badge size="small" status="info">{`${bundledSkillsCount} ${bundledSkillsCount === 1 ? 'skill' : 'skills'}`}</Badge>
                                )}
                                {bundledPluginsCount > 0 && (
                                    <Badge size="small">{`${bundledPluginsCount} ${bundledPluginsCount === 1 ? 'plugin' : 'plugins'}`}</Badge>
                                )}
                                {childHasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                                {childHasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                                {childHasLocalMcp && <Badge size="small" status="critical">Local MCP Server</Badge>}
                            </HorizontalStack>
                        </div>
                    );
                } else if (header.value === 'agenticCategory') {
                    cells.push(
                        <div
                            key={`${header.value}-${child.id}`}
                            style={{ cursor: 'pointer', width: header.boxWidth }}
                            onClick={() => handleChildClick(child)}
                        >
                            <Text variant="bodyMd" as="span">{prettifiedChild.agenticCategory || '-'}</Text>
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
    }, [isPluginScope, children, handleChildClick, childHeaders, columnConfig, showCategoryColumn, maliciousSkillKeys, pluginCountByAgentTag]);

    const columnContentTypes = useMemo(
        () => ["text", ...childHeaders.map(() => "text")],
        [childHeaders],
    );

    return (
        <td colSpan={expandedColSpan} style={{ padding: '0px !important' }} className="control-row">
            <Box width="100%">
                <DataTable
                    rows={isPluginScope ? pluginRows : (configRow ? [configRow, ...rows] : rows)}
                    hasZebraStripingOnData
                    headings={[]}
                    columnContentTypes={columnContentTypes}
                />
            </Box>
        </td>
    );
};

/**
 * AgentEndpointTreeTable component
 * Displays collections grouped by endpoint ID with expandable rows showing agentic resources
 */
function AgentEndpointTreeTable({ collections, promotedBulkActions, filterType, showCategoryColumn = false }) {
    const [groupedData, setGroupedData] = useState([]);

    const parentHeaders = showCategoryColumn ? parentHeadersScoped : parentHeadersBase;

    useEffect(() => {
        if (collections && collections.length > 0) {
            const grouped = groupByEndpointId(collections);
            const expandedColSpan =
                parentHeadersBase.length + (showCategoryColumn ? 1 : 0) + INDEX_TABLE_SELECTION_COLUMN_COUNT;
            const prettified = prettifyGroupedData(grouped, filterType, showCategoryColumn, expandedColSpan);
            const sorted = func.sortFunc(prettified, 'endpointId', 1);
            setGroupedData(sorted);
        } else {
            setGroupedData([]);
        }
    }, [collections, filterType, showCategoryColumn]);

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
