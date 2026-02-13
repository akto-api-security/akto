import { Box, Checkbox, HorizontalStack, Icon, Text, TextField, Tooltip, VerticalStack } from '@shopify/polaris'
import { InfoMinor } from '@shopify/polaris-icons'
import React, { useEffect, useState } from 'react'
import Dropdown from '../../../components/layouts/Dropdown'
import testingApi from '../../testing/api';

const AktoDastOptions = ({ outscopeUrls, setOutscopeUrls, urlTemplatePatterns, setUrlTemplatePatterns, applicationPages, setApplicationPages, maxPageVisits, setMaxPageVisits, domLoadTimeout, setDomLoadTimeout, waitAfterEvent, setWaitAfterEvent, enableJsRendering, setEnableJsRendering, parseSoapServices, setParseSoapServices, parseRestServices, setParseRestServices, clickExternalLinks, setClickExternalLinks, crawlingTime, setCrawlingTime, runTestAfterCrawling, setRunTestAfterCrawling, selectedMiniTestingService, setSelectedMiniTestingService }) => {
    const [miniTestingServiceNames, setMiniTestingServiceNames] = useState([]);
    const handleMiniTestingServiceChange = (value) => {
        setSelectedMiniTestingService(value)
    }

    useEffect(() => {
        testingApi.fetchMiniTestingServiceNames().then(({ miniTestingServiceNames }) => {
            const miniTestingServiceNamesOptions = (miniTestingServiceNames || []).map(name => {
                return {
                    label: name,
                    value: name
                }
            });
            setMiniTestingServiceNames(miniTestingServiceNamesOptions);
            if (miniTestingServiceNamesOptions.length > 0) {
                setSelectedMiniTestingService(miniTestingServiceNamesOptions[0].value);
            }
        });
    }, []);

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

            {/* <TextField
                label={
                    <HorizontalStack gap="1">
                        <Text>URL Template Patterns</Text>
                        <Tooltip content="URL patterns to make templates (eg: /api/users/*, /products/*). Separate multiple patterns with a comma." dismissOnMouseOut>
                            <Icon source={InfoMinor} color="subdued" />
                        </Tooltip>
                    </HorizontalStack>
                }
                placeholder="URL patterns to make templates (eg: /api/users/*, /products/*). Separate multiple patterns with a comma."
                value={urlTemplatePatterns}
                onChange={(value) => setUrlTemplatePatterns(value)}
            /> */}

            <TextField
                label={
                    <HorizontalStack gap="1">
                        <Text>Application Pages</Text>
                        <Tooltip content="Application pages to crawl (eg: /login, /dashboard, /settings). Separate multiple pages with a comma. These pages will be crawled first." dismissOnMouseOut>
                            <Icon source={InfoMinor} color="subdued" />
                        </Tooltip>
                    </HorizontalStack>
                }
                placeholder="Application pages to crawl (eg: /login, /dashboard, /settings). Separate multiple pages with a comma. These pages will be crawled first."
                value={applicationPages}
                onChange={(value) => setApplicationPages(value)}
            />

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

                <HorizontalStack gap={3} wrap={false}>
                    <Checkbox
                        label="Run tests after crawling"
                        checked={runTestAfterCrawling}
                        onChange={(checked) => {
                            setRunTestAfterCrawling(checked)
                            if (!checked) {
                                setSelectedMiniTestingService("")
                            } else {
                                setSelectedMiniTestingService(miniTestingServiceNames.length > 0 ? miniTestingServiceNames[0].value : "")
                            }
                        }}
                    />
                </HorizontalStack>
                {runTestAfterCrawling && miniTestingServiceNames.length > 0 && (
                    <Dropdown
                        label="Select testing module:"
                        menuItems={miniTestingServiceNames}
                        selected={handleMiniTestingServiceChange}
                        initial={selectedMiniTestingService}
                    />
                )}
            </VerticalStack>
        </VerticalStack>
    )
}

export default AktoDastOptions