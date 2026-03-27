import React, { useCallback, useState } from 'react'
import { Box, Button, Text, VerticalStack } from '@shopify/polaris'
import TitleWithInfo from '@/apps/dashboard/components/shared/TitleWithInfo'
import testingApi from '../api'

function isGptVulnAnalysisEnabled() {
    try {
        return window.STIGG_FEATURE_WISE_ALLOWED?.AKTO_GPT_AI?.isGranted === true
    } catch (e) {
        return false
    }
}

function sourceLabel(source) {
    if (!source) return 'Evidence'
    const s = String(source).toLowerCase()
    if (s === 'user_prompt' || s === 'user') return 'From tested interaction'
    if (s === 'assistant_response' || s === 'assistant' || s === 'system') return 'From AI agent response'
    return source
}

function buildPostHocPayload(selectedTestRunResult, issueDetails, conversations, hasConversations) {
    const testContext = {
        category: selectedTestRunResult?.testCategoryId || selectedTestRunResult?.testCategory || 'UNKNOWN',
        description: issueDetails?.description || selectedTestRunResult?.name || '',
        severity: issueDetails?.severity || 'HIGH',
        cwe: issueDetails?.cwe || []
    }
    let body = ''
    if (hasConversations && conversations?.length) {
        const users = conversations.filter((c) => c.role === 'user')
        const systems = conversations.filter((c) => c.role === 'system')
        const u = users[users.length - 1]?.message || ''
        const a = systems[systems.length - 1]?.message || ''
        body = `USER:\n${u}\n\nASSISTANT:\n${a}`
    } else {
        const msg = selectedTestRunResult?.testResults?.[0]?.message
        body = typeof msg === 'string' ? msg : JSON.stringify(msg ?? {})
    }
    return {
        resultId: selectedTestRunResult?.id != null ? `posthoc_${selectedTestRunResult.id}` : null,
        testContext,
        request: {
            method: 'POST',
            url: '/red-team-transcript',
            contentType: 'text/plain'
        },
        response: {
            statusCode: 200,
            headers: {},
            body
        }
    }
}

