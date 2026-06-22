const OP_TYPES = new Set(['query', 'mutation', 'subscription'])

/**
 * Parse a GraphQL endpoint URL into its tree segments.
 * URL format: /graphql/{operationType}/{operationName}/{segmentName}
 */
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

/**
 * Build a 3-level tree from a flat list of GraphQL endpoints.
 *
 * Default (groupByOperation=false):
 *   operationType → fieldName (last segment) → operationName (leaf)
 *
 * Toggled (groupByOperation=true):
 *   operationType → operationName (second-to-last) → fieldName (leaf)
 */
function buildGraphQLTree(endpoints, groupByOperation) {
    const opTypeMap = {}

    endpoints.forEach(ep => {
        const parsed = parseGraphQLUrl(ep.endpoint)
        if (!parsed) return

        const { operationType, operationName, segmentName } = parsed

        if (!opTypeMap[operationType]) opTypeMap[operationType] = {}

        // Decide which segment is group (level 2) and which is leaf (level 3)
        const groupKey = groupByOperation && operationName ? operationName : segmentName
        const leafKey = groupByOperation && operationName ? segmentName : operationName

        if (leafKey) {
            if (!opTypeMap[operationType][groupKey]) opTypeMap[operationType][groupKey] = {}
            if (!opTypeMap[operationType][groupKey][leafKey]) opTypeMap[operationType][groupKey][leafKey] = []
            opTypeMap[operationType][groupKey][leafKey].push(ep)
        } else {
            // 3-part URL: direct child of operationType
            const DIRECT = '__direct__'
            if (!opTypeMap[operationType][DIRECT]) opTypeMap[operationType][DIRECT] = {}
            if (!opTypeMap[operationType][DIRECT][groupKey]) opTypeMap[operationType][DIRECT][groupKey] = []
            opTypeMap[operationType][DIRECT][groupKey].push(ep)
        }
    })

    const roots = []
    const ORDER = ['query', 'mutation', 'subscription']
    const opTypes = [...ORDER.filter(t => opTypeMap[t]), ...Object.keys(opTypeMap).filter(t => !ORDER.includes(t))]

    opTypes.forEach(opType => {
        const opTypeNode = {
            displayName: opType,
            level: opType,
            isTerminal: false,
            children: [],
            id: opType,
        }

        const groupMap = opTypeMap[opType]

        Object.keys(groupMap).forEach(groupName => {
            const leafMap = groupMap[groupName]

            if (groupName === '__direct__') {
                Object.keys(leafMap).forEach(name => {
                    const ep = leafMap[name][0]
                    opTypeNode.children.push({
                        displayName: name,
                        level: `${opType}#${name}`,
                        isTerminal: true,
                        children: [],
                        id: ep.id || `${opType}#${name}`,
                        ...ep,
                    })
                })
            } else {
                const groupLevel = `${opType}#${groupName}`
                const groupNode = {
                    displayName: groupName,
                    level: groupLevel,
                    isTerminal: false,
                    children: [],
                    id: groupLevel,
                }

                Object.keys(leafMap).forEach(leafName => {
                    const ep = leafMap[leafName][0]
                    groupNode.children.push({
                        displayName: leafName,
                        level: `${groupLevel}#${leafName}`,
                        isTerminal: true,
                        children: [],
                        id: ep.id || `${groupLevel}#${leafName}`,
                        ...ep,
                    })
                })

                opTypeNode.children.push(groupNode)
            }
        })

        if (opTypeNode.children.length > 0) {
            roots.push(opTypeNode)
        }
    })

    return roots
}

/**
 * Build a flat array of rows with `path` arrays for AG Grid treeData.
 * path = [operationType, groupKey, leafKey] (3 levels)
 *        or [operationType, segmentName] (2 levels, no operationName)
 */
function buildGraphQLFlatRows(endpoints, groupByOperation) {
    const rows = []
    endpoints.forEach(ep => {
        const parsed = parseGraphQLUrl(ep.endpoint)
        if (!parsed) return
        const { operationType, operationName, segmentName } = parsed

        let path
        if (!operationName) {
            // 3-part URL: /graphql/query/fieldName — no intermediate grouping
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

export { buildGraphQLTree, buildGraphQLFlatRows, parseGraphQLUrl }
