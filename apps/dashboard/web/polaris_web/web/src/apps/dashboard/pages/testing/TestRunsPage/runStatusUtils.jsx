import { Text, Tooltip, VerticalStack, Box } from '@shopify/polaris';
import TooltipText from '../../../components/shared/TooltipText';
import { getStatus, getAlternateTestsInfo } from '../transform';

const BLOCKING_STATES = new Set(['STOPPED', 'FAILED', 'FAIL']);
const NON_ACTIONABLE_STATES = new Set(['RUNNING', 'SCHEDULED']);

export const RUN_STATUS_LABELS = {
  domainUnreachable: 'Domain unreachable',
  skipped: 'Skipped',
  needConfig: 'Need configuration',
  http403: '403 Forbidden',
  http401: '401 Unauthorized',
  http5xx: '5XX errors',
  http429: '429 Rate limited',
  cloudflare: 'Cloudflare errors',
};

const RUN_STATUS_ORDER = [
  'domainUnreachable',
  'http5xx',
  'http403',
  'http401',
  'skipped',
  'needConfig',
  'http429',
  'cloudflare',
];

function formatCount(count) {
  if (count >= 1000) {
    return count.toLocaleString();
  }
  return String(count);
}

function getBlockingRunMessage(state, metadata) {
  if (metadata?.error) {
    return metadata.error;
  }
  if (metadata?.tokenRateLimited) {
    return metadata.tokenRateLimited;
  }
  if (BLOCKING_STATES.has(getStatus(state))) {
    return getAlternateTestsInfo(state);
  }
  return null;
}

function getExecutionIssues(statusCounts) {
  if (!statusCounts) {
    return [];
  }

  return RUN_STATUS_ORDER
    .map((key) => ({ key, count: statusCounts[key] || 0 }))
    .filter((item) => item.count > 0);
}

function buildSummaryText(issues) {
  if (issues.length === 0) {
    return '-';
  }

  const topIssues = issues.slice(0, 2).map((item) => RUN_STATUS_LABELS[item.key]);
  const remaining = issues.length - topIssues.length;
  if (remaining > 0) {
    return `${topIssues.join(', ')} +${remaining} more`;
  }
  return topIssues.join(', ');
}

function buildTooltipContent(issues) {
  return (
    <VerticalStack gap="2">
      <Text variant="bodyMd">Execution issues found. Open this test run to investigate:</Text>
      <VerticalStack gap="1">
        {issues.map((item) => (
          <Text key={item.key} variant="bodySm">
            {`- ${RUN_STATUS_LABELS[item.key]}: ${formatCount(item.count)}`}
          </Text>
        ))}
      </VerticalStack>
      <Box paddingBlockStart="1" borderBlockStartWidth="1" borderColor="border-subdued">
        <Text variant="bodySm" color="subdued">
          Approximate counts from sampled non-vulnerable test results.
        </Text>
      </Box>
    </VerticalStack>
  );
}

export function buildRunStatusCell(state, metadata, statusCounts) {
  const blockingMessage = getBlockingRunMessage(state, metadata);
  if (blockingMessage) {
    return (
      <Box maxWidth="200px">
        <TooltipText text={blockingMessage} tooltip={blockingMessage} />
      </Box>
    );
  }

  if (NON_ACTIONABLE_STATES.has(getStatus(state))) {
    return (
      <Text variant="bodyMd" as="span" color="subdued">
        -
      </Text>
    );
  }

  const issues = getExecutionIssues(statusCounts);
  const summary = buildSummaryText(issues);

  if (summary === '-') {
    return (
      <Text variant="bodyMd" as="span" color="subdued">
        -
      </Text>
    );
  }

  return (
    <Tooltip content={buildTooltipContent(issues)} hoverDelay={300} width="wide">
      <span style={{ cursor: 'help' }}>
        <Text variant="bodyMd" as="span" tone="warning">
          {summary}
        </Text>
      </span>
    </Tooltip>
  );
}
