import request from '../../../../../../util/request'

const API_BASE = '/api/linear'

/**
 * Get current Linear integration configuration
 */
export const getLinearIntegration = async () => {
  try {
    const response = await request({
      url: `${API_BASE}/config`,
      method: 'get',
    })
    return response
  } catch (err) {
    console.error('[Linear] Error fetching integration config:', err)
    throw err
  }
}

/**
 * Test Linear connection with workspace URL and API token
 */
export const testLinearConnection = async (workspaceUrl, apiToken) => {
  try {
    if (!workspaceUrl || !apiToken) {
      throw new Error('Workspace URL and API token are required')
    }

    const response = await request({
      url: `${API_BASE}/test`,
      method: 'post',
      data: {
        workspaceUrl,
        apiToken,
      },
    })
    return response
  } catch (err) {
    console.error('[Linear] Error testing connection:', err)
    throw err
  }
}

/**
 * Save Linear integration configuration
 */
export const saveLinearIntegration = async (config) => {
  try {
    if (!config.workspaceUrl || !config.apiToken || !config.defaultProjectId || !config.defaultTeamId) {
      throw new Error('Workspace URL, API token, project ID, and team ID are required')
    }

    const response = await request({
      url: `${API_BASE}/save`,
      method: 'post',
      data: {
        workspaceUrl: config.workspaceUrl,
        apiToken: config.apiToken,
        defaultProjectId: config.defaultProjectId,
        defaultTeamId: config.defaultTeamId,
        severityToPriorityMap: config.severityToPriorityMap || {},
        issueTemplate: config.issueTemplate || {},
      },
    })
    return response
  } catch (err) {
    console.error('[Linear] Error saving integration:', err)
    throw err
  }
}

/**
 * Disable Linear integration
 */
export const disableLinearIntegration = async () => {
  try {
    const response = await request({
      url: `${API_BASE}/disable`,
      method: 'post',
      data: {},
    })
    return response
  } catch (err) {
    console.error('[Linear] Error disabling integration:', err)
    throw err
  }
}

/**
 * Get list of created Linear issues
 */
export const getLinearIssues = async (params = {}) => {
  try {
    const queryParams = new URLSearchParams()
    if (params.status) queryParams.append('status', params.status)
    if (params.severity) queryParams.append('severity', params.severity)
    if (params.apiId) queryParams.append('apiId', params.apiId)
    if (params.page) queryParams.append('page', params.page)

    const url = `${API_BASE}/issues${queryParams.toString() ? '?' + queryParams.toString() : ''}`

    const response = await request({
      url,
      method: 'get',
    })
    return response
  } catch (err) {
    console.error('[Linear] Error fetching issues:', err)
    throw err
  }
}

/**
 * Create a Linear issue manually for a finding
 */
export const createLinearIssue = async (findingData) => {
  try {
    if (!findingData.findingType || !findingData.findingId || !findingData.apiId) {
      throw new Error('Finding type, ID, and API ID are required')
    }

    const response = await request({
      url: `${API_BASE}/issue/create`,
      method: 'post',
      data: {
        findingType: findingData.findingType,
        findingId: findingData.findingId,
        apiId: findingData.apiId,
        apiName: findingData.apiName || '',
        severityLevel: findingData.severityLevel || 'MEDIUM',
        description: findingData.description || '',
        requestPayload: findingData.requestPayload || null,
        responsePayload: findingData.responsePayload || null,
      },
    })
    return response
  } catch (err) {
    console.error('[Linear] Error creating issue:', err)
    throw err
  }
}
