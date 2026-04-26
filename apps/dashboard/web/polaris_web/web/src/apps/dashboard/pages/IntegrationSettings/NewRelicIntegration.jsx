import React, { useEffect, useState } from 'react'
import {
  Page,
  Card,
  FormLayout,
  TextField,
  Select,
  Button,
  Banner,
  Stack,
  Text,
  Box,
  Spinner,
} from '@shopify/polaris'
import { useIntegrationStore } from '../../../../store/SessionStore'
import {
  getNewRelicIntegration,
  testNewRelicConnection,
  saveNewRelicIntegration,
  disableNewRelicIntegration,
} from './api'
import ConnectionStatusBadge from '../../../../components/IntegrationSettings/ConnectionStatusBadge'

export default function NewRelicIntegration() {
  const store = useIntegrationStore()
  const [loading, setLoading] = useState(true)
  const [formLoading, setFormLoading] = useState(false)
  const [testLoading, setTestLoading] = useState(false)
  const [error, setError] = useState(null)
  const [successMessage, setSuccessMessage] = useState(null)
  const [testResult, setTestResult] = useState(null)
  const [apiKey, setApiKey] = useState('')
  const [accountId, setAccountId] = useState('')
  const [region, setRegion] = useState('US')

  // Load current configuration on mount
  useEffect(() => {
    const loadIntegration = async () => {
      try {
        setLoading(true)
        const response = await getNewRelicIntegration()
        const data = response.data

        if (data) {
          setAccountId(data.accountId || '')
          setRegion(data.region || 'US')
          store.setNewRelicConfig(data)
        }
      } catch (err) {
        console.error('[NewRelic] Error loading integration:', err)
        if (err.response?.status !== 404) {
          setError('Failed to load integration configuration')
        }
      } finally {
        setLoading(false)
      }
    }

    loadIntegration()
  }, [store])

  // Clear success message after 5 seconds
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5000)
      return () => clearTimeout(timer)
    }
  }, [successMessage])

  const handleTestConnection = async () => {
    try {
      setTestLoading(true)
      setError(null)

      // Validate inputs
      if (!apiKey || !accountId || !region) {
        setError('Please fill in all fields')
        return
      }

      const result = await testNewRelicConnection(apiKey, accountId, region)
      setTestResult(result.data)
      setSuccessMessage('Connection successful!')
    } catch (err) {
      console.error('[NewRelic] Test connection error:', err)
      setError(err.response?.data?.error || 'Connection test failed')
      setTestResult(null)
    } finally {
      setTestLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      setFormLoading(true)
      setError(null)

      // Validate inputs
      if (!apiKey || !accountId || !region) {
        setError('Please fill in all fields')
        return
      }

      // Validate API key format
      if (!/^NRAA[A-Za-z0-9]{58}$/.test(apiKey)) {
        setError(
          'API key must start with NRAA followed by 58 alphanumeric characters'
        )
        return
      }

      // Validate account ID format
      if (!/^\d+$/.test(accountId)) {
        setError('Account ID must contain only numeric digits')
        return
      }

      const response = await saveNewRelicIntegration(apiKey, accountId, region)
      store.setNewRelicConfig(response.data)
      setSuccessMessage('Integration saved successfully!')
      setTestResult(null)
    } catch (err) {
      console.error('[NewRelic] Save error:', err)
      const errorMsg =
        err.response?.data?.actionErrors?.[0] || 'Failed to save integration'
      setError(errorMsg)
    } finally {
      setFormLoading(false)
    }
  }

  const handleDisable = async () => {
    if (
      !window.confirm(
        'Disable NewRelic integration? Metrics export will stop.'
      )
    ) {
      return
    }

    try {
      setFormLoading(true)
      await disableNewRelicIntegration()
      store.clearNewRelicConfig()
      setSuccessMessage('Integration disabled')
      setApiKey('')
      setTestResult(null)
    } catch (err) {
      console.error('[NewRelic] Disable error:', err)
      setError('Failed to disable integration')
    } finally {
      setFormLoading(false)
    }
  }

  if (loading) {
    return (
      <Page title="NewRelic Integration">
        <Card>
          <Box padding="5" textAlign="center">
            <Spinner />
            <Text as="p" variant="bodyMd">
              Loading configuration...
            </Text>
          </Box>
        </Card>
      </Page>
    )
  }

  return (
    <Page
      title="NewRelic Integration"
      subtitle="Export API metrics to NewRelic dashboard"
    >
      <Stack gap="4">
        {/* Status Card */}
        <Card>
          <Box padding="4">
            <Stack gap="2">
              <Text as="h2" variant="headingMd">
                Connection Status
              </Text>
              <ConnectionStatusBadge
                connected={store.newrelic?.enabled || false}
                lastSyncTime={store.newrelic?.lastSyncTime}
                accountId={store.newrelic?.accountId}
              />
            </Stack>
          </Box>
        </Card>

        {/* Success Banner */}
        {successMessage && (
          <Banner tone="success" onDismiss={() => setSuccessMessage(null)}>
            <Text as="p">{successMessage}</Text>
          </Banner>
        )}

        {/* Error Banner */}
        {error && (
          <Banner tone="critical" onDismiss={() => setError(null)}>
            <Text as="p">{error}</Text>
          </Banner>
        )}

        {/* Test Result Banner */}
        {testResult && (
          <Banner tone="success">
            <Text as="p">
              ✓ Connected to: <strong>{testResult.accountName}</strong> (Account{' '}
              {testResult.accountId})
            </Text>
          </Banner>
        )}

        {/* Configuration Card */}
        <Card>
          <Box padding="4">
            <FormLayout>
              <Text as="h3" variant="headingMd">
                Credentials
              </Text>

              <TextField
                label="API Key"
                type="password"
                value={apiKey}
                onChange={setApiKey}
                placeholder="NRAA..."
                helpText="License key from NewRelic (starts with NRAA)"
                disabled={formLoading || testLoading}
                requiredIndicator
              />

              <TextField
                label="Account ID"
                value={accountId}
                onChange={setAccountId}
                placeholder="12345678"
                helpText="Numeric ID from NewRelic account settings"
                disabled={formLoading || testLoading}
                requiredIndicator
              />

              <Select
                label="Region"
                options={[
                  { label: 'US', value: 'US' },
                  { label: 'EU', value: 'EU' },
                ]}
                value={region}
                onChange={setRegion}
                disabled={formLoading || testLoading}
              />

              <Stack gap="2">
                <Button
                  onClick={handleTestConnection}
                  loading={testLoading}
                  disabled={formLoading}
                  variant="secondary"
                >
                  Test Connection
                </Button>

                <Button
                  onClick={handleSave}
                  loading={formLoading}
                  disabled={testLoading}
                  primary
                >
                  {store.newrelic?.enabled ? 'Update' : 'Save'} Configuration
                </Button>

                {store.newrelic?.enabled && (
                  <Button
                    onClick={handleDisable}
                    loading={formLoading}
                    disabled={testLoading}
                    tone="critical"
                  >
                    Disable Integration
                  </Button>
                )}
              </Stack>
            </FormLayout>
          </Box>
        </Card>
      </Stack>
    </Page>
  )
}
