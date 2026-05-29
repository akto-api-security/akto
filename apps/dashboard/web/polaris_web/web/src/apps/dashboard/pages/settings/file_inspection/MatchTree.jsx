import React, { useState, useCallback, useEffect, useContext, createContext } from 'react'
import { Tree } from 'react-arborist'
import { Text, HorizontalStack, Modal, Box, VerticalStack, Spinner, Badge } from '@shopify/polaris'
import settingRequests from '../api'
import func from '@/util/func'

const INDENT = 20
const ROW_HEIGHT = 28
const BLOB_PREVIEW_LIMIT = 4000

const C = {
    guide:      '#e2e4e7',
    folderIcon: '#f5a623',
    chevron:    '#9ca3af',
    rowHover:   'rgba(0,0,0,0.04)',
    truncDot:   '#f49342',
    metaText:   '#6b7280',
    name:       '#1a1a1a',
    nameBold:   '#111',
}

const FileClickCtx = createContext(() => {})

function formatBytes(b) {
    if (b == null) return null
    if (b < 1024) return b + ' B'
    if (b < 1048576) return (b / 1024).toFixed(1) + ' KB'
    return (b / 1048576).toFixed(1) + ' MB'
}

function buildTree(matches) {
    if (!matches || matches.length === 0) return []
    const nodeMap = {}
    for (const m of matches.filter(m => m.exists !== false)) {
        const parts = m.path.split('/').filter(Boolean)
        let currentPath = ''
        for (let i = 0; i < parts.length; i++) {
            const parentPath = currentPath
            currentPath = '/' + parts.slice(0, i + 1).join('/')
            if (!nodeMap[currentPath]) {
                nodeMap[currentPath] = { id: currentPath, name: parts[i], match: null, children: [] }
            }
            if (i === parts.length - 1) nodeMap[currentPath].match = m
            if (parentPath && nodeMap[parentPath]) {
                const p = nodeMap[parentPath]
                if (!p.children.find(c => c.id === currentPath)) p.children.push(nodeMap[currentPath])
            }
        }
    }
    const allIds = new Set(Object.keys(nodeMap))
    const roots = Object.values(nodeMap).filter(n => {
        const pp = n.id.slice(0, n.id.lastIndexOf('/')) || null
        return !pp || !allIds.has(pp)
    })
    function finalize(n) {
        if (n.children.length === 0) delete n.children
        else n.children.forEach(finalize)
        return n
    }
    return roots.map(finalize)
}

function countAll(nodes) {
    return nodes.reduce((acc, n) => acc + 1 + (n.children ? countAll(n.children) : 0), 0)
}

function FolderIcon({ open }) {
    return open ? (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M1 4a1 1 0 011-1h4l1.5 1.5H14a1 1 0 011 1v7a1 1 0 01-1 1H2a1 1 0 01-1-1V4z" fill={C.folderIcon} />
        </svg>
    ) : (
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M1 4a1 1 0 011-1h4l1.5 1.5H14a1 1 0 011 1v7a1 1 0 01-1 1H2a1 1 0 01-1-1V4z" fill={C.folderIcon} opacity="0.6" />
        </svg>
    )
}

function FileIcon({ ext }) {
    const color = ['txt','log'].includes(ext) ? '#60a5fa'
        : ['json','yaml','yml'].includes(ext) ? '#34d399'
        : ['sh','bash','zsh'].includes(ext) ? '#a78bfa'
        : ext === 'plist' ? '#fb923c'
        : '#8c93a3'
    return (
        <svg width="14" height="16" viewBox="0 0 14 16" fill="none">
            <path d="M2 1h7l3 3v11H2V1z" fill={color} opacity="0.15" stroke={color} strokeWidth="1" />
            <path d="M9 1v3h3" stroke={color} strokeWidth="1" fill="none" />
            <line x1="4" y1="7" x2="10" y2="7" stroke={color} strokeWidth="1" opacity="0.7" />
            <line x1="4" y1="10" x2="10" y2="10" stroke={color} strokeWidth="1" opacity="0.5" />
        </svg>
    )
}

