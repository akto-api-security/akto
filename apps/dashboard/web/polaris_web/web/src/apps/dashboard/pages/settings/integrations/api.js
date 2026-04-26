import request from '@/util/request'

/**
 * NewRelic Integration API Endpoints
 */
const apiRequests = {
  /**
   * Get NewRelic integration configuration
   */
  getNewRelicIntegration: async () => {
    const response = await request({
      url: '/api/integration/newrelic/get',
      method: 'post',
      data: {}
    })
    return response
  },

  /**
   * Save NewRelic integration configuration
   */
  saveNewRelicIntegration: async ({ apiKey, accountId, region }) => {
    const response = await request({
      url: '/api/integration/newrelic/save',
      method: 'post',
      data: {
        apiKey,
        accountId,
        region
      }
    })
    return response
  },

  /**
   * Test NewRelic connection
   */
  testNewRelicConnection: async ({apiKey, accountId, region}) => {
    const response = await request({
      url: '/api/testNewRelicIntegration',
      method: 'post',
      data: {
        apiKey,
        accountId,
        region
      }
    })
    return response
  },

  /**
   * Disable NewRelic integration
   */
  disableNewRelicIntegration: async () => {
    const response = await request({
      url: '/api/integration/newrelic/disable',
      method: 'post',
      data: {}
    })
    return response
  }
}

export default apiRequests
