import { VerticalStack, Card, Button, HorizontalStack, Text, Box, TextField, Banner, Badge, IndexFiltersMode, Link } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import React, { useState } from 'react'
import GithubServerTable from "../../../components/tables/GithubServerTable";
import { CellType } from "../../../components/tables/rows/GithubRow";
import func from "@/util/func";
import collectionsApi from "./api"
import { useNavigate } from "react-router-dom";

const resourceName = {
    singular: "URL",
    plural: "URLs",
};

const headers = [
    {
        text: "URL",
        value: "url",
        title: "URL",
        type: CellType.TEXT,
    },
    {
        text: "Collection ID",
        value: "collectionId",
        title: "Collection ID",
        type: CellType.TEXT,
    },
    {
        text: "Collection Name",
        value: "name",
        title: "Collection Name",
        type: CellType.TEXT,
    },
    {
        text: "In API Info",
        value: "apiInfoBadge",
        title: "In API Info",
    },
    {
        text: "In Single Type Info",
        value: "singleTypeInfoBadge",
        title: "In Single Type Info",
    },
];

const sortOptions = [
    {
        label: "URL",
        value: "url asc",
        directionLabel: "A-Z",
        sortKey: "url",
        columnIndex: 0,
    },
    {
        label: "URL",
        value: "url desc",
        directionLabel: "Z-A",
        sortKey: "url",
        columnIndex: 0,
    },
];

function DebugEndpointsMode() {
    const [missingUrlsResults, setMissingUrlsResults] = useState([])
    const [loading, setLoading] = useState(false)
    const [urlsInput, setUrlsInput] = useState('');
    const [dataLoaded, setDataLoaded] = useState(false);
    const navigate = useNavigate();

    const handleAnalyze = async () => {
        if (!urlsInput.trim()) {
            func.setToast(true, true, "Please enter URLs to analyze");
            return;
        }

        // Parse URLs - split by newlines and filter empty lines
        const urls = urlsInput
            .split('\n')
            .map(line => line.trim())
            .filter(line => line.length > 0);

        if (urls.length === 0) {
            func.setToast(true, true, "Please enter valid URLs");
            return;
        }

        setLoading(true);
        try {
            const response = await collectionsApi.findMissingUrls(urls);

            if (!response || !Array.isArray(response) || response.length === 0) {
                func.setToast(true, true, "No results returned from analysis");
                setMissingUrlsResults([]);
                setDataLoaded(false);
                return;
            }

            setMissingUrlsResults(response);
            setDataLoaded(true);
            func.setToast(true, false, `Analysis complete! Found ${response.length} results.`);

        } catch (error) {
            console.error("Error finding missing URLs:", error);
            func.setToast(true, true, "Error analyzing URLs. Please try again.");
            setMissingUrlsResults([]);
            setDataLoaded(false);
        } finally {
            setLoading(false);
        }
    };

    const handleClear = () => {
        setUrlsInput('');
        setMissingUrlsResults([]);
        setDataLoaded(false);
    };

    const inputCard = (
        <Card key="input-card">
            <VerticalStack gap="4">
                <Banner status="info">
                    Enter URLs (one per line) to check their presence in api_info and single_type_info collections
                </Banner>
                <TextField
                    label="URLs to analyze"
                    value={urlsInput}
                    onChange={setUrlsInput}
                    multiline={10}
                    placeholder="/creditcard/v3/RegisterPaymentNetworkToken&#10;/creditcard/v3/AddCCByToken&#10;/v4/pci/DetokenizeCC"
                    helpText="Enter one URL per line"
                />
                <HorizontalStack gap="2">
                    <Button primary onClick={handleAnalyze} loading={loading}>
                        Analyze URLs
                    </Button>
                    <Button onClick={handleClear} disabled={loading}>
                        Clear
                    </Button>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    );

    const summaryCard = dataLoaded && missingUrlsResults.length > 0 ? (
        <Card key="summary-card">
            <VerticalStack gap="4">
                <Text variant="headingMd">Debug Summary</Text>
                <HorizontalStack gap="4">
                    <Box>
                        <Text variant="bodyMd" color="subdued">Total URLs Analyzed:</Text>
                        <Text variant="headingLg">{missingUrlsResults.length}</Text>
                    </Box>
                    <Box>
                        <Text variant="bodyMd" color="subdued">URLs in API Info:</Text>
                        <Text variant="headingLg">{missingUrlsResults.filter(r => r.apiInfo).length}</Text>
                    </Box>
                    <Box>
                        <Text variant="bodyMd" color="subdued">URLs in Single Type Info:</Text>
                        <Text variant="headingLg">{missingUrlsResults.filter(r => r.singleTypeInfo).length}</Text>
                    </Box>
                    <Box>
                        <Text variant="bodyMd" color="subdued">URLs Missing:</Text>
                        <Text variant="headingLg" color="critical">{missingUrlsResults.filter(r => !r.apiInfo).length}</Text>
                    </Box>
                </HorizontalStack>
            </VerticalStack>
        </Card>
    ) : null;

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
        // Transform data for GithubServerTable
        const transformedData = missingUrlsResults.map((result, index) => ({
            ...result,
            id: `${result.url}-${result.collectionId || index}`,
            collectionId: result.collectionId ? (
                <Link
                    removeUnderline
                    onClick={() => window.open("/dashboard/observe/inventory/" + result.collectionId, '_blank')}
                >
                    {result.collectionId}
                </Link>
            ) : '',
            apiInfoBadge: (
                <Badge status={result.apiInfo ? "success" : "critical"}>
                    {result.apiInfo ? "Yes" : "No"}
                </Badge>
            ),
            singleTypeInfoBadge: (
                <Badge status={result.singleTypeInfo ? "success" : "warning"}>
                    {result.singleTypeInfo ? "Yes" : "No"}
                </Badge>
            ),
        }));

        // Apply search filter
        const filteredData = queryValue && typeof queryValue === 'string'
            ? transformedData.filter(item =>
                item.url.toLowerCase().includes(queryValue.toLowerCase())
              )
            : transformedData;

        // Apply sorting
        const sortedData = [...filteredData].sort((a, b) => {
            const aVal = a[sortKey] || '';
            const bVal = b[sortKey] || '';
            if (sortOrder === 1) {
                return aVal > bVal ? 1 : -1;
            } else {
                return aVal < bVal ? 1 : -1;
            }
        });

        // Apply pagination
        const paginatedData = sortedData.slice(skip, skip + limit);

        return {
            value: paginatedData,
            total: filteredData.length
        };
    }

    const tableComponent = dataLoaded && missingUrlsResults.length > 0 ? (
        <GithubServerTable
            key={`debug-table-${missingUrlsResults.length}-${JSON.stringify(missingUrlsResults[0])}`}
            pageLimit={50}
            headers={headers}
            resourceName={resourceName}
            sortOptions={sortOptions}
            loading={false}
            fetchData={fetchData}
            filters={[]}
            selectable={false}
            headings={headers}
            useNewRow={true}
            condensedHeight={true}
            mode={IndexFiltersMode.Default}
            hideQueryField={false}
        />
    ) : null;

    const components = [
        inputCard,
        summaryCard,
        tableComponent
    ].filter(Boolean);

    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent={"Debug mode to analyze URLs across api_info and single_type_info collections"}
                    titleText={"Debug Endpoints Mode"}
                />
            }
            components={components}
            backUrl="/dashboard/observe/inventory"
        />
    )
}

export default DebugEndpointsMode
