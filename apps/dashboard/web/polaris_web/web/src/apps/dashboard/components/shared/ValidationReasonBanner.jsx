import React from 'react'
import { Banner, Box, Text, VerticalStack } from '@shopify/polaris'

const PASSED_MARKER = '\npassed: '
const FAILED_MARKER = '\nfailed: '

export function parseValidationReason(validationReason) {
    if (!validationReason || !validationReason.trim()) {
        return null
    }

    const text = validationReason.trim()
    const passedIdx = text.indexOf(PASSED_MARKER)
    const failedIdx = text.indexOf(FAILED_MARKER)

    if (passedIdx === -1 && failedIdx === -1) {
        return { operator: null, passed: null, failed: null, raw: text }
    }

    const headerEnd = passedIdx >= 0
        ? passedIdx
        : (failedIdx >= 0 ? failedIdx : text.length)

    let operator = null
    if (headerEnd > 0) {
        operator = text.substring(0, headerEnd).replace(/^[\n]+/, '').replace(/:$/, '').trim()
        if (!operator) {
            operator = null
        }
    }

    let passed = null
    if (passedIdx >= 0) {
        const start = passedIdx + PASSED_MARKER.length
        const end = failedIdx >= 0 && failedIdx > passedIdx ? failedIdx : text.length
        passed = text.substring(start, end).trim()
        if (!passed) {
            passed = null
        }
    }

    let failed = null
    if (failedIdx >= 0) {
        failed = text.substring(failedIdx + FAILED_MARKER.length).trim()
        if (!failed) {
            failed = null
        }
    }

    return { operator, passed, failed, raw: null }
}

function ReasonBlock({ body }) {
    if (!body) {
        return null
    }

    return (
        <VerticalStack gap="1">
            <Box paddingInlineStart="2">
                <Text as="p" variant="bodyMd">
                    <span style={{ whiteSpace: 'pre-wrap' }}>{body}</span>
                </Text>
            </Box>
        </VerticalStack>
    )
}

function ValidationReasonBanner({ validationReason }) {
    const parsed = parseValidationReason(validationReason)
    if (!parsed) {
        return null
    }

    const title = 'Validation details'

    if (parsed.raw) {
        return (
            <Banner title={title} status="info">
                <Text as="p" variant="bodyMd">
                    <span style={{ whiteSpace: 'pre-wrap' }}>{parsed.raw}</span>
                </Text>
            </Banner>
        )
    }

    return (
        <Banner title={title} status="info">
            <VerticalStack gap="3">
                <ReasonBlock body={parsed.passed} />
                <ReasonBlock body={parsed.failed} />
            </VerticalStack>
        </Banner>
    )
}

export default ValidationReasonBanner
