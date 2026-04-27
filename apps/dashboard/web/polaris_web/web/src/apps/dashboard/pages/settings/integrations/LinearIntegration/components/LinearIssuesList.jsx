import React, { useState, useEffect } from 'react'
import {
  Card,
  IndexTable,
  Badge,
  Button,
  VerticalStack,
  HorizontalStack,
  Text,
  Box,
  TextField,
  Select,
  Pagination,
  Spinner,
  Modal,
  Banner,
  Link,
} from '@shopify/polaris'
import { ExternalMinor, DeleteMinor, RefreshMinor } from '@shopify/polaris-icons'

export default function LinearIssuesList({
  issues = [],
  loading = false,
  total = 0,
  page = 1,
  pageSize = 50,
  filters = {},
  onFiltersChange = () => {},
  onPageChange = () => {},
  onRefresh = () => {},
  onDeleteMapping = () => {},
}) {
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const [selectedMappingId, setSelectedMappingId] = useState(null)
  const [statusFilter, setStatusFilter] = useState(filters.status || '')
  const [severityFilter, setSeverityFilter] = useState(filters.severity || '')

  const handleStatusFilterChange = (value) => {
    setStatusFilter(value)
    onFiltersChange({ ...filters, status: value })
  }

  const handleSeverityFilterChange = (value) => {
    setSeverityFilter(value)
    onFiltersChange({ ...filters, severity: value })
  }

  const handleDeleteClick = (mappingId) => {
    setSelectedMappingId(mappingId)
    setDeleteConfirmOpen(true)
  }

  const handleDeleteConfirm = async () => {
    if (selectedMappingId) {
      await onDeleteMapping(selectedMappingId)
      setDeleteConfirmOpen(false)
      setSelectedMappingId(null)
    }
  }

  const formatDate = (timestamp) => {
    if (!timestamp) return 'N/A'
    const date = new Date(timestamp * 1000)
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const getSeverityColor = (severity) => {
    switch (severity?.toUpperCase()) {
      case 'HIGH':
        return 'critical'
      case 'MEDIUM':
        return 'warning'
      case 'LOW':
        return 'info'
      default:
        return 'default'
    }
  }

  const getStatusColor = (status) => {
    if (!status) return 'default'
    const lower = status.toLowerCase()
    if (lower === 'open') return 'warning'
    if (lower === 'in_progress') return 'info'
    if (lower === 'done' || lower === 'closed') return 'success'
    return 'default'
  }

  if (loading && issues.length === 0) {
    return (
      <Card>
        <Box padding="5" textAlign="center">
          <Spinner />
          <Text as="p" variant="bodyMd" tone="subdued">
            Loading issues...
          </Text>
        </Box>
      </Card>
    )
  }

  if (issues.length === 0) {
    return (
      <Card>
        <Box padding="5" textAlign="center">
          <Text as="p" variant="bodyMd" tone="subdued">
            No Linear issues created yet
          </Text>
          <Text as="p" variant="bodySm" tone="subdued">
            Configure the integration and threats/tests will automatically create issues
          </Text>
        </Box>
      </Card>
    )
  }

  const totalPages = Math.ceil(total / pageSize)

  return (
    <Card>
      <Box padding="4">
        <VerticalStack gap="4">
          {/* Header */}
          <HorizontalStack gap="2" distribution="fill">
            <Box>
              <Text as="h2" variant="headingMd">
                Created Issues ({total})
              </Text>
              <Text as="p" variant="bodySm" tone="subdued">
                View and manage Linear issues created from Akto findings
              </Text>
            </Box>
            <Box textAlign="right">
              <Button
                icon={RefreshMinor}
                onClick={onRefresh}
                loading={loading}
                accessibilityLabel="Refresh issues"
              >
                Refresh
              </Button>
            </Box>
          </HorizontalStack>

          {/* Filters */}
          <HorizontalStack gap="3" distribution="equalSpacing">
            <div style={{ flex: 1 }}>
              <Select
                label="Status"
                options={[
                  { label: 'All Status', value: '' },
                  { label: 'Open', value: 'open' },
                  { label: 'In Progress', value: 'in_progress' },
                  { label: 'Done', value: 'done' },
                ]}
                value={statusFilter}
                onChange={handleStatusFilterChange}
              />
            </div>
            <div style={{ flex: 1 }}>
              <Select
                label="Severity"
                options={[
                  { label: 'All Severity', value: '' },
                  { label: 'High', value: 'HIGH' },
                  { label: 'Medium', value: 'MEDIUM' },
                  { label: 'Low', value: 'LOW' },
                ]}
                value={severityFilter}
                onChange={handleSeverityFilterChange}
              />
            </div>
          </HorizontalStack>

          {/* Table */}
          <IndexTable
            resourceName={{ singular: 'issue', plural: 'issues' }}
            items={issues}
            headings={[
              { title: 'Issue Key' },
              { title: 'Title' },
              { title: 'Status' },
              { title: 'Severity' },
              { title: 'API' },
              { title: 'Created' },
              { title: 'Actions' },
            ]}
            rowMarkup={issues.map((issue) => (
              <IndexTable.Row key={issue.mappingId} id={issue.mappingId}>
                <IndexTable.Cell>
                  <Link
                    external
                    url={issue.linearIssueUrl}
                    accessibilityLabel={`Open ${issue.linearIssueKey} in Linear`}
                  >
                    {issue.linearIssueKey}
                  </Link>
                </IndexTable.Cell>
                <IndexTable.Cell>
                  <Text variant="bodyMd">{issue.title || 'Untitled'}</Text>
                </IndexTable.Cell>
                <IndexTable.Cell>
                  <Badge tone={getStatusColor(issue.status)}>
                    {issue.status || 'unknown'}
                  </Badge>
                </IndexTable.Cell>
                <IndexTable.Cell>
                  <Badge tone={getSeverityColor(issue.severity)}>
                    {issue.severity || 'unknown'}
                  </Badge>
                </IndexTable.Cell>
                <IndexTable.Cell>
                  <Text variant="bodySm" tone="subdued">
                    {issue.apiName || 'N/A'}
                  </Text>
                </IndexTable.Cell>
                <IndexTable.Cell>
                  <Text variant="bodySm">{formatDate(issue.createdTs)}</Text>
                </IndexTable.Cell>
                <IndexTable.Cell>
                  <Button
                    size="slim"
                    icon={DeleteMinor}
                    tone="critical"
                    onClick={() => handleDeleteClick(issue.mappingId)}
                    accessibilityLabel="Delete issue mapping"
                  >
                    Delete
                  </Button>
                </IndexTable.Cell>
              </IndexTable.Row>
            ))}
          />

          {/* Pagination */}
          {totalPages > 1 && (
            <Box textAlign="center">
              <Pagination
                hasPrevious={page > 1}
                onPrevious={() => onPageChange(page - 1)}
                hasNext={page < totalPages}
                onNext={() => onPageChange(page + 1)}
                label={`Page ${page} of ${totalPages}`}
              />
            </Box>
          )}
        </VerticalStack>
      </Box>

      {/* Delete Confirmation Modal */}
      <Modal
        open={deleteConfirmOpen}
        onClose={() => setDeleteConfirmOpen(false)}
        title="Delete Issue Mapping"
        primaryAction={{
          content: 'Delete',
          tone: 'critical',
          onAction: handleDeleteConfirm,
        }}
        secondaryActions={[
          {
            content: 'Cancel',
            onAction: () => setDeleteConfirmOpen(false),
          },
        ]}
      >
        <Modal.Section>
          <Text as="p" variant="bodyMd">
            Are you sure you want to delete this issue mapping? The issue will remain in Linear,
            but the mapping will be removed from Akto.
          </Text>
        </Modal.Section>
      </Modal>
    </Card>
  )
}
