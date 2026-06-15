import React, { useState } from 'react'
import { Button, Box, HorizontalStack, Modal, Text, TextField, VerticalStack } from '@shopify/polaris'

function generateGlob(raw) {
    const lines = raw.split('\n').map(l => l.trim()).filter(Boolean)
    if (lines.length === 0) return ''
    if (lines.length === 1) return lines[0]

    const split = lines.map(l => l.split('/').filter(s => s !== ''))
    const maxLen = Math.max(...split.map(s => s.length))

    const segments = []
    for (let i = 0; i < maxLen; i++) {
        const vals = [...new Set(split.map(s => s[i]).filter(v => v != null))]
        if (vals.length === 1) {
            segments.push(vals[0])
            continue
        }
        // paths differ in depth from this segment onward — use **
        const depths = split.map(s => s.length)
        if (!depths.every(d => d === depths[0])) {
            segments.push('**')
            break
        }
        // same depth but different names — find common prefix + suffix
        const prefix = vals.reduce((p, v) => {
            let j = 0
            while (j < p.length && j < v.length && p[j] === v[j]) j++
            return p.slice(0, j)
        }, vals[0])
        const suffix = vals.reduce((s, v) => {
            let j = 0
            while (j < s.length && j < v.length && s[s.length - 1 - j] === v[v.length - 1 - j]) j++
            return j === 0 ? '' : s.slice(s.length - j)
        }, vals[0])
        segments.push(prefix + '*' + suffix)
    }

    return '/' + segments.join('/')
}

function GlobHelper({ onUse }) {
    const [open, setOpen] = useState(false)
    const [input, setInput] = useState('')
    const [result, setResult] = useState('')

    function handleClose() {
        setOpen(false)
        setInput('')
        setResult('')
    }

    return (
        <>
            <Button onClick={() => setOpen(true)}>Glob helper</Button>
            <Modal
                open={open}
                onClose={handleClose}
                title="Glob pattern generator"
                secondaryActions={[{ content: 'Close', onAction: handleClose }]}
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        <Text variant="bodySm" color="subdued">
                            Enter one example path per line. The tool finds the common pattern.
                        </Text>
                        <TextField
                            label="Example paths"
                            value={input}
                            onChange={setInput}
                            multiline={5}
                            autoComplete="off"
                            placeholder={'/Users/alice/logs/app-2024-01.log\n/Users/alice/logs/app-2024-02.log'}
                        />
                        <Button primary onClick={() => setResult(generateGlob(input))} disabled={!input.trim()}>
                            Generate
                        </Button>
                        {!!result && (
                            <Box background="bg-surface-secondary" padding="3" borderRadius="2" borderColor="border-subdued" borderWidth="1">
                                <VerticalStack gap="2">
                                    <Text variant="bodySm" color="subdued">Generated pattern</Text>
                                    <HorizontalStack align="space-between" wrap={false}>
                                        <Text variant="bodyMd" fontWeight="semibold" breakWord>{result}</Text>
                                        <Button plain onClick={() => { onUse(result); handleClose() }}>Use this</Button>
                                    </HorizontalStack>
                                </VerticalStack>
                            </Box>
                        )}
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        </>
    )
}

export default GlobHelper
