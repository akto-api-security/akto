import { Box, Checkbox, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import Dropdown from '../../../components/layouts/Dropdown'

const AktoDastOptions = ({ outscopeUrls, setOutscopeUrls, maxPageVisits, setMaxPageVisits, domLoadTimeout, setDomLoadTimeout, waitAfterEvent, setWaitAfterEvent, enableJsRendering, setEnableJsRendering, parseSoapServices, setParseSoapServices, parseRestServices, setParseRestServices, clickExternalLinks, setClickExternalLinks, crawlingTime, setCrawlingTime }) => {

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


            <Dropdown
                label="Crawling time"
                menuItems={[
                    { value: 600, label: "10 minutes", id: "600" },
                    { value: 3600, label: "60 minutes", id: "3600" },
                    { value: 86400, label: "1 Day", id: "86400" },
                    { value: 172800, label: "2 Days", id: "172800" },
                    { value: 345600, label: "4 Days", id: "345600" }
                ]}
                initial={crawlingTime}
                selected={(val) => setCrawlingTime(val)}
            />

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