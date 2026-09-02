import { useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import PersistStore from "../../../../main/PersistStore";
import func from "../../../../../util/func";
import { Badge, IndexFiltersMode, Avatar, Box, Button, ChoiceList, HorizontalStack, Modal, Text, TextField, VerticalStack } from "@shopify/polaris";
import SessionStore from "../../../../main/SessionStore";
import { labelMap } from "../../../../main/labelHelperMap";
import { formatActorId, extractRuleViolated, extractBehaviour, getBehaviourTone, resolveComplianceClauseMap, mergePolicyComplianceMap, parseStoredRiskScore, parseStoredReason, truncateToWords } from "../utils/formatUtils";
import threatDetectionRequests from "../api";
import { LABELS } from "../constants";
import { isAgenticSecurityCategory, isEndpointSecurityCategory, isApiSecurityCategory } from "../../../../main/labelHelper";
import { fetchEndpointShieldUsernameMap, getUsernameForCollection } from "../../observe/api_collections/endpointShieldHelper";
import IpReputationScore from "./IpReputationScore";
import guardrailApi from "../../guardrails/api";
import { buildApprovedByPolicy, isServerApproved } from "../../guardrails/utils";
import AdvancedPayloadSearch from "../../guardrails/violations/AdvancedPayloadSearch";
import { addAdvancedFilter, filterFromEditorSelection, toLatestApiOrigRegex } from "../../guardrails/violations/attributeSearch";
import { deriveAgenticType, extractEvidenceText } from "../../guardrails/violations/violationsData";

const resourceName = {
  singular: "activity",
  plural: "activities",
};

const RISK_SCORE_OPS = [
  { label: "Equals", value: "equals" },
  { label: "Greater than", value: "greaterThan" },
  { label: "Less than", value: "lessThan" },
];

const RISK_SCORE_OP_LABELS = {
  equals: "Equals",
  greaterThan: "Greater than",
  lessThan: "Less than",
};

function parseRiskScoreFilter(values) {
  const raw = Array.isArray(values) ? values[0] : values;
  if (!raw) return { operator: "greaterThan", amount: "" };
  const text = String(raw);
  const idx = text.indexOf(":");
  if (idx === -1) return { operator: RISK_SCORE_OP_LABELS[text] ? text : "greaterThan", amount: "" };
  return { operator: text.slice(0, idx), amount: text.slice(idx + 1) };
}

function encodeRiskScoreFilter(operator, amount) {
  if (!operator || amount === "" || amount == null) return [];
  const n = Number(amount);
  if (!Number.isFinite(n)) return [];
  return [`${operator}:${amount}`];
}

function RiskScoreFilterControl({ selected, onChange, onClose }) {
  const selectedKey = Array.isArray(selected) ? selected.join(",") : String(selected || "");
  const initial = parseRiskScoreFilter(selected);
  const [operator, setOperator] = useState(initial.operator);
  const [amount, setAmount] = useState(initial.amount);

  useEffect(() => {
    const next = parseRiskScoreFilter(selectedKey ? selectedKey.split(",") : []);
    setOperator(next.operator);
    setAmount(next.amount);
  }, [selectedKey]);

  const commit = (nextOp, nextAmount) => {
    const encoded = encodeRiskScoreFilter(nextOp, nextAmount);
    if (encoded.length === 0 && !selectedKey) return;
    onChange(encoded);
  };

  return (
    <VerticalStack gap="2">
      <ChoiceList
        title="Condition"
        titleHidden
        choices={RISK_SCORE_OPS}
        selected={operator ? [operator] : []}
        onChange={(vals) => {
          const v = vals[0];
          if (!v) return;
          setOperator(v);
          if (amount !== "") commit(v, amount);
        }}
      />
      <div
        onKeyDown={(e) => {
          if (e.key !== "Enter") return;
          e.preventDefault();
          e.stopPropagation();
          commit(operator, amount);
          onClose?.();
        }}
      >
        <TextField
          label="Value"
          labelHidden
          type="number"
          value={amount}
          placeholder="e.g. 0.8"
          autoComplete="off"
          onChange={setAmount}
          onBlur={() => commit(operator, amount)}
        />
      </div>
    </VerticalStack>
  );
}

const getHeaders = () => {
  const baseHeaders = [
    {
      text: "Severity",
      value: "severityComp",
      title: "Severity",
    },
    {
      text: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
      value: "endpointComp",
      title: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
    },
    {
      text: "Host",
      value: "host",
      title: "Host",
    },
    {
      text: isEndpointSecurityCategory() ? "Username" : "Threat Actor",
      value: "actorComp",
      title: isEndpointSecurityCategory() ? "Username" : "Actor",
      filterKey: 'actor'
    },
  ];

  if (isAgenticSecurityCategory() || isEndpointSecurityCategory()) {
    baseHeaders.push({
      text: "Evidence",
      value: "evidenceLine",
      title: "Evidence",
      maxWidth: "260px",
      type: CellType.TEXT,
      tooltipKey: "evidenceLineFull",
    });
    baseHeaders.push({
      text: "Reason",
      value: "reason",
      title: "Reason",
      maxWidth: "240px",
      type: CellType.TEXT,
      tooltipKey: "reasonFull",
    });
    baseHeaders.push({
      text: "Evidence",
      value: "evidence",
      title: "Evidence",
      maxWidth: "200px",
      type: CellType.TEXT,
      tooltipKey: "evidenceFull",
    });
  }

  if (func.shouldShowIpReputation()) {
    baseHeaders.push({
      text: "Reputation",
      value: "reputationScore",
      title: "IP Reputation",
    });
  }

  baseHeaders.push({
    text: "Filter",
    value: "filterId",
    title: labelMap[PersistStore.getState().dashboardCategory]["Attack type"],
  });

  // Only show detection type for Agentic Security (Argus) and Endpoint Security (Atlas), not for API Security
  if (isAgenticSecurityCategory() || isEndpointSecurityCategory()) {
    baseHeaders.push({
      text: "Detection Type",
      value: "detectionType",
      title: "Detection Type",
    });
    baseHeaders.push({
      text: "Risk score",
      value: "riskScore",
      title: "Risk score",
      sortActive: true,
    });
    baseHeaders.push({
      text: "Rule Violated",
      value: "ruleViolated",
      title: "Rule Violated",
      maxWidth: "200px",
    });
    baseHeaders.push({
      text: "Behaviour",
      value: "behaviour",
      title: "Behaviour",
      maxWidth: "120px",
    });
  }
  baseHeaders.push({
    text: "Compliance",
    value: "compliance",
    title: "Compliance",
    maxWidth: "200px",
  });

  // Successful Exploit column is only relevant for API Security (not Argus/Agentic or Atlas/Endpoint)
  if (isApiSecurityCategory()) {
    baseHeaders.push({
      text: "successfulExploit",
      value: "successfulComp",
      title: "Successful Exploit",
      maxWidth: "90px",
    });
  }

  baseHeaders.push(
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
    }
  );

  return baseHeaders;
};

