import func from "@/util/func"

// One ascending/descending pair per sortable header. The table starts on the first option, so the default sort goes first.
// A key ending in "Ts" holds a number: the "asc" option lists the largest first; a text key lists A-Z.
export const buildSortOptions = (headers, defaultSortKey) => {
    const options = headers.flatMap((header, index) => {
        if (!header.sortKey) return []
        const numeric = header.sortKey.endsWith('Ts')
        const [first, second] = header.sortLabels || (numeric ? ['Newest', 'Oldest'] : ['A-Z', 'Z-A'])
        return [
            { label: header.text, value: `${header.sortKey} asc`, directionLabel: first, sortKey: header.sortKey, columnIndex: index + 1 },
            { label: header.text, value: `${header.sortKey} desc`, directionLabel: second, sortKey: header.sortKey, columnIndex: index + 1 },
        ]
    })
    return options.sort((a, b) => (b.sortKey === defaultSortKey) - (a.sortKey === defaultSortKey))
}

export const disambiguateLabel = (key, value) => func.convertToDisambiguateLabelObj(value, null, 2)
