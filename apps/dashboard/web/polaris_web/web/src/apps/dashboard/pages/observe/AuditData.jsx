
import { Text, HorizontalStack, VerticalStack, Box, Icon, Tooltip, IndexFiltersMode } from "@shopify/polaris"
import { CircleTickMajor, CircleCancelMajor, SettingsMajor, ClockMinor } from "@shopify/polaris-icons";
import { useEffect, useMemo, useReducer, useRef, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import api from "./api"
import collectionApi from "./api_collections/api"
import func from "@/util/func"
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";
import { MethodBox } from "./GetPrettifyEndpoint";
import { CellType } from "../../components/tables/rows/GithubRow";
import PersistStore from "../../../main/PersistStore";
import ConditionalApprovalModal from "../../components/modals/ConditionalApprovalModal";
import RegistryBadge from "../../components/shared/RegistryBadge";
import AllowlistBadge from "../../components/shared/AllowlistBadge";
import ComponentRiskAnalysisBadges from "./components/ComponentRiskAnalysisBadges";
import { isEndpointSecurityCategory } from "../../../main/labelHelper";
import AuditDataDrawer from "./AuditDataDrawer";
import CollectionIcon from "../../components/shared/CollectionIcon";
import settingsApi from "../settings/api";
import { intersectServerActionFlags, getRegistryOverride } from "./auditServerActionFlags";
import "../../components/shared/style.css";

const TAB_IDS = { ALL: 'all', MCP_SERVERS: 'mcp_servers', SKILLS: 'skills' };
const TABS_DEFAULT = ['All', 'MCP Servers', 'Skills'];
const TABS_ENDPOINT_SECURITY = ['MCP Servers', 'Skills'];
const MCP_TYPES = ['mcp-tool', 'mcp-resource', 'mcp-prompt', 'mcp-server'];

const headingsEndpointSecurity = [
    {
        title: 'MCP Server',
        text: 'MCP Server',
        value: 'mcpServerNameComp',
        filterKey: 'mcpServer',
    },
    {
        title: 'AI Agent',
        text: 'AI Agent',
        value: 'aiAgentNameComp',
        type: CellType.TEXT,
        filterKey: 'aiAgent',
    },
    {
        title: 'Last Detected',
        text: "Last Detected",
        value: "lastDetectedComp",
        sortActive: true,
        sortKey: 'lastDetected',
        type: CellType.TEXT
    },
    {
        title: 'Updated',
        text: "Updated",
        value: "updatedTimestampComp",
        sortKey: 'updatedTimestamp',
        sortActive: true,
        type: CellType.TEXT
    },
    {
        title: 'Access Types',
        text: "Access Types",
        value: "apiAccessTypesComp",
    },
    {
        title: 'Remarks',
        text: "Remarks",
        value: "remarksComp"
    },
    {
        title: 'Marked By',
        text: "Marked By",
        value: "markedBy",
        type: CellType.TEXT
    },
]

const headingsDefault = [
    {
        title: 'Type',
        value: 'typeComp',
        text: 'Type',
        filterKey: 'type',
    },
    {
        title: 'Risk Analysis',
        value: 'riskAnalysisComp',
        text: 'Risk Analysis',
    },
    {
        text: "Agentic Component name",
        value: "resourceName",
        title: "Agentic Component name",
        type: CellType.TEXT,
    },
    {
        text: "Collection name",
        value: "collectionName",
        title: "Collection name",
        filterKey: 'collectionName',
        type: CellType.TEXT,
    },
    {
        title: 'Last Detected',
        text: "Last Detected",
        value: "lastDetectedComp",
        sortActive: true,
        sortKey: 'lastDetected',
        type: CellType.TEXT
    },
    {
        title: 'Updated',
        text: "Updated",
        value: "updatedTimestampComp",
        sortKey: 'updatedTimestamp',
        sortActive: true,
        type: CellType.TEXT
    },
    {
        title: 'Access Types',
        text: "Access Types",
        value: "apiAccessTypesComp",
        filterKey: 'apiAccessTypes',
    },
    {
        title: 'Remarks',
        text: "Remarks",
        value: "remarksComp"
    },
    {
        title: 'Marked By',
        text: "Marked By",
        value: "markedBy",
        type: CellType.TEXT,
        filterKey: 'markedBy',
    },
    {
        title: '',
        type: CellType.ACTION,
    }
]

const headingsSkills = [
    {
        text: "Skill",
        value: "skillName",
        title: "Skill",
        type: CellType.TEXT,
    },
    {
        text: "AI Agent",
        value: "collectionsComp",
        title: "AI Agent",
        type: CellType.TEXT,
    },
    {
        title: 'Last Detected',
        text: "Last Detected",
        value: "lastDetectedComp",
        sortActive: true,
        sortKey: 'lastDetected',
        type: CellType.TEXT
    },
    {
        title: 'Status',
        text: "Status",
        value: "statusComp",
    },
    {
        title: 'Marked By',
        text: "Marked By",
        value: "markedBy",
        type: CellType.TEXT,
        filterKey: 'markedBy',
    },
]

const sortOptions = [
    { label: 'Last Detected', value: 'lastDetected asc', directionLabel: 'Oldest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Last Detected', value: 'lastDetected desc', directionLabel: 'Newest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Updated', value: 'updatedTimestamp asc', directionLabel: 'Oldest', sortKey: 'updatedTimestamp', columnIndex: 4 },
    { label: 'Updated', value: 'updatedTimestamp desc', directionLabel: 'Newest', sortKey: 'updatedTimestamp', columnIndex: 4 },
   
];

const MCP_TYPE_CHOICES = [
    { label: "Tool", value: "mcp-tool" },
    { label: "Resource", value: "mcp-resource" },
    { label: "Prompt", value: "mcp-prompt" },
    { label: "Server", value: "mcp-server" }
];

let filtersDefault = [
    {
        key: 'type',
        label: 'Type',
        title: 'Type',
        choices: MCP_TYPE_CHOICES,
    },
    {
        key: 'markedBy',
        label: 'Marked By',
        title: 'Marked By',
        choices: [],
    },
    {
        key: 'apiAccessTypes',
        label: 'Access Types',
        title: 'Access Types',
        choices: [
            { label: "Public", value: "PUBLIC" },
            { label: "Private", value: "PRIVATE" },
            { label: "Partner", value: "PARTNER" },
            { label: "Third Party", value: "THIRD_PARTY" }
        ],
    },
    {
        key: 'collectionName',
        label: 'Collection Name',
        title: 'Collection Name',
        choices: [],
    }
]

const getTabFilters = (tabId) => {
    // Skills are sourced from api_info; the audit filter set is built for mcp_audit_info
    // and would over-constrain. Return an empty filter list so the chips bar is clean.
    if (tabId === TAB_IDS.SKILLS) return [];
    return filtersDefault;
}

let filtersEndpointSecurity = [
    {
        key: 'markedBy',
        label: 'Marked By',
        title: 'Marked By',
        choices: [],
    },
    {
        key: 'apiAccessTypes',
        label: 'Access Types',
        title: 'Access Types',
        choices: [
            { label: "Public", value: "PUBLIC" },
            { label: "Private", value: "PRIVATE" },
            { label: "Partner", value: "PARTNER" },
            { label: "Third Party", value: "THIRD_PARTY" }
        ],
    },
    {
        key: 'aiAgent',
        label: 'AI Agent',
        title: 'AI Agent',
        choices: [],
    },
    {
        key: 'mcpServer',
        label: 'MCP Server',
        title: 'MCP Server',
        choices: [],
    }
]

const resourceName = {
    singular: 'audit record',
    plural: 'audit records',
};

const isAtlasEndpointCollection = (allCollections, collectionId) => {
    if (!allCollections || !collectionId) return false;
    const collection = allCollections.find(col => col.id === collectionId);
    if (!collection || !collection.envType || !Array.isArray(collection.envType)) return false;
    return collection.envType.some(env => env.value && env.value.toLowerCase() === 'endpoint');
};

const convertDataIntoTableFormat = (auditRecord, collectionName, collectionRegistry) => {
    const allCollections = PersistStore.getState().allCollections;
    let temp = {...auditRecord}

    // Merged-server rows come from the aggregation pipeline and carry agentName/serverName/
    // groupedHexIds directly. The backend already collapses remarksArr into the most-restrictive
    // remarks/markedBy/approvalConditions on the row.
    const isMerged = Array.isArray(auditRecord?.groupedHexIds) && (auditRecord?.agentName !== undefined || auditRecord?.serverName !== undefined);

    if (isMerged) {
        temp['type'] = 'mcp-server';
        temp['hexId'] = auditRecord.groupedHexIds.length > 0 ? auditRecord.groupedHexIds[0] : auditRecord?._id;
        temp['resourceName'] = auditRecord?._id;
        temp['aiAgentName'] = auditRecord?.agentName || '-';
        const serverName = auditRecord?.serverName || auditRecord?._id;
        temp['mcpServerName'] = serverName;
        temp['mcpServerNameComp'] = (
            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                <Box className="audit-table-icon">
                    <CollectionIcon
                        hostName={serverName}
                        assetTagValue={serverName}
                        displayName={serverName}
                    />
                </Box>
                <Text>{serverName}</Text>
                {temp?.verified && <AllowlistBadge />}
            </HorizontalStack>
        );
        // Endpoint-security path: derive isEndpointSource by checking any of the merged
        // collections is an Atlas endpoint collection.
        temp['isEndpointSource'] = (auditRecord?.groupedHostCollectionIds || []).some(
            (cid) => isAtlasEndpointCollection(allCollections, cid)
        );
        temp['aiAgentNameComp'] = (
            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                <Box className="audit-table-icon">
                    <CollectionIcon
                        hostName={temp['aiAgentName']}
                        assetTagValue={temp['aiAgentName']}
                        displayName={temp['aiAgentName']}
                    />
                </Box>
                <Text>{temp['aiAgentName']}</Text>
            </HorizontalStack>
        );
    } else {
        temp['isEndpointSource'] = isAtlasEndpointCollection(allCollections, auditRecord?.hostCollectionId);
    }

    temp['typeComp'] = (
        <MethodBox method={""} url={(temp?.type || "TOOL").toLowerCase()}/>
    )

    temp['riskAnalysisComp'] = <ComponentRiskAnalysisBadges componentRiskAnalysis={temp?.componentRiskAnalysis} />;

    temp['apiAccessTypesComp'] = temp?.apiAccessTypes && temp?.apiAccessTypes.length > 0 && temp?.apiAccessTypes.join(', ') ;
    temp['lastDetectedComp'] = temp?.lastDetected ? func.prettifyEpoch(temp.lastDetected) : "-"
    temp['updatedTimestampComp'] = temp?.updatedTimestamp ? func.prettifyEpoch(temp.updatedTimestamp) : "-"
    temp['approvedAtComp'] = temp?.approvedAt ? func.prettifyEpoch(temp.approvedAt) : "-"
    temp['expiresAtComp'] = temp?.approvalConditions?.expiresAt ? (() => {
        const expirationDate = new Date(temp.approvalConditions.expiresAt * 1000);
        return expirationDate.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
            timeZone: window.TIME_ZONE === 'Us/Pacific' ? 'America/Los_Angeles' : window.TIME_ZONE
        });
    })() : null
    temp['remarksComp'] = (
        (temp?.remarks === null || temp?.remarks === "" || !temp?.remarks) ?
            <HorizontalStack gap="1" blockAlign="center">
                <Text variant="bodyMd">Approved</Text>
                <Tooltip content="Audit Pending">
                    <Icon source={ClockMinor} color="warning" />
                </Tooltip>
            </HorizontalStack> :
            <VerticalStack gap="1">
                <Text variant="bodyMd">{temp?.remarks}</Text>
                {temp?.approvalConditions && (
                    <Box paddingBlockStart="1">
                        <VerticalStack gap="0">
                            {(() => {
                                const approvalDetails = [
                                    { condition: temp?.approvalConditions?.justification, label: 'Justification', value: temp.approvalConditions.justification },
                                    { condition: temp?.approvedAt, label: 'Approved at', value: temp.approvedAtComp },
                                    { condition: temp?.expiresAtComp, label: 'Expires At', value: temp.expiresAtComp },
                                    { condition: temp?.approvalConditions?.allowedIps, label: 'Allowed IPs', value: temp.approvalConditions.allowedIps?.join(', ') },
                                    { condition: temp?.approvalConditions?.allowedIpRange, label: 'Allowed IP Ranges', value: temp.approvalConditions.allowedIpRange },
                                    { condition: temp?.approvalConditions?.allowedEndpoints, label: 'Allowed Endpoints', value: temp.approvalConditions.allowedEndpoints?.map(ep => ep.name).join(', ') }
                                ];
                                
                                const elements = [];
                                for (let i = 0; i < approvalDetails.length; i++) {
                                    const detail = approvalDetails[i];
                                    if (detail.condition) {
                                        elements.push(
                                            <Text key={i} variant="bodySm" color="subdued">
                                                <Text as="span" fontWeight="medium">{detail.label}:</Text> {detail.value}
                                            </Text>
                                        );
                                    }
                                }
                                return elements;
                            })()}
                        </VerticalStack>
                    </Box>
                )}
            </VerticalStack>
    )
    if (!isMerged) {
        temp['collectionName'] = (
            <HorizontalStack gap="2" align="center">
                <Text>{collectionName}</Text>
                {collectionRegistry === "available" && <RegistryBadge />}
            </HorizontalStack>
        );
    }
    // Required by GithubRow for tree-style expand/collapse
    temp['id'] = temp.hexId;
    temp['name'] = temp.hexId;
    temp['isTerminal'] = false;
    return temp;
}

