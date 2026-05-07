import { useCallback, useEffect, useState } from "react"
import {
    ActionList,
    Box,
    Button,
    HorizontalStack,
    Popover,
    Spinner,
    Text,
    VerticalStack,
} from "@shopify/polaris"
import {
    ClockMinor,
    ProfileMinor,
    GlobeMinor,
    InfoMinor,
} from "@shopify/polaris-icons"
import FlyLayout from "../../components/layouts/FlyLayout"
import api from "./api"
import settingsApi from "../settings/api"
import func from "@/util/func"
import ComponentRiskAnalysisBadges from "./components/ComponentRiskAnalysisBadges"
import GithubSimpleTable from "../../components/tables/GithubSimpleTable"
import { CellType } from "../../components/tables/rows/GithubRow"
import GithubCell from "../../components/tables/cells/GithubCell"
import { getServerActionFlags, getRegistryOverride } from "./auditServerActionFlags"

const childResourceName = { singular: "item", plural: "items" }

const childHeadings = [
    { text: "Type", value: "typeComp", title: "Type" },
    { text: "Risk Analysis", value: "riskAnalysisComp", title: "Risk Analysis" },
    { text: "Name", value: "resourceName", title: "Name", type: CellType.TEXT },
    { text: "Access Types", value: "apiAccessTypesComp", title: "Access Types" },
    { text: "Remarks", value: "remarksComp", title: "Remarks" },
    { text: "Marked By", value: "markedBy", title: "Marked By", type: CellType.TEXT },
]

const childTypeLabel = (type) => {
    switch (type) {
        case "mcp-tool": return "Tool"
        case "mcp-resource": return "Resource"
        case "mcp-prompt": return "Prompt"
        default: return type || "-"
    }
}

const remarksTone = (remarks) => {
    if (remarks === "Rejected") return "critical"
    if (remarks === "Approved") return "success"
    if (remarks === "Conditionally Approved") return "attention"
    return undefined
}

// Children come back as merged docs ({_id: resourceName, groupedHexIds, …}) with
// remarks/markedBy/approvalConditions already collapsed server-side. Promote a stable
// hexId so the existing rendering and update call sites stay unchanged.
const normalizeChildRow = (raw) => {
    if (!raw) return raw
    const hexId = Array.isArray(raw.groupedHexIds) && raw.groupedHexIds.length > 0
        ? raw.groupedHexIds[0]
        : raw._id
    return { ...raw, hexId }
}

function ActionDropdown({ label, items, loading, disabled }) {
    const [open, setOpen] = useState(false)
    const wrapped = items.map((it) => ({
        ...it,
        onAction: () => {
            setOpen(false)
            if (it.disabled) return
            if (typeof it.onAction === "function") it.onAction()
        },
    }))
    return (
        <Popover
            active={open}
            activator={
                <Button
                    onClick={() => setOpen((p) => !p)}
                    disclosure
                    loading={loading}
                    disabled={disabled}
                >
                    {label}
                </Button>
            }
            onClose={() => setOpen(false)}
            autofocusTarget="first-node"
            preferredAlignment="left"
        >
            <ActionList actionRole="menuitem" items={wrapped} />
        </Popover>
    )
}

