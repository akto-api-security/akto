import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import issuesApi from '../../issues/api';
import api from '../api';
import PersistStore from '../../../../main/PersistStore';
import { Avatar, Box, Frame, InlineGrid, InlineStack, LegacyCard, Text, TopBar, BlockStack, Icon } from '@shopify/polaris'
import './styles.css'
import transform from '../transform';
import LocalStore from '../../../../main/LocalStorageStore';

function ExportHtml() {
    const params = useParams();
    const testingRunSummaryId = params.summaryId
    const issuesFilter = params.issuesFilter
    let  moreInfoSections = transform.getInfoSectionsHeaders().filter(item => item.title !== 'Jira')
    
    const [vulnerableResultsMap, setVulnerableResultsMap] = useState([]);
    const [dataToCurlObj, setDataToCurlObj] = useState({});
    const [severitiesCount, setSeveritiesCount] = useState({ HIGH: 0, MEDIUM: 0, LOW: 0 });
    const collectionsMap = PersistStore(state => state.collectionsMap)

    const subCategoryMap = LocalStore(state => state.subCategoryMap)

    const createVulnerabilityMap = (testingRunResults) => {
        let categoryVsVulMap = {}
        let high = 0
        let medium = 0
        let low = 0
        testingRunResults?.length > 0 && testingRunResults.forEach((testingRun) => {
            let subtype = testingRun?.testSubType
            let subCategory = subCategoryMap?.[subtype]
            if (!subCategory) {
                return
            }
            let severity = subCategory?.superCategory?.severity?._name
            let severityIndex = 0;
            switch (severity) {
                case 'HIGH':
                    ++high
                    severityIndex = 2
                    break;
                case 'MEDIUM':
                    ++medium
                    severityIndex = 1
                    break;
                case 'LOW':
                    ++low
                    severityIndex = 0
                    break;
                default:
                    break;
            }

            let vulnerabilities = categoryVsVulMap[subtype]
            if (vulnerabilities === undefined) {
                vulnerabilities = JSON.parse(JSON.stringify(subCategory))
            }
            let vulnerableTestingRunResults = vulnerabilities["vulnerableTestingRunResults"]
            if (vulnerableTestingRunResults === undefined) {
                vulnerableTestingRunResults = []
            }
            vulnerableTestingRunResults.push(testingRun)
            vulnerabilities['vulnerableTestingRunResults'] = vulnerableTestingRunResults
            vulnerabilities['severityIndex'] = severityIndex
            categoryVsVulMap[subtype] = vulnerabilities
        })
        setSeveritiesCount({ HIGH: high, MEDIUM: medium, LOW: low });
        let localCopy = vulnerableResultsMap
        Object.keys(categoryVsVulMap).forEach((category) => {
            let obj = categoryVsVulMap[category]
            localCopy.push({ category: obj })
        })

        let compare = function (a, b) {
            let severityA = a[Object.keys(a)[0]]['severityIndex']
            let severityB = b[Object.keys(a)[0]]['severityIndex']
            return severityB - severityA
        }
        localCopy.sort(compare)
        setVulnerableResultsMap(localCopy)
    }

    const fetchVulnerableData = async () => {
        let resultsCount = 0;
        let vulnerableTestingRunResults = []
        let sampleDataVsCurlMap = {}

        if (testingRunSummaryId) {
            while (true) {
                let testingRunCountsFromDB = 0
                await api.fetchVulnerableTestingRunResults(testingRunSummaryId, resultsCount).then((resp) => {
                    vulnerableTestingRunResults = [...vulnerableTestingRunResults, ...resp.testingRunResults]
                    testingRunCountsFromDB = resp.testingRunResults.length
                    sampleDataVsCurlMap = { ...sampleDataVsCurlMap, ...resp.sampleDataVsCurlMap }
                })
                resultsCount += 50
                if (testingRunCountsFromDB < 50) {
                    //EOF: break as no further documents exists
                    break
                }
            }
        } else if (issuesFilter) {
            while (true) {
                let testingRunCountsFromDB = 0
                let filters = JSON.parse(atob(issuesFilter))
                await issuesApi.fetchVulnerableTestingRunResultsFromIssues(filters, resultsCount).then(resp => {
                    vulnerableTestingRunResults = [...vulnerableTestingRunResults, ...resp.testingRunResults]
                    testingRunCountsFromDB = resp.totalIssuesCount
                    sampleDataVsCurlMap = { ...sampleDataVsCurlMap, ...resp.sampleDataVsCurlMap }
                })
                resultsCount += 50
                if (testingRunCountsFromDB < 50 || resultsCount >= 1000) {
                    //EOF: break as no further documents exists
                    break
                }
            }
        }
        setDataToCurlObj(sampleDataVsCurlMap)
        createVulnerabilityMap(vulnerableTestingRunResults)
    }

    useEffect(() => {
        fetchVulnerableData()
    }, [])

    const headerComp = (
        <div className="header-css">
            <Box width="60%">
                <InlineStack align="space-between">
                    <Box paddingBlockStart={300}>
                        <Avatar size="md" shape="square" source="/public/akto_colored.svg" customer name='aktoLogo' />
                    </Box>
                    <Text variant="headingXl">Akto Vulnerabilities Report</Text>
                </InlineStack>
            </Box>
        </div>
    )
    const headerEditor = (
        <TopBar secondaryMenu={headerComp} />
    )

    const getColor = (item) => {
        switch (item.category.severityIndex) {
            case 0:
                return "bg-caution"

            case 1:
                return "bg-warning"

            case 2:
                return "bg-critical"

            default:
                return "";
        }
    }

    const fillContent = (item) => {
        return transform.fillMoreInformation(item.category, moreInfoSections);
    }


    const cardTitleComponent = (item) => {
        return (
            <Box borderWidth="1" background={getColor(item)}>
                <InlineStack>
                    <Box borderInlineEndWidth='1' width='35%'>
                        <Box padding={100}>
                            <Text variant="headingMd">Vulnerability</Text>
                        </Box>
                    </Box>
                    <Box>
                        <Box padding={100}>
                            <Text variant="headingMd">{item?.category.testName}</Text>
                        </Box>
                    </Box>
                </InlineStack>
            </Box>
        );
    }

    return (
        <Frame topBar={headerEditor}>
            <div className="html-component" style={{ padding: "32px" }}>
                <BlockStack gap="500">
                    <BlockStack gap="300">
                        <Text variant="headingLg" fontWeight="medium">Summary of alerts</Text>
                        <Box borderWidth="2" borderRadius="1" width="40%">
                            <InlineGrid columns={2}>
                                <div style={{ background: "#666666", borderRight: '2px solid white', borderBottom: '2px solid white' }}>
                                    <InlineStack align="center">
                                        <Box padding="100">
                                            <Text variant="bodyLg" fontWeight="medium" tone="text-inverse">
                                                Severity
                                            </Text>
                                        </Box>
                                    </InlineStack>
                                </div>
                                <div style={{ background: "#666666", borderBottom: '2px solid white' }}>
                                    <InlineStack align="center">
                                        <Box padding="100">
                                            <Text variant="bodyLg" fontWeight="medium" tone="text-inverse">
                                                Vulnerable APIs
                                            </Text>
                                        </Box>
                                    </InlineStack>
                                </div>
                            </InlineGrid>
                            {Object.keys(severitiesCount)?.map((element, index) => (
                                <InlineGrid columns={2} key={index}>
                                    <div style={{ background: "#e8e8e8", borderRight: '2px solid white', borderBottom: (index < 2 ? "2px solid white" : "") }}>
                                        <InlineStack align="center">
                                            <Box padding="100">
                                                <Text variant="bodyMd" fontWeight="medium">
                                                    {element}
                                                </Text>
                                            </Box>
                                        </InlineStack>
                                    </div>
                                    <div style={{ background: "#e8e8e8", borderBottom: (index < 2 ? "2px solid white" : "") }}>
                                        <InlineStack align="center">
                                            <Box padding="100">
                                                <Text variant="bodyMd" fontWeight="medium">
                                                    {severitiesCount[element]}
                                                </Text>
                                            </Box>
                                        </InlineStack>
                                    </div>
                                </InlineGrid>
                            ))}
                        </Box>
                    </BlockStack>
                    <BlockStack gap={400}>
                        <Text variant="headingLg" fontWeight="medium">Vulnerabilities details</Text>
                        <BlockStack gap={300}>
                            {vulnerableResultsMap?.map((item, index) => (
                                <LegacyCard sectioned title={cardTitleComponent(item)} key={index}>
                                    <LegacyCard.Section>
                                        <MoreInformationComponent
                                            key={index}
                                            sections={fillContent(item)}
                                            item={item}
                                            dataToCurlObj={dataToCurlObj}
                                            collectionsMap={collectionsMap}
                                        />
                                    </LegacyCard.Section>
                                </LegacyCard>
                            ))}
                        </BlockStack>
                    </BlockStack>
                </BlockStack>
            </div>
        </Frame>
    );
}