const getSortOptions = (headers) => {
  const detectedIdx = headers.findIndex((h) => h.value === "discoveredTs") + 1;
  if (detectedIdx === 0) return [];
  const options = [
    {
      label: "Discovered time",
      value: "detectedAt asc",
      directionLabel: "Newest",
      sortKey: "detectedAt",
      columnIndex: detectedIdx,
    },
    {
      label: "Discovered time",
      value: "detectedAt desc",
      directionLabel: "Oldest",
      sortKey: "detectedAt",
      columnIndex: detectedIdx,
    },
  ];
  const riskIdx = headers.findIndex((h) => h.value === "riskScore") + 1;
  if (riskIdx > 0) {
    options.push(
      {
        label: "Risk score",
        value: "riskScore asc",
        directionLabel: "Highest",
        sortKey: "riskScore",
        columnIndex: riskIdx,
      },
      {
        label: "Risk score",
        value: "riskScore desc",
        directionLabel: "Lowest",
        sortKey: "riskScore",
        columnIndex: riskIdx,
      },
    );
  }
  return options;
};

let filters = [];

function SusDataTable({ currDateRange, rowClicked, triggerRefresh, label = LABELS.THREAT, initialTab, onRegisterPayloadSearch }) {
  const location = useLocation();
  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  const [loading, setLoading] = useState(true);
  const misconfigRowMetaRef = useRef({});
  const collectionsMap = PersistStore((state) => state.collectionsMap);
  const hostNameMap = PersistStore((state) => state.hostNameMap);
  const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);
  const guardrailComplianceMap = SessionStore((state) => state.guardrailComplianceMap);
  const setGuardrailComplianceMap = SessionStore((state) => state.setGuardrailComplianceMap);
  const guardrailApprovedByPolicy = SessionStore((state) => state.guardrailApprovedByPolicy);
  const setGuardrailApprovedByPolicy = SessionStore((state) => state.setGuardrailApprovedByPolicy);
  const needsGuardrailCompliance = label === LABELS.GUARDRAIL || isAgenticSecurityCategory() || isEndpointSecurityCategory();
  const tabIndexMap = { active: 0, under_review: 1, ignored: 2, needs_approval: 3, training: 4, skills_evaluations: 4, misconfigured_settings: 5 };
  const resolvedInitialTab = initialTab || 'active';
  const [currentTab, setCurrentTab] = useState(resolvedInitialTab);
  const [selected, setSelected] = useState(tabIndexMap[resolvedInitialTab] || 0)
  const [currentFilters, setCurrentFilters] = useState({})
  const [totalFilteredCount, setTotalFilteredCount] = useState(0)
  const [usernameMap, setUsernameMap] = useState({});
  const [usernameMapLoaded, setUsernameMapLoaded] = useState(!isEndpointSecurityCategory());
  const [advancedFilters, setAdvancedFilters] = useState([]);
  const [advancedFetchKey, setAdvancedFetchKey] = useState(0);

  const handleAdvancedFiltersChange = (next) => {
    setAdvancedFilters(next);
    setAdvancedFetchKey((k) => k + 1);
  };

  useEffect(() => {
    if (!onRegisterPayloadSearch) return undefined;
    onRegisterPayloadSearch((text, side, line) => {
      const parsed = filterFromEditorSelection(text, line, side);
      if (!parsed) {
        func.setToast(true, true, "Select a field and value, like host: example.com");
        return;
      }
      setAdvancedFilters((prev) => addAdvancedFilter(prev, parsed));
      setAdvancedFetchKey((k) => k + 1);
      func.setToast(true, false, "Added as search filter");
    });
    return undefined;
  }, [onRegisterPayloadSearch]);

  // Inline "Approve server" (Needs Approval tab). approveRow holds the raw event being approved.
  const [approveRow, setApproveRow] = useState(null);
  const [approveMode, setApproveMode] = useState("ALWAYS"); // ALWAYS | DURATION
  const [approveDays, setApproveDays] = useState("7");
  const [approveLoading, setApproveLoading] = useState(false);

  const openInlineApprove = (x) => {
    setApproveMode("ALWAYS");
    setApproveDays("7");
    setApproveRow(x);
  };

  // Threat events for skills carry the threat/traffic collection id in `apiCollectionId`, which is
  // NOT the inventory collection that renders the skill content. That inventory collection is keyed
  // by host, so resolve it by reverse-looking up the host in hostNameMap / collectionsMap.
  const resolveSkillCollectionId = (host) => {
    if (!host || host === '-') return null;
    const match = (map) => Object.keys(map || {}).find((id) => map[id] === host);
    return match(hostNameMap) || match(collectionsMap) || null;
  };

  // Open the skill-content page (inventory ApiDetails flyout, Values tab) for a skill threat row.
  const openSkillContent = (x) => {
    const collectionId = resolveSkillCollectionId(x?.host);
    if (!collectionId) {
      func.setToast(true, true, "Could not find the skill collection for this host");
      return;
    }
    // Inventory endpoints are stored path-only; strip any host prefix before matching selected_url.
    const pathOnlyUrl = String(x?.url || "").replace(/^https?:\/\/[^/]+/, "");
    const params = new URLSearchParams();
    params.set("selected_url", pathOnlyUrl);
    params.set("selected_method", x?.method || "POST");
    params.set("agentic_view", "skills");
    const navigateUrl = `${window.location.origin}/dashboard/observe/inventory/${collectionId}?${params.toString()}`;
    window.open(navigateUrl, "_blank");
  };

  const submitInlineApprove = async () => {
    const policyName = approveRow?.filterId;
    const serverId = approveRow?.host;
    if (!policyName) { func.setToast(true, true, "Could not resolve the policy for this event"); return; }
    if (!serverId || serverId === '-') { func.setToast(true, true, "Could not resolve the server for this event"); return; }
    let value = 0;
    if (approveMode === "DURATION") {
      value = parseInt(approveDays, 10);
      if (!Number.isInteger(value) || value <= 0) { func.setToast(true, true, "Enter a valid number of days"); return; }
    }
    setApproveLoading(true);
    try {
      // request util rejects (and toasts the backend error) on non-2xx, so reaching here = success.
      await guardrailApi.approveServerForPolicy({
        policyName,
        approvedServerId: serverId,
        approvedServerName: serverId,
        approvalMode: approveMode,
        approvalValue: value,
      });
      const scope = approveMode === "DURATION" ? `for ${value} day(s)` : "always";
      func.setToast(true, false, `Approved ${serverId} ${scope}`);
      setApproveRow(null);
      // Refresh the approved-servers map + table so the row drops off Needs Approval immediately.
      await refreshApprovedByPolicy();
      if (triggerRefresh) triggerRefresh();
    } catch {
      // Error toast already surfaced by the request interceptor; keep the modal open.
    } finally {
      setApproveLoading(false);
    }
  };

  useEffect(() => {
    if (isEndpointSecurityCategory()) {
      fetchEndpointShieldUsernameMap().then(map => {
        setUsernameMap(map);
        setUsernameMapLoaded(true);
      });
    }
  }, []);

  useEffect(() => {
    if (!needsGuardrailCompliance) return;
    Promise.all([
      api.fetchGuardrailComplianceInfos(),
      guardrailApi.fetchGuardrailPolicies()
    ]).then(([complianceResp, policiesResp]) => {
      const capabilityMap = {};

      (complianceResp?.guardrailComplianceInfos || []).forEach((entry) => {
        const capability = (entry._id || '').replace('guardrails/', '').replace('.conf', '');
        if (capability) capabilityMap[capability] = entry.mapComplianceToListClauses;
      });

      mergePolicyComplianceMap(capabilityMap, policiesResp?.guardrailPolicies);

      setGuardrailComplianceMap(capabilityMap);
      setGuardrailApprovedByPolicy(buildApprovedByPolicy(policiesResp?.guardrailPolicies));
    }).catch((error) => {
      console.error('Error loading guardrail compliance:', error);
    });
  }, [label]);

  // Refetch policies and refresh the approved-servers map (e.g. right after an approve),
  // so the just-approved server drops off the Needs Approval tab immediately.
  const refreshApprovedByPolicy = async () => {
    try {
      const resp = await guardrailApi.fetchGuardrailPolicies();
      setGuardrailApprovedByPolicy(buildApprovedByPolicy(resp?.guardrailPolicies));
    } catch (error) {
      console.error('Error refreshing approved servers:', error);
    }
  };

  const baseTabs = [
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
  ]

  // Check if AGENT_TRAFFIC_LOGS feature is enabled
  const hasAgentTrafficLogsAccess = func.checkForFeatureSaas('AGENT_TRAFFIC_LOGS');
  // Add Training Data tab only for guardrail events and if feature is enabled
  const guardrailExtraTabs = [];
  // "Needs Approval" is Endpoint (Atlas) only — approval behaviour is not supported for Agentic
  // (Argus). Gated on category (not the label prop) because the Atlas page passes label=THREAT.
  if (isEndpointSecurityCategory()) {
    guardrailExtraTabs.push({
      content: 'Needs Approval',
      badge: 'Beta',
      onAction: () => { setCurrentTab('needs_approval'); },
      id: 'needs_approval',
      index: 3
    });
  }
  if (label === LABELS.GUARDRAIL && hasAgentTrafficLogsAccess) {
    guardrailExtraTabs.push({
      content: 'Training Data',
      onAction: () => { setCurrentTab('training'); },
      id: 'training',
      index: 4
    });
  }
  // "Skills Evaluations" — events with filterId == "skill_evaluation", filtered server-side.
  // Endpoint (Atlas) only. Positioned after the guardrail extra tabs.
  const skillsExtraTabs = [];
  if (isEndpointSecurityCategory()) {
    skillsExtraTabs.push({
      content: 'Skills Evaluations',
      badge: 'Beta',
      onAction: () => { setCurrentTab('skills_evaluations'); },
      id: 'skills_evaluations',
      index: baseTabs.length + guardrailExtraTabs.length
    });
  }
  // "Misconfigured Settings" — events whose latestApiEndpoint contains "/config/" (config-scanner
  // findings, e.g. "/codex/config/mcp_servers.computer-use.command"), filtered server-side via the
  // same "only"/"exclude" convention as Skills Evaluations. Endpoint (Atlas) only.
  const configExtraTabs = [];
  if (isEndpointSecurityCategory()) {
    configExtraTabs.push({
      content: 'Misconfigured Settings',
      badge: 'Beta',
      onAction: () => { setCurrentTab('misconfigured_settings'); },
      id: 'misconfigured_settings',
      index: baseTabs.length + guardrailExtraTabs.length + skillsExtraTabs.length
    });
  }
  const tableTabs = [...baseTabs, ...guardrailExtraTabs, ...skillsExtraTabs, ...configExtraTabs]

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
  }

  // Helper function to validate filter requirements for bulk operations
  const validateFiltersForBulkOperation = (operationType = 'operation') => {
    // Check if both URL and Attack Type filters are present
    if (!currentFilters.url || currentFilters.url.length === 0 ||
        !currentFilters.latestAttack || currentFilters.latestAttack.length === 0) {
      const message = operationType === 'ignore'
        ? 'Both URL and Attack Type filters are required to ignore events. This prevents accidentally blocking too many future events.'
        : 'Both URL and Attack Type filters are required for filter-based operations. This ensures precise targeting of events.';
      func.setToast(true, true, message);
      return false;
    }

    // Check if any other filters are applied (only URL and attack category are allowed)
    const hasOtherFilters = (currentFilters.actor && currentFilters.actor.length > 0) ||
                           (currentFilters.type && currentFilters.type.length > 0) ||
                           (currentFilters.apiCollectionId && currentFilters.apiCollectionId.length > 0)
    
    if (hasOtherFilters) {
      const message = 'Only URL and Attack Category filters are allowed for bulk operations. Please remove other filters (Actor, Type, Collection) and try again.';
      func.setToast(true, true, message);
      return false;
    }

    return true;
  }

  // Generic handler for bulk operations on selected IDs
  const handleBulkOperation = async (selectedIds, operation, newState = null) => {
    const actionLabels = {
      ignore: { ing: 'ignoring', ed: 'ignored' },
      delete: { ing: 'deleting', ed: 'deleted' },
      markForReview: { ing: 'marking for review', ed: 'marked for review' },
      removeFromReview: { ing: 'removing from review', ed: 'removed from review' },
      markForTraining: { ing: 'marking for training', ed: 'marked for training' }
    };

    const label = actionLabels[operation];

    if (!selectedIds || selectedIds.length === 0) {
      func.setToast(true, true, 'No events selected');
      return;
    }

    const validIds = selectedIds.filter(id => id != null && id !== '');

    if (validIds.length === 0) {
      func.setToast(true, true, 'No valid events selected');
      return;
    }

    try {
      let response;
      if (operation === 'delete') {
        response = await threatDetectionRequests.deleteMaliciousEvents({ eventIds: validIds });
      } else {
        response = await threatDetectionRequests.updateMaliciousEventStatus({ eventIds: validIds, status: newState });
      }

      const isSuccess = operation === 'delete' ? response?.deleteSuccess : response?.updateSuccess;
      const count = operation === 'delete' ? response?.deletedCount : response?.updatedCount;
      const errorMessage = operation === 'delete' ? response?.deleteMessage : response?.updateMessage;

      if (isSuccess) {
        func.setToast(true, false, `${count || validIds.length} event${validIds.length === 1 ? '' : 's'} ${label.ed} successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, `Failed to ${operation} events: ${errorMessage || 'Unknown error'}`);
      }
    } catch (error) {
      func.setToast(true, true, `Error ${label.ing} events`);
    }
  }

  // Generic handler for filtered bulk operations
  const handleFilteredOperation = async (operation, newState = null) => {
    const actionLabels = {
      ignore: { ing: 'ignoring', ed: 'ignored' },
      delete: { ing: 'deleting', ed: 'deleted' },
      markForReview: { ing: 'marking for review', ed: 'marked for review' },
      removeFromReview: { ing: 'removing from review', ed: 'removed from review' },
      markForTraining: { ing: 'marking for training', ed: 'marked for training' }
    };

    const label = actionLabels[operation];

    // Validate filters
    const validationType = operation === 'ignore' ? 'ignore' : undefined;
    if (!validateFiltersForBulkOperation(validationType)) return;

    try {
      let response;
      const filterParams = [
        currentFilters.actor || [],
        currentFilters.url || [],
        currentFilters.type || [],
        currentFilters.latestAttack || [],
        startTimestamp,
        endTimestamp,
        currentTab.toUpperCase(),
        currentFilters.host || []
      ];

      if (operation === 'delete') {
        response = await threatDetectionRequests.deleteMaliciousEvents({
          actors: filterParams[0],
          urls: filterParams[1],
          types: filterParams[2],
          latestAttack: filterParams[3],
          startTimestamp: filterParams[4],
          endTimestamp: filterParams[5],
          statusFilter: filterParams[6],
          hosts: filterParams[7]
        });
      } else {
        response = await threatDetectionRequests.updateMaliciousEventStatus({
          actors: filterParams[0],
          urls: filterParams[1],
          types: filterParams[2],
          latestAttack: filterParams[3],
          startTimestamp: filterParams[4],
          endTimestamp: filterParams[5],
          statusFilter: filterParams[6],
          status: newState,
          hosts: filterParams[7]
        });
      }

      const isSuccess = operation === 'delete' ? response?.deleteSuccess : response?.updateSuccess;
      const count = operation === 'delete' ? response?.deletedCount : response?.updatedCount;

      if (isSuccess) {
        func.setToast(true, false, `${count || 0} events ${label.ed} successfully`);
        if (triggerRefresh) {
          triggerRefresh();
        }
      } else {
        func.setToast(true, true, `Failed to ${operation === 'delete' ? 'delete' : operation} filtered events`);
      }
    } catch (error) {
      func.setToast(true, true, `Error ${label.ing} filtered events`);
    }
  }

  // Simplified handler functions using the generic handlers
  const handleBulkIgnore = (selectedIds) => handleBulkOperation(selectedIds, 'ignore', 'IGNORED');
  const handleBulkDelete = (selectedIds) => handleBulkOperation(selectedIds, 'delete');
  const handleMisconfigGroupDelete = async (selectedIds) => {
    const validIds = (selectedIds || []).filter(id => id != null && id !== '');
    if (validIds.length === 0) {
      func.setToast(true, true, 'No valid events selected');
      return;
    }

    try {
      for (const id of validIds) {
        const meta = misconfigRowMetaRef.current[id];
        if (meta?.host && meta?.actor && meta?.url) {
          await threatDetectionRequests.deleteMaliciousEvents({ hosts: [meta.host], actors: [meta.actor], urls: [meta.url] });
        } else {
          await threatDetectionRequests.deleteMaliciousEvents({ eventIds: [id] });
        }
      }
      if (triggerRefresh) {
        triggerRefresh();
      }
    } catch (error) {
      func.setToast(true, true, 'Error deleting events');
    }
  };
  const handleBulkMarkForReview = (selectedIds) => handleBulkOperation(selectedIds, 'markForReview', 'UNDER_REVIEW');
  const handleBulkRemoveFromReview = (selectedIds) => handleBulkOperation(selectedIds, 'removeFromReview', 'ACTIVE');
  const handleBulkMarkForTraining = async (selectedIds) => {
    if (!hasAgentTrafficLogsAccess) return;
    // Execute the bulk operation - backend will handle calling agent-traffic-analyzer
    await handleBulkOperation(selectedIds, 'markForTraining', 'TRAINING');
  };

  // Simplified filtered operation handlers
  const handleIgnoreAllFiltered = () => handleFilteredOperation('ignore', 'IGNORED');
  const handleDeleteAllFiltered = () => handleFilteredOperation('delete');
  const handleMarkAllFilteredForReview = () => handleFilteredOperation('markForReview', 'UNDER_REVIEW');
  const handleRemoveAllFilteredFromReview = () => handleFilteredOperation('removeFromReview', 'ACTIVE');
  const handleMarkAllFilteredForTraining = async () => {
    if (!hasAgentTrafficLogsAccess) {
      return;
    }
    // Execute the filtered operation
    await handleFilteredOperation('markForTraining', 'TRAINING');
  };

  const promotedBulkActions = (selectedIds) => {
    const actions = [];

    // Determine the count to display
    let eventCount = 0;
    let eventText = '';
    let useFilterBasedUpdate = false;

    // Check if "All" is selected - when GithubServerTable passes 'All' as selectedIds
    if (selectedIds === 'All') {
      // When "select all" is clicked, use the total count from API
      eventCount = totalFilteredCount;
      eventText = `ALL ${eventCount} event${eventCount === 1 ? '' : 's'}`;
      useFilterBasedUpdate = true;
    } else if (Array.isArray(selectedIds) && selectedIds.length > 0) {
      // When specific items are selected
      eventCount = selectedIds.length;
      eventText = `${eventCount} selected event${eventCount === 1 ? '' : 's'}`;
      useFilterBasedUpdate = false;
    }

    if (eventCount === 0) return actions;

    // Helper function to create an action button
    const createAction = (label, actionType, validationType = null, includeWarning = false) => {
      const warningText = includeWarning
        ? '\n\nNote: Future events matching these URL and Attack Type combinations will be automatically blocked.'
        : '';

      return {
        content: `${label} ${eventText}`,
        onAction: () => {
          if (useFilterBasedUpdate) {
            if (!validateFiltersForBulkOperation(validationType)) return;
            const message = actionType === 'delete'
              ? `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`
              : `Are you sure you want to ${label.toLowerCase()} ${eventText}?${warningText}`;
            const handlers = {
              markForReview: handleMarkAllFilteredForReview,
              ignore: handleIgnoreAllFiltered,
              removeFromReview: handleRemoveAllFilteredFromReview,
              reactivate: handleRemoveAllFilteredFromReview,
              delete: handleDeleteAllFiltered,
              markForTraining: handleMarkAllFilteredForTraining
            };
            func.showConfirmationModal(message, label, handlers[actionType]);
          } else {
            const message = actionType === 'delete'
              ? `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`
              : includeWarning && actionType === 'ignore'
                ? `Are you sure you want to ${label.toLowerCase()} ${eventText}?`
                : `Are you sure you want to ${label.toLowerCase()} ${eventText}?`;
            const handlers = {
              markForReview: () => handleBulkMarkForReview(selectedIds),
              ignore: () => handleBulkIgnore(selectedIds),
              removeFromReview: () => handleBulkRemoveFromReview(selectedIds),
              reactivate: () => handleBulkRemoveFromReview(selectedIds),
              delete: () => (currentTab === 'misconfigured_settings' ? handleMisconfigGroupDelete(selectedIds) : handleBulkDelete(selectedIds)),
              markForTraining: () => handleBulkMarkForTraining(selectedIds)
            };
            func.showConfirmationModal(message, label, handlers[actionType]);
          }
        },
      };
    };

    // Define actions for each tab
    const tabActions = {
      'active': [
        { label: 'Mark for Review', type: 'markForReview' },
        { label: 'Ignore', type: 'ignore', validationType: 'ignore', warning: true },
        ...(label === LABELS.GUARDRAIL && hasAgentTrafficLogsAccess ? [{ label: 'Mark for Training', type: 'markForTraining' }] : [])
      ],
      'under_review': [
        { label: 'Remove from Review', type: 'removeFromReview' },
        { label: 'Ignore', type: 'ignore', validationType: 'ignore', warning: true },
        ...(label === LABELS.GUARDRAIL && hasAgentTrafficLogsAccess ? [{ label: 'Mark for Training', type: 'markForTraining' }] : [])
      ],
      'ignored': [
        { label: 'Reactivate', type: 'reactivate' },
        ...(label === LABELS.GUARDRAIL && hasAgentTrafficLogsAccess ? [{ label: 'Mark for Training', type: 'markForTraining' }] : [])
      ],
      'training': [
        // No actions for training data - training data cannot be removed
      ]
    };

    // Add tab-specific actions
    const currentTabActions = tabActions[currentTab] || [];
    currentTabActions.forEach(({ label, type, validationType, warning }) => {
      actions.push(createAction(label, type, validationType, warning));
    });

    if (isEndpointSecurityCategory() && (currentTab === 'skills_evaluations' || currentTab === 'misconfigured_settings')) {
      actions.push(createAction('Mark for Review', 'markForReview'));
      actions.push(createAction('Ignore', 'ignore', 'ignore', true));
    }

    // Delete button for all tabs
    actions.push(createAction('Delete', 'delete'));

    return actions;
  };

  const limit = 50;

  async function fetchData(
    sortKey,
    sortOrder,
    skip,
    _limit,
    filters,
    _filterOperators,
    queryValue
  ) {
    setLoading(true);
    // "Needs Approval" is a client-side view over ACTIVE events filtered to behaviour==="approval".
    // Fetch active events with a high limit (single page) and filter after mapping.
    const isNeedsApproval = currentTab === 'needs_approval';
    const isSkillsEvaluations = currentTab === 'skills_evaluations';
    const isMisconfiguredSettings = currentTab === 'misconfigured_settings';
    // Needs Approval is a client-side view (fetch a big page, filter after mapping). Skills
    // Evaluations / Misconfigured Settings are SERVER-paginated: each shows ACTIVE events narrowed
    // to its own partition by the backend (x-skill-eval-mode / x-config-eval-mode headers), so
    // totals/pagination are correct.
    const isClientSideView = isNeedsApproval;
    const effectiveStatus = (isNeedsApproval || isSkillsEvaluations || isMisconfiguredSettings) ? 'ACTIVE' : currentTab.toUpperCase();
    const effectiveSkip = isClientSideView ? 0 : skip;
    const effectiveLimit = isClientSideView ? 200 : limit;
    // Skills Evaluations / Misconfigured Settings partitions (Atlas only): "only" on their own tab,
    // "exclude" on the Active tab so neither shows up there. Backend applies each independently
    // (gated to contextSource=ENDPOINT); undefined elsewhere.
    const skillEvaluationMode = isEndpointSecurityCategory()
      ? (isSkillsEvaluations ? 'only' : (currentTab === 'active' ? 'exclude' : undefined))
      : undefined;
    const configEvaluationMode = isEndpointSecurityCategory()
      ? (isMisconfiguredSettings ? 'only' : (currentTab === 'active' ? 'exclude' : undefined))
      : undefined;
    let sourceIpsFilter = [],
      apiCollectionIdsFilter = [],
      matchingUrlFilter = [],
      typeFilter = [],
      latestAttack = [],
      hostFilter = [],
      severityFilter = [];
    let latestApiOrigRegex = toLatestApiOrigRegex(queryValue, advancedFilters) || "";
    if (filters?.actor) {
      sourceIpsFilter = filters?.actor;
    }
    if (filters?.apiCollectionId) {
      apiCollectionIdsFilter = filters?.apiCollectionId;
    }
    if (filters?.url) {
      matchingUrlFilter = filters?.url;
    }
    if(filters?.type){
      typeFilter = filters?.type
    }
    if(filters?.latestAttack){
      latestAttack = filters?.latestAttack
    }
    if(filters?.host){
      hostFilter = filters?.host
    }
    if(filters?.severity){
      severityFilter = filters?.severity
    }

    let riskScoreFilterType;
    let riskScoreFilterValue;
    if (isAgenticSecurityCategory() || isEndpointSecurityCategory()) {
      const parsed = parseRiskScoreFilter(filters?.riskScore);
      if (parsed.operator && parsed.amount !== "") {
        const n = Number(parsed.amount);
        if (Number.isFinite(n)) {
          riskScoreFilterType = parsed.operator;
          riskScoreFilterValue = n;
        }
      }
    }

    // Store current filters for bulk operations
    setCurrentFilters({
      actor: sourceIpsFilter,
      apiCollectionId: apiCollectionIdsFilter,
      url: matchingUrlFilter,
      type: typeFilter,
      latestAttack: latestAttack,
      host: hostFilter,
      severity: severityFilter,
      sortKey: sortKey,
      sortOrder: sortOrder
    });
    
    const sort = { [sortKey]: sortOrder };
    // Successful Exploit filter is only relevant for API Security (not Argus/Agentic or Atlas/Endpoint)
    let successfulBool = undefined;
    if (isApiSecurityCategory()) {
      const successfulFilterValue = Array.isArray(filters?.successfulExploit) ? filters?.successfulExploit?.[0] : filters?.successfulExploit;
      successfulBool = (successfulFilterValue === true || successfulFilterValue === 'true') ? true
                        : (successfulFilterValue === false || successfulFilterValue === 'false') ? false
                        : undefined;
    }
    const res = await api.fetchSuspectSampleData(
      effectiveSkip,
      sourceIpsFilter,
      apiCollectionIdsFilter,
      matchingUrlFilter,
      typeFilter,
      sort,
      startTimestamp,
      endTimestamp,
      latestAttack,
      effectiveLimit,
      effectiveStatus,
      successfulBool,
      label, // Use the label prop (THREAT or GUARDRAIL)
      hostFilter,
      latestApiOrigRegex,
      undefined,
      undefined,
      severityFilter,
      skillEvaluationMode,
      configEvaluationMode,
      riskScoreFilterType,
      riskScoreFilterValue
    );

    // Store the total count for filtered results
    setTotalFilteredCount(res.total || 0);
    let total = res.total;
    if (isMisconfiguredSettings) {
      misconfigRowMetaRef.current = {};
    }
    let ret = (res?.maliciousEvents || []).map((x) => {
      if (isMisconfiguredSettings) {
        misconfigRowMetaRef.current[x.id] = { host: x.host, actor: x.actor, url: x.url };
      }
      const severity = (isAgenticSecurityCategory() || isEndpointSecurityCategory())
        ? (x?.severity || "HIGH")
        : (x?.severity || threatFiltersMap[x?.filterId]?.severity || "HIGH")

      const complianceMapData = resolveComplianceClauseMap(x, needsGuardrailCompliance, threatFiltersMap, guardrailComplianceMap);
      const complianceList = Object.keys(complianceMapData);

      const isSessionBased = x?.sessionId && x.sessionId !== '';

      let nextUrl = null;
      if (x.refId && x.eventType && x.filterId) {
        const params = new URLSearchParams(location.search);
        params.set("refId", x.refId);
        params.set("eventType", x.eventType);
        if(x?.actor !== undefined && x?.actor.length > 0){
          params.set("actor", x.actor);
        }
        params.set("filterId", x.filterId);
        if (x.status) {
          params.set("eventStatus", x.status.toUpperCase());
        }
        nextUrl = `${location.pathname}?${params.toString()}${location.hash}`;
      }
      
      const rowData = {
        ...x,
        id: x.id,
        actorComp: isEndpointSecurityCategory()
          ? getUsernameForCollection({ displayName: x.host || collectionsMap[x.apiCollectionId] }, usernameMap, x.actor)
          : formatActorId(x.actor),
        host: x.host || "-",
        endpointComp: String(x?.url || "").includes('/skills/') ? (
          // Skill rows link to the skill-content page (Values tab markdown) for this host.
          // preventDefault/stopPropagation stops the row's <a href=nextUrl> and onRowClick.
          <div
            onClick={(e) => { e.preventDefault(); e.stopPropagation(); openSkillContent(x); }}
            style={{ cursor: "pointer" }}
          >
            <GetPrettifyEndpoint
              maxWidth="300px"
              method={x.method}
              url={x.url}
              isNew={false}
            />
          </div>
        ) : (
          <GetPrettifyEndpoint
            maxWidth="300px"
            method={x.method}
            url={x.url}
            isNew={false}
          />
        ),
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: func.prettifyEpoch(x.timestamp || 0),
        sourceIPComponent: x?.ip || "-",
        type: x?.type || "-",
        severityComp: (<div className={`badge-wrapper-${severity}`}>
                          <Badge size="small">{func.toSentenceCase(severity)}</Badge>
                      </div>
        ),
        ...((isAgenticSecurityCategory() || isEndpointSecurityCategory()) && {
          riskScore: parseStoredRiskScore(x?.metadata) ?? "",
          ...(() => {
            const r = parseStoredReason(x?.metadata);
            if (!r) return { reason: "", reasonFull: "" };
            const { preview, full } = truncateToWords(r, 30);
            return { reason: preview, reasonFull: full };
          })(),
          ...(() => {
            const typeLabel = deriveAgenticType(x.url, x.method);
            const full = extractEvidenceText(x.payload, typeLabel, 1500);
            const preview = extractEvidenceText(x.payload, typeLabel, 300);
            return { evidence: preview || "-", evidenceFull: full || "-" };
          })(),
        }),
        // Successful Exploit is only shown for API Security (not Argus/Agentic or Atlas/Endpoint)
        ...(isApiSecurityCategory() && {
          successfulComp: (
            <Badge size="small">{x?.successfulExploit ? "True" : "False"}</Badge>
          ),
        }),
        detectionType: (
          <Badge status={isSessionBased ? 'info' : 'default'}>
            {isSessionBased ? 'Session' : 'Single Prompt'}
          </Badge>
        ),
        ...((isAgenticSecurityCategory() || isEndpointSecurityCategory()) && {
          detectionType: (
            <Badge status={isSessionBased ? 'info' : 'default'}>
              {isSessionBased ? 'Session' : 'Single Prompt'}
            </Badge>
          ),
          ruleViolated: extractRuleViolated(x?.metadata),
          // Raw behaviour string kept as an explicit field so it survives onRowClick
          // (the raw `metadata` passthrough is dropped by the table); used by the flyout's
          // "Approve server" action for "approval" behaviour policies.
          behaviourRaw: extractBehaviour(x?.metadata),
          ...(() => {
            const e = typeof x?.evidenceLine === "string" ? x.evidenceLine.trim() : "";
            if (!e) return { evidenceLine: "", evidenceLineFull: "" };
            const { preview, full } = truncateToWords(e, 30);
            return { evidenceLine: preview, evidenceLineFull: full };
          })(),
          behaviour: (() => {
            const b = extractBehaviour(x?.metadata);
            if (!b) return '-';
            // Display "Human Approval" for the "approval" behaviour (value stays "approval").
            const label = String(b).toLowerCase() === 'approval' ? 'Human Approval' : func.toSentenceCase(b);
            return <Badge tone={getBehaviourTone(b)}>{label}</Badge>;
          })(),
        }),
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
        nextUrl: nextUrl,
        complianceMapData: complianceMapData,
        // Inline Approve button for the "Needs Approval" tab. Each cell is wrapped by GithubRow
        // in a Polaris <Link url={nextUrl}> (a real <a href>), so we must preventDefault to stop
        // the row's anchor navigation (the "reload"), plus stopPropagation for the row click.
        approveAction: (
          <div onClick={(e) => { e.preventDefault(); e.stopPropagation(); }}>
            <Button size="slim" onClick={() => openInlineApprove(x)}>Approve</Button>
          </div>
        )
      };

      if (func.shouldShowIpReputation()) {
        rowData.reputationScore = <IpReputationScore ipAddress={x.actor} />;
      }

      return rowData;
    });
    // Needs Approval tab: keep only approval-behaviour rows (client-side Option A), and drop
    // rows whose (policy, server) is already approved for that policy.
    if (isNeedsApproval) {
      ret = ret.filter(r =>
        String(r.behaviourRaw || '').toLowerCase() === 'approval' &&
        !isServerApproved(guardrailApprovedByPolicy, r.filterId, r.host)
      );
      total = ret.length;
    }
    // Skills Evaluations tab: rows are already the skill-evaluation set (filtered server-side via
    // x-skill-eval-mode: filterId == "skill_evaluation"), so total/pagination come straight from
    // the backend. Active applies the complementary "exclude" mode, so skill-evaluation rows don't
    // also appear there.
    setLoading(false);
    return { value: ret, total: total };
  }

  async function fillFilters() {
    const res = await api.fetchFiltersThreatTable(startTimestamp, endTimestamp);
    let urlChoices = (res?.urls || [])
      .map((x) => {
        const url = x || "/"
        return { label: url, value: x };
      });
    let ipChoices = (res?.ips || []).map((x) => {
      return { label: x, value: x };
    });

    // Extract unique hosts from the fetched data
    let hostChoices = [];
    if (res?.hosts && Array.isArray(res.hosts) && res.hosts.length > 0) {
      hostChoices = res.hosts
        .filter(host => host && host.trim() !== '' && host !== '-')
        .map(x => ({ label: x, value: x }));
    }

    // Policy triggered (latestAttack): merge subCategory from API (actual data) with threatFiltersMap (configured templates)
    const subCategoryFromApi = (res?.subCategory || []).map(x => ({ label: x, value: x }));
    const fromThreatFiltersMap = Object.entries(threatFiltersMap || {}).map(([key, value]) => ({
      label: value?._id || key,
      value: value?._id || key
    }));
    const uniqueByValue = new Map();
    [...subCategoryFromApi, ...fromThreatFiltersMap].forEach(({ label, value }) => {
      if (value && !uniqueByValue.has(value)) {
        uniqueByValue.set(value, { label: label || value, value });
      }
    });
    const attackTypeChoices = Array.from(uniqueByValue.values());

    filters = [
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
          {label: 'Rule based', value: 'Rule-Based'},
          {label: 'Anomaly', value: 'Anomaly'},
        ],
      },
      {
        key: 'latestAttack',
        label: labelMap[PersistStore.getState().dashboardCategory]["Latest attack sub-category"],
        type: 'select',
        choices: attackTypeChoices,
        multiple: true
      },
      {
        key: 'severity',
        label: "Severity",
        title: "Severity",
        choices: [
          { label: 'Critical', value: 'CRITICAL' },
          { label: 'High', value: 'HIGH' },
          { label: 'Medium', value: 'MEDIUM' },
          { label: 'Low', value: 'LOW' },
        ],
        multiple: true
      },
    ];

    if (isAgenticSecurityCategory() || isEndpointSecurityCategory()) {
      filters.push({
        key: 'riskScore',
        label: "Risk score",
        title: "Risk score",
        choices: [],
        renderFilter: ({ selected, onChange }) => (
          <RiskScoreFilterControl selected={selected} onChange={onChange} />
        ),
      });
    }

    // Successful Exploit filter is only relevant for API Security (not Argus/Agentic or Atlas/Endpoint)
    if (isApiSecurityCategory()) {
      filters.push({
        key: 'successfulExploit',
        label: 'Successful Exploit',
        title: 'Successful Exploit',
        choices: [
          { label: 'True', value: 'true' },
          { label: 'False', value: 'false' }
        ],
        singleSelect: true
      });
    }
  }

  useEffect(() => {
    fillFilters();
  }, [threatFiltersMap, startTimestamp, endTimestamp]);

  function disambiguateLabel(key, value) {
    switch (key) {
      case "apiCollectionId":
        return func.convertToDisambiguateLabelObj(value, collectionsMap, 2);
      case "latestAttack":
        const latestAttackLabelMap = Object.fromEntries(
          Object.entries(threatFiltersMap || {}).map(([k, v]) => [
            v?._id || k,
            v?.category?.name || v?._id || k
          ])
        );
        return func.convertToDisambiguateLabelObj(value, latestAttackLabelMap, 2);
      case "riskScore": {
        const parsed = parseRiskScoreFilter(value);
        const opLabel = RISK_SCORE_OP_LABELS[parsed.operator] || parsed.operator;
        return parsed.amount !== "" ? `${opLabel} ${parsed.amount}` : opLabel;
      }
      default:
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }
  }

  // Recompute rows once the async guardrail compliance map has loaded (same pattern as usernameMapLoaded).
  const guardrailComplianceLoaded = !needsGuardrailCompliance || Object.keys(guardrailComplianceMap).length > 0;
  const key = startTimestamp + endTimestamp + (usernameMapLoaded ? '_u' : '') + (guardrailComplianceLoaded ? '_gc' : '');
  const headers = getHeaders();
  if (currentTab === 'needs_approval') {
    headers.push({ text: "Action", value: "approveAction", title: "Action" });
  }
  const sortOptions = getSortOptions(headers);
  return (
    <>
      <GithubServerTable
        key={key}
        onRowClick={(data) => rowClicked(data)}
        pageLimit={limit}
        headers={headers}
        resourceName={resourceName}
        sortOptions={sortOptions}
        disambiguateLabel={disambiguateLabel}
        loading={loading}
        fetchData={fetchData}
        filters={filters}
        selectable={true}
        promotedBulkActions={promotedBulkActions}
        headings={headers}
        useNewRow={true}
        condensedHeight={true}
        tableTabs={tableTabs}
        selected={selected}
        onSelect={handleSelectedTab}
        mode={IndexFiltersMode.Default}
        searchAccessory={
          <AdvancedPayloadSearch
            filters={advancedFilters}
            onChange={handleAdvancedFiltersChange}
            showTags={false}
          />
        }
        searchBelow={advancedFilters.length > 0 ? (
          <AdvancedPayloadSearch
            filters={advancedFilters}
            onChange={handleAdvancedFiltersChange}
            showButton={false}
          />
        ) : null}
        callFromOutside={advancedFetchKey}
      />

      <Modal
        open={approveRow !== null}
        onClose={() => setApproveRow(null)}
        title="Approve server"
        primaryAction={{ content: "Approve", loading: approveLoading, onAction: submitInlineApprove }}
        secondaryActions={[{ content: "Cancel", onAction: () => setApproveRow(null) }]}
      >
        <Modal.Section>
          <VerticalStack gap="4">
            <Text variant="bodyMd">
              Approving <Text as="span" fontWeight="semibold">{approveRow?.host || "this server"}</Text> will
              allow it to bypass the <Text as="span" fontWeight="semibold">{approveRow?.filterId || "policy"}</Text> guardrail policy on future requests.
            </Text>
            <ChoiceList
              title="Approve for"
              choices={[
                { label: "Always", value: "ALWAYS" },
                { label: "Number of days", value: "DURATION" },
              ]}
              selected={[approveMode]}
              onChange={(v) => setApproveMode(v[0])}
            />
            {approveMode === "DURATION" && (
              <TextField
                label="Number of days"
                type="number"
                min={1}
                value={approveDays}
                onChange={setApproveDays}
                autoComplete="off"
              />
            )}
          </VerticalStack>
        </Modal.Section>
      </Modal>
    </>
  );
}

export default SusDataTable;
