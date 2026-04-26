import SessionStore from '../../main/SessionStore'

/**
 * useIntegrationStore Hook
 * Wrapper around SessionStore for NewRelic integration state
 */
export const useIntegrationStore = () => {
  // Get NewRelic state from SessionStore
  const newrelic = SessionStore(state => state.newrelic)
  const setNewRelicConfig = SessionStore(state => state.setNewRelicConfig)
  const clearNewRelicConfig = SessionStore(state => state.clearNewRelicConfig)

  return {
    newrelic,
    setNewRelicConfig,
    clearNewRelicConfig
  }
}
