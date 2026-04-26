import React from 'react'
import { Badge, Box, VerticalStack, Text } from '@shopify/polaris'

/**
 * ConnectionStatusBadge Component
 * Displays NewRelic integration connection status with account details
 */
export default function ConnectionStatusBadge({ accountId, region, lastSyncTime, isEnabled }) {
  // Determine badge status
  const status = isEnabled ? 'success' : 'warning'
  const statusText = isEnabled ? 'Connected' : 'Disconnected'

  // Format last sync time
  const formatSyncTime = (timestamp) => {
    if (!timestamp) return 'Never'
    const date = new Date(timestamp)
    return date.toLocaleString()
  }

  return (
    <Box padding="400" borderColor="border" borderWidth="1" borderRadius="200">
      <VerticalStack gap="300">
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Badge status={status}>{statusText}</Badge>
        </Box>

        {isEnabled && accountId && (
          <VerticalStack gap="200">
            <Box>
              <Text variant="bodySm" color="subdued">
                Account ID
              </Text>
              <Text variant="bodyMd">{accountId}</Text>
            </Box>

            {region && (
              <Box>
                <Text variant="bodySm" color="subdued">
                  Region
                </Text>
                <Text variant="bodyMd">{region}</Text>
              </Box>
            )}

            {lastSyncTime && (
              <Box>
                <Text variant="bodySm" color="subdued">
                  Last Sync
                </Text>
                <Text variant="bodyMd">{formatSyncTime(lastSyncTime)}</Text>
              </Box>
            )}
          </VerticalStack>
        )}
      </VerticalStack>
    </Box>
  )
}