function VulnerableMultiStepTestDetails(props) {
    const getTruncatedString = (str) => {
        if (str && str.length > 3000) {
            return str.substr(0, 3000) + '  .........';
        }
        return str;
    }

    const getOriginalCurl = (message) => {
        return props.dataToCurlObj[message]
    }

    const getResponse = (message) => {
        let messageJson = JSON.parse(message)
        if (messageJson['response']) {
            return JSON.stringify(messageJson['response'])
        }
        return JSON.stringify({ "statusCode": messageJson['statusCode'], "body": messageJson['responsePayload'], "headers": messageJson['responseHeaders'] })
    }

    return (
        <div>
            <div className="attempts-div" key={props.index}>
                {Object.keys(props.testingRun.workflowTest.mapNodeIdToWorkflowNodeDetails).map((stepId, index) => {
                    let step = props.testingRun.workflowTest.mapNodeIdToWorkflowNodeDetails[stepId]
                    let nodeResultMap = props.testingRun.testResults[0].nodeResultMap[stepId]
                    if (nodeResultMap.message !== '[]') {
                        let attempt = nodeResultMap.message
                        return (
                            <div key={index}>
                                <div className="row-div-1">
                                    <InlineStack gap={100}>
                                        <span className="api-text">
                                            Vulnerable endpoint:
                                        </span>
                                        <span className="url-text">
                                            {step.apiInfoKey.url}
                                        </span>
                                    </InlineStack>
                                    <InlineStack>
                                        <Box paddingInlineEnd={400}>
                                            <span className="api-text">
                                                Collection name: {" "}
                                            </span>
                                            <span className="url-text">
                                                {props.collectionsMap[step.apiInfoKey.apiCollectionId]}
                                            </span>
                                        </Box>
                                    </InlineStack>
                                </div>
                                <div>
                                    <div className="row-div">
                                        <span className="title-name" style={{ fontWeight: "500" }}>
                                            Original request
                                        </span>
                                        <span className="url-name" style={{ fontWeight: "500" }}>
                                            Attempt
                                        </span>
                                    </div>
                                    <div className="row-div">
                                        <span className="message" style={{ borderRight: "1px solid #47466A73" }}>
                                            {getTruncatedString(getOriginalCurl(step.originalMessage))}
                                        </span>
                                        <span className="message">
                                            {getTruncatedString(getOriginalCurl(attempt))}
                                        </span>
                                    </div>
                                    <div className="row-div">
                                        <span className="title-name" style={{ fontWeight: "500" }}>
                                            Original Response
                                        </span>
                                        <span className="url-name" style={{ fontWeight: "500" }}>
                                            Attempt Response
                                        </span>
                                    </div>
                                    <div className="row-div">
                                        <span className="message" style={{ borderRight: "1px solid #47466A73" }}>
                                            {getTruncatedString(getResponse(step.originalMessage))}
                                        </span>
                                        <span className="message">
                                            {getTruncatedString(getResponse(attempt))}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        );
                    }
                })}

            </div>
        </div>
    );

}


