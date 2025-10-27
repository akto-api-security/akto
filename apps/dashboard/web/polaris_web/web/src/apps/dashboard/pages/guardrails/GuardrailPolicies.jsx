import { useReducer, useState, useEffect } from "react";
import { Box, EmptySearchResult, HorizontalStack, VerticalStack, Popover, ActionList, Button, Icon, Badge, Text} from '@shopify/polaris';
import {CancelMinor, EditMinor, FileMinor, HideMinor, ViewMinor, ChecklistMajor} from '@shopify/polaris-icons';
import CreateGuardrailModal from "./components/CreateGuardrailModal";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";
import { produce } from "immer"
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";
import SessionStore from "../../../main/SessionStore";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { labelMap } from '../../../main/labelHelperMap';
import PersistStore from '@/apps/main/PersistStore';
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import guardRailData from "./dummyData";
import api from "./api";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import dayjs from "dayjs";

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
    text: "Created on",
    title: "Created on",
    value: "createdTs",
    type: CellType.TEXT,
    sortActive: true,
  },
  {
    text: "Updated on",
    title: "Updated on",
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

const sortOptions = [
  {
    label: "Created on",
    value: "createdTs asc",
    directionLabel: "Newest",
    sortKey: "createdTs",
    columnIndex: 4,
  },
  {
    label: "Created on",
    value: "createdTs desc",
    directionLabel: "Oldest",
    sortKey: "createdTs",
    columnIndex: 4,
  },
  {
    label: "Updated on",
    value: "updatedTs asc",
    directionLabel: "Newest",
    sortKey: "updatedTs",
    columnIndex: 5,
  },
  {
    label: "Updated on",
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

function GuardrailPolicies() {
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [policyData, setPolicyData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [editingPolicy, setEditingPolicy] = useState(null);
    const [isEditMode, setIsEditMode] = useState(false);

    // Load guardrail policies on component mount
    useEffect(() => {
        fetchGuardrailPolicies();
    }, []);

    const fetchGuardrailPolicies = async () => {
        setLoading(true);
        try {
            const response = await api.fetchGuardrailPolicies();
            if (response && response.guardrailPolicies) {
                const formattedPolicies = response.guardrailPolicies
                    .sort((a, b) => {
                        // First sort by active status (active first)
                        if (a.active !== b.active) {
                            return b.active - a.active;
                        }
                        // Then by timestamp (latest first)
                        return (b.updatedTimestamp || b.createdTimestamp) - (a.updatedTimestamp || a.createdTimestamp);
                    })
                    .map(policy => ({
                        id: policy.hexId,
                        policy: policy.name,
                        category: determineCategoryFromPolicy(policy),
                        status: policy.active ? "Active" : "Inactive",
                        statusWithSummary: generateStatusWithSummary(policy),
                        severity: policy.severity,
                        severityComp: (
                            <div className={`badge-wrapper-${policy.severity.toUpperCase()}`}>
                                <Badge size="small">{policy.severity.toUpperCase()}</Badge>
                            </div>
                        ),
                        createdTs: func.prettifyEpoch(policy.createdTimestamp),
                        updatedTs: func.prettifyEpoch(policy.updatedTimestamp),
                        createdBy: policy.createdBy || "N/A",
                        updatedBy: policy.updatedBy || "N/A",
                        originalData: policy
                    }));
                setPolicyData(formattedPolicies);
            }
        } catch (error) {
            func.setToast(true, true, "Failed to load guardrail policies");
        } finally {
            setLoading(false);
        }
    };

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

    // Helper function to get effective selected Agent servers with fallback logic
    const getEffectiveSelectedAgentServers = (policy) => {
        if (policy.selectedAgentServersV2?.length > 0) {
            return policy.selectedAgentServersV2;
        }
        // Convert old format to new format for compatibility
        if (policy.selectedAgentServers?.length > 0) {
            return policy.selectedAgentServers.map(serverId => ({
                id: serverId,
                name: serverId // ID as name for old data
            }));
        }
        return [];
    };

    const generateStatusWithSummary = (policy) => {
        const status = policy.active ? "Active" : "Inactive";
        
        // Create systematic details similar to audit remarks
        const details = [];
        
        // Content filtering details
        if (policy.contentFiltering?.harmfulCategories || policy.contentFiltering?.promptAttacks) {
            const filters = [];
            if (policy.contentFiltering.harmfulCategories) filters.push("Harmful Categories");
            if (policy.contentFiltering.promptAttacks) filters.push("Prompt Attacks");
            details.push({ label: "Content Filters", value: filters.join(", ") });
        }

        // Denied topics details
        if (policy.deniedTopics?.length > 0) {
            const topicNames = policy.deniedTopics.map(topic => topic.topic || topic.name).slice(0, 2);
            const moreCount = policy.deniedTopics.length > 2 ? ` +${policy.deniedTopics.length - 2} more` : '';
            details.push({ 
                label: "Denied Topics", 
                value: `${topicNames.join(", ")}${moreCount}` 
            });
        }

        // Word filters and regex patterns
        const wordFilters = [];
        if (policy.wordFilters?.profanity) wordFilters.push("Profanity");
        if (policy.wordFilters?.custom?.length > 0) wordFilters.push(`${policy.wordFilters.custom.length} Custom Words`);
        
        // Use effective regex patterns - prefer V2 format with behavior, fallback to old format
        const effectiveRegexPatterns = getEffectiveRegexPatterns(policy);
        if (effectiveRegexPatterns?.length > 0) {
            const blockCount = effectiveRegexPatterns.filter(r => r.behavior === 'block').length;
            const maskCount = effectiveRegexPatterns.filter(r => r.behavior === 'mask').length;
            const regexSummary = [];
            if (blockCount > 0) regexSummary.push(`${blockCount} Block`);
            if (maskCount > 0) regexSummary.push(`${maskCount} Mask`);
            wordFilters.push(`Regex: ${regexSummary.join(', ')}`);
        }
        if (wordFilters.length > 0) {
            details.push({ label: "Word Filters", value: wordFilters.join(", ") });
        }

        // PII types details
        if (policy.piiTypes?.length > 0) {
            const piiNames = policy.piiTypes.map(pii => pii.type).slice(0, 2);
            const moreCount = policy.piiTypes.length > 2 ? ` +${policy.piiTypes.length - 2} more` : '';
            details.push({ 
                label: "PII Detection", 
                value: `${piiNames.join(", ")}${moreCount}` 
            });
        }

        // Server configuration details using effective methods
        const serverDetails = [];
        const effectiveMcpServers = getEffectiveSelectedMcpServers(policy);
        const effectiveAgentServers = getEffectiveSelectedAgentServers(policy);
        
        if (effectiveMcpServers.length > 0) {
            serverDetails.push(`${effectiveMcpServers.length} MCP Server${effectiveMcpServers.length > 1 ? 's' : ''}`);
        }
        if (effectiveAgentServers.length > 0) {
            serverDetails.push(`${effectiveAgentServers.length} Agent Server${effectiveAgentServers.length > 1 ? 's' : ''}`);
        }
        if (serverDetails.length > 0) {
            details.push({ label: "Target Servers", value: serverDetails.join(", ") });
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
            // Refresh the page to ensure data gets updated on screen
            window.location.reload();
        } catch (error) {
            func.setToast(true, true, "Failed to update guardrail status");
            setLoading(false);
        }
    };

    const handleEditPolicy = (policy) => {
        setEditingPolicy(policy.originalData);
        setIsEditMode(true);
        setShowCreateModal(true);
    };

    const emptyStateMarkup = (
        <EmptySearchResult
          title={'No guardrail policy found'}
          withIllustration
        />
      );

    const rowClicked = async(data) => {
        handleEditPolicy(data)
    }


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

            // Determine severity based on configuration
            let severity = "Low";
            if (guardrailData.contentFilters?.harmfulCategories || guardrailData.contentFilters?.promptAttacks) {
                severity = "High";
            } else if (guardrailData.deniedTopics?.length > 0 || guardrailData.piiFilters?.length > 0) {
                severity = "Medium";
            }

            // Prepare GuardrailPolicies object for backend
            const guardrailPolicyObject = {
                name: guardrailData.name,
                description: guardrailData.description || '',
                blockedMessage: guardrailData.blockedMessage || '',
                severity: severity.toUpperCase(),
                selectedMcpServers: guardrailData.selectedMcpServers || [],
                selectedAgentServers: guardrailData.selectedAgentServers || [],
                // Add V2 fields for enhanced server data
                selectedMcpServersV2: guardrailData.selectedMcpServersV2 || [],
                selectedAgentServersV2: guardrailData.selectedAgentServersV2 || [],
                deniedTopics: guardrailData.deniedTopics || [],
                piiTypes: guardrailData.piiFilters || [],
                regexPatterns: guardrailData.regexPatterns || [],
                // Add V2 field for enhanced regex data
                regexPatternsV2: guardrailData.regexPatternsV2 || [],
                contentFiltering: guardrailData.contentFilters || {},
                applyOnResponse: guardrailData.applyOnResponse || false,
                applyOnRequest: guardrailData.applyOnRequest || false,
                active: true
            };

            // Prepare request payload with nested policy object
            const requestPayload = {
                policy: guardrailPolicyObject,
                hexId: isEditMode && guardrailData.hexId ? guardrailData.hexId : null
            };

            let response;
            if (isEditMode && guardrailData.hexId) {
                // Update existing policy
                response = await api.createGuardrailPolicy(requestPayload);
                if (response) {
                    func.setToast(true, false, "Guardrail updated successfully");
                }
            } else {
                // Create new policy
                response = await api.createGuardrailPolicy(requestPayload);
                if (response) {
                    func.setToast(true, false, "Guardrail created successfully");
                }
            }
            
            if (response) {
                setShowCreateModal(false);
                setEditingPolicy(null);
                setIsEditMode(false);
                // Refresh the page to ensure data gets updated on screen
                if (isEditMode) {
                    window.location.reload();
                } else {
                    // For create, just refresh the policy list
                    await fetchGuardrailPolicies();
                }
            }
        } catch (error) {
            func.setToast(true, true, isEditMode ? "Failed to update guardrail" : "Failed to create guardrail");
        } finally {
            setLoading(false);
        }
    };


      const components = [
        <GithubSimpleTable
            key={`policies-table-${policyData.length}`}
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={policyData}
            hideQueryField={true}
            hidePagination={true}
            showFooter={false}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}   
            onRowClick={rowClicked}    
            rowClickable={true} 
            getActions={getActionsList}
            hasRowActions={true}
            hardCodedKey={true}
            loading={loading}

        />,   
        <CreateGuardrailModal
            key={2}
            isOpen={showCreateModal}
            onClose={() => {
                setShowCreateModal(false);
                setEditingPolicy(null);
                setIsEditMode(false);
            }}
            onSave={handleCreateGuardrail}
            editingPolicy={editingPolicy}
            isEditMode={isEditMode}
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
            primaryAction={<Button primary onClick={() => setShowCreateModal(true)}>Create Guardrail</Button>}
            components={components}
        />
}

export default GuardrailPolicies;