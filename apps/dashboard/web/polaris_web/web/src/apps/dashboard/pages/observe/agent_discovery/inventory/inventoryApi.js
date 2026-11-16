import dashboardApi from '../../../dashboard/api'
import observeApi from '../../api'

export const fetchInventory = () => dashboardApi.fetchArgusInventory()
export const fetchAgents = (params) => dashboardApi.fetchArgusInventoryAgents(params)
// Revoking a quiet identity is the existing NHI disable.
export const revokeIdentity = (identityId) => observeApi.disableNhiIdentity(identityId)
