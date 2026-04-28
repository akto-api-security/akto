import { useCallback, useEffect, useMemo, useState } from "react"
import {
    ActionList,
    Badge,
    Box,
    Button,
    HorizontalStack,
    IndexTable,
    Popover,
    Spinner,
    Text,
    VerticalStack,
    useIndexResourceState,
} from "@shopify/polaris"
import FlyLayout from "../../components/layouts/FlyLayout"
import AllowlistBadge from "../../components/shared/AllowlistBadge"
import api from "./api"
import func from "@/util/func"
import ComponentRiskAnalysisBadges from "./components/ComponentRiskAnalysisBadges"

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
            const collectionIds = Array.isArray(auditItem.groupedHostCollectionIds)
                && auditItem.groupedHostCollectionIds.length > 0
                ? auditItem.groupedHostCollectionIds
                : [auditItem.hostCollectionId]
            const res = await api.fetchAuditData(
                "lastDetected", -1, 0, 500,
                {
                    type: ["mcp-tool", "mcp-resource", "mcp-prompt"],
                    hostCollectionId: collectionIds,
                    lastDetected: [startTimestamp, endTimestamp],
                },
                {}, "", false, true
            )
            setChildren(res?.auditData || [])
        } catch (e) {
            setChildren([])
        } finally {
            setLoadingChildren(false)
        }
    }, [auditItem?.hexId, startTimestamp, endTimestamp])

    useEffect(() => {
        if (show && auditItem && isEndpointSecurity) {
            fetchChildren()
        } else if (!show) {
            setChildren([])
        }
    }, [show, auditItem?.hexId, fetchChildren, isEndpointSecurity])

    const resourceIDResolver = (item) => item.hexId
    const {
        selectedResources,
        allResourcesSelected,
        handleSelectionChange,
        clearSelection,
    } = useIndexResourceState(children, { resourceIDResolver })

    useEffect(() => {
        if (typeof clearSelection === "function") clearSelection()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [auditItem?.hexId])

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

    const updateSelectedChildren = async (remarks) => {
        if (!selectedResources || selectedResources.length === 0) return
        setBusy(true)
        try {
            await Promise.all(selectedResources.map(async (hexId) => {
                const child = children.find((c) => c.hexId === hexId)
                if (!child) return
                await api.updateAuditData(
                    child.hexId, remarks, null,
                    child.groupedHexIds, null, null, null
                )
            }))
            func.setToast(true, false, `${selectedResources.length} item${selectedResources.length === 1 ? "" : "s"} updated`)
            await fetchChildren()
            if (typeof clearSelection === "function") clearSelection()
            if (typeof onAfterUpdate === "function") onAfterUpdate("children")
        } catch (e) {
            func.setToast(true, true, "Failed to update selected items")
        } finally {
            setBusy(false)
        }
    }

    const requestChildrenConditional = () => {
        if (!selectedResources || selectedResources.length === 0) return
        if (typeof onRequestConditional !== "function") return
        const selectedItems = selectedResources
            .map((id) => children.find((c) => c.hexId === id))
            .filter(Boolean)
        onRequestConditional("children", auditItem, selectedItems)
    }

    // All bulk actions live behind one overflow trigger to keep the bar tidy.
    const bulkActions = useMemo(() => ([
        { content: "Allow", onAction: () => updateSelectedChildren("Approved") },
        {
            content: "Block",
            onAction: () => updateSelectedChildren("Rejected"),
            destructive: true,
        },
        { content: "Conditionally allow", onAction: requestChildrenConditional },
    ]), [selectedResources, children])

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
                    <Text variant="headingMd">{auditItem?.rawMcpServerName || auditItem?.resourceName}</Text>
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

    const childrenRows = children.map((child, index) => (
        <IndexTable.Row
            id={child.hexId}
            key={child.hexId}
            position={index}
            selected={selectedResources?.includes(child.hexId)}
        >
            <IndexTable.Cell>
                <Text variant="bodySm">{childTypeLabel(child.type)}</Text>
            </IndexTable.Cell>
            <IndexTable.Cell>
                <ComponentRiskAnalysisBadges componentRiskAnalysis={child?.componentRiskAnalysis} />
            </IndexTable.Cell>
            <IndexTable.Cell>
                <Text variant="bodySm">{child.resourceName}</Text>
            </IndexTable.Cell>
            <IndexTable.Cell>
                <Text variant="bodySm">{(child.apiAccessTypes || []).join(", ") || "-"}</Text>
            </IndexTable.Cell>
            <IndexTable.Cell>
                {child?.remarks
                    ? <Text variant="bodySm">{child.remarks}</Text>
                    : <Text variant="bodySm" color="critical" fontWeight="bold">Pending</Text>}
            </IndexTable.Cell>
            <IndexTable.Cell>
                <Text variant="bodySm">{child.markedBy || "-"}</Text>
            </IndexTable.Cell>
        </IndexTable.Row>
    ))

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
                    <IndexTable
                        resourceName={{ singular: "item", plural: "items" }}
                        itemCount={children.length}
                        selectedItemsCount={allResourcesSelected ? "All" : (selectedResources?.length || 0)}
                        onSelectionChange={handleSelectionChange}
                        selectable
                        bulkActions={bulkActions}
                        headings={[
                            { title: "Type" },
                            { title: "Risk Analysis" },
                            { title: "Name" },
                            { title: "Access Types" },
                            { title: "Remarks" },
                            { title: "Marked By" },
                        ]}
                    >
                        {childrenRows}
                    </IndexTable>
                )}
            </VerticalStack>
        </Box>
    )

    const titleComp = auditItem ? (
        <VerticalStack gap="1">
            <HorizontalStack gap="1" blockAlign="center">
                <Text variant="headingMd">
                    {isEndpointSecurity ? (auditItem?.rawMcpServerName || auditItem?.resourceName) : auditItem?.resourceName}
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
