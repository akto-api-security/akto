import { Box, Checkbox, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'

const AktoDastOptions = ({ outscopeUrls, setOutscopeUrls, maxPageVisits, setMaxPageVisits, domLoadTimeout, setDomLoadTimeout, waitAfterEvent, setWaitAfterEvent, enableJsRendering, setEnableJsRendering, parseSoapServices, setParseSoapServices, parseRestServices, setParseRestServices, clickExternalLinks, setClickExternalLinks }) => {

    return (
        <VerticalStack gap={4}>
            <Text variant='headingMd'>Akto DAST Options</Text>

            <HorizontalStack gap={2} wrap={false}>
                <TextField
                    label="Outscope URLs"
                    placeholder="Regex for URLs to exclude from the scan (eg: https://example.com/.*, https://another-example.com/.*)"
                    value={outscopeUrls}
                    onChange={(value) => setOutscopeUrls(value)}
                />

                <TextField
                    label="Maximum page visits"
                    placeholder="2"
                    type='number'
                    value={maxPageVisits}
                    onChange={(value) => setMaxPageVisits(value)}
                />
            </HorizontalStack>

            <HorizontalStack gap={2} wrap={false}>
                <TextField
                    label="DOM load timeout (ms)"
                    placeholder="3000"
                    type='number'
                    value={domLoadTimeout}
                    onChange={(value) => setDomLoadTimeout(value)}
                />

                <TextField
                    label="Wait after event (ms)"
                    placeholder="2000"
                    type='number'
                    value={waitAfterEvent}
                    onChange={(value) => setWaitAfterEvent(value)}
                />
            </HorizontalStack>

            <VerticalStack gap={2} wrap={false}>
                <HorizontalStack gap={3} wrap={false}>
                    <Checkbox
                        label="Enable JavaScript rendering"
                        checked={enableJsRendering}
                        onChange={(checked) => setEnableJsRendering(checked)}
                    />

                    <Checkbox
                        label="Parse SOAP web services"
                        checked={parseSoapServices}
                        onChange={(checked) => setParseSoapServices(checked)}
                    />
                </HorizontalStack>

                <HorizontalStack gap={3} wrap={false}>
                    <Checkbox
                        label="Parse REST web services"
                        checked={parseRestServices}
                        onChange={(checked) => setParseRestServices(checked)}
                    />

                    <Checkbox
                        label="Click external links"
                        checked={clickExternalLinks}
                        onChange={(checked) => setClickExternalLinks(checked)}
                    />
                </HorizontalStack>
            </VerticalStack>
        </VerticalStack>
    )
}

export default AktoDastOptions