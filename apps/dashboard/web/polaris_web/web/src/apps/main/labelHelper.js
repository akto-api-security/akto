import { labelMap } from "./labelHelperMap"
import PersistStore from "./PersistStore";

/**
 * Maps a label to its category-specific version.
 * @param {string} value - Original label value (e.g. 'API Discovery')
 * @param {string} category - Current dashboard category (e.g. 'MCP Security')
 * @returns {string} - Transformed label for that category
 */
export function mapLabel(value, category) {
  return labelMap?.[category]?.[value] || value
}

export const CATEGORY_MCP_SECURITY = 'MCP Security';
export const CATEGORY_GEN_AI = 'Gen AI';
export const CATEGORY_API_SECURITY = 'API Security';
export const CATEGORY_AGENTIC_SECURITY = 'Agentic Security';
export const SUB_CATEGORY_CLOUD_SECURITY = 'Cloud Security';
export const SUB_CATEGORY_ENDPOINT_SECURITY = 'Endpoint Security';

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

export function getSubCategory() {
  try {
    const category = PersistStore.getState().subCategory
    return category
  } catch(e){
    return SUB_CATEGORY_CLOUD_SECURITY
  }
}

export function isSubCategory(category) {
  return getSubCategory() === category;
}

export function shouldShowLeftNavSwitch() {
  return isMCPSecurityCategory() || isAgenticSecurityCategory();
}
