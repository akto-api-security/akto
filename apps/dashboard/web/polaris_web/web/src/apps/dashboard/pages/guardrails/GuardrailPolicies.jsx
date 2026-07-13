import { useState, useEffect, useMemo, useCallback } from "react";
import { EmptySearchResult, VerticalStack, Button, Badge, Text, Tag, HorizontalStack, Popover, ActionList, Scrollable, Avatar, Box } from '@shopify/polaris';
import { CancelMinor, ViewMinor, ChecklistMajor } from '@shopify/polaris-icons';
import CreateGuardrailPage from "./components/CreateGuardrailPage";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import { getDashboardCategory, mapLabel, isEndpointSecurityCategory, getReportCategoryShortName, shortNameToCategory } from "../../../main/labelHelper";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import { ENTERPRISE_LICENSE_COMPLIANCE_ORIGIN } from "./components/enterpriseLicenseComplianceCatalog"
import api from "./api";
import { transformPolicyForBackend, SEVERITY, normalizeBehaviourValue } from "./utils";
import GUARDRAIL_PRESETS from "./guardrailPresets";
import PersistStore from '../../../main/PersistStore';
import {
    buildAgentFilterOptions,
    getApplicableAgentKeys,
    applyAgentFilterToRows,
    splitAgentServersV2,
} from "./serverTargetingUtils";

// Apply ?category= override synchronously before first render — mirrors ThreatReport.jsx/
// VulnerabilityReport.jsx. A deep-link opened in a fresh tab (e.g. from a violation's
// "Triggered by the <policy>" link) has no PersistStore session, so dashboardCategory
// defaults to API_SECURITY and every request here (fetchGuardrailPolicies etc.) goes out
// with the wrong x-context-source header, leaving the page stuck on "Loading...".
const categoryShortName = getReportCategoryShortName();
const categoryOverride = shortNameToCategory[categoryShortName];
if (categoryOverride) {
    PersistStore.getState().setDashboardCategory(categoryOverride);
}

const resourceName = {
  singular: "policy",
  plural: "policies",
};

const headings = [
  {
    text: "Severity",
    value: "severityComp",
    title: "Severity",
  },
  {
    text: "Policy",
    value: "policy",
    title: "Policy",
  },
  {
    text: "Category",
    value: "category",
    title: "Category",
  },
  {
    text: "Status",
    value: "statusWithSummary",
    title: "Status",
  },
  {
    text: "Created",
    title: "Created",
    value: "createdTs",
    type: CellType.TEXT,
    sortActive: true,
  },
  {
    text: "Updated",
    title: "Updated",
    value: "updatedTs",
    type: CellType.TEXT,
  },
  {
    text: "Created by",
    title: "Created by",
    value: "createdBy",
    type: CellType.TEXT,
  },
  {
    text: "Updated by",
    title: "Updated by", 
    value: "updatedBy",
    type: CellType.TEXT,
  },
  {
    title: '',
    type: CellType.ACTION,
  }
];

const agentFilterHeader = {
    value: 'agent',
    filterKey: 'agent',
    filterLabel: 'Agent',
    title: 'Agent',
    showFilter: true,
};

const sortOptions = [
  {
    label: "Created",
    value: "createdTs asc",
    directionLabel: "Newest",
    sortKey: "createdTs",
    columnIndex: 4,
  },
  {
    label: "Created",
    value: "createdTs desc",
    directionLabel: "Oldest",
    sortKey: "createdTs",
    columnIndex: 4,
  },
  {
    label: "Updated",
    value: "updatedTs asc",
    directionLabel: "Newest",
    sortKey: "updatedTs",
    columnIndex: 5,
  },
  {
    label: "Updated",
    value: "updatedTs desc",
    directionLabel: "Oldest",
    sortKey: "updatedTs",
    columnIndex: 5,
  },
  {
    label: "Status",
    value: "status asc",
    directionLabel: "Active first",
    sortKey: "status",
    columnIndex: 3,
  },
  {
    label: "Status",
    value: "status desc",
    directionLabel: "Inactive first",
    sortKey: "status",
    columnIndex: 3,
  },
];

