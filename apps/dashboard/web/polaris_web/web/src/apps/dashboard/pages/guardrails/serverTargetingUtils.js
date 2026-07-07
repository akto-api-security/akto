import { groupCollectionsByAgent, extractServiceName } from '../observe/agentic/constants';
import { isEndpointSecurityCategory } from '../../../main/labelHelper';

export const isVisibilityOnly = (collection) =>
    collection.envType?.some(tag =>
        tag.keyName === 'visibilityOnly' && tag.value === 'true'
    );

const groupToOption = (g) => ({
    label: g.groupKey,
    value: g.groupKey,
});

export const buildAgentFilterOptions = (allCollections) => {
    const nonVisibility = (allCollections || []).filter(c => !isVisibilityOnly(c));
    if (isEndpointSecurityCategory()) {
        const agentGroups = groupCollectionsByAgent(nonVisibility);
        return agentGroups.map(groupToOption);
    }
    const toOption = (c) => {
        const name = c.hostName || c.displayName || c.name || '';
        return { label: name, value: name };
    };
    const dedup = (opts) => [...new Map(opts.map(o => [o.value, o])).values()].filter(o => o.value);
    return dedup(
        nonVisibility
            .filter(c => c.envType?.some(t => t.keyName === 'gen-ai'))
            .map(toOption)
    );
};

export const getLlmServiceKeySet = (allCollections) => {
    const keys = new Set();
    (allCollections || []).forEach(c => {
        if (c.envType?.some(e => e.keyName === 'browser-llm')) {
            const rawName = c.hostName || c.displayName || '';
            const svcKey = extractServiceName(rawName) || rawName;
            if (svcKey) keys.add(svcKey);
        }
    });
    return keys;
};

export const resolveServerEntryKey = (entry, allCollections) => {
    const col = (allCollections || []).find(c => c.id?.toString() === entry.id?.toString());
    const rawName = col ? (col.hostName || col.displayName || '') : (entry.name || String(entry.id || ''));
    if (!isEndpointSecurityCategory()) return rawName;
    return extractServiceName(rawName) || rawName;
};

export const splitAgentServersV2 = (rawEntries, allCollections) => {
    const llmKeySet = getLlmServiceKeySet(allCollections);
    const agents = [];
    const llms = [];
    (rawEntries || []).forEach(s => {
        const col = (allCollections || []).find(c => c.id?.toString() === s.id?.toString());
        const isLlm = col
            ? col.envType?.some(e => e.keyName === 'browser-llm')
            : llmKeySet.has(s.name || '');
        if (isLlm) llms.push(s);
        else agents.push(s);
    });
    return { agents, llms };
};

export const getApplicableAgentKeys = (policy, allCollections, agentOptions) => {
    if (policy.applyToAllServers === true || policy.applyToAllServers == null) {
        return (agentOptions || []).map(o => o.value).filter(Boolean);
    }

    const raw = policy.selectedAgentServersV2?.length > 0
        ? policy.selectedAgentServersV2
        : (policy.selectedAgentServers || []).map(id => ({ id, name: id }));
    const { agents } = splitAgentServersV2(raw, allCollections);

    const keys = new Set();
    agents.forEach(entry => {
        const resolved = resolveServerEntryKey(entry, allCollections);
        if (resolved) keys.add(resolved);
    });
    return [...keys];
};

export const policyAppliesToAgent = (policy, agentKey, allCollections, agentOptions = []) => {
    if (!agentKey) return true;
    if (policy.applyToAllServers === true || policy.applyToAllServers == null) return true;
    return getApplicableAgentKeys(policy, allCollections, agentOptions).includes(agentKey);
};

export const getAgentFilterValues = (filters) => {
    const agentFilter = filters?.agent;
    if (!agentFilter) return [];
    if (Array.isArray(agentFilter)) return agentFilter;
    if (agentFilter?.values) return agentFilter.values || [];
    return [agentFilter];
};

export const applyAgentFilterToRows = (rows, filters) => {
    const agentValues = getAgentFilterValues(filters);
    if (!agentValues.length) return rows;
    return rows.filter(row => (row.agent || []).some(v => agentValues.includes(v)));
};
