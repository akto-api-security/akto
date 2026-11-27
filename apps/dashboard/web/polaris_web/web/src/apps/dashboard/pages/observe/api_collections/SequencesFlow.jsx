import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { EdgeText, getBezierPath } from 'react-flow-renderer';
import { Banner, Box, Text, VerticalStack } from '@shopify/polaris';
import api from '../api';
import FlowGraphLayout from './FlowGraphLayout';
import { createCustomNode } from './FlowGraphComponents';
import DropdownSearch from '../../../components/shared/DropdownSearch';
import { getDashboardCategory, mapLabel } from '@/apps/main/labelHelper'

const LABEL_WIDTH = 200;
const ROW_HEIGHT = 120;
const NODE_WIDTH = 280;
const NODE_HEIGHT = 60;
// Path boxes and the fan sit far apart so the lines stay flat and their labels have room
const PATH_STEP = NODE_WIDTH + 320;
const FAN_GAP = 600;
const LABEL_OFFSET = 110;
// Starting APIs and fan entries are shown six at a time
const PAGE = 6;

const SequencesNode = createCustomNode('#ccc', '1px', '#555', '0 1px 3px rgba(0,0,0,0.1)', true);

function TextLabel({ data }) {
  const style = data.action
    ? { cursor: 'pointer', color: '#2c6ecb', background: '#ffffff', border: '1px solid #c9cccf', borderRadius: '4px', padding: '6px 10px', display: 'inline-block' }
    : { color: '#6d7175', fontStyle: data.italic ? 'italic' : 'normal' };
  return (
    <div style={{ width: `${LABEL_WIDTH - 40}px`, textAlign: data.align || 'right', fontSize: '13px' }}>
      <span style={style}>{data.text}</span>
    </div>
  );
}

// Label drawn level, just before the target box, where fanned lines are already apart
function ExplorerEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data }) {
  const markerId = `explorer-arrow-${id}`;
  const color = data.color || '#8c9196';
  return (
    <g>
      <defs>
        <marker id={markerId} markerWidth="14" markerHeight="14" refX="9" refY="7" orient="auto" markerUnits="userSpaceOnUse">
          <path d="M0,0 L9,7 L0,14" fill="none" stroke={color} />
        </marker>
      </defs>
      <path id={id} className="react-flow__edge-path" d={getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition })} style={{ stroke: color, strokeWidth: data.width, fill: 'none' }} markerEnd={`url(#${markerId})`} />
      {data.label ? (
        <EdgeText x={targetX - LABEL_OFFSET} y={targetY - 14} label={data.label} labelStyle={{ fontSize: 13, fill: '#202223' }} labelShowBg labelBgStyle={{ fill: '#ffffff', stroke: '#c9cccf', strokeWidth: 0.5 }} labelBgPadding={[6, 3]} labelBgBorderRadius={4} />
      ) : null}
    </g>
  );
}

const nodeTypes = { sequencesNode: SequencesNode, textLabel: TextLabel };
const edgeTypes = { explorerEdge: ExplorerEdge };

// Api key format: "<collectionId> <url> <method>"
const parseKey = (key) => {
  const parts = key.trim().split(' ');
  return { method: parts[parts.length - 1], url: parts.slice(1, -1).join(' ') };
};

const describe = (key) => {
  const { method, url } = parseKey(key);
  return `${method} ${url}`;
};

// An event reason names APIs as "METHOD /url" without the query string or trailing slash
const matchesReason = (key, reasonApi) => {
  const { method, url } = parseKey(key);
  return `${method} ${url.split('?')[0].replace(/\/+$/, '') || '/'}` === reasonApi;
};

const UNUSUAL_COLOR = '#d72c0d';

const edge = (id, source, target, label, width, color) => ({ id, source, target, sourceHandle: 'b', targetHandle: 't', type: 'explorerEdge', data: { label, width, color } });

const share = (weight, total) => {
  const percent = 100 * weight / total;
  return percent > 0 && percent < 0.5 ? '<1%' : `${Math.round(percent)}%`;
};