function Chevron({ open }) {
    return (
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none"
            style={{ transform: open ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 0.15s ease', flexShrink: 0 }}>
            <path d="M4 2l4 4-4 4" stroke={C.chevron} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
    )
}

function StatusDot({ m }) {
    if (!m?.contentTruncated) return null
    return <span style={{ width: 6, height: 6, borderRadius: '50%', background: C.truncDot, flexShrink: 0 }} />
}

function NodeRow({ node, style }) {
    const [hovered, setHovered] = useState(false)
    const onFileClick = useContext(FileClickCtx)
    const m = node.data.match
    const isDir = m ? !!m.isDir : !!node.children
    const ext = !isDir && node.data.name.includes('.') ? node.data.name.split('.').pop().toLowerCase() : ''

    const guides = []
    for (let i = 0; i < node.level; i++) {
        guides.push(
            <div key={i} style={{
                position: 'absolute', left: i * INDENT + 10,
                top: 0, bottom: 0, width: 1,
                background: C.guide, pointerEvents: 'none',
            }} />
        )
    }

    return (
        <div style={{ ...style, position: 'relative' }}>
            {guides}
            <div
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                onClick={() => isDir ? node.toggle() : onFileClick(m)}
                style={{
                    display: 'flex', alignItems: 'center', gap: 5,
                    paddingLeft: node.level * INDENT + 4, paddingRight: 12,
                    height: ROW_HEIGHT, borderRadius: 4,
                    background: hovered ? C.rowHover : 'transparent',
                    cursor: 'pointer', boxSizing: 'border-box',
                }}
            >
                <span style={{ width: 14, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    {isDir && <Chevron open={node.isOpen} />}
                </span>
                <span style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                    {isDir ? <FolderIcon open={node.isOpen} /> : <FileIcon ext={ext} />}
                </span>
                <span style={{ flex: 1, overflow: 'hidden', display: 'flex', alignItems: 'center', gap: 5, minWidth: 0 }}>
                    <span style={{ color: isDir ? C.nameBold : C.name, fontSize: 13, fontWeight: isDir ? 600 : 400, whiteSpace: 'nowrap' }}>
                        {node.data.name}
                    </span>
                    <StatusDot m={m} />
                </span>
                {!isDir && m?.size != null && (
                    <span style={{ flexShrink: 0, color: C.metaText, fontSize: 11 }}>{formatBytes(m.size)}</span>
                )}
            </div>
        </div>
    )
}

const preStyle = {
    margin: 0, padding: '10px 12px',
    background: '#f8f9fa', border: '1px solid #e5e7eb', borderRadius: 6,
    fontSize: 12, lineHeight: 1.6, overflowX: 'auto',
    maxHeight: 400, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-all',
}

function FileModal({ match, onClose }) {
    const [state, setState] = useState('idle')
    const [content, setContent] = useState('')

    const load = useCallback(async () => {
        if (!match?.sha256 || !match?.contentBlobName) return
        setState('loading')
        try {
            const res = await settingRequests.getFileInspectionContent(match.sha256)
            if (!res?.blobContent) throw new Error('empty')
            setContent(res.blobContent)
            setState('loaded')
        } catch (e) {
            func.setToast(true, true, 'Failed to load: ' + e.message)
            setState('error')
        }
    }, [match?.sha256])

    useEffect(() => {
        if (match) load()
        else { setState('idle'); setContent('') }
    }, [match?.sha256])

    if (!match) return null

    const fileName = match.path ? match.path.split('/').pop() : '-'
    const displayContent = match.contentInline || content

    return (
        <Modal
            open={!!match}
            onClose={onClose}
            title={fileName}
            secondaryActions={[{ content: 'Close', onAction: onClose }]}
            large
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <HorizontalStack gap="4">
                        {match.size != null && <Text variant="bodySm" color="subdued">{formatBytes(match.size)}</Text>}
                        {!!match.modifiedAt && <Text variant="bodySm" color="subdued">Modified {func.prettifyEpoch(match.modifiedAt)}</Text>}
                        {!!match.contentTruncated && <Badge tone="warning">File too large to read</Badge>}
                        {!!match.readError && <Badge tone="critical">{match.readError}</Badge>}
                    </HorizontalStack>
                    {match.contentTruncated && !displayContent && (
                        <Text variant="bodySm" color="subdued">File exceeded size limit — content was not collected.</Text>
                    )}
                    {!match.contentTruncated && !match.sha256 && !match.contentInline && (
                        <Text variant="bodySm" color="subdued">No content available.</Text>
                    )}
                    {state === 'loading' && (
                        <HorizontalStack gap="2"><Spinner size="small" /><Text variant="bodySm" color="subdued">Loading...</Text></HorizontalStack>
                    )}
                    {state === 'error' && (
                        <Text variant="bodySm" color="critical">Failed to load content.</Text>
                    )}
                    {!!displayContent && (
                        <pre style={preStyle}>
                            {displayContent.length > BLOB_PREVIEW_LIMIT
                                ? displayContent.slice(0, BLOB_PREVIEW_LIMIT) + '\n\n... (truncated)'
                                : displayContent}
                        </pre>
                    )}
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

function MatchTree({ matches }) {
    const [selectedMatch, setSelectedMatch] = useState(null)
    const treeData = buildTree(matches)
    const missingCount = matches ? matches.filter(m => m.exists === false).length : 0

    if (!treeData.length && missingCount === 0) return null

    const height = Math.min(countAll(treeData) * ROW_HEIGHT + 16, 560)

    return (
        <FileClickCtx.Provider value={setSelectedMatch}>
            {treeData.length > 0 && (
                <Box background="bg-surface" borderRadius="2" borderColor="border-subdued" borderWidth="1" padding="2">
                    <Tree
                        data={treeData}
                        openByDefault={true}
                        width="100%"
                        height={height}
                        indent={INDENT}
                        rowHeight={ROW_HEIGHT}
                        overscanCount={8}
                        disableDrag={true}
                        disableDrop={true}
                    >
                        {NodeRow}
                    </Tree>
                </Box>
            )}
            {missingCount > 0 && (
                <Box
                    background="bg-surface-caution-subdued"
                    borderRadius="2"
                    borderColor="border-caution-subdued"
                    borderWidth="1"
                    padding="3"
                >
                    <HorizontalStack gap="2" align="start">
                        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" style={{ flexShrink: 0, marginTop: 1 }}>
                            <path d="M7 1L13 12H1L7 1Z" fill="#f49342" opacity="0.2" stroke="#f49342" strokeWidth="1.2" strokeLinejoin="round" />
                            <line x1="7" y1="5.5" x2="7" y2="8.5" stroke="#f49342" strokeWidth="1.3" strokeLinecap="round" />
                            <circle cx="7" cy="10.2" r="0.7" fill="#f49342" />
                        </svg>
                        <Text variant="bodySm" color="subdued">
                            {missingCount} path{missingCount !== 1 ? 's' : ''} not found on this device
                        </Text>
                    </HorizontalStack>
                </Box>
            )}
            <FileModal match={selectedMatch} onClose={() => setSelectedMatch(null)} />
        </FileClickCtx.Provider>
    )
}

export default MatchTree
