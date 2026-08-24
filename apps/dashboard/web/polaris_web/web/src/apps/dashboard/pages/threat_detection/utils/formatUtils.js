import React from 'react';
import { Text } from "@shopify/polaris";
import { getGuardrailCapabilityForRule } from '../constants/guardrailRuleDefinitions';
import SessionStore from '@/apps/main/SessionStore';
import threatDetectionApi from '../api';
import guardrailApi from '../../guardrails/api';

// Regular expression to validate IP address (IPv4 and IPv6)
const IPV4_REGEX = /^(\d{1,3}\.){3}\d{1,3}$/;
const IPV6_REGEX = /^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$/;

export const formatActorId = (actorId) => {
  if (!actorId) return "-";

  const isValidIP = IPV4_REGEX.test(actorId) || IPV6_REGEX.test(actorId);

  if (isValidIP) {
    return (
      <Text variant="bodyMd" fontWeight="medium">
        {actorId}
      </Text>
    );
  } else {
    return (
      <Text variant="bodyMd" fontWeight="medium">
        Non IP Value
      </Text>
    );
  }
};

export const extractRuleViolated = (metadata) => {
  if (!metadata) return "-";

  try {
    const metadataObj = JSON.parse(metadata);
    return metadataObj.rule_violated || metadataObj.ruleViolated || "-";
  } catch (e) {
    return "-";
  }
};

/**
 * Resolves the compliance-clause map for a single threat/malicious event.
 * Guardrail (Agentic/Endpoint): keyed by capability derived from metadata.rule_violated.
 * API Security: lives on the threat filter template, keyed by filterId.
 * Returns {} when nothing matches (callers do Object.keys() on it).
 */
const DB_COMPLIANCE_CAPABILITIES = [
  { capability: "deniedTopics", prefixes: ["BanTopics", "BanSubstrings", "deniedTopics", "denied_topic"] },
  { capability: "llmRule", prefixes: ["UserDefinedLLMRule", "LLMRule"] },
];

export const getDbComplianceCapability = (ruleViolated) => {
  if (!ruleViolated || ruleViolated === "-") return null;
  const v = ruleViolated.trim().toLowerCase();
  const def = DB_COMPLIANCE_CAPABILITIES.find((d) => d.prefixes.some((p) => v.startsWith(p.toLowerCase())));
  return def ? def.capability : null;
};

export const dbComplianceKey = (policyName, capability) => `${policyName}::${capability}`;

// Populates SessionStore's guardrailComplianceMap: per-capability infos, merged with any
// clauses defined on the guardrail policies themselves. Was previously copy-pasted into
// ThreatCompliancePage.jsx, SusDataTable.jsx and ViolationsPage.jsx (new UI) independently -
// centralised here so there's one fetch implementation. Skips the network calls entirely when
// the store is already populated (e.g. a prior visit to Threat/Guardrail Activity already
// loaded it in this session).
export const loadGuardrailComplianceMap = async (force = false) => {
  const existing = SessionStore.getState().guardrailComplianceMap;
  if (!force && existing && Object.keys(existing).length > 0) {
    return existing;
  }
  try {
    const [complianceResp, policiesResp] = await Promise.all([
      threatDetectionApi.fetchGuardrailComplianceInfos(),
      guardrailApi.fetchGuardrailPolicies(),
    ]);
    const capabilityMap = {};
    (complianceResp?.guardrailComplianceInfos || []).forEach((entry) => {
      const capability = (entry._id || '').replace('guardrails/', '').replace('.conf', '');
      if (capability) capabilityMap[capability] = entry.mapComplianceToListClauses;
    });
    mergePolicyComplianceMap(capabilityMap, policiesResp?.guardrailPolicies);
    SessionStore.getState().setGuardrailComplianceMap(capabilityMap);
    return capabilityMap;
  } catch (e) {
    console.error(`Failed to load guardrail compliance map: ${e?.message}`);
    return existing || {};
  }
};


export const mergePolicyComplianceMap = (capabilityMap, guardrailPolicies = []) => {
  const addCompliance = (key, compliance) => {
    if (!compliance || Object.keys(compliance).length === 0) return;
    if (!capabilityMap[key]) capabilityMap[key] = {};
    Object.entries(compliance).forEach(([framework, clauses]) => {
      capabilityMap[key][framework] = [...new Set([...(capabilityMap[key][framework] || []), ...(clauses || [])])];
    });
  };
  (guardrailPolicies || []).forEach((policy) => {
    (policy.deniedTopics || []).forEach((topic) => addCompliance(dbComplianceKey(policy.name, "deniedTopics"), topic.compliance));
    addCompliance(dbComplianceKey(policy.name, "llmRule"), policy.llmRule?.compliance);
  });
  return capabilityMap;
};

export const resolveComplianceClauseMap = (event, isGuardrail, threatFiltersMap = {}, guardrailComplianceMap = {}) => {
  let base = {};
  if (isGuardrail) {
    const ruleViolated = extractRuleViolated(event?.metadata);
    const dbCapability = getDbComplianceCapability(ruleViolated);
    if (dbCapability) {
      base = guardrailComplianceMap[dbComplianceKey(event?.filterId, dbCapability)] || {};
    } else {
      const capability = getGuardrailCapabilityForRule(ruleViolated);
      base = guardrailComplianceMap[capability] || guardrailComplianceMap[event?.filterId] || {};
    }
  } else {
    base = threatFiltersMap[event?.filterId]?.compliance?.mapComplianceToListClauses || {};
  }
  if (event?.owaspCategories?.length > 0) {
    return { ...base, "OWASP Agentic Skills Top 10": event.owaspCategories.map(o => o.id) };
  }
  return base;
};

export const extractBehaviour = (metadata) => {
  if (!metadata) return null;

  try {
    const metadataObj = JSON.parse(metadata);
    return metadataObj.behaviour || null;
  } catch (e) {
    return null;
  }
};

/**
 * Extracts the LLM-generated overview/remediation markdown that settings-scanners
 * (Claude/Codex/Copilot config risk) attach directly to metadata per-event. Callers
 * fall back to the static per-field JSON maps (SETTINGS_RISK_CONFIGS) when these are absent.
 */
export const extractOverviewAndRemediation = (metadata) => {
  if (!metadata) return { overview: null, remediation: null };

  try {
    const metadataObj = JSON.parse(metadata);
    return {
      overview: metadataObj.overview || null,
      remediation: metadataObj.remediation || null
    };
  } catch (e) {
    return { overview: null, remediation: null };
  }
};

export const getBehaviourTone = (behaviour) =>
  behaviour === 'block' ? 'critical' : behaviour === 'warn' ? 'attention' : behaviour === 'alert' ? 'info' : undefined;
