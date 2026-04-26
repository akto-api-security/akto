import React, { useEffect, useState, useRef, useCallback } from 'react'
import {
  Card,
  Button,
  TextField,
  Select,
  Banner,
  Box,
  Stack,
  VerticalStack,
  HorizontalStack,
  Spinner,
  Text,
  Popover,
  ActionList,
} from '@shopify/polaris'
import IntegrationsLayout from './IntegrationsLayout'
import ConnectionStatusBadge from '../../../components/IntegrationSettings/ConnectionStatusBadge'
import { useIntegrationForm } from '../../../hooks/useIntegrationForm'
import { useIntegrationStore } from '@/store/useIntegrationStore'
import apiRequests from './api'

/**
 * NewRelic Integration Page
 * Allows users to configure and manage NewRelic integration for exporting runtime metrics
 */
export default function NewRelicIntegration() {
  const {
    newrelic,
    setNewRelicConfig,
    clearNewRelicConfig,
  } = useIntegrationStore()

  const {
    values,
    errors,
    touched,
    handleChange,
    handleBlur,
    validateForm,
    reset,
    setValues,
  } = useIntegrationForm({
    apiKey: '',
    accountId: '',
    region: 'US',
  })

  const [loading, setLoading] = useState(false)
  const [testing, setTesting] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isDisabling, setIsDisabling] = useState(false)
  const [regionPopoverActive, setRegionPopoverActive] = useState(false)
  const regionButtonRef = useRef(null)

  // Load existing config on mount
  useEffect(() => {
    const loadConfig = async () => {
      try {
        setLoading(true)
        const response = await apiRequests.getNewRelicIntegration()
        if (response && response.accountId) {
          setValues({
            apiKey: '',
            accountId: response.accountId,
            region: response.region || 'US',
          })
        }
      } catch (err) {
        console.error('Failed to load NewRelic config:', err)
      } finally {
        setLoading(false)
      }
    }

    loadConfig()
  }, [])

  const handleTestConnection = async () => {
    if (!validateForm()) {
      setErrorMessage('Please fix validation errors before testing')
      return
    }

    setTesting(true)
    setErrorMessage('')
    setSuccessMessage('')

    try {
      const response = await apiRequests.testNewRelicConnection({
        apiKey: values.apiKey,
        accountId: values.accountId,
        region: values.region,
      })

      setSuccessMessage(
        `Connected to NewRelic account: ${response.accountName || response.accountId}`
      )
    } catch (err) {
      setErrorMessage(
        err.response?.data?.message ||
        err.message ||
        'Failed to connect to NewRelic'
      )
    } finally {
      setTesting(false)
    }
  }

  const handleSave = async () => {
    if (!validateForm()) {
      setErrorMessage('Please fix validation errors')
      return
    }

    setLoading(true)
    setErrorMessage('')
    setSuccessMessage('')

    try {
      const response = await apiRequests.saveNewRelicIntegration({
        apiKey: values.apiKey,
        accountId: values.accountId,
        region: values.region,
      })

      setNewRelicConfig({
        accountId: response.accountId,
        region: response.region,
        enabled: true,
      })

      setSuccessMessage('NewRelic integration saved successfully!')
      reset()
    } catch (err) {
      setErrorMessage(
        err.response?.data?.message ||
        err.message ||
        'Failed to save configuration'
      )
    } finally {
      setLoading(false)
    }
  }

  const handleDisable = async () => {
    if (!window.confirm('Are you sure you want to disable NewRelic integration?')) {
      return
    }

    setIsDisabling(true)
    setErrorMessage('')
    setSuccessMessage('')

    try {
      await apiRequests.disableNewRelicIntegration()
      clearNewRelicConfig()
      setSuccessMessage('NewRelic integration disabled')
      reset()
    } catch (err) {
      setErrorMessage(
        err.response?.data?.message ||
        err.message ||
        'Failed to disable integration'
      )
    } finally {
      setIsDisabling(false)
    }
  }

  const handleRegionSelect = useCallback((region) => {
    handleChange('region', region)
    setRegionPopoverActive(false)
  }, [])

  // if (loading) {
  //   return (
  //     <IntegrationsLayout
  //       title="NewRelic Integration"
  //       description="Configure NewRelic to export API runtime metrics and monitoring data"
  //     >
  //       <Card>
  //         <Box padding="500" display="flex" justifyContent="center">
  //           <Spinner accessibilityLabel="Loading" />
  //         </Box>
  //       </Card>
  //     </IntegrationsLayout>
  //   )
  // }

  const isConfigured = newrelic && newrelic.accountId

  const NewRelicCard = <Card>
        <VerticalStack gap="500" padding="500">
          {/* Status Section */}
          <Box paddingBlockEnd="300">
            <VerticalStack gap="300">
              <Text as="h2" variant="headingMd">
                Connection Status
              </Text>
              <Box>
                <ConnectionStatusBadge
                  connected={isConfigured && newrelic.enabled}
                  lastSyncTime={newrelic?.lastSyncTime}
                  accountId={newrelic?.accountId}
                />
              </Box>
            </VerticalStack>
          </Box>

          {/* Messages */}
          {successMessage && (
            <Banner tone="success">
              <p>{successMessage}</p>
            </Banner>
          )}
          {errorMessage && (
            <Banner tone="critical">
              <p>{errorMessage}</p>
            </Banner>
          )}

          {/* Configuration Form */}
          {!isConfigured ? (
            <Box paddingBlockEnd="600">
              <VerticalStack gap="400">
                <Text as="h3" variant="headingMd">
                  Set Up NewRelic Integration
                </Text>

                <TextField
                  label="API Key"
                  type="password"
                  value={values.apiKey}
                  onChange={(value) => handleChange('apiKey', value)}
                  onBlur={() => handleBlur('apiKey')}
                  error={touched.apiKey && errors.apiKey ? errors.apiKey : ''}
                  placeholder="NRAA..."
                  helpText="NewRelic API key starting with NRAA (62 characters total)"
                  required
                />

                <TextField
                  label="Account ID"
                  value={values.accountId}
                  onChange={(value) => handleChange('accountId', value)}
                  onBlur={() => handleBlur('accountId')}
                  error={touched.accountId && errors.accountId ? errors.accountId : ''}
                  placeholder="e.g., 1234567"
                  helpText="Your NewRelic account ID (numeric)"
                  required
                />

                <Box>
                  <Text as="label" variant="bodySm">
                    Region
                  </Text>
                  <Popover
                    active={regionPopoverActive}
                    onClose={() => setRegionPopoverActive(false)}
                    activator={
                      <button
                        ref={regionButtonRef}
                        onClick={() => setRegionPopoverActive(!regionPopoverActive)}
                        style={{
                          width: '100%',
                          padding: '8px 12px',
                          border: '1px solid #d0d0d0',
                          borderRadius: '4px',
                          fontSize: '14px',
                          textAlign: 'left',
                          cursor: 'pointer',
                          backgroundColor: '#fff',
                          marginTop: '4px',
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <span>{values.region}</span>
                        <span>▼</span>
                      </button>
                    }
                  >
                    <ActionList
                      items={[
                        {
                          content: 'US',
                          onAction: () => handleRegionSelect('US'),
                        },
                        {
                          content: 'EU',
                          onAction: () => handleRegionSelect('EU'),
                        },
                      ]}
                    />
                  </Popover>
                </Box>

                <div style={{ marginTop: '16px' }}>
                  <HorizontalStack>
                    <div style={{ marginRight: '8px' }}>
                      <Button
                        onClick={handleTestConnection}
                        loading={testing}
                        disabled={testing || loading}
                      >
                        Test Connection
                      </Button>
                    </div>
                    <Button
                      primary
                      onClick={handleSave}
                      loading={loading}
                      disabled={loading || testing}
                    >
                      Save Configuration
                    </Button>
                  </HorizontalStack>
                </div>
              </VerticalStack>
            </Box>
          ) : (
            <Box>
              <VerticalStack gap="400">
                <Box>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    <strong>Account ID:</strong> {newrelic.accountId}
                  </Text>
                  <Text as="p" variant="bodyMd" tone="subdued">
                    <strong>Region:</strong> {newrelic.region || 'US'}
                  </Text>
                </Box>

                <Button
                  tone="critical"
                  onClick={handleDisable}
                  loading={isDisabling}
                  disabled={isDisabling}
                >
                  Disable Integration
                </Button>
              </VerticalStack>
            </Box>
          )}
        </VerticalStack>
      </Card>

  return (
    <IntegrationsLayout
      title="NewRelic Integration"
      cardContent="Configure NewRelic to export API runtime metrics and monitoring data"
      component={NewRelicCard} docsUrl=""
    >
      
    </IntegrationsLayout>
  )
}
