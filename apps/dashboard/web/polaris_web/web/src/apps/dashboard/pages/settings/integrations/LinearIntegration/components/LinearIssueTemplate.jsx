import React, { useState, useEffect } from 'react'
import {
  Card,
  FormLayout,
  TextField,
  Text,
  Box,
  VerticalStack,
  HorizontalStack,
  Checkbox,
  Tag,
  Button,
  Divider,
} from '@shopify/polaris'

const AVAILABLE_VARIABLES = [
  { name: 'apiName', example: 'GET /users/{id}', description: 'API endpoint name' },
  { name: 'apiMethod', example: 'GET', description: 'HTTP method' },
  { name: 'apiPath', example: '/users/{id}', description: 'API path' },
  { name: 'findingType', example: 'threat', description: 'Type of finding (threat or test)' },
  { name: 'description', example: 'SQL injection detected...', description: 'Finding description' },
  { name: 'severity', example: 'HIGH', description: 'Severity level' },
  { name: 'timestamp', example: '2026-04-26T10:30:00Z', description: 'ISO 8601 timestamp' },
]

const EXAMPLE_DATA = {
  apiName: 'GET /users/{id}',
  apiMethod: 'GET',
  apiPath: '/users/{id}',
  findingType: 'threat',
  description: 'SQL injection attempt detected in query parameter',
  severity: 'HIGH',
  timestamp: new Date().toISOString(),
}

export default function LinearIssueTemplate({ template = {}, onTemplateChange = () => {} }) {
  const [title, setTitle] = useState(template.title || '[{apiName}] {findingType}: {severity}')
  const [description, setDescription] = useState(
    template.description ||
      '{severity} severity {findingType} detected on {timestamp} in {apiPath}'
  )
  const [includeDetails, setIncludeDetails] = useState(template.includeDetails !== false)
  const [includeApiInfo, setIncludeApiInfo] = useState(template.includeApiInfo !== false)
  const [labels, setLabels] = useState(template.labels || ['security', 'akto'])
  const [labelInput, setLabelInput] = useState('')

  // Auto-save template when it changes
  useEffect(() => {
    const debounceTimer = setTimeout(() => {
      onTemplateChange({
        title,
        description,
        includeDetails,
        includeApiInfo,
        labels,
      })
    }, 300)

    return () => clearTimeout(debounceTimer)
  }, [title, description, includeDetails, includeApiInfo, labels, onTemplateChange])

  const renderPreview = (template_str) => {
    if (!template_str) return ''

    let preview = template_str
    Object.entries(EXAMPLE_DATA).forEach(([key, value]) => {
      preview = preview.replace(new RegExp(`{${key}}`, 'g'), value)
    })
    return preview
  }

  const handleAddLabel = () => {
    if (labelInput.trim() && !labels.includes(labelInput.trim())) {
      setLabels([...labels, labelInput.trim()])
      setLabelInput('')
    }
  }

  const handleRemoveLabel = (labelToRemove) => {
    setLabels(labels.filter((l) => l !== labelToRemove))
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      handleAddLabel()
    }
  }

  return (
    <Card title="Issue Template Configuration">
      <Box padding="4">
        <VerticalStack gap="5">
          {/* Title Template */}
          <div>
            <TextField
              label="Issue Title Template"
              value={title}
              onChange={setTitle}
              placeholder="[{apiName}] {findingType}: {severity}"
              multiline={false}
              helpText="Use {variable} syntax to include dynamic values"
            />
            <Box marginBlockStart="3">
              <Text as="p" variant="bodySm" tone="subdued">
                <strong>Preview:</strong>
              </Text>
              <Box
                padding="3"
                borderRadius="200"
                background="bg-surface-secondary"
                borderColor="border"
                borderWidth="1px"
              >
                <Text as="p" variant="bodySm">
                  {renderPreview(title) || '(empty)'}
                </Text>
              </Box>
            </Box>
          </div>

          {/* Description Template */}
          <div>
            <TextField
              label="Issue Description Template"
              value={description}
              onChange={setDescription}
              placeholder="{severity} severity {findingType} detected on {timestamp}"
              multiline={3}
              helpText="Use {variable} syntax for dynamic content"
            />
            <Box marginBlockStart="3">
              <Text as="p" variant="bodySm" tone="subdued">
                <strong>Preview:</strong>
              </Text>
              <Box
                padding="3"
                borderRadius="200"
                background="bg-surface-secondary"
                borderColor="border"
                borderWidth="1px"
              >
                <Text as="p" variant="bodySm">
                  {renderPreview(description) || '(empty)'}
                </Text>
              </Box>
            </Box>
          </div>

          {/* Options */}
          <div>
            <VerticalStack gap="3">
              <Checkbox
                label="Include test/threat details in description"
                checked={includeDetails}
                onChange={setIncludeDetails}
              />
              <Checkbox
                label="Include API method and path"
                checked={includeApiInfo}
                onChange={setIncludeApiInfo}
              />
            </VerticalStack>
          </div>

          {/* Labels */}
          <div>
            <Text as="label" variant="bodyMd">
              <strong>Labels</strong>
            </Text>
            <Text as="p" variant="bodySm" tone="subdued">
              Tags to apply to created issues (comma-separated)
            </Text>

            <Box marginBlockStart="3">
              <HorizontalStack gap="2" distribution="fill" blockAlign="center">
                <TextField
                  placeholder="Enter label"
                  value={labelInput}
                  onChange={setLabelInput}
                  onKeyDown={handleKeyDown}
                  helpText="Press Enter to add"
                />
                <Button onClick={handleAddLabel} accessibilityLabel="Add label">
                  Add
                </Button>
              </HorizontalStack>
            </Box>

            {/* Display tags */}
            {labels.length > 0 && (
              <Box marginBlockStart="3">
                <HorizontalStack gap="2" blockAlign="center">
                  {labels.map((label) => (
                    <Tag
                      key={label}
                      onRemove={() => handleRemoveLabel(label)}
                      accessibilityLabel={`Remove ${label} label`}
                    >
                      {label}
                    </Tag>
                  ))}
                </HorizontalStack>
              </Box>
            )}
          </div>

          <Divider />

          {/* Available Variables Reference */}
          <div>
            <Text as="h3" variant="headingMd">
              Available Variables
            </Text>
            <Text as="p" variant="bodySm" tone="subdued">
              Use these variables in your title and description templates:
            </Text>

            <Box marginBlockStart="3">
              <VerticalStack gap="3">
                {AVAILABLE_VARIABLES.map((variable) => (
                  <div key={variable.name}>
                    <VerticalStack gap="1">
                      <Text as="p" variant="bodyMd">
                        <code>{`{${variable.name}}`}</code> — {variable.description}
                      </Text>
                      <Text as="p" variant="bodySm" tone="subdued">
                        Example: <code>{variable.example}</code>
                      </Text>
                    </VerticalStack>
                  </div>
                ))}
              </VerticalStack>
            </Box>
          </div>
        </VerticalStack>
      </Box>
    </Card>
  )
}