function VulnerableEndpointDetails(props) {
    const getTruncatedString = (str) => {
        if (str && str.length > 3000) {
            return str.substr(0, 3000) + '  .........';
        }
        return str;
    }

    const getOriginalCurl = (message) => {
        return props.dataToCurlObj[message]
    }

    const getResponse = (message) => {
        let messageJson = JSON.parse(message)
        if (messageJson['response']) {
            return JSON.stringify(messageJson['response'])
        }
        return JSON.stringify({ "statusCode": messageJson['statusCode'], "body": messageJson['responsePayload'], "headers": messageJson['responseHeaders'] })
    }

    return (
        <div className="attempts-div" key={props.index}>
            <div className="row-div-1" key={props.index}>
                <InlineStack gap={100}>
                    <span className="api-text">
                        Vulnerable endpoint:
                    </span>
                    <span className="url-text">
                        {props.testingRun.apiInfoKey.url}
                    </span>
                </InlineStack>
                <InlineStack>
                    <Box paddingInlineEnd={400}>
                        <span className="api-text">
                            Collection name: {" "}
                        </span>
                        <span className="url-text">
                            {props.collectionsMap[props.testingRun.apiInfoKey.apiCollectionId]}
                        </span>
                    </Box>
                </InlineStack>
            </div>
            {props.testingRun?.testResults?.map((testRun, index1) => (
                <div key={index1}>
                    <div className="row-div">
                        <span className="title-name" style={{ fontWeight: "500" }}>
                            Original request
                        </span>
                        <span className="url-name" style={{ fontWeight: "500" }}>
                            Attempt
                        </span>
                    </div>
                    <div className="row-div">
                        <span className="message" style={{ borderRight: "1px solid #47466A73" }}>
                            {getTruncatedString(getOriginalCurl(testRun.originalMessage))}
                        </span>
                        <span className="message">
                            {getTruncatedString(getOriginalCurl(testRun.message))}
                        </span>
                    </div>
                    <div className="row-div">
                        <span className="title-name" style={{ fontWeight: "500" }}>
                            Original Response
                        </span>
                        <span className="url-name" style={{ fontWeight: "500" }}>
                            Attempt Response
                        </span>
                    </div>
                    <div className="row-div">
                        <span className="message" style={{ borderRight: "1px solid #47466A73" }}>
                            {getTruncatedString(getResponse(testRun.originalMessage))}
                        </span>
                        <span className="message">
                            {getTruncatedString(getResponse(testRun.message))}
                        </span>
                    </div>
                </div>
            ))}
        </div>
    );

}

