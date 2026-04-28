import { useCallback, useEffect, useState } from "react"
import {
    ActionList,
    Badge,
    Box,
    Button,
    HorizontalStack,
    Popover,
    Spinner,
    Text,
    VerticalStack,
} from "@shopify/polaris"
import FlyLayout from "../../components/layouts/FlyLayout"
import AllowlistBadge from "../../components/shared/AllowlistBadge"
import api from "./api"
import func from "@/util/func"
import ComponentRiskAnalysisBadges from "./components/ComponentRiskAnalysisBadges"
import GithubSimpleTable from "../../components/tables/GithubSimpleTable"
import { CellType } from "../../components/tables/rows/GithubRow"

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
                auditItem.groupedHexIds, cascadeIds, null, null
            )
            func.setToast(true, false, `Server ${remarks === "Approved" ? "allowed" : remarks === "Rejected" ? "blocked" : "updated"}`)
            if (typeof onAfterUpdate === "function") onAfterUpdate("server")
        } catch (e) {
            func.setToast(true, true, "Failed to update server")
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
                    child.groupedHexIds, null, null, null
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

    // Pre-format children into table-ready rows. id mirrors hexId so the table
    // selection state references the same key our update helpers look up by.
    const childRows = children.map((child) => ({
        ...child,
        id: child.hexId,
        typeComp: <Text variant="bodySm">{childTypeLabel(child.type)}</Text>,
        riskAnalysisComp: <ComponentRiskAnalysisBadges componentRiskAnalysis={child?.componentRiskAnalysis} />,
        apiAccessTypesComp: (child.apiAccessTypes || []).join(", ") || "-",
        remarksComp: child?.remarks
            ? <Text variant="bodySm">{child.remarks}</Text>
            : <Text variant="bodySm" color="critical" fontWeight="bold">Pending</Text>,
        markedBy: child.markedBy || "-",
    }))

    const serverActionItems = [
        { content: "Allow this server", onAction: () => updateServer("Approved") },
        {
            content: "Block this server",
            destructive: true,
            onAction: () => updateServer("Rejected"),
        },
        {
            content: "Conditionally allow this server",
            onAction: () => {
                if (typeof onRequestConditional === "function") {
                    onRequestConditional("server", auditItem, null)
                }
            },
        },
        ...(auditItem?.isEndpointSource && typeof onAddToAllowlist === "function" ? [{
            content: "Add to MCP Allowed List",
            onAction: () => onAddToAllowlist(auditItem),
        }] : []),
    ]

    const serverSection = auditItem ? (
        <Box padding="4" background="bg-subdued" borderRadius="2">
            <HorizontalStack align="space-between" blockAlign="center" gap="4">
                <VerticalStack gap="2">
                    <Text variant="headingMd">{auditItem?.mcpServerName || auditItem?.resourceName}</Text>
                    <HorizontalStack gap="2" blockAlign="center">
                        {auditItem?.aiAgentName && auditItem.aiAgentName !== "-" && (
                            <Badge tone="info">Agent: {auditItem.aiAgentName}</Badge>
                        )}
                        <Badge tone={remarksTone(auditItem?.remarks)}>
                            {auditItem?.remarks || "Pending"}
                        </Badge>
                        <Text color="subdued" variant="bodySm">
                            Last detected: {func.prettifyEpoch(auditItem?.lastDetected)}
                        </Text>
                    </HorizontalStack>
                </VerticalStack>
                <ActionDropdown
                    label="Action for this server"
                    items={serverActionItems}
                    loading={busy}
                />
            </HorizontalStack>
        </Box>
    ) : null

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

    const titleComp = auditItem ? (
        <VerticalStack gap="1">
            <HorizontalStack gap="1" blockAlign="center">
                <Text variant="headingMd">
                    {isEndpointSecurity ? (auditItem?.mcpServerName || auditItem?.resourceName) : auditItem?.resourceName}
                </Text>
                {isEndpointSecurity && auditItem?.verified && <AllowlistBadge />}
            </HorizontalStack>
            {isEndpointSecurity && auditItem?.aiAgentName && auditItem.aiAgentName !== "-" && (
                <Text variant="bodySm" color="subdued">
                    AI Agent: {auditItem.aiAgentName}
                </Text>
            )}
        </VerticalStack>
    ) : (
        <Text variant="headingMd">Audit Data</Text>
    )

    const recordDetailBody = auditItem ? (
        <VerticalStack gap="4">
            <Box padding="4" background="bg-subdued" borderRadius="2">
                <VerticalStack gap="3">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <VerticalStack gap="1">
                            <Text variant="headingMd">{auditItem.resourceName}</Text>
                            <HorizontalStack gap="2" blockAlign="center">
                                <Badge>{auditItem.type}</Badge>
                                <Badge tone={remarksTone(auditItem.remarks)}>
                                    {auditItem.remarks || "Pending"}
                                </Badge>
                            </HorizontalStack>
                        </VerticalStack>
                        <ActionDropdown
                            label="Action"
                            items={serverActionItems}
                            loading={busy}
                        />
                    </HorizontalStack>
                    {[
                        { label: "Last Detected", value: func.prettifyEpoch(auditItem.lastDetected) },
                        { label: "Updated", value: func.prettifyEpoch(auditItem.updatedTimestamp) },
                        { label: "Access Types", value: (auditItem.apiAccessTypes || []).join(", ") || "-" },
                        { label: "Marked By", value: auditItem.markedBy || "-" },
                        { label: "Collection", value: auditItem.mcpHost || "-" },
                    ].map(({ label, value }) => (
                        <HorizontalStack key={label} gap="2">
                            <Text variant="bodySm" color="subdued" fontWeight="medium">{label}:</Text>
                            <Text variant="bodySm">{value}</Text>
                        </HorizontalStack>
                    ))}
                </VerticalStack>
            </Box>
        </VerticalStack>
    ) : null

    // Bundle every section into a single FlyLayout slot so we control the gaps
    // ourselves; FlyLayout's stack has no spacing between siblings by default.
    const drawerBody = isEndpointSecurity ? (
        <VerticalStack gap="5">
            {serverSection}
            {childrenSection}
        </VerticalStack>
    ) : recordDetailBody

    return (
        <FlyLayout
            show={show}
            setShow={setShow}
            titleComp={titleComp}
            components={[drawerBody]}
        />
    )
}

export default AuditDataDrawer