export default function WhyFlaggedSection({
    flaggingDecision,
    validationFallback,
    hasConversations,
    conversations,
    selectedTestRunResult,
    issueDetails,
    httpExplain
}) {
    const [postHoc, setPostHoc] = useState({ loading: false, rationale: null, evidencePhrases: null, error: null })

    const hasStructured =
        flaggingDecision &&
        ((flaggingDecision.summary && String(flaggingDecision.summary).trim()) ||
            (Array.isArray(flaggingDecision.evidence) && flaggingDecision.evidence.length > 0) ||
            (Array.isArray(flaggingDecision.criteria) && flaggingDecision.criteria.length > 0))

    const fallbackText =
        validationFallback && String(validationFallback).trim() && String(validationFallback).toLowerCase() !== 'null'
            ? String(validationFallback).trim()
            : ''

    const runPostHoc = useCallback(async () => {
        if (!selectedTestRunResult?.vulnerable || !isGptVulnAnalysisEnabled()) return
        setPostHoc({ loading: true, rationale: null, evidencePhrases: null, error: null })
        try {
            const payload = buildPostHocPayload(selectedTestRunResult, issueDetails, conversations, hasConversations)
            const response = await testingApi.analyzeVulnerability(JSON.stringify(payload), 'redteaming')
            const analysisData = response.analysisResult || response
            const rationale = analysisData?.rationale || null
            const evidencePhrases = Array.isArray(analysisData?.evidencePhrases) ? analysisData.evidencePhrases : []
            setPostHoc({
                loading: false,
                rationale,
                evidencePhrases,
                error: !rationale && evidencePhrases.length === 0 ? 'No explanation returned. Try again later.' : null
            })
        } catch (err) {
            setPostHoc({
                loading: false,
                rationale: null,
                evidencePhrases: null,
                error: 'Could not generate an explanation.'
            })
        }
    }, [selectedTestRunResult, issueDetails, conversations, hasConversations])

    if (!selectedTestRunResult?.vulnerable) {
        return null
    }

    const httpRationale = httpExplain?.rationale && String(httpExplain.rationale).trim()
    const httpPhrases = Array.isArray(httpExplain?.evidencePhrases) ? httpExplain.evidencePhrases.filter(Boolean) : []

    const showGenerateButton =
        isGptVulnAnalysisEnabled() &&
        !postHoc.loading &&
        !postHoc.rationale &&
        !hasStructured &&
        !httpRationale &&
        httpPhrases.length === 0

    const hasAnythingToShow =
        hasStructured ||
        fallbackText ||
        httpRationale ||
        httpPhrases.length > 0 ||
        postHoc.rationale ||
        (postHoc.evidencePhrases && postHoc.evidencePhrases.length > 0) ||
        postHoc.loading ||
        postHoc.error ||
        showGenerateButton

    if (!hasAnythingToShow) {
        return null
    }

    return (
        <Box paddingBlockEnd="4">
            <VerticalStack gap="3">
                <TitleWithInfo
                    textProps={{ variant: 'bodyMd', fontWeight: 'semibold', color: 'subdued' }}
                    titleText="Why this was flagged"
                    tooltipContent="How this finding was decided for this run, with quotes from the evidence where available. Remediation tab has general fix guidance."
                />
                {hasStructured && flaggingDecision.summary ? (
                    <Text as="p" variant="bodyMd">
                        {flaggingDecision.summary}
                    </Text>
                ) : null}
                {hasStructured && Array.isArray(flaggingDecision.evidence) && flaggingDecision.evidence.length > 0 ? (
                    <VerticalStack gap="2">
                        {flaggingDecision.evidence.map((item, i) => (
                            <Box key={i} padding="3" background="bg-surface-secondary" borderRadius="2">
                                <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                                    {sourceLabel(item?.source)}
                                </Text>
                                <Box paddingBlockStart="1">
                                    <Text as="p" variant="bodyMd" tone="subdued">
                                        <span style={{ whiteSpace: 'pre-wrap' }}>{item?.excerpt}</span>
                                    </Text>
                                </Box>
                            </Box>
                        ))}
                    </VerticalStack>
                ) : null}
                {hasStructured && Array.isArray(flaggingDecision.criteria) && flaggingDecision.criteria.length > 0 ? (
                    <VerticalStack gap="1">
                        <Text variant="bodySm" fontWeight="semibold">
                            Criteria
                        </Text>
                        {flaggingDecision.criteria.map((c, i) => (
                            <Text key={i} as="p" variant="bodySm" tone="subdued">
                                {(c?.name || c?.id || 'Criterion') + (c?.met ? ' — met' : '')}
                            </Text>
                        ))}
                    </VerticalStack>
                ) : null}

                {!hasStructured && fallbackText ? (
                    <VerticalStack gap="1">
                        <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                            Validation note
                        </Text>
                        <Text as="p" variant="bodyMd">
                            <span style={{ whiteSpace: 'pre-wrap' }}>{fallbackText}</span>
                        </Text>
                    </VerticalStack>
                ) : null}

                {httpRationale || httpPhrases.length > 0 ? (
                    <VerticalStack gap="2">
                        <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                            Automated analysis
                        </Text>
                        {httpRationale ? (
                            <Text as="p" variant="bodyMd">
                                <span style={{ whiteSpace: 'pre-wrap' }}>{httpRationale}</span>
                            </Text>
                        ) : null}
                        {httpPhrases.length > 0 ? (
                            <VerticalStack gap="1">
                                {httpPhrases.map((p, i) => (
                                    <Box key={i} padding="2" background="bg-surface-secondary" borderRadius="2">
                                        <Text as="p" variant="bodySm" tone="subdued">
                                            <span style={{ whiteSpace: 'pre-wrap' }}>{p}</span>
                                        </Text>
                                    </Box>
                                ))}
                            </VerticalStack>
                        ) : null}
                    </VerticalStack>
                ) : null}

                {postHoc.loading ? <Text tone="subdued">Generating explanation…</Text> : null}
                {postHoc.error ? <Text tone="critical">{postHoc.error}</Text> : null}
                {postHoc.rationale || (postHoc.evidencePhrases && postHoc.evidencePhrases.length > 0) ? (
                    <VerticalStack gap="2">
                        <Text variant="bodySm" fontWeight="semibold" tone="subdued">
                            AI explanation (post-hoc)
                        </Text>
                        {postHoc.rationale ? (
                            <Text as="p" variant="bodyMd">
                                <span style={{ whiteSpace: 'pre-wrap' }}>{postHoc.rationale}</span>
                            </Text>
                        ) : null}
                        {Array.isArray(postHoc.evidencePhrases) && postHoc.evidencePhrases.length > 0 ? (
                            <VerticalStack gap="1">
                                {postHoc.evidencePhrases.map((p, i) => (
                                    <Box key={i} padding="2" background="bg-surface-secondary" borderRadius="2">
                                        <Text as="p" variant="bodySm" tone="subdued">
                                            <span style={{ whiteSpace: 'pre-wrap' }}>{p}</span>
                                        </Text>
                                    </Box>
                                ))}
                            </VerticalStack>
                        ) : null}
                    </VerticalStack>
                ) : null}

                {showGenerateButton ? (
                    <Box>
                        <Button onClick={runPostHoc}>Generate AI explanation</Button>
                        <Box paddingBlockStart="1">
                            <Text variant="bodySm" tone="subdued">
                                Uses the transcript or HTTP sample shown under Evidence. Not identical to the original scan
                                engine verdict.
                            </Text>
                        </Box>
                    </Box>
                ) : null}
            </VerticalStack>
        </Box>
    )
}
