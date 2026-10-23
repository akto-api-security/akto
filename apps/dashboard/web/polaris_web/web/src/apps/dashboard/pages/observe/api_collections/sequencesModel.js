import { graphlib, layout } from '@dagrejs/dagre';
import Graph from 'graphology';
import louvain from 'graphology-communities-louvain';

export const MIN_TRANSITION_COUNT = 10;
export const MIN_DEPENDENCY = 0.5;
export const MIN_LIFT = 2;
export const TOP_FLOWS = 10;
export const MIN_FLOW_STEPS = 3;
export const MAX_FLOW_STEPS = 6;
export const ALTERNATIVES_SHOWN = 5;
export const CLUSTER_EDGES_SHOWN = 60;

export const NODE_WIDTH = 260;
const NODE_BASE_HEIGHT = 56;
const NODE_LINE_HEIGHT = 18;

export const EDGE_ROLES = {
  FLOW: 'flow',
  LINK: 'link',
  ALTERNATIVE: 'alternative'
};

export const EDGE_ROLE_STYLES = {
  [EDGE_ROLES.FLOW]: { color: '#4b3fb3', dashed: false, label: 'Flow step', description: 'Part of the selected business flow' },
  [EDGE_ROLES.LINK]: { color: '#2c6ecb', dashed: false, label: 'Meaningful link', description: `Clear direction (dependency ≥ ${MIN_DEPENDENCY}) and at least ${MIN_LIFT}× more often than chance (lift)` },
  [EDGE_ROLES.ALTERNATIVE]: { color: '#8c9196', dashed: true, label: 'Other path', description: 'Another way into or out of the API you clicked' }
};

const CLUSTER_COLORS = ['#4b3fb3', '#b98900', '#2c6ecb', '#d72c0d', '#007f5f', '#9c6ade', '#e56e00', '#0e7c86', '#c4387c', '#5c5f62'];
export const clusterColor = (clusterId) => CLUSTER_COLORS[clusterId % CLUSTER_COLORS.length];

export function parseApiKey(path) {
  const parts = path.trim().split(' ');
  return {
    collectionId: Number(parts[0]),
    method: parts[parts.length - 1],
    url: parts.slice(1, -1).join(' ')
  };
}

export const formatPercent = (value) => `${Math.round(value * 100)}%`;

export const formatCount = (value) => (value >= 10000 ? `${(value / 1000).toFixed(0)}k` : value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value));

const edgeKey = (from, to) => `${from}|${to}`;

// Lift and dependency per A→B, plus the indexes needed for flows, clusters and alternatives.
export function analyzeSequences(sequences) {
  const selfLoopCounts = new Map();
  const edges = [];
  sequences.forEach((sequence) => {
    const from = sequence.paths[0];
    const to = sequence.paths[sequence.paths.length - 1];
    if (from === to) {
      selfLoopCounts.set(from, (selfLoopCounts.get(from) || 0) + sequence.transitionCount);
      return;
    }
    if (sequence.transitionCount < MIN_TRANSITION_COUNT) return;
    edges.push({ from, to, transitionCount: sequence.transitionCount, probability: sequence.probability, precedenceScore: sequence.precedenceScore });
  });

  const outTotals = new Map();
  const inTotals = new Map();
  const counts = new Map();
  let total = 0;
  edges.forEach((edge) => {
    outTotals.set(edge.from, (outTotals.get(edge.from) || 0) + edge.transitionCount);
    inTotals.set(edge.to, (inTotals.get(edge.to) || 0) + edge.transitionCount);
    counts.set(edgeKey(edge.from, edge.to), edge.transitionCount);
    total += edge.transitionCount;
  });

  const outgoing = new Map();
  const incoming = new Map();
  edges.forEach((edge) => {
    const reverse = counts.get(edgeKey(edge.to, edge.from)) || 0;
    edge.lift = (edge.transitionCount * total) / (outTotals.get(edge.from) * inTotals.get(edge.to));
    edge.dependency = (edge.transitionCount - reverse) / (edge.transitionCount + reverse + 1);
    edge.meaningful = edge.dependency >= MIN_DEPENDENCY && edge.lift >= MIN_LIFT;
    if (!outgoing.has(edge.from)) outgoing.set(edge.from, []);
    if (!incoming.has(edge.to)) incoming.set(edge.to, []);
    outgoing.get(edge.from).push(edge);
    incoming.get(edge.to).push(edge);
  });
  outgoing.forEach((list) => list.sort((a, b) => b.transitionCount - a.transitionCount));
  incoming.forEach((list) => list.sort((a, b) => b.transitionCount - a.transitionCount));

  const meaningful = edges.filter((edge) => edge.meaningful);
  const clusters = detectClusters(meaningful);
  return { edges, meaningful, selfLoopCounts, outgoing, incoming, ...clusters };
}

