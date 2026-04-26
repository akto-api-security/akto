import request from '../../../../util/api'

const API_BASE = '/api/integration/newrelic'

/**
 * Validate API key format
 */
const validateApiKey = (apiKey) => {
  if (!apiKey) {
    throw new Error('API key is required')
  }
  if (!/^(.*)$/.test(apiKey)) {
    throw new Error(
      'API key must start with NRA followed by alphanumeric characters'
    )
  }
}

/**
 * Validate account ID format
 */
const validateAccountId = (accountId) => {
  if (!accountId) {
    throw new Error('Account ID is required')
  }
  if (!/^\d+$/.test(accountId)) {
    throw new Error('Account ID must contain only numeric digits')
  }
}

/**
 * Validate region
 */
const validateRegion = (region) => {
  if (!region) {
    throw new Error('Region is required')
  }
  if (!['US', 'EU'].includes(region)) {
    throw new Error('Region must be US or EU')
  }
}

/**
 * Get current NewRelic integration configuration
 */
export const getNewRelicIntegration = async () => {
  try {
    const response = await request({
      url: `${API_BASE}/get`,
      method: 'get',
      timeout: 10000,
    })

    console.info('[API] Get NewRelic integration succeeded')
    return response.data
  } catch (error) {
    console.error('[API] Get NewRelic integration failed:', error)
    throw error
  }
}

/**
 * Test NewRelic connection with provided credentials
 */
export const testNewRelicConnection = async ({apiKey, accountId, region}) => {
  // Validate inputs before sending to API
  try {
    validateApiKey(apiKey)
    validateAccountId(accountId)
    validateRegion(region)
  } catch (validationError) {
    throw validationError
  }

  try {
    const response = await request({
      url: `/api/testNewRelicIntegration`,
      method: 'post',
      data: {
        apiKey,
        accountId,
        region,
      },
      timeout: 15000,
    })

    console.info('[API] Test NewRelic connection succeeded')
    return response.data
  } catch (error) {
    console.error('[API] Test NewRelic connection failed:', error)
    throw error
  }
}

/**
 * Save NewRelic integration configuration
 */
export const saveNewRelicIntegration = async (apiKey, accountId, region) => {
  // Validate inputs
  try {
    validateApiKey(apiKey)
    validateAccountId(accountId)
    validateRegion(region)
  } catch (validationError) {
    throw validationError
  }

  try {
    const response = await request({
      url: `${API_BASE}/save`,
      method: 'post',
      data: {
        apiKey,
        accountId,
        region,
      },
      timeout: 10000,
    })

    console.info('[API] Save NewRelic integration succeeded')
    return response.data
  } catch (error) {
    console.error('[API] Save NewRelic integration failed:', error)
    throw error
  }
}

/**
 * Disable NewRelic integration
 */
export const disableNewRelicIntegration = async () => {
  try {
    const response = await request({
      url: `${API_BASE}/disable`,
      method: 'post',
      data: {},
      timeout: 10000,
    })

    console.info('[API] Disable NewRelic integration succeeded')
    return response.data
  } catch (error) {
    console.error('[API] Disable NewRelic integration failed:', error)
    throw error
  }
}
