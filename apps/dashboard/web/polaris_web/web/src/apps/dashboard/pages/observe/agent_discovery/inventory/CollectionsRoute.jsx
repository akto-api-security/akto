import { Navigate } from 'react-router-dom'
import { usePermissions } from '@/util/permissions'
import { isAgenticSecurityCategory } from '../../../../../main/labelHelper'
import ApiCollections from '../../api_collections/ApiCollections'
import { ALL_ASSETS_URL, INVENTORY_PATH } from './inventoryUtils'

// Argus has no Collections page in its nav, so the Collections URL opens the inventory's All assets tab; other products keep Collections.
function CollectionsRoute() {
    const { canOpen } = usePermissions()
    if (isAgenticSecurityCategory() && canOpen(INVENTORY_PATH)) return <Navigate to={ALL_ASSETS_URL} replace />
    return <ApiCollections />
}

export default CollectionsRoute
