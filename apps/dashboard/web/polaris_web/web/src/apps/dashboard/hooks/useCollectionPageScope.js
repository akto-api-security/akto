import { useCallback, useMemo } from 'react'
import { getDashboardCategory, isAgenticSecurityCategory, mapLabel } from '@/apps/main/labelHelper'
import PersistStore from '@/apps/main/PersistStore'

function scopeAllCollectionsRowLabel() {
    if (isAgenticSecurityCategory()) {
        return 'All Agentic collections'
    }
    return 'All ' + mapLabel('API', getDashboardCategory()) + ' collections'
}

function buildCollectionScopeSearchOptions(allCollections) {
    const rows = (allCollections || [])
        .filter((c) => c?.type !== 'API_GROUP')
        .map((c) => {
            const name = c.displayName ?? c.name
            const label =
                name != null && String(name).trim() !== '' ? String(name) : `Collection ${c.id}`
            return { label, value: c.id }
        })
    return [
        { label: scopeAllCollectionsRowLabel(), value: 'all' },
        ...rows,
    ]
}

/**
 * Shared page-level API collection filter backed by PersistStore so the
 * selection survives page navigation and full page refreshes.
 */
export function useCollectionPageScope(allCollections) {
    const selectedCollectionId = PersistStore((state) => state.selectedCollectionScope)
    const setSelectedCollectionScope = PersistStore((state) => state.setSelectedCollectionScope)

    const collectionSearchOptions = useMemo(
        () => buildCollectionScopeSearchOptions(allCollections),
        [allCollections],
    )

    const pageScopeApiCollectionIds = useMemo(
        () => (selectedCollectionId == null ? [] : [selectedCollectionId]),
        [selectedCollectionId],
    )

    const collectionScopeLabel = useMemo(() => {
        if (selectedCollectionId == null) {
            return collectionSearchOptions[0]?.label ?? ''
        }
        const found = collectionSearchOptions.find((o) => o.value === selectedCollectionId)
        return found?.label ?? ''
    }, [selectedCollectionId, collectionSearchOptions])

    const onCollectionScopeSelect = useCallback((val) => {
        setSelectedCollectionScope(val === 'all' || val == null || val === '' ? null : Number(val))
    }, [setSelectedCollectionScope])

    const collectionScopePreSelected = useMemo(
        () => (selectedCollectionId == null ? ['all'] : [selectedCollectionId]),
        [selectedCollectionId],
    )

    const collectionSearchPlaceholder = mapLabel('API', getDashboardCategory()) + ' collection'

    return {
        collectionSearchOptions,
        selectedCollectionId,
        pageScopeApiCollectionIds,
        collectionScopeLabel,
        onCollectionScopeSelect,
        collectionScopePreSelected,
        collectionSearchPlaceholder,
    }
}