// Starting APIs in the left column; the clicked path continues from the selected one, and the
// last clicked API's next steps fan out to the right, busiest on top
function explorerGraph(starts, startsShown, steps, fan, fanShown, highlightKey) {
  const nodes = [];
  const edges = [];
  const visibleStarts = starts.slice(0, startsShown);
  visibleStarts.forEach((row, index) => {
    const y = index * ROW_HEIGHT;
    nodes.push({ id: `s${index}`, type: 'sequencesNode', position: { x: 0, y }, data: { ...parseKey(row.key), key: row.key, action: 'start', isSelected: steps.length === 1 && steps[0].key === row.key } });
  });
  if (starts.length > startsShown) {
    nodes.push({ id: 'more-starts', type: 'textLabel', position: { x: 0, y: visibleStarts.length * ROW_HEIGHT + 12 }, selectable: false, data: { text: `+ ${Math.min(PAGE, starts.length - startsShown)} more starting APIs`, align: 'left', action: 'more-starts' } });
  }
  const startIndex = steps.length ? visibleStarts.findIndex((row) => row.key === steps[0].key) : -1;
  if (startIndex < 0) return { nodes, edges, bounds: null };

  const pathY = startIndex * ROW_HEIGHT;
  const nodeId = (depth) => (depth === 0 ? `s${startIndex}` : `p${depth}`);
  steps.slice(1).forEach((step, offset) => {
    const depth = offset + 1;
    nodes.push({ id: `p${depth}`, type: 'sequencesNode', position: { x: depth * PATH_STEP, y: pathY }, data: { ...parseKey(step.key), key: step.key, depth, action: 'back', isSelected: depth === steps.length - 1 } });
    edges.push(edge(`${nodeId(depth - 1)}-p${depth}`, nodeId(depth - 1), `p${depth}`, step.label, 3));
  });
  if (!fan) return { nodes, edges, bounds: null };

  const rows = fan.rows.slice(0, fanShown);
  const allNext = fan.rows.reduce((sum, row) => sum + row.count, 0);
  const hidden = allNext - rows.reduce((sum, row) => sum + row.count, 0);
  const ended = fan.pathCount - allNext;
  const extras = [];
  if (hidden > 0) extras.push({ text: `+ ${Math.min(PAGE, fan.rows.length - rows.length)} more · ${share(hidden, fan.pathCount)}`, action: 'more-fan' });
  if (ended > 0) extras.push({ text: `Ends here · ${share(ended, fan.pathCount)}`, italic: true });

  const lastX = (steps.length - 1) * PATH_STEP;
  const x = lastX + NODE_WIDTH + FAN_GAP;
  const top = pathY - ((rows.length + extras.length - 1) / 2) * ROW_HEIGHT;
  const parentId = nodeId(steps.length - 1);
  // Thickness relative to the busiest next step, so the ranking shows even when every share is small
  const topCount = rows.length ? rows[0].count : 1;
  rows.forEach((row, index) => {
    const id = `c${index}`;
    nodes.push({ id, type: 'sequencesNode', position: { x, y: top + index * ROW_HEIGHT }, data: { ...parseKey(row.key), key: row.key, action: 'next', label: share(row.count, fan.pathCount) } });
    edges.push(edge(`${parentId}-${id}`, parentId, id, share(row.count, fan.pathCount), Math.max(1.5, 6 * row.count / topCount), row.key === highlightKey ? UNUSUAL_COLOR : null));
  });
  extras.forEach((extra, index) => {
    nodes.push({ id: `extra${index}`, type: 'textLabel', position: { x, y: top + (rows.length + index) * ROW_HEIGHT + 12 }, selectable: false, data: { ...extra, align: 'left' } });
  });

  // At the first step keep the starting column in view; deeper, frame the last clicked API and its fan
  const fanBottom = top + (rows.length + extras.length) * ROW_HEIGHT;
  const left = lastX;
  const minY = steps.length === 1 ? Math.min(0, top) : Math.min(pathY, top);
  const maxY = steps.length === 1 ? Math.max(fanBottom, (visibleStarts.length + 1) * ROW_HEIGHT) : Math.max(pathY + NODE_HEIGHT, fanBottom);
  const bounds = { x: left - 40, y: minY - 40, width: x + NODE_WIDTH - left + 80, height: maxY - minY + 80 };
  return { nodes, edges, bounds };
}

