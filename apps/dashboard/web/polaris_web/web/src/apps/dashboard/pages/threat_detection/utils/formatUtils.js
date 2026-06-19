import React from 'react';
import { Text } from "@shopify/polaris";
import { getGuardrailCapabilityForRule } from '../constants/guardrailRuleDefinitions';

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
export const resolveComplianceClauseMap = (event, isGuardrail, threatFiltersMap = {}, guardrailComplianceMap = {}) => {
  if (isGuardrail) {
    const capability = getGuardrailCapabilityForRule(extractRuleViolated(event?.metadata));
    return guardrailComplianceMap[capability] || guardrailComplianceMap[event?.filterId] || {};
  }
  return threatFiltersMap[event?.filterId]?.compliance?.mapComplianceToListClauses || {};
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

export const getBehaviourTone = (behaviour) =>
  behaviour === 'block' ? 'critical' : behaviour === 'warn' ? 'attention' : behaviour === 'alert' ? 'info' : undefined;