const isSystemPolicy = (row) => (row.createdBy || "").toLowerCase().includes("system");

const sortPinnedSystemPolicies = (systemRows) =>
  [...systemRows].sort((a, b) => {
    if (a.status !== b.status) return a.status === "Active" ? -1 : 1;
    const tsA = a.originalData?.updatedTimestamp ?? a.originalData?.createdTimestamp ?? 0;
    const tsB = b.originalData?.updatedTimestamp ?? b.originalData?.createdTimestamp ?? 0;
    return tsB - tsA;
  });

function GuardrailPolicies() {
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [policyData, setPolicyData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [editingPolicy, setEditingPolicy] = useState(null);
    const [isEditMode, setIsEditMode] = useState(false);
    const [isPreset, setIsPreset] = useState(false);
    const [presetsPopoverActive, setPresetsPopoverActive] = useState(false);
    const [pendingPolicyName, setPendingPolicyName] = useState(null);

    const allCollections = PersistStore(state => state.allCollections);

    const agentFilterOptions = useMemo(
        () => buildAgentFilterOptions(allCollections),
        [allCollections]
    );

    const tablePolicyData = useMemo(() => (
        policyData.map(row => ({
            ...row,
            agent: getApplicableAgentKeys(row.originalData, allCollections, agentFilterOptions),
        }))
    ), [policyData, allCollections, agentFilterOptions]);

    // Agent filter is Argus-only; hide on Atlas.
    const tableHeaders = isEndpointSecurityCategory()
        ? headings
        : [...headings, agentFilterHeader];

    const policyName = new URLSearchParams(window.location.search).get("policy");

    // Load guardrail policies on component mount
    useEffect(() => {
        fetchGuardrailPolicies();
    }, []);

    useEffect(() => {
        if (policyName) {
            setPendingPolicyName(policyName);
        }
    }, [policyName]);

    useEffect(() => {
        if (!pendingPolicyName || loading) return;

        const match = policyData.find((row) => row.originalData?.name === pendingPolicyName);
        if (match) {
            handleEditPolicy(match);
        }
        setPendingPolicyName(null);
    }, [pendingPolicyName, policyData, loading]);

    const fetchGuardrailPolicies = async () => {
        setLoading(true);
        try {
            const policies = await api.fetchAllGuardrailPolicies();
            if (policies) {
                const showSystemTag = func.isDemoAccount();
                let formattedPolicies = policies.map(policy => ({
                        id: policy.hexId,
                        policy: showSystemTag && (policy.createdBy || "").toLowerCase().includes("system")
                            ? (
                                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                    <Tag>Default</Tag>
                                    <Text as="span" style={{ fontWeight: "medium" }}>{policy.name}</Text>
                                </HorizontalStack>
                            )
                            : policy.name,
                        category: determineCategoryFromPolicy(policy),
                        status: policy.active ? "Active" : "Inactive",
                        statusWithSummary: generateStatusWithSummary(policy),
                        severity: policy.severity || SEVERITY.MEDIUM.value,
                        severityComp: (
                            <div className={`badge-wrapper-${(policy.severity || SEVERITY.MEDIUM.value).toUpperCase()}`}>
                                <Badge size="small">{(policy.severity || SEVERITY.MEDIUM.value).toUpperCase()}</Badge>
                            </div>
                        ),
                        createdTs: func.prettifyEpoch(policy.createdTimestamp),
                        updatedTs: func.prettifyEpoch(policy.updatedTimestamp),
                        createdBy: policy.createdBy || "-",
                        updatedBy: policy.updatedBy || "-",
                        originalData: policy
                    }));
                if (!func.isDemoAccount()) {
                    formattedPolicies = formattedPolicies.sort((a, b) => {
                        if (a.status !== b.status) return a.status === "Active" ? -1 : 1;
                        return (b.originalData?.updatedTimestamp ?? b.originalData?.createdTimestamp ?? 0) -
                            (a.originalData?.updatedTimestamp ?? a.originalData?.createdTimestamp ?? 0);
                    });
                }
                setPolicyData(formattedPolicies);
            }
        } catch (error) {
            func.setToast(true, true, "Failed to load guardrail policies");
        } finally {
            setLoading(false);
        }
    };

    const modifyData = useCallback((filters, dataSortKey, sortOrder) => {
        const filteredRows = applyAgentFilterToRows(tablePolicyData, filters);
        const systemRows = filteredRows.filter(isSystemPolicy);
        const customRows = filteredRows.filter((row) => !isSystemPolicy(row));
        const pinned = sortPinnedSystemPolicies(systemRows);
        const custom =
            dataSortKey && customRows.length > 0
                ? func.sortFunc([...customRows], dataSortKey, sortOrder, false)
                : [...customRows].sort((a, b) => {
                        if (a.status !== b.status) return a.status === "Active" ? -1 : 1;
                        const tsA = a.originalData?.updatedTimestamp ?? a.originalData?.createdTimestamp ?? 0;
                        const tsB = b.originalData?.updatedTimestamp ?? b.originalData?.createdTimestamp ?? 0;
                        return tsB - tsA;
                    });
        return [...pinned, ...custom];
    }, [tablePolicyData]);

    const determineCategoryFromPolicy = (policy) => {
        if (policy.piiTypes?.length > 0) {
            return "Data Privacy";
        } else if (policy.deniedTopics?.length > 0) {
            return "Topic Filtering";
        }
        return "Content Safety";
    };

    // Helper function to get effective regex patterns with fallback logic
    const getEffectiveRegexPatterns = (policy) => {
        if (policy.regexPatternsV2?.length > 0) {
            return policy.regexPatternsV2;
        }
        // Convert old format to new format for compatibility
        if (policy.regexPatterns?.length > 0) {
            return policy.regexPatterns.map(pattern => ({
                pattern: pattern,
                behavior: 'block' // Default behavior for old data
            }));
        }
        return [];
    };

    // Helper function to get effective selected MCP servers with fallback logic
    const getEffectiveSelectedMcpServers = (policy) => {
        if (policy.selectedMcpServersV2?.length > 0) {
            return policy.selectedMcpServersV2;
        }
        // Convert old format to new format for compatibility
        if (policy.selectedMcpServers?.length > 0) {
            return policy.selectedMcpServers.map(serverId => ({
                id: serverId,
                name: serverId // ID as name for old data
            }));
        }
        return [];
    };

    // selectedAgentServersV2 stores both AI agent and browser-LLM entries merged together.
    const splitPolicyAgentServers = (rawEntries) =>
        splitAgentServersV2(rawEntries, allCollections);

    // Returns mcp, agent, and llm server lists for a policy in one pass.
    const getEffectiveServers = (policy) => {
        const raw = policy.selectedAgentServersV2?.length > 0
            ? policy.selectedAgentServersV2
            : (policy.selectedAgentServers || []).map(id => ({ id, name: id }));
        const { agents, llms } = splitPolicyAgentServers(raw);
        return {
            mcp: getEffectiveSelectedMcpServers(policy),
            agents,
            llms
        };
    };

    const generateStatusWithSummary = (policy) => {
        const status = policy.active ? "Active" : "Inactive";
        
        // Create systematic details similar to audit remarks
        const details = [];
        
        // Content filtering details
        if (policy.contentFiltering?.harmfulCategories || policy.contentFiltering?.promptAttacks || policy.contentFiltering?.code) {
            const filters = [];
            if (policy.contentFiltering.harmfulCategories) filters.push("Harmful Categories");
            if (policy.contentFiltering.promptAttacks) filters.push("Prompt Attacks");
            if (policy.contentFiltering.code) filters.push("Code Detection");
            details.push({ label: "Content Filters", value: filters.join(", ") });
        }

        // User-defined denied topics (excluding enterprise derived ones)
        const userDeniedTopics = (policy.deniedTopics || []).filter(t => t?.origin !== ENTERPRISE_LICENSE_COMPLIANCE_ORIGIN);
        if (userDeniedTopics.length > 0) {
            const names = userDeniedTopics.map(t => t.topic || t.name).slice(0, 2);
            const more = userDeniedTopics.length > 2 ? ` +${userDeniedTopics.length - 2} more` : '';
            details.push({ label: "Denied Topics", value: `${names.join(", ")}${more}` });
        }

        // Enterprise license compliance topics
        const enterpriseTopics = (policy.deniedTopics || []).filter(t => t?.origin === ENTERPRISE_LICENSE_COMPLIANCE_ORIGIN);
        if (enterpriseTopics.length > 0) {
            const names = enterpriseTopics.map(t => t.topic || t.name).slice(0, 1);
            const more = enterpriseTopics.length > 1 ? ` +${enterpriseTopics.length - 1} more` : '';
            details.push({ label: "Enterprise License Compliance Filters", value: `${names.join(", ")}${more}` });
        }

        // Word filters
        const wordFilters = [];
        if (policy.wordFilters?.profanity) wordFilters.push("Profanity");
        if (policy.wordFilters?.custom?.length > 0) wordFilters.push(`${policy.wordFilters.custom.length} Custom Words`);
        if (wordFilters.length > 0) {
            details.push({ label: "Word Filters", value: wordFilters.join(", ") });
        }

        // Sensitive information filters (PII types and regex patterns)
        const sensitiveInfoFilters = [];

        // PII types
        if (policy.piiTypes?.length > 0) {
            const piiParts = policy.piiTypes.slice(0, 2).map((pii) => {
                const t = pii.type;
                const m = pii.minMatchCount != null && Number(pii.minMatchCount) > 1
                    ? ` (${Number(pii.minMatchCount)}+)`
                    : "";
                return `${t}${m}`;
            });
            const moreCount = policy.piiTypes.length > 2 ? ` +${policy.piiTypes.length - 2} more PIIs` : '';
            sensitiveInfoFilters.push(`${piiParts.join(", ")}${moreCount}`);
        }

        // Regex patterns
        const effectiveRegexPatterns = getEffectiveRegexPatterns(policy);
        if (effectiveRegexPatterns?.length > 0) {
            const patternCount = effectiveRegexPatterns.length;
            sensitiveInfoFilters.push(`${patternCount} regex pattern${patternCount > 1 ? 's' : ''}`);
        }

        if (sensitiveInfoFilters.length > 0) {
            details.push({
                label: "Sensitive Data Filters",
                value: sensitiveInfoFilters.join(", ")
            });
        }

        // Server configuration details
        if (policy.applyToAllServers || policy.applyToAllServers == null) {
            details.push({ label: "Target Servers", value: "All servers" });
        } else {
            const { mcp, agents, llms } = getEffectiveServers(policy);
            const serverDetails = [];
            if (mcp.length > 0) serverDetails.push(`${mcp.length} MCP Server${mcp.length > 1 ? 's' : ''}`);
            if (agents.length > 0) serverDetails.push(`${agents.length} Agent${agents.length > 1 ? 's' : ''}`);
            if (llms.length > 0) serverDetails.push(`${llms.length} LLM${llms.length > 1 ? 's' : ''}`);
            if (serverDetails.length > 0) {
                details.push({ label: "Target Servers", value: serverDetails.join(", ") });
            }
        }

        // User targeting (Atlas only)
        if (isEndpointSecurityCategory()) {
            const targetTeams = policy.targetTeams || [];
            const targetRoles = policy.targetRoles || [];
            if (targetTeams.length === 0 && targetRoles.length === 0) {
                details.push({ label: "Target Users", value: "All users" });
            } else {
                const userParts = [];
                if (targetTeams.length > 0) userParts.push(`${targetTeams.length} Team${targetTeams.length !== 1 ? 's' : ''}`);
                if (targetRoles.length > 0) userParts.push(`${targetRoles.length} Role${targetRoles.length !== 1 ? 's' : ''}`);
                details.push({ label: "Target Users", value: userParts.join(", ") });
            }
        }

        // Application scope
        if (policy.applyOnRequest || policy.applyOnResponse) {
            const scope = [];
            if (policy.applyOnRequest) scope.push("Requests");
            if (policy.applyOnResponse) scope.push("Responses");
            details.push({ label: "Apply On", value: scope.join(", ") });
        }

        return (
            <VerticalStack gap="1">
                <Text variant="bodySm" fontWeight="medium">
                    <span style={{ color: policy.active ? '#008060' : '#D72C0D' }}>
                        {status}
                    </span>
                </Text>
                {details.length > 0 && (
                    <VerticalStack gap="0">
                        {details.slice(0, 3).map((detail, index) => (
                            <Text key={index} variant="bodySm" color="subdued" style={{ fontSize: "11px" }}>
                                <span style={{ fontWeight: "medium" }}>{detail.label}:</span> {detail.value}
                            </Text>
                        ))}
                        {details.length > 3 && (
                            <Text variant="bodySm" color="subdued" style={{ fontSize: "10px", fontStyle: "italic" }}>
                                +{details.length - 3} more configuration{details.length - 3 > 1 ? 's' : ''}
                            </Text>
                        )}
                    </VerticalStack>
                )}
            </VerticalStack>
        );
    };

    const handleToggleStatus = async (policy) => {
        try {
            setLoading(true);
            const newStatus = !policy.originalData.active;
            
            // Prepare request payload with nested policy object
            const updatedPolicy = {
                ...policy.originalData,
                active: newStatus
            };
            
            const requestPayload = {
                policy: updatedPolicy,
                hexId: policy.originalData.hexId
            };
            
            await api.createGuardrailPolicy(requestPayload);

            func.setToast(true, false, `Guardrail ${newStatus ? 'activated' : 'deactivated'} successfully`);
            await fetchGuardrailPolicies();
        } catch (error) {
            func.setToast(true, true, "Failed to update guardrail status");
        } finally {
            setLoading(false);
        }
    };

    const handleEditPolicy = (policy) => {
        setEditingPolicy(policy.originalData);
        setIsEditMode(true);
        setShowCreateModal(true);
    };

    const handleSelectPreset = (presetData) => {
        setPresetsPopoverActive(false);
        setEditingPolicy(presetData);
        setIsEditMode(false);
        setIsPreset(true);
        setShowCreateModal(true);
    };

    const emptyStateMarkup = (
        <EmptySearchResult
            title="No guardrail policy found"
            description="Try changing the filters"
            withIllustration
        />
    );

    const disambiguateLabel = (key, value) => func.convertToDisambiguateLabelObj(value, null, 2);

    const rowClicked = async(data) => {
        handleEditPolicy(data)
    }

    const promotedBulkActions = (selectedPolicies) => {
        return [
            {
                content: `Delete ${selectedPolicies.length} polic${selectedPolicies.length > 1 ? "ies" : "y"}`,
                onAction: async () => {
                    const deleteConfirmationMessage = `Are you sure you want to delete ${selectedPolicies.length} polic${selectedPolicies.length > 1 ? "ies" : "y"}?`;
                    func.showConfirmationModal(deleteConfirmationMessage, "Delete", async () => {
                        try {
                            await api.deleteGuardrailPolicies(selectedPolicies);
                            func.setToast(true, false, `${selectedPolicies.length} polic${selectedPolicies.length > 1 ? "ies" : "y"} deleted successfully`);
                            await fetchGuardrailPolicies();
                        } catch (error) {
                            func.setToast(true, true, "Failed to delete policies");
                        }
                    });
                },
            },
        ];
    };

    const getActionsList = (item) => {
        const isActive = item.originalData?.active;
        const actionItems = [{
            title: 'Actions',
            items: [
                {
                    content: isActive ?
                        <span style={{ color: '#D72C0D' }}>Disable policy</span> :
                        <span style={{ color: '#008060' }}>Enable policy</span>,
                    icon: isActive ? CancelMinor : ChecklistMajor,
                    onAction: () => handleToggleStatus(item),
                    destructive: isActive
                },
                {
                    content: 'View details',
                    icon: ViewMinor,
                    onAction: () => handleEditPolicy(item),
                }
            ]
        }];
        return actionItems;
    };

    const handleCreateGuardrail = async (guardrailData) => {
        
        try {
            setLoading(true);

            // Prepare GuardrailPolicies object for backend
            // Transform field names using shared utility (same as playground)
            guardrailData = transformPolicyForBackend(guardrailData);
            
            const guardrailPolicyObject = {
                name: guardrailData.name,
                description: guardrailData.description || '',
                blockedMessage: guardrailData.blockedMessage || '',
                severity: guardrailData.severity || SEVERITY.MEDIUM.value,
                selectedMcpServers: guardrailData.selectedMcpServers || [],
                selectedAgentServers: guardrailData.selectedAgentServers || [],
                // Add V2 fields for enhanced server data
                selectedMcpServersV2: guardrailData.selectedMcpServersV2 || [],
                selectedAgentServersV2: guardrailData.selectedAgentServersV2 || [],
                // Block-only host blocklist
                blockedHosts: guardrailData.blockedHosts || [],
                blockPersonalAccounts: guardrailData.blockPersonalAccounts || false,
                ignorePhrases: guardrailData.ignorePhrases || [],
                deniedTopics: guardrailData.deniedTopics || [],
                enterpriseLicenseComplianceCategories: guardrailData.enterpriseLicenseComplianceCategories || [],
                regexPatterns: guardrailData.regexPatterns || [],
                // Add V2 field for enhanced regex data
                regexPatternsV2: guardrailData.regexPatternsV2 || [],
                // Use transformed field names from shared utility
                piiTypes: guardrailData.piiTypes,
                contentFiltering: guardrailData.contentFiltering,
                // Add LLM policy if present
                ...(guardrailData.llmRule ? { llmRule: guardrailData.llmRule } : {}),
                // Add Base Prompt Rule if present
                ...(guardrailData.basePromptRule ? { basePromptRule: guardrailData.basePromptRule } : {}),
                // Add Gibberish Detection if present (same pattern as llmRule)
                ...(guardrailData.gibberishDetection ? { gibberishDetection: guardrailData.gibberishDetection } : {}),
                // Add Advanced Scanner Detections if present
                ...(guardrailData.anonymizeDetection ? { anonymizeDetection: guardrailData.anonymizeDetection } : {}),
                ...(guardrailData.banCodeDetection ? { banCodeDetection: guardrailData.banCodeDetection } : {}),
                ...(guardrailData.secretsDetection ? { secretsDetection: guardrailData.secretsDetection } : {}),
                ...(guardrailData.sentimentDetection ? { sentimentDetection: guardrailData.sentimentDetection } : {}),
                ...(guardrailData.tokenLimitDetection ? { tokenLimitDetection: guardrailData.tokenLimitDetection } : {}),
                applyToAllServers: guardrailData.applyToAllServers ?? true,
                targetTeams: guardrailData.targetTeams || [],
                targetRoles: guardrailData.targetRoles || [],
                applyOnResponse: guardrailData.applyOnResponse || false,
                applyOnRequest: guardrailData.applyOnRequest || false,
                behaviour: guardrailData.behaviour != null
                    ? normalizeBehaviourValue(guardrailData.behaviour)
                    : null,
                url: guardrailData.url || '',
                confidenceScore: guardrailData.confidenceScore || 0,
                active: true
            };

            // Prepare request payload with nested policy object
            const requestPayload = {
                policy: guardrailPolicyObject,
                hexId: isEditMode && guardrailData.hexId ? guardrailData.hexId : null
            };

            const wasEdit = isEditMode && guardrailData.hexId;
            await api.createGuardrailPolicy(requestPayload);
            func.setToast(
                true,
                false,
                wasEdit ? "Guardrail updated successfully" : "Guardrail created successfully"
            );
            setShowCreateModal(false);
            setEditingPolicy(null);
            setIsEditMode(false);
            setIsPreset(false);
            await fetchGuardrailPolicies();
        } catch (error) {
            func.setToast(true, true, isEditMode ? "Failed to update guardrail" : "Failed to create guardrail");
        } finally {
            setLoading(false);
        }
    };


    // If showing create/edit page, render the full page component
    if (showCreateModal) {
        return (
            <CreateGuardrailPage
                onClose={() => {
                    setShowCreateModal(false);
                    setEditingPolicy(null);
                    setIsEditMode(false);
                    setIsPreset(false);
                }}
                onSave={handleCreateGuardrail}
                editingPolicy={editingPolicy}
                isEditMode={isEditMode}
                isPreset={isPreset}
            />
        );
    }

    const components = [
        <GithubSimpleTable
            key={`policies-table-${tablePolicyData.length}-${agentFilterOptions.length}`}
            resourceName={resourceName}
            useNewRow={true}
            headers={tableHeaders}
            headings={headings}
            data={tablePolicyData}
            filterStateUrl="/dashboard/guardrails/policies/"
            disambiguateLabel={disambiguateLabel}
            hideQueryField={true}
            pageLimit={20}
            showFooter={false}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}
            onRowClick={rowClicked}
            rowClickable={true}
            getActions={getActionsList}
            hasRowActions={true}
            preventRowClickOnActions={true}
            hardCodedKey={true}
            loading={loading || Boolean(pendingPolicyName)}
            loadingText={"Loading guardrail policies..."}
            selectable={true}
            promotedBulkActions={promotedBulkActions}
            {...(func.isDemoAccount() && { customFilters: true, modifyData })}
        />
    ];


    return <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={mapLabel("Guardrail Policies", getDashboardCategory())}
                    tooltipContent={"Identify malicious requests with Akto's powerful guardrailing capabilities"}
                />
            }
            isFirstPage={true}
            primaryAction={
                <HorizontalStack gap="2">
                    <Popover
                        active={presetsPopoverActive}
                        activator={
                            <Button disclosure onClick={() => setPresetsPopoverActive(!presetsPopoverActive)}>
                                Presets
                            </Button>
                        }
                        onClose={() => setPresetsPopoverActive(false)}
                    >
                        <Popover.Pane>
                            <Scrollable style={{ maxHeight: "350px" }}>
                                <ActionList
                                    actionRole="menuitem"
                                    items={GUARDRAIL_PRESETS.map(preset => ({
                                        content: preset.label,
                                        prefix: (
                                            <Box>
                                                <Avatar
                                                    source={func.getComplianceIcon(preset.icon || preset.label)}
                                                    shape="square"
                                                    size="extraSmall"
                                                />
                                            </Box>
                                        ),
                                        onAction: () => handleSelectPreset(preset.data),
                                    }))}
                                />
                            </Scrollable>
                        </Popover.Pane>
                    </Popover>
                    <Button primary onClick={() => setShowCreateModal(true)}>Create Guardrail</Button>
                </HorizontalStack>
            }
            components={components}
        />
}

export default GuardrailPolicies;