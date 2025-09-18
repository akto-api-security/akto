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

export function getDashboardCategory() {
  try {
    const category = PersistStore.getState().dashboardCategory
    return category
  } catch(e){
    return "API Security"
  }
}

export function isMCPSecurityCategory() {
  return getDashboardCategory() === 'MCP Security';
}

export function isGenAISecurityCategory() {
  return getDashboardCategory() === 'Gen AI';
}

export function isApiSecurityCategory() {
  return getDashboardCategory() === 'API Security';
}

export function isAgenticSecurityCategory() {
  return getDashboardCategory() === 'Agentic Security';
}