function MoreInformationComponent(props) {

    return (
        <BlockStack gap={"400"}>
            <LegacyCard>
                <LegacyCard.Section>
                    {
                        props.sections?.map((section, index) => {
                            return (
                                <LegacyCard.Subsection key={index}>
                                    <BlockStack gap="300">
                                        <InlineStack gap="200" align="start" blockAlign='start'>
                                            <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                                                {section?.icon && <Icon source={section.icon}></Icon>}
                                            </div>
                                            <Text variant='headingSm'>
                                                {section.title || "Heading"}
                                            </Text>
                                        </InlineStack>
                                        {section.content}
                                    </BlockStack>
                                </LegacyCard.Subsection>
                            );
                        })
                    }
                </LegacyCard.Section>
                <LegacyCard.Section>
                    {props.item?.category?.vulnerableTestingRunResults?.map((testingRun, index) => (
                        testingRun.workflowTest ? <VulnerableMultiStepTestDetails
                            key={index}
                            index={index}
                            testingRun={testingRun}
                            collectionsMap={props.collectionsMap}
                            dataToCurlObj={props.dataToCurlObj}
                        /> : <VulnerableEndpointDetails
                            key={index}
                            index={index}
                            testingRun={testingRun}
                            collectionsMap={props.collectionsMap}
                            dataToCurlObj={props.dataToCurlObj}
                        />
                    ))}
                </LegacyCard.Section>
            </LegacyCard>
        </BlockStack>
    );
}


export default ExportHtml