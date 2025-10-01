import SessionStore from "../../../../main/SessionStore";

export function updateThreatFiltersStore(templates, shortHand) {
    if (!Array.isArray(templates)) return;
    const maps = { mcp: {}, gen: {}, api: {} };
    templates.forEach((x) => {
        const trimmed = { ...x, content: '', ...x.info };
        const name = (trimmed?.category?.name || '').toLowerCase();
        delete trimmed['info'];

        if (name?.includes("mcp")) {
            maps.mcp[x.id] = trimmed;
        } else if (name?.includes("gen")) {
            maps.gen[x.id] = trimmed;
        } else {
            maps.api[x.id] = trimmed;
        }
    });

    try {
        const setThreatFiltersMap = SessionStore.getState().setThreatFiltersMap;
        if (typeof setThreatFiltersMap === 'function') {
            setThreatFiltersMap(maps[shortHand]);
        }
    } catch (e) {
        // best-effort
        console.error(`Failed to update threat filters store: ${e?.message}`);
    }
}


