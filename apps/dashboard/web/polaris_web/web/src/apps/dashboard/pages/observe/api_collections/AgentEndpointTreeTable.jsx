import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Badge, Box, HorizontalStack, Text } from '@shopify/polaris';
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
import useTable from '../../../components/tables/TableContext';
import AgenticCountBadges from '../agentic/AgenticCountBadges';
import { ChildRow, CollectionCheckbox, childCell, deviceRowId, expandGroupedSelection, useClearSelectionOnMount } from './groupedSelection';

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
                baseRiskScore: 0,
                baseRiskScoreReason: null,
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
        // Carry the base score/remarks from whichever child owns the current max risk score,
        // so the parent row's tooltip can explain the score it displays.
        if ((collection.riskScore || 0) >= groups[endpointId].riskScore) {
            groups[endpointId].baseRiskScore = collection.baseRiskScore;
            groups[endpointId].baseRiskScoreReason = collection.baseRiskScoreReason;
        }
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
 * A device's entities by type (every collection has exactly one, see getAgenticCategoryLabel) and
 * its skills, counted the way the server counts a user's (AgenticCountBadges): an entity is the
 * hostname without the device prefix (<agent>.<name>), a skill is its name alone.
 */
const summarizeChildren = (children) => {
    const keysByType = {};
    const skillNames = new Set();
    children.forEach(child => {
        const category = getAgenticCategoryLabel(child);
        const hostName = String(child.hostName || child.id);
        const entityKey = hostName.slice(hostName.indexOf('.') + 1).toLowerCase();
        (keysByType[category] = keysByType[category] || new Set()).add(entityKey);
        if (Array.isArray(child.skills) && child.skills.length > 0) {
            child.skills.forEach(s => { if (s) skillNames.add(String(s).toLowerCase()); });
        } else if (category === CLIENT_TYPES.SKILL && child.serviceName) {
            // A standalone skill collection is the skill itself
            skillNames.add(String(child.serviceName).toLowerCase());
        }
    });
    const countsByType = Object.fromEntries(Object.entries(keysByType).map(([type, keys]) => [type, keys.size]));
    return { countsByType, skillCount: skillNames.size };
};

/**
 * Prettifies the grouped endpoint data for display
 */