// Weights come back aged to now, so a fan's shares add up to at most 100%
const toFan = (resp) => ({
  rows: (resp?.nextSteps || []).map((row) => ({ key: row.next || row.seqKey, count: row.weight })),
  pathCount: resp?.pathWeight || 0,
});

// apiCollectionId 0 means the whole account. focusFrom/focusTo ("METHOD /url", from a threat event's reason)
// open the explorer on focusFrom and explain whether sessions ever went from it to focusTo
function SequencesFlow({ apiCollectionId, focusFrom, focusTo }) {
  const collectionId = Number(apiCollectionId) || 0;
  const [loading, setLoading] = useState(true);
  const [starts, setStarts] = useState([]);
  const [startsShown, setStartsShown] = useState(PAGE);
  const [steps, setSteps] = useState([]);
  const [fan, setFan] = useState(null);
  const [fanShown, setFanShown] = useState(PAGE);
  const [focus, setFocus] = useState(null);
  const fanCache = useRef(new Map());
  const flowInstance = useRef(null);

  const loadFan = useCallback(async (keys) => {
    const cacheKey = keys.join('|');
    if (!fanCache.current.has(cacheKey)) fanCache.current.set(cacheKey, toFan(await api.fetchSessionSteps(collectionId, keys)));
    return fanCache.current.get(cacheKey);
  }, [collectionId]);

  // Next new APIs after the path. A step back to an API already on the path (a list reloading after
  // an action) is looked through once, so list → remove → list → add shows as list → remove → add
  const loadNewSteps = useCallback(async (keys) => {
    const direct = await loadFan(keys);
    const onPath = new Set(keys);
    const last = keys[keys.length - 1];
    const merged = new Map();
    const add = (key, weight) => merged.set(key, (merged.get(key) || 0) + weight);
    const returns = direct.rows.filter((row) => onPath.has(row.key));
    direct.rows.filter((row) => !onPath.has(row.key)).forEach((row) => add(row.key, row.count));
    const afterReturns = await Promise.all(returns.map((row) => loadFan([last, row.key])));
    returns.forEach((row, index) => {
      const after = afterReturns[index];
      if (!after.pathCount) return;
      after.rows.filter((next) => !onPath.has(next.key)).forEach((next) => add(next.key, row.count * next.count / after.pathCount));
    });
    const rows = [...merged].map(([key, count]) => ({ key, count })).sort((a, b) => b.count - a.count);
    return { rows, pathCount: direct.pathCount };
  }, [loadFan]);

  // showKey: a fan entry to keep visible even if it is past the first page
  const goTo = useCallback(async (nextSteps, showKey) => {
    try {
      const nextFan = await loadNewSteps(nextSteps.map((s) => s.key));
      const showIndex = showKey ? nextFan.rows.findIndex((row) => row.key === showKey) : -1;
      setFan(nextFan);
      setFanShown(Math.max(PAGE, showIndex + 1));
      setSteps(nextSteps);
    } catch (error) {
      console.error('Error fetching session steps:', error);
    }
  }, [loadNewSteps]);

  // Opens on focusFrom when given, else on the busiest API, already fanned out
  const openExplorer = useCallback(async () => {
    try {
      fanCache.current = new Map();
      const startRows = toFan(await api.fetchSessionSteps(collectionId, [])).rows;
      const fromRow = focusFrom ? startRows.find((row) => matchesReason(row.key, focusFrom)) : null;
      const toRow = fromRow && focusTo ? startRows.find((row) => matchesReason(row.key, focusTo)) : null;
      setStarts(fromRow ? [fromRow, ...startRows.filter((row) => row !== fromRow)] : startRows);
      setStartsShown(PAGE);
      setSteps([]);
      setFan(null);
      if (fromRow && focusTo) {
        // Detection looks at the call made right after focusFrom, so the proof uses that fan, not the look-through one
        const direct = await loadFan([fromRow.key]);
        const seen = toRow ? direct.rows.find((row) => row.key === toRow.key) : null;
        setFocus({ fromKey: fromRow.key, toKey: toRow?.key, seenShare: seen && direct.pathCount ? share(seen.count, direct.pathCount) : null });
      } else {
        setFocus(null);
      }
      const first = fromRow || startRows[0];
      if (first) await goTo([{ key: first.key }], toRow?.key);
    } catch (error) {
      console.error('Error fetching session steps:', error);
      setStarts([]);
    }
  }, [collectionId, focusFrom, focusTo, loadFan, goTo]);

  // A searched API moves to the top of the starting column, so its path and fan are drawn from there
  const selectStart = useCallback((key) => {
    const row = starts.find((start) => start.key === key);
    if (!row) return;
    setStarts([row, ...starts.filter((start) => start !== row)]);
    setStartsShown(PAGE);
    goTo([{ key }]);
  }, [starts, goTo]);

  const searchOptions = useMemo(() => starts.map((row) => ({ label: describe(row.key), value: row.key })), [starts]);

  useEffect(() => {
    setLoading(true);
    openExplorer().finally(() => setLoading(false));
  }, [openExplorer]);

  const onNodeClick = useCallback((event, node) => {
    const { action, key, depth, label } = node.data;
    if (action === 'start') goTo([{ key }]);
    else if (action === 'back') goTo(steps.slice(0, depth + 1));
    else if (action === 'next') goTo([...steps, { key, label }]);
    else if (action === 'more-starts') setStartsShown((shown) => shown + PAGE);
    else if (action === 'more-fan') setFanShown((shown) => shown + PAGE);
  }, [steps, goTo]);

  const focusShown = focus && steps.length === 1 && steps[0].key === focus.fromKey;
  const graph = useMemo(() => explorerGraph(starts, startsShown, steps, fan, fanShown, focusShown ? focus.toKey : null),
    [starts, startsShown, steps, fan, fanShown, focusShown, focus]);

  // Zoom after a click on the path or the fan, not when more starting APIs are listed
  const bounds = graph.bounds;
  const boundsKey = bounds ? `${steps.map((s) => s.key).join('|')}#${fanShown}` : '';
  useEffect(() => {
    if (boundsKey && bounds && flowInstance.current) flowInstance.current.fitBounds(bounds, { padding: 0.05, duration: 400 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [boundsKey]);
  const onInit = useCallback((instance) => {
    flowInstance.current = instance;
    if (bounds) instance.fitBounds(bounds, { padding: 0.05 });
  }, [bounds]);

  const bannerContent = (
    <VerticalStack gap="4">
      <Box maxWidth="560px">
        <DropdownSearch
          id="session-explorer-search"
          placeholder={"Search " + mapLabel('API', getDashboardCategory()) + "s in this collection"}
          optionsList={searchOptions}
          setSelected={selectStart}
        />
      </Box>
      {focusShown ? (
        <Banner title="Why this step was unusual" status="critical">
          <Text variant="bodyMd">
            {focus.seenShare
              ? `${focusTo} came right after ${focusFrom} in only ${focus.seenShare} of the calls that followed it, too rarely to count as a normal step.`
              : `After ${focusFrom}, sessions went on to the ${fan?.rows.length || 0} APIs shown. ${focusTo} is not one of them: no session called it right after ${focusFrom}.`}
          </Text>
        </Banner>
      ) : null}
    </VerticalStack>
  );

  return (
    <FlowGraphLayout
      loading={loading}
      loadingMessage={"Loading " + mapLabel('API', getDashboardCategory()) + " sessions..."}
      nodes={graph.nodes}
      edges={graph.edges}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      defaultEdgeType="explorerEdge"
      emptyMessage="No session data recorded yet for this collection"
      bannerTitle="Session explorer"
      bannerStats={[]}
      bannerContent={bannerContent}
      showData={graph.nodes.length > 0}
      onNodeClick={onNodeClick}
      onInit={onInit}
      showMiniMap={false}
    />
  );
}

export default SequencesFlow;
