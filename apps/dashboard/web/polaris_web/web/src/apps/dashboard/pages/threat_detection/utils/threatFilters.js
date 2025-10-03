import { isAgenticSecurityCategory, isApiSecurityCategory, isGenAISecurityCategory, isMCPSecurityCategory } from "../../../../main/labelHelper";
import SessionStore from "../../../../main/SessionStore";

export function updateThreatFiltersStore(templates) {
    if (!Array.isArray(templates)) return;
    let filteredTemplates = {}
    let filteredTemplatesArray = []
    templates.forEach((x) => {
        const trimmed = { ...x, content: '', ...x.info };
        const name = (trimmed?.category?.name || '').toLowerCase();
        delete trimmed['info'];

        if (name?.includes("mcp") && (isMCPSecurityCategory() || isAgenticSecurityCategory())) {
            filteredTemplates[x.id] = trimmed;
            filteredTemplatesArray.push(x)
        } else if (name?.includes("gen") && (isGenAISecurityCategory() || isAgenticSecurityCategory())) {
            filteredTemplates[x.id] = trimmed;
            filteredTemplatesArray.push(x)
        } else if (isApiSecurityCategory()) {
            filteredTemplates[x.id] = trimmed;
            filteredTemplatesArray.push(x)
        }
    });

    try {
        const setThreatFiltersMap = SessionStore.getState().setThreatFiltersMap;
        if (typeof setThreatFiltersMap === 'function') {
            setThreatFiltersMap(filteredTemplates);
        }
    } catch (e) {
        // best-effort
        console.error(`Failed to update threat filters store: ${e?.message}`);
    }
    return filteredTemplatesArray;
}