const prettifyGroupedData = (groupedData, filterType, showCategoryColumn, headings) => {
    // Scoped to one plugin: the matched collections are AGENT collections, so their skill/misconfig
    // flags belong to the agent, not the plugin. Plugins are siblings of skills, not containers.
    const isPluginScope = filterType === FILTER_TYPES.PLUGIN;
    return groupedData.map(group => {
        const pluginCount = group.pluginNames?.size || 0;
        const { countsByType, skillCount } = summarizeChildren(group.children);
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

        const rowId = deviceRowId(group.endpointId);
        return {
            // Selecting the device selects all its collections (see groupedSelection)
            id: rowId,
            // Plugin scope matches the agents' collections; a device's bulk actions take only its
            // plugin collections, never the agents
            allIds: isPluginScope
                ? group.children.filter(c => getAgenticCategoryLabel(c) === CLIENT_TYPES.PLUGIN).map(c => c.id)
                : group.apiCollectionIds,
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
                    {isPluginScope
                        ? <Badge size="small" status="new">{pluginCount}</Badge>
                        : <AgenticCountBadges collectionCount={group.children.length} countsByType={countsByType} skillCount={skillCount} pluginCount={countsByType[CLIENT_TYPES.PLUGIN] || 0} />}
                    {!isPluginScope && group.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                    {!isPluginScope && group.hasLocalMcpServer && <Badge size="small" status="critical">Local MCP Server</Badge>}
                    {hasMisconfiguredConfig && <Badge size="small" status="attention">Misconfigured</Badge>}
                    {hasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                </HorizontalStack>
            ),
            ...(showCategoryColumn ? { parentTypeComp: "-" } : {}),
            riskScoreComp: isPluginScope ? '-' : transform.wrapRiskScoreTooltip(
                <Badge status={transform.getStatus(riskScore)} size="small">{riskScore}</Badge>,
                riskScore, group.baseRiskScore, group.baseRiskScoreReason
            ),
            sensitiveSubTypes: isPluginScope ? '-' : transform.prettifySubtypes(group.sensitiveInRespTypes || []),
            sensitiveSubTypesVal: isPluginScope ? '-' : ((group.sensitiveInRespTypes || []).join(' ') || '-'),
            lastTraffic: func.prettifyEpoch(group.detectedTimestamp),
            discovered: func.prettifyEpoch(group.startTs === Infinity ? 0 : group.startTs),
            endpointTagsComp: endpointTags.length > 0 ? endpointTags.join(', ') : '-',
            isTerminal: false,
            // Function to create expandable children row
            collapsibleRow: (
                <ChildrenTable
                    parentRowId={rowId}
                    children={group.children}
                    filterType={filterType}
                    headings={headings}
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
 * Rows under an expanded device: one row of the device table per collection (with its own
 * checkbox), plus the config and plugin rows, cell for cell with the device row's headings.
 */
const ChildrenTable = ({ parentRowId, children, filterType, headings, misconfiguredCollectionId, pluginNames }) => {
    const navigate = useNavigate();
    const { selectedItems } = useTable();
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

    const configRow = misconfiguredCollectionId ? (
        <ChildRow
            key="config"
            onClick={handleConfigClick}
            cells={headings.map(header => header.value === 'displayNameComp' ? childCell(header, (
                <HorizontalStack gap="1" align="start" wrap={false}>
                    <Text variant="bodyMd" as="span">config</Text>
                    <Badge size="small" status="attention">Misconfigured</Badge>
                </HorizontalStack>
            )) : childCell(header, null))}
        />
    ) : null;

    if (isPluginScope) {
        const target = children[0];
        const openPlugins = () => target?.id && navigate(`/dashboard/observe/inventory/${target.id}?agentic_view=plugins`);
        return pluginNames.map(pluginName => (
            <ChildRow
                key={`plugin-${pluginName}`}
                onClick={openPlugins}
                cells={headings.map(header => {
                    if (header.value === 'displayNameComp') {
                        return childCell(header, (
                            <HorizontalStack gap="1" align="start" wrap={false}>
                                <Box maxWidth="200px"><TooltipText tooltip={pluginName} text={pluginName} /></Box>
                                <Badge size="small">Plugin</Badge>
                            </HorizontalStack>
                        ));
                    }
                    return childCell(header, header.value === 'parentTypeComp' ? <Text variant="bodyMd" as="span">Plugin</Text> : null);
                })}
            />
        ));
    }

    const childRows = children.map(child => {
        const childRiskScore = child.riskScore || 0;
        const displayValue = child[columnConfig.displayField] || child.splitApiCollectionName;
        const childCategory = getAgenticCategoryLabel(child);
        const showsBundledSkills = childCategory === CLIENT_TYPES.AI_AGENT || childCategory === CLIENT_TYPES.MCP_SERVER || childCategory === CLIENT_TYPES.SKILL;
        const bundledSkillsCount = showsBundledSkills && Array.isArray(child.skills) ? child.skills.length : 0;
        // Only agents (not MCP servers) have plugins under them.
        const childOwnerTag = childCategory === CLIENT_TYPES.AI_AGENT ? findAssetTag(child.envTypeOriginal)?.value : null;
        const bundledPluginsCount = childOwnerTag ? (pluginCountByAgentTag[childOwnerTag] || 0) : 0;
        const childHasMaliciousSkill = Array.isArray(child.skills) && child.skills.some(s => maliciousSkillKeys.has(skillCollectionKey(child.id, s)));

        const contentFor = (header) => {
            switch (header.value) {
                case 'collapsibleIcon':
                    return <CollectionCheckbox collectionId={child.id} parentRowId={parentRowId} />;
                case 'displayNameComp':
                    return (
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
                            {hasPersonalAccountTag(child.envTypeOriginal) && <Badge size="small" status="warning">Contains personal account</Badge>}
                            {childHasMaliciousSkill && <Badge size="small" status="critical">Malicious Skills</Badge>}
                            {hasLocalMcpServerTag(child.envTypeOriginal) && <Badge size="small" status="critical">Local MCP Server</Badge>}
                        </HorizontalStack>
                    );
                case 'parentTypeComp':
                    return <Text variant="bodyMd" as="span">{childCategory || '-'}</Text>;
                case 'riskScoreComp':
                    return transform.wrapRiskScoreTooltip(
                        <Badge status={transform.getStatus(childRiskScore)} size="small">{childRiskScore}</Badge>,
                        childRiskScore, child.baseRiskScore, child.baseRiskScoreReason
                    );
                case 'sensitiveSubTypes':
                    return transform.prettifySubtypes(child.sensitiveInRespTypes || []);
                case 'lastTraffic':
                    return func.prettifyEpoch(child.detectedTimestamp || 0);
                case 'discovered':
                    return func.prettifyEpoch(child.startTs || 0);
                default:
                    return null;
            }
        };

        return (
            <ChildRow
                key={`child-${child.id}`}
                selected={selectedItems.includes(parentRowId) || selectedItems.includes(child.id)}
                onClick={() => handleChildClick(child)}
                cells={headings.map(header => childCell(header, contentFor(header), true))}
            />
        );
    });

    return <>{configRow}{childRows}</>;
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
            const prettified = prettifyGroupedData(grouped, filterType, showCategoryColumn,
                showCategoryColumn ? parentHeadersScoped : parentHeadersBase);
            const sorted = func.sortFunc(prettified, 'endpointId', 1);
            setGroupedData(sorted);
        } else {
            setGroupedData([]);
        }
    }, [collections, filterType, showCategoryColumn]);

    const disambiguateLabel = useCallback((key, value) => {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }, []);

    useClearSelectionOnMount();

    // Bulk actions get collection ids: a selected device stands for every collection under it
    const allIdsByRowId = useMemo(() => new Map(groupedData.map(row => [row.id, row.allIds])), [groupedData]);
    const groupedBulkActions = useCallback((selectedIds) => (
        promotedBulkActions(expandGroupedSelection(selectedIds, id => allIdsByRowId.get(id)))
    ), [allIdsByRowId, promotedBulkActions]);

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
            isMultipleItemsSelected={true}
            selectAllAddsRowIds={true}
            promotedBulkActions={promotedBulkActions && groupedBulkActions}
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