function AuditDataDrawer({
    auditItem,
    show,
    setShow,
    startTimestamp,
    endTimestamp,
    onRequestConditional,
    onAfterUpdate,
    onAddToAllowlist,
    isEndpointSecurity,
}) {
    const [children, setChildren] = useState([])
    const [loadingChildren, setLoadingChildren] = useState(false)
    const [busy, setBusy] = useState(false)
    const [registryConfigured, setRegistryConfigured] = useState(false)

    useEffect(() => {
        if (!show || !isEndpointSecurity) return
        let cancelled = false
        ;(async () => {
            try {
                const res = await settingsApi.fetchMcpRegistries()
                const list = res?.mcpRegistries
                const ok = Array.isArray(list) && list.length > 0
                if (!cancelled) setRegistryConfigured(ok)
            } catch {
                if (!cancelled) setRegistryConfigured(false)
            }
        })()
        return () => { cancelled = true }
    }, [show, isEndpointSecurity])

    const fetchChildren = useCallback(async () => {
        if (!auditItem) return
        setLoadingChildren(true)
        try {
            const agentName = auditItem.aiAgentName && auditItem.aiAgentName !== "-" ? auditItem.aiAgentName : null
            const serverName = auditItem.mcpServerName && auditItem.mcpServerName !== "-" ? auditItem.mcpServerName : null
            const res = await api.fetchAuditData(
                "lastDetected", -1, 0, 500,
                {
                    type: ["mcp-tool", "mcp-resource", "mcp-prompt"],
                    lastDetected: [startTimestamp, endTimestamp],
                },
                {}, "", false, agentName, serverName
            )
            setChildren((res?.auditData || []).map(normalizeChildRow))
        } catch (e) {
            setChildren([])
        } finally {
            setLoadingChildren(false)
        }
    }, [auditItem?.hexId, auditItem?.aiAgentName, auditItem?.mcpServerName, startTimestamp, endTimestamp])

    useEffect(() => {
        if (show && auditItem && isEndpointSecurity) {
            fetchChildren()
        } else if (!show) {
            setChildren([])
        }
    }, [show, auditItem?.hexId, fetchChildren, isEndpointSecurity])

    const cascadeIds = auditItem?.type === "mcp-server"
        && Array.isArray(auditItem?.groupedHostCollectionIds)
        ? auditItem.groupedHostCollectionIds
        : null

    const updateServer = async (remarks) => {
        if (!auditItem) return
        setBusy(true)
        try {
            await api.updateAuditData(
                auditItem.hexId, remarks, null,
                auditItem.groupedHexIds, cascadeIds, null
            )
            func.setToast(true, false, `Server ${remarks === "Approved" ? "allowed" : remarks === "Rejected" ? "blocked" : "updated"}`)
            if (typeof onAfterUpdate === "function") onAfterUpdate("server")
        } catch (e) {
            func.setToast(true, true, "Failed to update server")
        } finally {
            setBusy(false)
        }
    }

    const blockForAllAgents = async () => {
        if (!auditItem) return
        const serverName = auditItem.mcpServerName && auditItem.mcpServerName !== "-" ? auditItem.mcpServerName : null
        if (!serverName) {
            func.setToast(true, true, "Cannot determine server name")
            return
        }
        setBusy(true)
        try {
            await api.updateAuditData(
                auditItem.hexId, "Rejected", null,
                auditItem.groupedHexIds, cascadeIds, serverName
            )
            func.setToast(true, false, "Server blocked for all agents")
            if (typeof onAfterUpdate === "function") onAfterUpdate("server")
        } catch (e) {
            func.setToast(true, true, "Failed to block server for all agents")
        } finally {
            setBusy(false)
        }
    }

    const updateSelectedChildren = async (remarks, selectedHexIds) => {
        if (!Array.isArray(selectedHexIds) || selectedHexIds.length === 0) return
        setBusy(true)
        try {
            await Promise.all(selectedHexIds.map(async (hexId) => {
                const child = children.find((c) => c.hexId === hexId)
                if (!child) return
                await api.updateAuditData(
                    child.hexId, remarks, null,
                    child.groupedHexIds, null, null
                )
            }))
            func.setToast(true, false, `${selectedHexIds.length} item${selectedHexIds.length === 1 ? "" : "s"} updated`)
            await fetchChildren()
            if (typeof onAfterUpdate === "function") onAfterUpdate("children")
        } catch (e) {
            func.setToast(true, true, "Failed to update selected items")
        } finally {
            setBusy(false)
        }
    }

    const requestChildrenConditional = (selectedHexIds) => {
        if (!Array.isArray(selectedHexIds) || selectedHexIds.length === 0) return
        if (typeof onRequestConditional !== "function") return
        const selectedItems = selectedHexIds
            .map((id) => children.find((c) => c.hexId === id))
            .filter(Boolean)
        onRequestConditional("children", auditItem, selectedItems)
    }

    // GithubSimpleTable owns the selection state and passes the selected ids in.
    const promotedBulkActions = (selectedHexIds) => ([
        { content: "Allow", onAction: () => updateSelectedChildren("Approved", selectedHexIds) },
        {
            content: "Block",
            onAction: () => updateSelectedChildren("Rejected", selectedHexIds),
            destructive: true,
        },
        { content: "Conditionally allow", onAction: () => requestChildrenConditional(selectedHexIds) },
    ])

    // Registry-precedence override: when registry is configured but parent server
    // is not verified, every child inherits Rejected/MCP Registry display.
    const parentOverride = getRegistryOverride(auditItem, registryConfigured)

    // Pre-format children into table-ready rows. id mirrors hexId so the table
    // selection state references the same key our update helpers look up by.
    const childRows = children.map((child) => ({
        ...child,
        id: child.hexId,
        typeComp: <Text variant="bodySm">{childTypeLabel(child.type)}</Text>,
        riskAnalysisComp: <ComponentRiskAnalysisBadges componentRiskAnalysis={child?.componentRiskAnalysis} />,
        apiAccessTypesComp: (child.apiAccessTypes || []).join(", ") || "-",
        remarksComp: parentOverride
            ? <Text variant="bodySm" color="critical">{parentOverride.remarks}</Text>
            : (child?.remarks
                ? <Text variant="bodySm">{child.remarks}</Text>
                : <Text variant="bodySm">Approved</Text>),
        markedBy: parentOverride ? parentOverride.markedBy : (child.markedBy || "-"),
    }))

    const flags = getServerActionFlags(auditItem, {
        registryConfigured,
        addHandlerAvailable: typeof onAddToAllowlist === "function",
    })

    const serverActionItems = [
        {
            content: "Allow this server",
            onAction: () => updateServer("Approved"),
            disabled: !flags.allow,
        },
        {
            content: "Block this server",
            destructive: true,
            onAction: () => updateServer("Rejected"),
            disabled: !flags.block,
        },
        {
            content: "Block for all agents",
            destructive: true,
            onAction: () => blockForAllAgents(),
        },
        {
            content: "Conditionally allow this server",
            onAction: () => {
                if (typeof onRequestConditional === "function") {
                    onRequestConditional("server", auditItem, null)
                }
            },
            disabled: !flags.conditional,
        },
        {
            content: "Add to MCP registry",
            onAction: () => {
                if (typeof onAddToAllowlist === "function") onAddToAllowlist(auditItem)
            },
            disabled: !flags.add,
        },
    ]

    const childrenSection = (
        <Box>
            <VerticalStack gap="3">
                <Text variant="headingMd">Tools, resources & prompts</Text>
                {loadingChildren ? (
                    <HorizontalStack gap="2" align="center">
                        <Spinner size="small" />
                        <Text>Loading...</Text>
                    </HorizontalStack>
                ) : children.length === 0 ? (
                    <Text color="subdued">No tools, resources, or prompts found for this MCP server.</Text>
                ) : (
                    <GithubSimpleTable
                        key={`children-${auditItem?.hexId || ""}-${children.length}`}
                        resourceName={childResourceName}
                        useNewRow={true}
                        condensedHeight={true}
                        headers={childHeadings}
                        headings={childHeadings}
                        data={childRows}
                        selectable={true}
                        promotedBulkActions={promotedBulkActions}
                        disambiguateLabel={(_key, value) => value}
                        hideQueryField={true}
                        hidePagination={true}
                        showFooter={false}
                        pageLimit={children.length}
                        // Isolate the table's persisted filter scope from the main page —
                        // otherwise it inherits the audit table's pageFiltersMap (keyed by
                        // window.location.pathname) and re-applies filters like apiAccessTypes
                        // client-side, hiding every child whose value doesn't match.
                        filterStateUrl={`audit-drawer/${auditItem?.aiAgentName || ""}/${auditItem?.mcpServerName || ""}`}
                    />
                )}
            </VerticalStack>
        </Box>
    )

    const auditCellData = auditItem ? {
        resourceName: auditItem.resourceName,
        typeBadge: [auditItem.type].filter(Boolean),
        remarksBadge: [parentOverride ? parentOverride.remarks : (auditItem.remarks || "Approved")],
        lastDetected: auditItem.lastDetected ? func.prettifyEpoch(auditItem.lastDetected) : "-",
        updatedTimestamp: auditItem.updatedTimestamp ? func.prettifyEpoch(auditItem.updatedTimestamp) : "-",
        markedBy: parentOverride ? parentOverride.markedBy : (auditItem.markedBy || "-"),
        mcpHost: auditItem.mcpHost || "-",
        aiAgentName: (isEndpointSecurity && auditItem.aiAgentName && auditItem.aiAgentName !== "-")
            ? auditItem.aiAgentName
            : undefined,
    } : null

    const auditCellHeaders = [
        { value: "resourceName", itemOrder: 1 },
        { value: "typeBadge", itemOrder: 2 },
        { value: "remarksBadge", itemOrder: 2 },
        { value: "lastDetected", itemOrder: 3, icon: ClockMinor, iconTooltip: "Last detected" },
        { value: "updatedTimestamp", itemOrder: 3, icon: ClockMinor, iconTooltip: "Last updated" },
        { value: "markedBy", itemOrder: 3, icon: ProfileMinor, iconTooltip: "Marked by" },
        { value: "mcpHost", itemOrder: 3, icon: GlobeMinor, iconTooltip: "Collection" },
        { value: "aiAgentName", itemOrder: 3, icon: InfoMinor, iconTooltip: "AI Agent" },
    ]

    const auditGetStatus = (item) => remarksTone(item)

    const recordDetailBody = auditItem ? (
        <HorizontalStack gap="2" align="space-between" wrap={false}>
            <GithubCell
                width="100%"
                nameWidth="38vw"
                data={auditCellData}
                headers={auditCellHeaders}
                getStatus={auditGetStatus}
            />
            {isEndpointSecurity && (
                <ActionDropdown
                    label="Action"
                    items={serverActionItems}
                    loading={busy}
                />
            )}
        </HorizontalStack>
    ) : null

    // Bundle every section into a single FlyLayout slot so we control the gaps
    // ourselves; FlyLayout's stack has no spacing between siblings by default.
    const drawerBody = isEndpointSecurity ? (
        <VerticalStack gap="5">
            {recordDetailBody}
            {childrenSection}
        </VerticalStack>
    ) : recordDetailBody

    return (
        <FlyLayout
            show={show}
            setShow={setShow}
            titleComp={"Agent Details"}
            components={[drawerBody]}
        />
    )
}

export default AuditDataDrawer
