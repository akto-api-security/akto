// The collections a script can be written for or validated against, as dropdown options. Deactivated
// collections are left out: the backend refuses to replay them.
export function collectionLabel(collection) {
    return collection.displayName || collection.name || String(collection.id)
}

export function collectionOptions(allCollections) {
    return (allCollections || [])
        .filter((c) => !c.deactivated)
        .map((c) => ({ label: collectionLabel(c), value: c.id }))
}
