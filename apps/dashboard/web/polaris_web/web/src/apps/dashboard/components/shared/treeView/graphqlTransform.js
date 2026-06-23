const OP_TYPES = new Set(['query', 'mutation', 'subscription'])

function parseGraphQLUrl(url) {
    if (!url) return null
    const parts = url.split('/').filter(Boolean)
    const opTypeIndex = parts.findIndex(p => OP_TYPES.has(p.toLowerCase()))
    if (opTypeIndex === -1) return null

    const operationType = parts[opTypeIndex].toLowerCase()
    const remaining = parts.slice(opTypeIndex + 1)

    if (remaining.length === 0) return null

    const segmentName = remaining[remaining.length - 1]
    const operationName = remaining.length > 1 ? remaining[remaining.length - 2] : null

    return { operationType, operationName, segmentName }
}

// Build flat rows with path arrays for AG Grid treeData.
// path = [operationType, groupKey, leafKey] (3 levels)
//        or [operationType, segmentName] (2 levels, no operationName)
function buildGraphQLFlatRows(endpoints, groupByOperation) {
    const rows = []
    endpoints.forEach(ep => {
        const parsed = parseGraphQLUrl(ep.endpoint)
        if (!parsed) return
        const { operationType, operationName, segmentName } = parsed

        let path
        if (!operationName) {
            path = [operationType, segmentName]
        } else if (groupByOperation) {
            path = [operationType, operationName, segmentName]
        } else {
            path = [operationType, segmentName, operationName]
        }

        rows.push({ ...ep, path })
    })
    return rows
}

export { buildGraphQLFlatRows, parseGraphQLUrl }
