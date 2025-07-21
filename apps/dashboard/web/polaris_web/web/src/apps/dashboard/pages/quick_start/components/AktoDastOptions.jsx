import { Box, Checkbox, HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'

const AktoDastOptions = () => {
    const [crawlingPageLimit, setCrawlingPageLimit] = useState('');
    const [maxPageVisits, setMaxPageVisits] = useState('');
    const [domLoadTimeout, setDomLoadTimeout] = useState('');
    const [waitAfterEvent, setWaitAfterEvent] = useState('');
    const [enableJsRendering, setEnableJsRendering] = useState(false);
    const [parseSoapServices, setParseSoapServices] = useState(false);
    const [parseRestServices, setParseRestServices] = useState(false);
    const [clickExternalLinks, setClickExternalLinks] = useState(false);

    return (
        <VerticalStack gap={4}>
            <Text variant='headingMd'>Akto DAST Options</Text>

            <HorizontalStack gap={2}>
                <TextField
                    label="Crawling page limit"
                    placeholder="9"
                    value={crawlingPageLimit}
                    onChange={(value) => setCrawlingPageLimit(value)}
                />

                <TextField
                    label="Maximum page visits"
                    placeholder="2"
                    value={maxPageVisits}
                    onChange={(value) => setMaxPageVisits(value)}
                />
            </HorizontalStack>

            <HorizontalStack gap={2}>
                <TextField
                    label="DOM load timeout (ms)"
                    placeholder="3000"
                    value={domLoadTimeout}
                    onChange={(value) => setDomLoadTimeout(value)}
                />

                <TextField
                    label="Wait after event (ms)"
                    placeholder="2000"
                    value={waitAfterEvent}
                    onChange={(value) => setWaitAfterEvent(value)}
                />
            </HorizontalStack>

            <VerticalStack gap={2}>
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