// Louvain communities over meaningful links; each cluster is a group of APIs that are used together.
function detectClusters(meaningful) {
  const graph = new Graph({ type: 'undirected' });
  meaningful.forEach((edge) => {
    graph.mergeNode(edge.from);
    graph.mergeNode(edge.to);
    graph.updateEdge(edge.from, edge.to, (attrs) => ({ weight: (attrs.weight || 0) + edge.transitionCount }));
  });
  const nodeCluster = new Map();
  if (graph.order === 0) return { nodeCluster, clusters: [] };

  const assignment = louvain(graph, { getEdgeWeight: 'weight', rng: seededRandom(42) });
  const byCommunity = new Map();
  Object.entries(assignment).forEach(([path, community]) => {
    if (!byCommunity.has(community)) byCommunity.set(community, []);
    byCommunity.get(community).push(path);
  });

  const volumeOf = new Map();
  meaningful.forEach((edge) => {
    if (assignment[edge.from] === assignment[edge.to]) {
      volumeOf.set(assignment[edge.from], (volumeOf.get(assignment[edge.from]) || 0) + edge.transitionCount);
    }
  });

  const clusters = [...byCommunity.entries()]
    .map(([community, paths]) => ({ community, paths, volume: volumeOf.get(community) || 0 }))
    .filter((cluster) => cluster.paths.length >= 2)
    .sort((a, b) => b.volume - a.volume)
    .map((cluster, index) => ({ id: index, paths: cluster.paths, volume: cluster.volume, ...describeCluster(cluster.paths) }));
  clusters.forEach((cluster) => cluster.paths.forEach((path) => nodeCluster.set(path, cluster.id)));
  return { nodeCluster, clusters };
}

// Most common collection and leading URL segments, so a cluster can be recognised at a glance.
function describeCluster(paths) {
  const collectionCounts = new Map();
  const prefixCounts = new Map();
  paths.forEach((path) => {
    const { collectionId, url } = parseApiKey(path);
    collectionCounts.set(collectionId, (collectionCounts.get(collectionId) || 0) + 1);
    const prefix = url.split('/').filter((segment) => segment && !segment.endsWith('.php') && !/^v\d+$/.test(segment)).slice(0, 2).join('/');
    prefixCounts.set(prefix, (prefixCounts.get(prefix) || 0) + 1);
  });
  const top = (map) => [...map.entries()].sort((a, b) => b[1] - a[1])[0][0];
  return { collectionId: top(collectionCounts), urlPrefix: `/${top(prefixCounts)}` };
}

