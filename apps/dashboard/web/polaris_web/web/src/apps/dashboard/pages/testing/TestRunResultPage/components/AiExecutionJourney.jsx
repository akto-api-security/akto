import {
    Badge,
    Box,
    HorizontalStack,
    Scrollable,
    Text,
    VerticalStack,
} from '@shopify/polaris';
import PropTypes from 'prop-types';
import { MOCK_EXECUTION_TRACE } from './mockExecutionData';
import { CHAT_ASSETS, JOURNEY_TEXT } from './chatConstants';
import { shouldShowSmartTestingExecutionTrace } from '../smartTestingUtils';

const TRACE_SCROLL_MAX_HEIGHT = 'min(40vh, 360px)';

const TRACE_COLOR = {
    dot: '#2c6ecb',
    line: '#e1e3e5',
    dotCritical: '#d72c0d',
    dotCriticalRing: 'rgba(215, 44, 13, 0.15)',
    phase: '#2c6ecb',
    body: '#202223',
};

const layout = {
    event: { display: 'flex', gap: '12px', paddingBottom: '16px' },
    eventLast: { display: 'flex', gap: '12px', paddingBottom: 0 },
    rail: { display: 'flex', flexDirection: 'column', alignItems: 'center', width: '12px', flexShrink: 0 },
    dot: { width: '10px', height: '10px', borderRadius: '50%', backgroundColor: TRACE_COLOR.dot, flexShrink: 0 },
    dotCritical: {
        width: '10px',
        height: '10px',
        borderRadius: '50%',
        backgroundColor: TRACE_COLOR.dotCritical,
        boxShadow: `0 0 0 3px ${TRACE_COLOR.dotCriticalRing}`,
        flexShrink: 0,
    },
    line: { flex: 1, width: '2px', minHeight: '12px', marginTop: '4px', backgroundColor: TRACE_COLOR.line },
    body: { flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: '10px' },
    phase: { fontSize: '15px', fontWeight: 600, color: TRACE_COLOR.phase },
    bodyText: { fontSize: '14px', lineHeight: 1.6, color: TRACE_COLOR.body, margin: 0 },
    mono: {
        fontFamily: "SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace",
        fontSize: '13px',
        lineHeight: 1.55,
        margin: 0,
    },
};

function phaseLabel(phase) {
    return phase.charAt(0) + phase.slice(1).toLowerCase();
}

function TraceEvent({ event, isLast }) {
    const paragraphs = event.content.split('\n\n').filter((p) => p.trim());

    return (
        <Box as="div" style={isLast ? layout.eventLast : layout.event}>
            <Box as="div" style={layout.rail}>
                <Box as="span" style={event.isVulnerable ? layout.dotCritical : layout.dot} />
                {!isLast ? <Box as="span" style={layout.line} /> : null}
            </Box>
            <Box as="div" style={layout.body}>
                <HorizontalStack gap="2" blockAlign="center" wrap>
                    <Text as="span" style={layout.phase}>
                        {phaseLabel(event.phase)}
                    </Text>
                    {event.attempt != null && <Badge size="small">Attempt {event.attempt}</Badge>}
                    {event.statusCode != null && (
                        <Badge size="small" tone={event.statusCode === 200 ? 'critical' : 'info'}>
                            HTTP {event.statusCode}
                        </Badge>
                    )}
                    {event.isVulnerable && (
                        <Badge tone="critical" size="small">
                            {JOURNEY_TEXT.VULNERABLE_BADGE}
                        </Badge>
                    )}
                </HorizontalStack>
                {event.requestSummary ? (
                    <Box padding="3" background="bg-surface-secondary" borderRadius="2">
                        <Text as="p" breakWord style={layout.mono}>
                            {event.requestSummary}
                        </Text>
                    </Box>
                ) : null}
                {paragraphs.map((paragraph, idx) => (
                    <Text key={idx} as="p" breakWord style={layout.bodyText}>
                        {paragraph}
                    </Text>
                ))}
            </Box>
        </Box>
    );
}

function AiExecutionJourney({ runAutomatedTests = false }) {
    if (!shouldShowSmartTestingExecutionTrace(runAutomatedTests)) {
        return null;
    }

    const { agent, runMode, endpoint, objective, events } = MOCK_EXECUTION_TRACE;

    return (
        <Box padding="3" background="bg-surface-secondary" borderRadius="2" borderWidth="1" borderColor="border">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center" wrap>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Box width="20px" minHeight="20px">
                            <img src={CHAT_ASSETS.MAGIC_ICON} alt="" aria-hidden="true" width="18" height="18" />
                        </Box>
                        <VerticalStack gap="0">
                            <Text variant="headingMd" as="h3">
                                {JOURNEY_TEXT.HEADER}
                            </Text>
                            <Text variant="bodyMd" tone="subdued">
                                {agent} · {runMode}
                            </Text>
                        </VerticalStack>
                    </HorizontalStack>
                    <Badge tone="attention" size="small">
                        {JOURNEY_TEXT.MOCK_BADGE}
                    </Badge>
                </HorizontalStack>

                <Box padding="3" background="bg-surface" borderRadius="2">
                    <VerticalStack gap="2">
                        <Text variant="bodyMd" fontWeight="semibold" breakWord style={layout.mono}>
                            {endpoint}
                        </Text>
                        <Text variant="bodyMd" tone="subdued">
                            {objective}
                        </Text>
                    </VerticalStack>
                </Box>

                <Box>
                    <Text variant="bodyMd" fontWeight="semibold">
                        {JOURNEY_TEXT.TRACE_LOG}
                    </Text>
                    <Box
                        borderWidth="1"
                        borderColor="border"
                        borderRadius="2"
                        background="bg-surface"
                        marginBlockStart="2"
                    >
                        <Scrollable shadow focusable style={{ maxHeight: TRACE_SCROLL_MAX_HEIGHT }}>
                            <Box padding="4">
                                {events.map((event, index) => (
                                    <TraceEvent
                                        key={event.id}
                                        event={event}
                                        isLast={index === events.length - 1}
                                    />
                                ))}
                            </Box>
                        </Scrollable>
                    </Box>
                </Box>
            </VerticalStack>
        </Box>
    );
}

AiExecutionJourney.propTypes = {
    runAutomatedTests: PropTypes.bool,
};

export default AiExecutionJourney;