function AuditData() {
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedAuditItem, setSelectedAuditItem] = useState(null);
    const [filterVersion, setFilterVersion] = useState(0);
    const [showDrawer, setShowDrawer] = useState(false);
    // Scope of the in-flight conditional-approval modal: 'server' | 'agent' | 'children'.
    const [conditionalScope, setConditionalScope] = useState('server');
    // For the 'children' scope, the actual child records the user selected in the drawer.
    const [conditionalChildren, setConditionalChildren] = useState(null);
    // When opening conditional approval from bulk selection (1+ MCP server rows); same approval applies to each.
    const [conditionalBulkRows, setConditionalBulkRows] = useState(null);

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const collectionsRegistryStatusMap = PersistStore(state => state.collectionsRegistryStatusMap)

    const isEndpointSecurity = isEndpointSecurityCategory();
    const definedTableTabs = isEndpointSecurity ? TABS_ENDPOINT_SECURITY : TABS_DEFAULT;

    const tableSelectedTab = PersistStore((state) => state.tableSelectedTab);
    const setTableSelectedTab = PersistStore((state) => state.setTableSelectedTab);
    const persistedTabId = tableSelectedTab[window.location.pathname];
    const allowedTabIds = definedTableTabs.map(t => func.getKeyFromName(t));
    const initialSelectedTab = persistedTabId && allowedTabIds.includes(persistedTabId)
        ? persistedTabId
        : TAB_IDS.MCP_SERVERS;
    const [selectedTab, setSelectedTab] = useState(initialSelectedTab);
    const [selected, setSelected] = useState(
        func.getTableTabIndexById(0, definedTableTabs, initialSelectedTab)
    );
    const [tabCounts, setTabCounts] = useState({ all: 0, mcp_servers: 0, skills: 0 });

    // Skills are flat AGENT_SKILL records in both modes; only the MCP tab differs
    // between endpoint-security (merged) and default (flat).
    const isSkillsTab = selectedTab === TAB_IDS.SKILLS;
    const useEndpointMergedView = isEndpointSecurity && !isSkillsTab;

    const filters = useMemo(() => {
        if (useEndpointMergedView) return filtersEndpointSecurity;
        return getTabFilters(selectedTab);
    }, [useEndpointMergedView, selectedTab]);

    const headings = useMemo(() => {
        if (useEndpointMergedView) return headingsEndpointSecurity;
        if (isSkillsTab) return headingsSkills;
        return headingsDefault;
    }, [useEndpointMergedView, isSkillsTab]);

    const handleSelectedTab = (selectedIndex) => {
        setSelected(selectedIndex);
        const tabId = func.getKeyFromName(definedTableTabs[selectedIndex] || definedTableTabs[0]);
        setSelectedTab(tabId);
        setTableSelectedTab({
            ...tableSelectedTab,
            [window.location.pathname]: tabId,
        });
    };

    const tableTabs = func.getTableTabsContent(
        definedTableTabs,
        func.getTabsCount(definedTableTabs, { _counts: tabCounts }),
        setSelectedTab,
        selectedTab,
        tabCounts[selectedTab] || 0
    );

    const [registryConfigured, setRegistryConfigured] = useState(false);
    const endpointRowCacheRef = useRef({});

    useEffect(() => {
        if (!isEndpointSecurity) return;
        let cancelled = false;
        (async () => {
            try {
                const res = await settingsApi.fetchMcpRegistries();
                const list = res?.mcpRegistries;
                const ok = Array.isArray(list) && list.length > 0;
                if (!cancelled) setRegistryConfigured(ok);
            } catch {
                if (!cancelled) setRegistryConfigured(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [isEndpointSecurity]);

    function disambiguateLabel(key, value) {
        switch (key) {
            case "type":
            case "markedBy":
            case "apiAccessTypes":
            case "aiAgent":
            case "mcpServer":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            case "collectionName":
                return func.convertToDisambiguateLabelObj(value, collectionsMap, 1)
            default:
                return value;
        }
    }

    const cascadeIdsForItem = (item) => (
        item?.type === 'mcp-server' && Array.isArray(item?.groupedHostCollectionIds)
            ? item.groupedHostCollectionIds
            : null
    );

    const getRowsForSelectedIds = (selectedIds) => {
        if (!Array.isArray(selectedIds) || selectedIds.length === 0) return [];
        return selectedIds.map((id) => endpointRowCacheRef.current[String(id)]).filter(Boolean);
    };

    const bulkIntersectionFlagsForIds = (selectedIds) => {
        const rows = getRowsForSelectedIds(selectedIds);
        if (!rows.length || rows.length !== selectedIds.length) {
            return { allow: false, block: false, conditional: false, add: false };
        }
        return intersectServerActionFlags(rows, {
            registryConfigured,
            addHandlerAvailable: true,
        });
    };

    const bulkUpdateServers = async (remarks, selectedIds) => {
        const rows = getRowsForSelectedIds(selectedIds);
        if (!rows.length) return;
        const hexIds = [];
        const cascades = [];
        for (const row of rows) {
            if (Array.isArray(row.groupedHexIds) && row.groupedHexIds.length) {
                row.groupedHexIds.forEach((h) => {
                    if (h) hexIds.push(String(h));
                });
            } else if (row.hexId) hexIds.push(String(row.hexId));
            const c = cascadeIdsForItem(row);
            if (c) {
                c.forEach((id) => {
                    if (id != null && !cascades.includes(id)) cascades.push(id);
                });
            }
        }
        const unique = [...new Set(hexIds)];
        if (!unique.length) return;
        try {
            await api.updateAuditData(unique[0], remarks, null, unique, cascades.length ? cascades : null, null);
            func.setToast(true, false, "Updated selected servers");
            window.location.reload();
        } catch (e) {
            func.setToast(true, true, "Bulk update failed");
        }
    };

    const bulkAddToRegistry = async (selectedIds) => {
        const rows = getRowsForSelectedIds(selectedIds);
        if (!rows.length) return;
        const names = [...new Set(rows.map((r) => r.mcpServerName).filter(Boolean))];
        try {
            await api.addMcpAllowlistUrls(names);
            func.setToast(true, false, "Added selected servers to MCP registry");
            window.location.reload();
        } catch (e) {
            func.setToast(true, true, "One or more registry adds failed");
        }
    };

    // Non-endpoint-security row actions (... menu)
    const GreenTickIcon = () => <Icon source={CircleTickMajor} tone="success" />;
    const GreenSettingsIcon = () => <Icon source={SettingsMajor} tone="success" />;
    const RedCancelIcon = () => <Icon source={CircleCancelMajor} tone="critical" />;

    const updateAuditData = async (hexId, remarks) => {
        await api.updateAuditData(hexId, remarks)
        window.location.reload();
    }

    const addToMcpAllowlist = async (mcpServerUrls) => {
        try {
            await api.addMcpAllowlistUrls(mcpServerUrls);
            const label = Array.isArray(mcpServerUrls) ? mcpServerUrls.join(", ") : mcpServerUrls;
            func.setToast(true, false, `${label} added to MCP allowed list successfully`);
            window.location.reload();
        } catch (error) {
            const errorMsg = error?.response?.data?.actionErrors?.[0] || "Failed to add to MCP allowed list";
            func.setToast(true, true, errorMsg);
        }
    };

    const getActionsList = (item) => {
        return [{ title: 'Actions', items: [
            {
                content: <span style={{ color: '#008060' }}>Conditional Approval</span>,
                icon: GreenSettingsIcon,
                onAction: () => { setSelectedAuditItem(item); setModalOpen(true); },
            },
            {
                content: <span style={{ color: '#008060' }}>Mark as resolved</span>,
                icon: GreenTickIcon,
                onAction: () => { updateAuditData(item.hexId, "Approved") },
            },
            ...(item.isEndpointSource ? [{
                content: <span style={{ color: '#008060' }}>Add to MCP Allowed List</span>,
                icon: GreenTickIcon,
                onAction: () => { addToMcpAllowlist(item.mcpServerName) },
            }] : []),
            {
                content: <span style={{ color: '#D72C0D' }}>Disapprove</span>,
                icon: RedCancelIcon,
                onAction: () => { updateAuditData(item.hexId, "Rejected") },
                destructive: true,
            },
        ]}]
    }

    // Endpoint-security: row click opens drawer
    const handleRowClick = (rowData) => {
        setSelectedAuditItem(rowData);
        setShowDrawer(true);
    }

    const handleAfterDrawerUpdate = (scope) => {
        // Server-scope mutations affect rows beyond the drawer; reload to resync the
        // parent table. Children mutations stay inside the drawer, which refetches
        // its own list — no reload needed.
        if (scope === 'server') {
            window.location.reload();
        }
    }

    const handleRequestConditional = (scope, item, selectedChildren) => {
        setConditionalBulkRows(null);
        setSelectedAuditItem(item);
        setConditionalScope(scope);
        setConditionalChildren(scope === 'children' ? selectedChildren : null);
        setModalOpen(true);
    }

    const handleRequestConditionalBulk = (rows) => {
        if (!Array.isArray(rows) || rows.length === 0) return;
        setConditionalBulkRows([...rows]);
        setSelectedAuditItem(rows[0]);
        setConditionalScope('server');
        setConditionalChildren(null);
        setModalOpen(true);
    };

    // Block/unblock skills — dual-writes to both api_info (legacy readers) and
    // mcp_audit_info (new source of truth). The backend reuses the existing
    // "remarks" convention ("Rejected" = blocked, "Approved" = allowed) used for
    // MCP-server flows. mcpHosts narrows the audit write to the agents shown on
    // this skill row; without it the audit write would touch every agent.
    const bulkUpdateSkills = async (isBlocked, selectedIds) => {
        const rows = getRowsForSelectedIds(selectedIds);
        const validRows = rows.filter(r => r && r.skillName &&
            ((Array.isArray(r.apiCollectionIds) && r.apiCollectionIds.length) ||
             (Array.isArray(r.aiAgents) && r.aiAgents.length)));
        if (!validRows.length) {
            func.setToast(true, true, "Could not resolve selected skills");
            return;
        }
        try {
            await Promise.all(validRows.map((r) =>
                collectionApi.updateSkillBlockStatus(
                    r.apiCollectionIds || [],
                    r.skillName,
                    isBlocked,
                    r.aiAgents || []
                )
            ));
            func.setToast(true, false, isBlocked ? "Blocked selected skills" : "Allowed selected skills");
            window.location.reload();
        } catch (e) {
            func.setToast(true, true, "Bulk update failed");
        }
    };

    const skillPromotedBulkActions = (selectedIds) => {
        if (selectedIds === "All" || !Array.isArray(selectedIds) || selectedIds.length === 0) {
            return [];
        }
        const n = selectedIds.length;
        const rows = getRowsForSelectedIds(selectedIds);
        const allBlocked = rows.length > 0 && rows.every(r => r?.isSkillBlocked);
        const allAllowed = rows.length > 0 && rows.every(r => !r?.isSkillBlocked);
        const countPhrase = `${n} selected skill${n === 1 ? "" : "s"}`;
        const allowLabel = n === 1 ? "Allow this skill" : `Allow ${countPhrase}`;
        const blockLabel = n === 1 ? "Block this skill" : `Block ${countPhrase}`;
        const actions = [];
        if (!allAllowed) {
            actions.push({
                content: allowLabel,
                onAction: () => bulkUpdateSkills(false, selectedIds),
            });
        }
        if (!allBlocked) {
            actions.push({
                content: blockLabel,
                destructive: true,
                onAction: () => bulkUpdateSkills(true, selectedIds),
            });
        }
        return actions;
    };

    const endpointPromotedBulkActions = (selectedIds) => {
        if (selectedIds === "All" || !Array.isArray(selectedIds) || selectedIds.length === 0) {
            return [];
        }
        const n = selectedIds.length;
        const countPhrase = `${n} selected server${n === 1 ? "" : "s"}`;
        const allowLabel = n === 1 ? "Allow this server" : `Allow ${countPhrase}`;
        const blockLabel = n === 1 ? "Block this server" : `Block ${countPhrase}`;
        const rows = getRowsForSelectedIds(selectedIds);
        const flags = bulkIntersectionFlagsForIds(selectedIds);
        const guard = (allowed, run) => {
            if (!allowed) {
                func.setToast(true, true, "This action is not available for all selected rows.");
                return;
            }
            run();
        };
        const actions = [
            {
                content: allowLabel,
                disabled: !flags.allow,
                onAction: () => guard(flags.allow, () => bulkUpdateServers("Approved", selectedIds)),
            },
            {
                content: blockLabel,
                destructive: true,
                disabled: !flags.block,
                onAction: () => guard(flags.block, () => bulkUpdateServers("Rejected", selectedIds)),
            },
        ];
        if (flags.conditional && rows.length === selectedIds.length) {
            actions.push({
                content: n === 1 ? "Conditionally allow this server" : "Conditionally allow selected servers",
                onAction: () => handleRequestConditionalBulk(rows),
            });
        }
        actions.push({
            content: "Add to MCP registry",
            disabled: !flags.add,
            onAction: () => guard(flags.add, () => bulkAddToRegistry(selectedIds)),
        });
        return actions;
    };

    const updateAuditDataWithConditions = async (_hexId, approvalData, _hexIds, _item) => {
        try {
            if (conditionalScope === 'children') {
                if (!Array.isArray(conditionalChildren) || conditionalChildren.length === 0) return
                await Promise.all(conditionalChildren.map((child) =>
                    api.updateAuditData(child.hexId, null, approvalData, child.groupedHexIds, null, null)
                ))
                setConditionalBulkRows(null);
                window.location.reload()
            } else {
                const bulkTargets =
                    Array.isArray(conditionalBulkRows) && conditionalBulkRows.length > 0
                        ? conditionalBulkRows
                        : selectedAuditItem
                          ? [selectedAuditItem]
                          : [];
                if (bulkTargets.length === 0) return;
                await Promise.all(
                    bulkTargets.map((item) =>
                        api.updateAuditData(
                            item?.hexId,
                            null,
                            approvalData,
                            item?.groupedHexIds,
                            cascadeIdsForItem(item),
                            null
                        )
                    )
                );
                setConditionalBulkRows(null);
                window.location.reload();
            }
        } catch (e) {
            func.setToast(true, true, 'Failed to apply conditional approval')
        }
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filterParams, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;

        // Skills tab reads raw AGENT_SKILL audit rows from /api/fetchSkillsData
        // (mcp_audit_info backed). One row per detection record — no client-side
        // grouping, server pagination is the source of truth.
        if (isSkillsTab) {
            try {
                const effectiveSortKey = sortKey || 'lastDetected';
                const res = await api.fetchSkillsData(skip, limit, effectiveSortKey, sortOrder, {}, queryValue || '');
                const auditRows = (res && Array.isArray(res.auditData)) ? res.auditData : [];

                // For block/allow we need apiCollectionIds. If the audit row already
                // carries hostCollectionId, use it. Otherwise resolve from mcpHost via
                // the already-loaded collectionsMap (hostName ends with ".<mcpHost>").
                const hostToIds = {};
                Object.entries(collectionsMap).forEach(([id, name]) => {
                    if (!name) return;
                    const s = String(name);
                    const dot = s.lastIndexOf('.');
                    if (dot < 0) return;
                    const host = s.slice(dot + 1);
                    if (!hostToIds[host]) hostToIds[host] = [];
                    hostToIds[host].push(Number(id));
                });

                endpointRowCacheRef.current = {};

                auditRows.forEach((r) => {
                    const skillName = r?.resourceName || '-';
                    const mcpHost = r?.mcpHost || '-';
                    const lastSeen = typeof r?.lastDetected === 'number' ? r.lastDetected : 0;
                    const isBlocked = r?.remarks === 'Rejected';
                    const markedBy = r?.markedBy || '-';
                    const rowHexId = r?.hexId || r?.id || `${skillName}::${mcpHost}::${lastSeen}`;
                    const collectionIds = (typeof r?.hostCollectionId === 'number' && r.hostCollectionId > 0)
                        ? [r.hostCollectionId]
                        : (hostToIds[mcpHost] || []);

                    const dataObj = {
                        id: rowHexId,
                        hexId: rowHexId,
                        skillName,
                        apiCollectionIds: collectionIds,
                        aiAgents: mcpHost !== '-' ? [mcpHost] : [],
                        isSkillBlocked: isBlocked,
                        lastSeen,
                        lastDetectedComp: lastSeen ? func.prettifyEpoch(lastSeen) : '-',
                        markedBy,
                        // AI agent name with logo — same pattern as MCP-server tab's aiAgentNameComp.
                        collectionsComp: mcpHost === '-' ? (
                            <Text>-</Text>
                        ) : (
                            <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                                <Box className="audit-table-icon">
                                    <CollectionIcon
                                        hostName={mcpHost}
                                        assetTagValue={mcpHost}
                                        displayName={mcpHost}
                                    />
                                </Box>
                                <Text>{mcpHost}</Text>
                            </HorizontalStack>
                        ),
                        statusComp: (
                            <Text variant="bodyMd" color={isBlocked ? 'critical' : 'success'}>
                                {isBlocked ? 'Blocked' : 'Allowed'}
                            </Text>
                        ),
                        isTerminal: false,
                        name: skillName,
                    };
                    ret.push(dataObj);
                    endpointRowCacheRef.current[String(dataObj.id)] = dataObj;
                });
                total = typeof res?.total === 'number' ? res.total : ret.length;
            } catch (e) {}
            setTabCounts((prev) => ({ ...prev, [selectedTab]: total }));
            setLoading(false);
            return { value: ret, total };
        }

        let finalFilters = {...filterParams}
        finalFilters['lastDetected'] = [startTimestamp, endTimestamp]

        if (useEndpointMergedView) {
            finalFilters['type'] = ['mcp-server']
        } else {
            // Tab-driven type scoping for the MCP tab; the All tab respects whatever
            // the user picked. (Skills tab is handled in its own branch above and never
            // reaches this code.)
            const userTypes = Array.isArray(filterParams['type']) ? filterParams['type'] : [];
            if (selectedTab === TAB_IDS.MCP_SERVERS) {
                finalFilters['type'] = userTypes.length
                    ? userTypes.filter(t => MCP_TYPES.includes(t))
                    : MCP_TYPES;
                if (finalFilters['type'].length === 0) {
                    finalFilters['type'] = MCP_TYPES;
                }
            }

            // hostCollectionId scoping mirrors the original un-tabbed audit page: scope to
            // the user's allowed collections. Backend RBAC (UsersCollectionsList) still
            // applies to legacy records without contextSource.
            finalFilters['hostCollectionId'] = (filterParams['collectionName'] || []).map(id => parseInt(id))
            if (finalFilters['hostCollectionId'].length === 0) {
                finalFilters['hostCollectionId'] = Object.keys(collectionsMap).map(id => parseInt(id))
            }
            delete finalFilters['collectionName']
        }

        try {
            const res = await api.fetchAuditData(sortKey, sortOrder, skip, limit, finalFilters, filterOperators, queryValue, useEndpointMergedView)
            if (res && res.auditData) {
                if (useEndpointMergedView || isSkillsTab) {
                    endpointRowCacheRef.current = {};
                }
                res.auditData.forEach((auditRecord) => {
                    let collectionName = "-";
                    if(collectionsMap[auditRecord?.hostCollectionId]){
                        collectionName = collectionsMap[auditRecord?.hostCollectionId];
                    } else if(auditRecord?.mcpHost !== null && auditRecord?.mcpHost !== ""){
                        collectionName = auditRecord?.mcpHost;
                    }
                    const collectionRegistryStatus = collectionsRegistryStatusMap[auditRecord?.hostCollectionId];
                    const dataObj = convertDataIntoTableFormat(
                        auditRecord,
                        collectionName,
                        collectionRegistryStatus
                    )
                    const override = getRegistryOverride(dataObj, registryConfigured);
                    if (override) {
                        dataObj.markedBy = override.markedBy;
                        dataObj.remarksComp = (
                            <Text variant="bodyMd" color="critical">{override.remarks}</Text>
                        );
                    }
                    ret.push(dataObj);
                    endpointRowCacheRef.current[String(dataObj.hexId || dataObj.id)] = dataObj;
                })
                total = res.total || 0;
            }
        } catch (error) {
        }

        setTabCounts((prev) => ({ ...prev, [selectedTab]: total }));

        setLoading(false);
        return {value: ret, total: total};
    }

    const fillFilters = async () => {
        // Always populate the default-mode filter choices: the Skills tab uses them in both modes.
        try {
            const res = await api.fetchAuditData('lastDetected', -1, 0, 1000, { lastDetected: [startTimestamp, endTimestamp] }, {}, '', false)
            const markedByUsers = new Set()
            if (res && Array.isArray(res.auditData)) {
                res.auditData.forEach((rec) => { if (rec?.markedBy) markedByUsers.add(rec.markedBy) })
            }
            filtersDefault[1].choices = Array.from(markedByUsers).sort().map(u => ({ label: u, value: u }))
        } catch (e) {}
        filtersDefault[3].choices = Object.entries(collectionsMap).map(([id, name]) => ({ label: name, value: id }));

        if (isEndpointSecurity) {
            try {
                const serversRes = await api.fetchAuditData(
                    'lastDetected', -1, 0, 1000,
                    { type: ['mcp-server'], lastDetected: [startTimestamp, endTimestamp] },
                    {}, '', true
                )
                const agents = new Set()
                const servers = new Set()
                const markedByUsers = new Set()
                if (serversRes && Array.isArray(serversRes.auditData)) {
                    serversRes.auditData.forEach((rec) => {
                        if (rec?.agentName) agents.add(rec.agentName)
                        if (rec?.serverName) servers.add(rec.serverName)
                        if (rec?.markedBy) markedByUsers.add(rec.markedBy)
                    })
                }
                filtersEndpointSecurity[0].choices = Array.from(markedByUsers).sort().map(u => ({ label: u, value: u }))
                filtersEndpointSecurity[2].choices = Array.from(agents).sort().map(a => ({ label: a, value: a }))
                filtersEndpointSecurity[3].choices = Array.from(servers).sort().map(s => ({ label: s, value: s }))
            } catch (e) {}
            setFilterVersion(v => v + 1)
        }
    }

    useEffect(() => {
        fillFilters()
    }, [collectionsMap, startTimestamp, endTimestamp])

    // Tab badge counts for the two tabs that aren't the currently-rendered table.
    // The active tab's count is set from fetchData's total; here we cheap-count the others
    // with limit=1 fetches so the badges stay accurate as the user navigates. In endpoint-
    // security mode the MCP count is fetched via the merged aggregation so the badge matches
    // the row count the user will actually see.
    useEffect(() => {
        const collectionIds = Object.keys(collectionsMap).map(id => parseInt(id));
        if (collectionIds.length === 0) return;
        let cancelled = false;

        const countFor = async (typeFilter, merge, scopeByCollection) => {
            const filterParams = {
                lastDetected: [startTimestamp, endTimestamp],
            };
            if (!merge && scopeByCollection) {
                filterParams.hostCollectionId = collectionIds;
            }
            if (typeFilter) filterParams.type = typeFilter;
            try {
                const res = await api.fetchAuditData('lastDetected', -1, 0, 1, filterParams, {}, '', !!merge);
                return res?.total || 0;
            } catch {
                return 0;
            }
        };

        const countSkills = async () => {
            try {
                const res = await api.fetchSkillsData(0, 1, 'lastDetected', -1, {}, '');
                return res?.total || 0;
            } catch {
                return 0;
            }
        };

        (async () => {
            const targets = [];
            // [tabId, typeFilter, merge, scopeByCollection]
            // All and MCP keep the original collection scoping for parity with the
            // pre-tabs behavior. Skills is sourced from api_info via a separate fetch.
            if (!isEndpointSecurity && selectedTab !== TAB_IDS.ALL) {
                targets.push([TAB_IDS.ALL, null, false, true]);
            }
            if (selectedTab !== TAB_IDS.MCP_SERVERS) {
                // In endpoint-security mode the MCP tab is merged; the count must also be merged.
                targets.push([TAB_IDS.MCP_SERVERS, isEndpointSecurity ? ['mcp-server'] : MCP_TYPES, isEndpointSecurity, true]);
            }
            const results = await Promise.all(targets.map(([, tf, mg, scope]) => countFor(tf, mg, scope)));
            if (cancelled) return;
            setTabCounts((prev) => {
                const next = { ...prev };
                targets.forEach(([tabId], i) => { next[tabId] = results[i]; });
                return next;
            });
            if (selectedTab !== TAB_IDS.SKILLS) {
                const skillsTotal = await countSkills();
                if (cancelled) return;
                setTabCounts((prev) => ({ ...prev, [TAB_IDS.SKILLS]: skillsTotal }));
            }
        })();

        return () => { cancelled = true; };
    }, [isEndpointSecurity, collectionsMap, startTimestamp, endTimestamp, selectedTab])

    const primaryActions = (
        <HorizontalStack gap={"2"}>
            <DateRangeFilter
                initialDispatch={currDateRange} 
                dispatch={(dateObj) => dispatchCurrDateRange({
                    type: "update", 
                    period: dateObj.period, 
                    title: dateObj.title, 
                    alias: dateObj.alias
                })}
            />
        </HorizontalStack>
    )

    return (
        <>
            <PageWithMultipleCards
            title={
                <Text as="div" variant="headingLg">
                Audit Data
              </Text>
            }
            isFirstPage={true}
            primaryAction={primaryActions}
            components = {[
                <GithubServerTable
                        key={startTimestamp + endTimestamp + (useEndpointMergedView ? filterVersion : filtersDefault[1].choices.length + filtersDefault[3].choices.length) + String(useEndpointMergedView) + String(registryConfigured) + `-${selectedTab}`}
                        headers={headings}
                        resourceName={resourceName}
                        appliedFilters={[]}
                        sortOptions={sortOptions}
                        disambiguateLabel={disambiguateLabel}
                        loading={loading}
                        fetchData={fetchData}
                        filters={filters}
                        hideQueryField={false}
                        getStatus={func.getTestResultStatus}
                        useNewRow={true}
                        condensedHeight={true}
                        pageLimit={20}
                        headings={headings}
                        tableTabs={tableTabs}
                        selected={selected}
                        onSelect={handleSelectedTab}
                        mode={IndexFiltersMode.Default}
                        {...(useEndpointMergedView
                            ? {
                                onRowClick: handleRowClick,
                                rowClickable: true,
                                selectable: true,
                                promotedBulkActions: endpointPromotedBulkActions,
                                filterStateUrl: "audit-data-endpoint",
                            }
                            : isSkillsTab
                                ? {
                                    selectable: true,
                                    promotedBulkActions: skillPromotedBulkActions,
                                }
                                : { getActions: (item) => getActionsList(item), hasRowActions: true }
                        )}
                />,
            ]}
            />

            {useEndpointMergedView ? (
                <>
                    <AuditDataDrawer
                        auditItem={selectedAuditItem}
                        show={showDrawer}
                        setShow={setShowDrawer}
                        startTimestamp={startTimestamp}
                        endTimestamp={endTimestamp}
                        onRequestConditional={handleRequestConditional}
                        onAfterUpdate={handleAfterDrawerUpdate}
                        onAddToAllowlist={(item) => addToMcpAllowlist(item?.mcpServerName)}
                        isEndpointSecurity={isEndpointSecurity}
                    />
                    <ConditionalApprovalModal
                        isOpen={modalOpen}
                        onClose={() => {
                            setModalOpen(false);
                            setConditionalChildren(null);
                            setConditionalBulkRows(null);
                        }}
                        onApprove={updateAuditDataWithConditions}
                        auditItem={selectedAuditItem}
                    />
                </>
            ) : isSkillsTab ? (
                <ConditionalApprovalModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false);
                        setConditionalBulkRows(null);
                    }}
                    onApprove={updateAuditDataWithConditions}
                    auditItem={selectedAuditItem}
                />
            ) : (
                <ConditionalApprovalModal
                    isOpen={modalOpen}
                    onClose={() => setModalOpen(false)}
                    onApprove={(hexId, approvalData) => {
                        api.updateAuditData(selectedAuditItem?.hexId, null, approvalData).then(() => window.location.reload())
                    }}
                    auditItem={selectedAuditItem}
                />
            )}
        </>
    )
}

export default AuditData
