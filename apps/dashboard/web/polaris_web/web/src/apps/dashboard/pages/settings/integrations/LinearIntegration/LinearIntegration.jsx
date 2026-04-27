import React, { useEffect, useState } from 'react'
import {
  Page,
  Card,
  FormLayout,
  TextField,
  Select,
  Button,
  Banner,
  Text,
  Box,
  Spinner,
  Modal,
  Badge,
  Divider,
  VerticalStack,
  HorizontalStack,
} from '@shopify/polaris'
import SessionStore from '../../../../../main/SessionStore'
import {
  getLinearIntegration,
  testLinearConnection,
  saveLinearIntegration,
  disableLinearIntegration,
  getLinearIssues,
} from './api'
import LinearIssuesList from './components/LinearIssuesList'
import LinearIssueTemplate from './components/LinearIssueTemplate'

export default function LinearIntegration() {
  const linear = SessionStore(state => state.linear)
  const setLinearConfig = SessionStore(state => state.setLinearConfig)
  const clearLinearConfig = SessionStore(state => state.clearLinearConfig)

  // Loading states
  const [loading, setLoading] = useState(true)
  const [formLoading, setFormLoading] = useState(false)
  const [testLoading, setTestLoading] = useState(false)
  const [issuesLoading, setIssuesLoading] = useState(false)

  // Messages
  const [error, setError] = useState(null)
  const [successMessage, setSuccessMessage] = useState(null)

  // Form state
  const [workspaceUrl, setWorkspaceUrl] = useState('')
  const [apiToken, setApiToken] = useState('')
  const [showApiToken, setShowApiToken] = useState(false)
  const [defaultProjectId, setDefaultProjectId] = useState('')
  const [defaultTeamId, setDefaultTeamId] = useState('')

  // Test results
  const [testResult, setTestResult] = useState(null)
  const [teams, setTeams] = useState([])
  const [projects, setProjects] = useState([])

  // Template and mapping
  const [issueTemplate, setIssueTemplate] = useState({
    title: '[{apiName}] {findingType}: {severity}',
    description: '{severity} severity {findingType} detected on {timestamp}',
    includeDetails: true,
    includeApiInfo: true,
    labels: ['security', 'akto'],
  })

  const [severityToPriorityMap, setSeverityToPriorityMap] = useState({
    HIGH: 'urgent',
    MEDIUM: 'high',
    LOW: 'medium',
  })

  // Issues list
  const [issues, setIssues] = useState([])
  const [issuesTotal, setIssuesTotal] = useState(0)
  const [issuesPage, setIssuesPage] = useState(1)
  const [issuesFilters, setIssuesFilters] = useState({})

  // Modal state
  const [showDisableModal, setShowDisableModal] = useState(false)

  // Load configuration on mount
  useEffect(() => {
    const loadConfig = async () => {
      try {
        setLoading(true)
        const response = await getLinearIntegration()

        if (response.data && response.data.isConfigured) {
          setWorkspaceUrl(response.data.workspaceUrl || '')
          setDefaultProjectId(response.data.defaultProjectId || '')
          setDefaultTeamId(response.data.defaultTeamId || '')
          setIssueTemplate(response.data.issueTemplate || issueTemplate)
          setSeverityToPriorityMap(response.data.severityToPriorityMap || severityToPriorityMap)
          setLinearConfig(response.data)
        }
      } catch (err) {
        console.error('[Linear] Error loading config:', err)
        if (err.response?.status !== 404) {
          setError('Failed to load integration configuration')
        }
      } finally {
        setLoading(false)
      }
    }

    loadConfig()
  }, [])

  // Load issues when configuration is ready
  useEffect(() => {
    if (linear?.isConfigured) {
      loadIssues()
    }
  }, [linear?.isConfigured, issuesPage])

  // Clear success message after 5 seconds
  useEffect(() => {
    if (successMessage) {
      const timer = setTimeout(() => setSuccessMessage(null), 5000)
      return () => clearTimeout(timer)
    }
  }, [successMessage])

  const loadIssues = async () => {
    try {
      setIssuesLoading(true)
      const response = await getLinearIssues({
        ...issuesFilters,
        page: issuesPage,
      })
      setIssues(response.data?.issues || [])
      setIssuesTotal(response.data?.total || 0)
    } catch (err) {
      console.error('[Linear] Error loading issues:', err)
      // Don't show error if integration not configured
      if (linear?.isConfigured) {
        setError('Failed to load issues')
      }
    } finally {
      setIssuesLoading(false)
    }
  }

  const validateInputs = () => {
    if (!workspaceUrl) {
      setError('Workspace URL is required')
      return false
    }

    if (!workspaceUrl.match(/^https:\/\/app\.linear\.app$/)) {
      setError('Workspace URL must be https://app.linear.app')
      return false
    }

    if (!apiToken) {
      setError('API token is required')
      return false
    }

    return true
  }

  const handleTestConnection = async () => {
    try {
      setTestLoading(true)
      setError(null)

      if (!validateInputs()) {
        setTestLoading(false)
        return
      }

      const result = await testLinearConnection(workspaceUrl, apiToken)
      console.log('[Linear] Test response:', JSON.stringify(result.data, null, 2))

      if (result.data && result.data.testResult && result.data.testResult.success) {
        setTeams(result.data.testResult.teams || [])
        setProjects(result.data.testResult.projects || [])
        setTestResult(result.data.testResult)
        setSuccessMessage('Connection successful!')
      } else {
        const errorMsg = result.data?.testResult?.message || result.data?.message || JSON.stringify(result.data?.testResult || result.data) || 'Connection test failed'
        console.log('[Linear] Error message:', errorMsg)
        setError(errorMsg)
        setTestResult(null)
      }
    } catch (err) {
      console.error('[Linear] Test connection error:', err)
      const errorMsg = err.response?.data?.actionErrors?.[0] || err.message || 'Connection test failed'
      setError(errorMsg)
      setTestResult(null)
    } finally {
      setTestLoading(false)
    }
  }

  const handleSave = async () => {
    try {
      setFormLoading(true)
      setError(null)

      if (!validateInputs()) {
        setFormLoading(false)
        return
      }

      if (!defaultProjectId || !defaultTeamId) {
        setError('Please select a project and team')
        setFormLoading(false)
        return
      }

      const config = {
        workspaceUrl,
        apiToken,
        defaultProjectId,
        defaultTeamId,
        severityToPriorityMap,
        issueTemplate,
      }

      const response = await saveLinearIntegration(config)
      setLinearConfig(response.data)
      setSuccessMessage('Integration saved successfully!')
      setApiToken('')
      setTestResult(null)
      loadIssues()
    } catch (err) {
      console.error('[Linear] Save error:', err)
      const errorMsg =
        err.response?.data?.actionErrors?.[0] || err.message || 'Failed to save integration'
      setError(errorMsg)
    } finally {
      setFormLoading(false)
    }
  }

  const handleDisable = async () => {
    try {
      setFormLoading(true)
      setShowDisableModal(false)
      await disableLinearIntegration()
      clearLinearConfig()
      setSuccessMessage('Integration disabled')
      setWorkspaceUrl('')
      setApiToken('')
      setDefaultProjectId('')
      setDefaultTeamId('')
      setTestResult(null)
      setIssues([])
    } catch (err) {
      console.error('[Linear] Disable error:', err)
      setError('Failed to disable integration')
    } finally {
      setFormLoading(false)
    }
  }

  const isConfigured = linear?.isConfigured || false

  if (loading) {
    return (
      <Page title="Linear Integration">
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

  const projectOptions = projects
    .filter((p) => !defaultTeamId || p.teamId === defaultTeamId)
    .map((p) => ({
      label: p.projectName,
      value: p.projectId,
    }))

  const teamOptions = teams.map((t) => ({
    label: t.teamName,
    value: t.teamId,
  }))

  return (
    <Page title="Linear Integration" subtitle="Configure Linear workspace for issue tracking">
      <VerticalStack gap="4">
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

        {/* Connection Status Card */}
        <Card title="Connection Status">
          <Box padding="4">
            <VerticalStack gap="4">
              <HorizontalStack distribution="fill" blockAlign="center">
                <div>
                  <Text as="p" variant="bodyMd">
                    Status
                  </Text>
                  <Box marginBlockStart="2">
                    <Badge tone={isConfigured ? 'success' : 'default'}>
                      {isConfigured ? 'Connected' : 'Not Connected'}
                    </Badge>
                  </Box>
                </div>
                {isConfigured && (
                  <div>
                    <Text as="p" variant="bodySm" tone="subdued">
                      Workspace: {linear?.workspaceUrl}
                    </Text>
                  </div>
                )}
              </HorizontalStack>
            </VerticalStack>
          </Box>
        </Card>

        {/* Configuration Card */}
        <Card title="Configuration">
          <Box padding="4">
            <FormLayout>
              <TextField
                label="Workspace URL"
                value={workspaceUrl}
                onChange={setWorkspaceUrl}
                placeholder="https://app.linear.app"
                disabled={formLoading || testLoading}
                helpText="Your Linear workspace URL"
                type="url"
              />

              <TextField
                label="API Token"
                value={showApiToken ? apiToken : '●'.repeat(Math.min(apiToken.length, 20))}
                onChange={setApiToken}
                placeholder="lin_api_..."
                type={showApiToken ? 'text' : 'password'}
                disabled={formLoading || testLoading}
                helpText="Create an API token in Linear workspace settings"
                connectedRight={
                  <Button
                    onClick={() => setShowApiToken(!showApiToken)}
                    disabled={!apiToken}
                    accessibilityLabel={showApiToken ? 'Hide token' : 'Show token'}
                  >
                    {showApiToken ? 'Hide' : 'Show'}
                  </Button>
                }
              />

              <HorizontalStack gap="4">
                <div style={{ flex: 1 }}>
                  {teamOptions.length > 0 ? (
                    <Select
                      label="Team"
                      options={[{ label: 'Select a team', value: '' }, ...teamOptions]}
                      value={defaultTeamId}
                      onChange={setDefaultTeamId}
                      disabled={testLoading || formLoading}
                    />
                  ) : (
                    <TextField
                      label="Team ID"
                      value={defaultTeamId}
                      onChange={setDefaultTeamId}
                      placeholder="Enter team ID (e.g., TEAM-123)"
                      disabled={testLoading || formLoading}
                      helpText="Enter Linear team ID manually or run Test Connection to populate"
                    />
                  )}
                </div>
                <div style={{ flex: 1 }}>
                  {projectOptions.length > 0 ? (
                    <Select
                      label="Project"
                      options={[{ label: 'Select a project', value: '' }, ...projectOptions]}
                      value={defaultProjectId}
                      onChange={setDefaultProjectId}
                      disabled={testLoading || formLoading}
                    />
                  ) : (
                    <TextField
                      label="Project ID"
                      value={defaultProjectId}
                      onChange={setDefaultProjectId}
                      placeholder="Enter project ID (e.g., PROJ-456)"
                      disabled={testLoading || formLoading}
                      helpText="Enter Linear project ID manually or run Test Connection to populate"
                    />
                  )}
                </div>
              </HorizontalStack>

              <HorizontalStack gap="2">
                <Button
                  onClick={handleTestConnection}
                  loading={testLoading}
                  disabled={formLoading}
                  accessibilityLabel="Test connection"
                >
                  Test Connection
                </Button>
                <Button
                  variant="primary"
                  onClick={handleSave}
                  loading={formLoading}
                  disabled={testLoading || !defaultTeamId || !defaultProjectId}
                  accessibilityLabel="Save configuration"
                >
                  Save Configuration
                </Button>
                {isConfigured && (
                  <Button
                    tone="critical"
                    onClick={() => setShowDisableModal(true)}
                    disabled={formLoading || testLoading}
                    accessibilityLabel="Disable integration"
                  >
                    Disable Integration
                  </Button>
                )}
              </HorizontalStack>
            </FormLayout>
          </Box>
        </Card>

        {/* Severity Mapping Card */}
        <Card title="Severity to Priority Mapping">
          <Box padding="4">
            <Text as="p" variant="bodySm" tone="subdued">
              Configure how Akto severity levels map to Linear priorities
            </Text>
            <Box marginBlockStart="4">
              <FormLayout>
                <Select
                  label="High Severity → Priority"
                  options={[
                    { label: 'Urgent', value: 'urgent' },
                    { label: 'High', value: 'high' },
                    { label: 'Medium', value: 'medium' },
                    { label: 'Low', value: 'low' },
                  ]}
                  value={severityToPriorityMap.HIGH || 'urgent'}
                  onChange={(value) =>
                    setSeverityToPriorityMap({ ...severityToPriorityMap, HIGH: value })
                  }
                />

                <Select
                  label="Medium Severity → Priority"
                  options={[
                    { label: 'Urgent', value: 'urgent' },
                    { label: 'High', value: 'high' },
                    { label: 'Medium', value: 'medium' },
                    { label: 'Low', value: 'low' },
                  ]}
                  value={severityToPriorityMap.MEDIUM || 'high'}
                  onChange={(value) =>
                    setSeverityToPriorityMap({ ...severityToPriorityMap, MEDIUM: value })
                  }
                />

                <Select
                  label="Low Severity → Priority"
                  options={[
                    { label: 'Urgent', value: 'urgent' },
                    { label: 'High', value: 'high' },
                    { label: 'Medium', value: 'medium' },
                    { label: 'Low', value: 'low' },
                  ]}
                  value={severityToPriorityMap.LOW || 'medium'}
                  onChange={(value) =>
                    setSeverityToPriorityMap({ ...severityToPriorityMap, LOW: value })
                  }
                />
              </FormLayout>
            </Box>
          </Box>
        </Card>

        {/* Template Card */}
        {isConfigured && <LinearIssueTemplate template={issueTemplate} onTemplateChange={setIssueTemplate} />}

        {/* Issues List */}
        {isConfigured && (
          <LinearIssuesList
            issues={issues}
            loading={issuesLoading}
            total={issuesTotal}
            page={issuesPage}
            pageSize={50}
            filters={issuesFilters}
            onFiltersChange={(newFilters) => {
              setIssuesFilters(newFilters)
              setIssuesPage(1)
            }}
            onPageChange={setIssuesPage}
            onRefresh={() => {
              setIssuesPage(1)
              loadIssues()
            }}
            onDeleteMapping={async (mappingId) => {
              // TODO: Implement delete mapping
              console.log('Delete mapping:', mappingId)
            }}
          />
        )}
      </VerticalStack>

      {/* Disable Confirmation Modal */}
      <Modal
        open={showDisableModal}
        onClose={() => setShowDisableModal(false)}
        title="Disable Linear Integration"
        primaryAction={{
          content: 'Disable',
          tone: 'critical',
          onAction: handleDisable,
          loading: formLoading,
        }}
        secondaryActions={[
          {
            content: 'Cancel',
            onAction: () => setShowDisableModal(false),
          },
        ]}
      >
        <Modal.Section>
          <Text as="p" variant="bodyMd">
            Are you sure you want to disable Linear integration? Existing issues in Linear will remain,
            but no new issues will be created.
          </Text>
        </Modal.Section>
      </Modal>
    </Page>
  )
}
