import { labelMap } from "./labelHelperMap"
import PersistStore from "./PersistStore";
import func from "@/util/func";

/**
 * Maps a label to its category-specific version.
 * @param {string} value - Original label value (e.g. 'API Discovery')
 * @param {string} category - Current dashboard category (e.g. 'MCP Security')
 * @returns {string} - Transformed label for that category
 */
export function mapLabel(value, category) {
  // For demo accounts: apply special Argus/Atlas label mappings
  if (func.isDemoAccount() && (category === CATEGORY_AGENTIC_SECURITY || category === CATEGORY_ENDPOINT_SECURITY)) {
    // Demo-specific label mappings
    if (value === "Quick Start") return "Connectors";
    if (value === "API Discovery") return "Agentic AI Discovery";
    if (value === "API Testing") return "AI Red Teaming";
    if (value === "Threat Detection") return "Guardrails";
  }

  // Apply standard label mappings for all accounts and categories
  return labelMap?.[category]?.[value] || value;
}

export const CATEGORY_MCP_SECURITY = 'MCP Security';
export const CATEGORY_GEN_AI = 'Gen AI';
export const CATEGORY_API_SECURITY = 'API Security';
export const CATEGORY_AGENTIC_SECURITY = 'Agentic Security';
export const CATEGORY_DAST = 'DAST';
export const CATEGORY_ENDPOINT_SECURITY = 'Endpoint Security';

export function getDashboardCategory() {
  try {
    const category = PersistStore.getState().dashboardCategory
    return category
  } catch(e){
    return CATEGORY_API_SECURITY
  }
}

export function isCategory(category) {
  return getDashboardCategory() === category;
}

export function isMCPSecurityCategory() {
  return isCategory(CATEGORY_MCP_SECURITY);
}

export function isGenAISecurityCategory() {
  return isCategory(CATEGORY_GEN_AI);
}

export function isApiSecurityCategory() {
  return isCategory(CATEGORY_API_SECURITY);
}

export function isAgenticSecurityCategory() {
  return isCategory(CATEGORY_AGENTIC_SECURITY);
}

export function isDastCategory() {
  return isCategory(CATEGORY_DAST);
}

export function isEndpointSecurityCategory() {
  return func.isAtlasArgusAccount() && isCategory(CATEGORY_ENDPOINT_SECURITY);
}