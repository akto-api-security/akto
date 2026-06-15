import { Text, Tooltip, VerticalStack, Box } from '@shopify/polaris';
import TooltipText from '../../../components/shared/TooltipText';
import { getStatus, getAlternateTestsInfo } from '../transform';

const BLOCKING_STATES = new Set(['STOPPED', 'FAILED', 'FAIL']);
const NON_ACTIONABLE_STATES = new Set(['RUNNING', 'SCHEDULED']);

// Non status-code execution issues. Status codes themselves are surfaced dynamically by their
// real value (e.g. http_429, http_404, http_500) the same way 429 used to be, rather than being
// collapsed into generic buckets.
const STATIC_LABELS = {
  domainUnreachable: 'Domain unreachable',
  skipped: 'Skipped',
  needConfig: 'Need configuration',
  cloudflare: 'Cloudflare errors',
};

// Render order for the non status-code buckets (appended after the status codes).
const STATIC_ORDER = ['domainUnreachable', 'cloudflare', 'skipped', 'needConfig'];

// Friendly names for common HTTP status codes; any other code falls back to "<code> error".
const HTTP_CODE_LABELS = {
  '400': '400 Bad Request',
  '401': '401 Unauthorized',
  '403': '403 Forbidden',
  '404': '404 Not Found',
  '405': '405 Method Not Allowed',
  '406': '406 Not Acceptable',
  '408': '408 Request Timeout',
  '409': '409 Conflict',
  '410': '410 Gone',
  '413': '413 Payload Too Large',
  '415': '415 Unsupported Media Type',
  '422': '422 Unprocessable Entity',
  '429': '429 Rate limited',
  '500': '500 Internal Server Error',
  '501': '501 Not Implemented',
  '502': '502 Bad Gateway',
  '503': '503 Service Unavailable',
  '504': '504 Gateway Timeout',
};

// Keys rendered with a highlighted (critical) tone in the scan run status column.
const HIGHLIGHT_KEYS = new Set(['http_403']);

function statusCodeFromKey(key) {
  const match = /^http_(\d{3})$/.exec(key);
  return match ? match[1] : null;
}

function labelForKey(key) {
  const code = statusCodeFromKey(key);
  if (code) {
    return HTTP_CODE_LABELS[code] || `${code} error`;
  }
  return STATIC_LABELS[key] || key;
}

// 403 first (highlighted), then remaining status codes ascending, then the static buckets.
function issueOrderScore(key) {
  if (HIGHLIGHT_KEYS.has(key)) {
    return -1;
  }
  const code = statusCodeFromKey(key);
  if (code) {
    return parseInt(code, 10);
  }
  const idx = STATIC_ORDER.indexOf(key);
  return 1000 + (idx === -1 ? STATIC_ORDER.length : idx);
}

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

  return Object.entries(statusCounts)
    .map(([key, count]) => ({ key, count: count || 0 }))
    .filter((item) => item.count > 0)
    .sort((a, b) => issueOrderScore(a.key) - issueOrderScore(b.key));
}

function issueTone(key) {
  return HIGHLIGHT_KEYS.has(key) ? 'critical' : 'warning';
}

function buildSummaryNodes(issues) {
  const topIssues = issues.slice(0, 2);
  const remaining = issues.length - topIssues.length;

  const nodes = [];
  topIssues.forEach((item, idx) => {
    nodes.push(
      <Text
        key={item.key}
        as="span"
        variant="bodyMd"
        tone={issueTone(item.key)}
        fontWeight={HIGHLIGHT_KEYS.has(item.key) ? 'semibold' : undefined}
      >
        {labelForKey(item.key)}
      </Text>
    );
    if (idx < topIssues.length - 1) {
      nodes.push(
        <Text key={`${item.key}-sep`} as="span" variant="bodyMd">
          {', '}
        </Text>
      );
    }
  });

  if (remaining > 0) {
    nodes.push(
      <Text key="more" as="span" variant="bodyMd" color="subdued">
        {` +${remaining} more`}
      </Text>
    );
  }
  return nodes;
}

function buildTooltipContent(issues) {
  return (
    <VerticalStack gap="2">
      <Text variant="bodyMd">Execution issues found. Open this test run to investigate:</Text>
      <VerticalStack gap="1">
        {issues.map((item) => (
          <Text
            key={item.key}
            variant="bodySm"
            tone={HIGHLIGHT_KEYS.has(item.key) ? 'critical' : undefined}
            fontWeight={HIGHLIGHT_KEYS.has(item.key) ? 'semibold' : undefined}
          >
            {`- ${labelForKey(item.key)}: ${formatCount(item.count)}`}
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

  if (issues.length === 0) {
    return (
      <Text variant="bodyMd" as="span" color="subdued">
        -
      </Text>
    );
  }

  return (
    <Tooltip content={buildTooltipContent(issues)} hoverDelay={300} width="wide">
      <span style={{ cursor: 'help' }}>
        {buildSummaryNodes(issues)}
      </span>
    </Tooltip>
  );
}
