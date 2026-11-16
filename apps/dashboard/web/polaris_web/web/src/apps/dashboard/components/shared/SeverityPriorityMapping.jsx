import { Badge, Box, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import Dropdown from '../layouts/Dropdown'
import func from "@/util/func"

export const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export const DEFAULT_SEVERITY_TO_PRIORITY = { CRITICAL: '1', HIGH: '2', MEDIUM: '3', LOW: '4' }

const PRIORITY_OPTIONS = [
    { label: 'Urgent', value: '1' },
    { label: 'High', value: '2' },
    { label: 'Medium', value: '3' },
    { label: 'Low', value: '4' },
    { label: 'No priority', value: '0' }
]

const SEVERITY_BADGE_STATUS = { CRITICAL: 'critical', HIGH: 'warning', MEDIUM: 'attention', LOW: 'info' }

const priorityLabel = (value) => PRIORITY_OPTIONS.find(option => option.value === value)?.label || ''

const MappingCell = ({ children, header, compact }) => (
    <Box
        padding={compact ? '2' : '4'}
        background={header ? 'bg-surface-secondary' : undefined}
        borderWidth="1"
        borderColor="border"
        borderRadius="2"
    >
        <HorizontalStack align="center" blockAlign="center">
            {children}
        </HorizontalStack>
    </Box>
)

const SeverityPriorityMapping = ({ rowId, severityToPriority, editing, onChange }) => (
    <VerticalStack gap="2">
        <HorizontalGrid columns={2} gap="2">
            <MappingCell header><Text variant="bodyMd" fontWeight="semibold">Akto Severity</Text></MappingCell>
            <MappingCell header><Text variant="bodyMd" fontWeight="semibold">Linear Priority</Text></MappingCell>
        </HorizontalGrid>
        {SEVERITIES.map(severity => (
            <HorizontalGrid columns={2} gap="2" key={`${rowId}-${severity}`}>
                <MappingCell>
                    <Badge status={SEVERITY_BADGE_STATUS[severity]}>{func.toSentenceCase(severity)}</Badge>
                </MappingCell>
                <MappingCell compact={editing}>
                    {editing
                        ? <Dropdown
                            menuItems={PRIORITY_OPTIONS}
                            initial={severityToPriority[severity]}
                            selected={(value) => onChange(severity, value)}
                            placeHolder="Select Priority"
                        />
                        : <Text variant="bodyMd">{priorityLabel(severityToPriority[severity])}</Text>}
                </MappingCell>
            </HorizontalGrid>
        ))}
    </VerticalStack>
)

export default SeverityPriorityMapping
