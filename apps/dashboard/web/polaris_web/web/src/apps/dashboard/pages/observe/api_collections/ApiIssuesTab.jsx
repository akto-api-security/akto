import { Box, Text, VerticalStack } from "@shopify/polaris";
import { useState, useEffect } from "react";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import transformIssues from "../../issues/transform";
import func from "@/util/func";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import IssuesApi from "../../issues/api"
import SpinnerCentered from "../../../components/progress/SpinnerCentered";
import SessionStore from "../../../../main/SessionStore";
import threatDetectionApi from "../../threat_detection/api";

const ApiIssuesTab = ({ apiDetail, collectionIssuesData, isThreatEnabled }) => {
    const [filteredIssues, setFilteredIssues] = useState([]);
    const [loadingIssues, setLoadingIssues] = useState(false);
    const localSubCategoryMap = LocalStore.getState().subCategoryMap;
    const apiCollectionMap = PersistStore(state => state.collectionsMap);
    const hostNameMap = PersistStore.getState().hostNameMap;
    const threatFiltersMap = SessionStore.getState().threatFiltersMap;
    const [totalThreatIssues, setTotalThreatIssues] = useState(0);

    const issuesHeaders = [
        { text: "Severity", title: "Severity", value: "severity" },
        { text: "Issue Name", title: "Issue Name", value: "issueName" },
        { text: "Category", title: "Category", value: "category" },
        { text: "Domains", title: "Domains", value: "domains" },
        { text: "Compliance", title: "Compliance", value: "compliance" },
        { text: "Discovered", title: "Discovered", value: "creationTime" }
    ];

    const filterIssuesData = async (apiCollectionId, endpoint, method) => {
        try {
            setLoadingIssues(true);

            const rawFilteredIssues = (collectionIssuesData || []).filter(issue =>
                issue.id?.apiInfoKey &&
                issue.id.apiInfoKey.method === method &&
                issue.id.apiInfoKey.url === endpoint &&
                issue.id.apiInfoKey.apiCollectionId === apiCollectionId
            );
            
            const uniqueIssuesMap = new Map();
            rawFilteredIssues.forEach(item => {
                const key = `${item?.id?.testSubCategory}|${item?.severity}`;
                const domain = hostNameMap[item?.id?.apiInfoKey?.apiCollectionId] || 
                              apiCollectionMap[item?.id?.apiInfoKey?.apiCollectionId];
                
                if (!uniqueIssuesMap.has(key)) {
                    uniqueIssuesMap.set(key, {
                        id: item?.id,
                        severity: func.toSentenceCase(item?.severity),
                        compliance: Object.keys(localSubCategoryMap[item?.id?.testSubCategory]?.compliance?.mapComplianceToListClauses || {}),
                        severityType: item?.severity,
                        issueName: item?.id?.testSubCategory,
                        category: item?.id?.testSubCategory,
                        numberOfEndpoints: 1,
                        creationTime: item?.creationTime,
                        issueStatus: item?.unread.toString(),
                        testRunName: "Test Run",
                        domains: domain ? [domain] : [],
                        urls: [{
                            method: item?.id?.apiInfoKey?.method,
                            url: item?.id?.apiInfoKey?.url,
                            id: JSON.stringify(item?.id),
                            issueDescription: item?.description,
                            jiraIssueUrl: item?.jiraIssueUrl || "",
                        }],
                    });
                } else {
                    const existingIssue = uniqueIssuesMap.get(key);
                    if (domain && !existingIssue.domains.includes(domain)) {
                        existingIssue.domains.push(domain);
                    }
                    existingIssue.urls.push({
                        method: item?.id?.apiInfoKey?.method,
                        url: item?.id?.apiInfoKey?.url,
                        id: JSON.stringify(item?.id),
                        issueDescription: item?.description,
                        jiraIssueUrl: item?.jiraIssueUrl || "",
                    });
                    existingIssue.numberOfEndpoints += 1;
                }
            });
            
            const groupedIssues = Array.from(uniqueIssuesMap.values());
            const severityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
            groupedIssues.sort((a, b) => {
                const aSeverity = (a.severityType || '').toUpperCase();
                const bSeverity = (b.severityType || '').toUpperCase();
                return (severityOrder[aSeverity] ?? 99) - (severityOrder[bSeverity] ?? 99);
            });
            
            const tableData = await transformIssues.convertToIssueTableData(groupedIssues, localSubCategoryMap);

            const modifiedTableData = tableData.map(item => {
                const { collapsibleRow, ...rest } = item;
                return rest;
            });
            
            setFilteredIssues(modifiedTableData);
        } catch (error) {
            console.error("Error filtering issues:", error);
            setFilteredIssues([]);
        } finally {
            setLoadingIssues(false);
        }
    };

    const fetchThreatIssuesData = async (apiCollectionId, endpoint, method) => {
        try {
            setLoadingIssues(true);
            const resp = await threatDetectionApi.fetchSuspectSampleData(0, [], [apiCollectionId], [endpoint], [], {}, 0, func.timeNow(), [], 10, 'ACTIVE', true, 'threat', [], '', [method]);
            let threatIssuesData = [];
            if(resp?.maliciousEvents && resp?.maliciousEvents?.length > 0) {
                resp?.maliciousEvents?.filter(event => event?.successfulExploit).forEach(event => {
                    const threatFilter = threatFiltersMap[event?.filterId];
                    threatIssuesData.push({
                        id: event?.actor,
                        severity: threatFilter?.severity || "HIGH",
                        issueName: threatFilter?.name,
                        category: event?.category,
                        domains: [event?.host],
                        compliance: [],
                        creationTime: func.prettifyEpochTime(event?.timestamp),
                        url: event?.url,
                        method: event?.method,
                        apiCollectionId: event?.apiCollectionId,
                    });
                });
                setFilteredIssues(threatIssuesData);
            }
            setTotalThreatIssues(resp?.total || 0);
        } catch (error) {
            console.error("Error filtering issues:", error);
            setFilteredIssues([]);
        }
    }

    useEffect(() => {
        if (apiDetail?.apiCollectionId && collectionIssuesData && !isThreatEnabled) {
            filterIssuesData(apiDetail.apiCollectionId, apiDetail.endpoint, apiDetail.method);
        }
        if(isThreatEnabled) {
            fetchThreatIssuesData(apiDetail.apiCollectionId, apiDetail.endpoint, apiDetail.method);
        }
    }, [apiDetail, collectionIssuesData, localSubCategoryMap, isThreatEnabled]);

    const handleRowClick = async (issue) => {
        if(isThreatEnabled) {
            let filtersMap = PersistStore.getState().filtersMap;
            const tempKey = `/dashboard/protection/threat-activity/`
            if(filtersMap !== null && filtersMap.hasOwnProperty(tempKey)){
                delete filtersMap[tempKey];
                PersistStore.getState().setFiltersMap(filtersMap);
            }


            if(issue.url.length > 0){
                const navigateUrl = window.location.origin + "/dashboard/protection/threat-activity?filters=url__" + issue.url;
                window.open(navigateUrl, "_blank")
            }
        }else{
            if (!issue.id || !issue.id[0]) {
                return;
            }
            setLoadingIssues(true);
            try {
                const resp = await IssuesApi.fetchTestingRunResult(JSON.parse(issue.id[0]));
                const hexId = resp?.testingRunResult?.hexId;
                if (hexId) {
                    const url = `/dashboard/reports/issues?result=${hexId}`;
                    window.open(url, '_blank');
                } else {
                    func.setToast(true, true, 'Could not find test run result.');
                }
            } catch (e) {
                console.error("Error in onRowClick:", e);
                func.setToast(true, true, 'Failed to fetch test run result.');
            } finally {
                setLoadingIssues(false);
            }
        }
    }

    return (
        <Box paddingBlockStart={"4"}>
            {loadingIssues ? (
                <SpinnerCentered />
            ) : (
                <VerticalStack gap={2}>
                    {totalThreatIssues > 10 && <Text variant="headingSm">Showing 10 issues out of {totalThreatIssues}</Text>}
                    <GithubSimpleTable
                        key="issues-table"
                        data={filteredIssues}
                        resourceName={{ singular: "issue", plural: "issues" }}
                        headers={issuesHeaders}
                        headings={issuesHeaders}
                        useNewRow={true}
                        condensedHeight={true}
                        hideQueryField={true}
                        loading={loadingIssues}
                        onRowClick={handleRowClick}
                        pageLimit={10}
                        showFooter={false}
                    />
                </VerticalStack>
            )}
        </Box>
    );
};

export default ApiIssuesTab; 