function seededRandom(seed) {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

// Widest-path flow decomposition: repeatedly take the path with the largest bottleneck volume
// (longer paths weighted up), then subtract that volume from its links so flows don't double count.
export function extractFlows({ meaningful }) {
  const capacity = new Map();
  const adjacency = new Map();
  const edgeByKey = new Map();
  meaningful.forEach((edge) => {
    capacity.set(edgeKey(edge.from, edge.to), edge.transitionCount);
    edgeByKey.set(edgeKey(edge.from, edge.to), edge);
    if (!adjacency.has(edge.from)) adjacency.set(edge.from, []);
    adjacency.get(edge.from).push(edge.to);
  });
  const score = (volume, length) => volume * Math.log2(length);

  const findBest = () => {
    let bestPath = null;
    let bestVolume = 0;
    let bestScore = 0;
    adjacency.forEach((_, start) => {
      const stack = [[start, [start], Infinity]];
      while (stack.length > 0) {
        const [node, path, volume] = stack.pop();
        if (path.length >= MIN_FLOW_STEPS && score(volume, path.length) > bestScore) {
          bestPath = path;
          bestVolume = volume;
          bestScore = score(volume, path.length);
        }
        if (path.length >= MAX_FLOW_STEPS) continue;
        (adjacency.get(node) || []).forEach((next) => {
          if (path.includes(next)) return;
          const nextVolume = Math.min(volume, capacity.get(edgeKey(node, next)));
          if (nextVolume > 0 && score(nextVolume, MAX_FLOW_STEPS) > bestScore) stack.push([next, path.concat(next), nextVolume]);
        });
      }
    });
    return { path: bestPath, volume: bestVolume };
  };

  const flows = [];
  for (let rank = 0; rank < TOP_FLOWS; rank++) {
    const { path, volume } = findBest();
    if (!path) break;
    const links = [];
    for (let i = 0; i < path.length - 1; i++) {
      const key = edgeKey(path[i], path[i + 1]);
      capacity.set(key, capacity.get(key) - volume);
      links.push(edgeByKey.get(key));
    }
    flows.push({ id: path.join('>'), rank: rank + 1, path, links, volume });
  }
  return flows;
}

export const CURATED_FLOWS = [
  { name: 'Configure waitlist', path: ['-2016970724 /communityhost/deleteWaitlistConfig POST', '-2016970724 /communityhost/addWaitlistConfig POST', '-2016970724 /communityhost/getWaitlistPolicyData POST', '-2016970724 /index.php/communityhost/applyWaitlistOnStations POST', '-2016970724 /index.php/communityhost/refreshStationStatus POST'] },
  { name: 'SSO login', path: ['-1292505895 /auth/logout GET', '-1292505895 /auth/login POST', '-1292505895 /login/oauth2/code/STRING GET'] },
  { name: 'User login', path: ['-772584174 /api/v1/user/logout/token POST', '-772584174 /api/v1/user/current GET', '-772584174 /api/v1/user/login POST', '-772584174 /api/oauth2/authorize GET'] },
  { name: 'Update tariff (OCPI)', path: ['25374612 /ocpi/emsp/2.1.1/tariffs/US/EVG/STRING PUT', '25374612 /den/cpo/FLOAT/locations/STRING GET', '25374612 /den/cpo/FLOAT/tariffs/STRING GET'] },
  { name: 'Provision mobile device', path: ['323592847 /restapi/tokenUpload POST', '323592847 /backend.php/restapi/INTEGER/getOrgProvisionInfo POST', '323592847 /backend.php/mobileapi/v5 POST'] },
  { name: 'View station details', path: ['-2016970724 /backend.php/stations/get_station/INTEGER POST', '-2016970724 /backend.php/stations/get_station_sim_details/ POST', '-2016970724 /backend.php/stations/get_device_model_img_s3_url/ POST'] },
  { name: 'Search stations on map', path: ['-862047973 /fixed-area/all-stations POST', '-862047973 /fixed-match/search POST', '-862047973 /fixed-area/rectangular POST'] },
];

export function curatedFlows({ edges }) {
  const byKey = new Map(edges.map((edge) => [edgeKey(edge.from, edge.to), edge]));
  return CURATED_FLOWS.map(({ name, path }, index) => {
    const links = path.slice(1).map((to, i) => byKey.get(edgeKey(path[i], to))).filter(Boolean);
    return { id: path.join('>'), rank: index + 1, name, path, links, volume: links.length ? Math.min(...links.map((link) => link.transitionCount)) : 0 };
  }).filter((flow) => flow.links.length === flow.path.length - 1);
}

export function clusterEdges(analysis, clusterId) {
  return analysis.meaningful
    .filter((edge) => analysis.nodeCluster.get(edge.from) === clusterId && analysis.nodeCluster.get(edge.to) === clusterId)
    .slice()
    .sort((a, b) => b.transitionCount - a.transitionCount)
    .slice(0, CLUSTER_EDGES_SHOWN);
}

// Other ways into `to` and out of `from` (for an edge), or around a single API (for a node).
export function alternativesFor(analysis, { from, to }) {
  const result = { into: [], outOf: [] };
  if (to) result.into = (analysis.incoming.get(to) || []).filter((edge) => edge.from !== from).slice(0, ALTERNATIVES_SHOWN);
  if (from) result.outOf = (analysis.outgoing.get(from) || []).filter((edge) => edge.to !== to).slice(0, ALTERNATIVES_SHOWN);
  return result;
}

// Lays out the given edges left-to-right. `roles` maps edge keys to an EDGE_ROLES value.
export function buildGraph(analysis, selected, { collectionsMap = {}, roles = new Map() }) {
  const unique = new Map();
  selected.forEach((edge) => unique.set(edgeKey(edge.from, edge.to), edge));
  const shown = [...unique.values()];
  const maxCount = Math.max(1, ...shown.map((edge) => edge.transitionCount));

  const nodeData = new Map();
  shown.forEach((edge) => {
    [edge.from, edge.to].forEach((path) => {
      if (nodeData.has(path)) return;
      const { collectionId, method, url } = parseApiKey(path);
      const selfLoopCount = analysis.selfLoopCounts.get(path) || 0;
      const clusterId = analysis.nodeCluster.get(path);
      const height = NODE_BASE_HEIGHT + NODE_LINE_HEIGHT + (selfLoopCount > 0 ? NODE_LINE_HEIGHT : 0);
      nodeData.set(path, {
        id: `n${nodeData.size + 1}`,
        height,
        data: { path, method, url, collectionName: collectionsMap[collectionId] || String(collectionId), selfLoopCount, clusterId }
      });
    });
  });

  const graph = new graphlib.Graph();
  graph.setGraph({ rankdir: 'LR', nodesep: 40, ranksep: 220, marginx: 20, marginy: 20 });
  graph.setDefaultEdgeLabel(() => ({}));
  nodeData.forEach((node) => graph.setNode(node.id, { width: NODE_WIDTH, height: node.height }));
  shown.forEach((edge) => graph.setEdge(nodeData.get(edge.from).id, nodeData.get(edge.to).id));
  layout(graph);

  const nodes = [];
  nodeData.forEach((node) => {
    const { x, y } = graph.node(node.id);
    nodes.push({ id: node.id, type: 'sequencesNode', data: node.data, position: { x: x - NODE_WIDTH / 2, y: y - node.height / 2 } });
  });

  const edges = shown.map((edge) => {
    const source = nodeData.get(edge.from);
    const target = nodeData.get(edge.to);
    return {
      id: `e${source.id}-${target.id}`,
      source: source.id,
      target: target.id,
      sourceHandle: 's',
      targetHandle: 't',
      type: 'sequencesEdge',
      data: { ...edge, role: roles.get(edgeKey(edge.from, edge.to)) || EDGE_ROLES.LINK, width: 1.5 + 4.5 * (edge.transitionCount / maxCount) }
    };
  });

  return { nodes, edges };
}

export { edgeKey };
