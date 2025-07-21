import func from "@/util/func"
import ShowListInBadge from "../../components/shared/ShowListInBadge"
import { Badge, Box, HorizontalStack, Link, Tag, Text, Avatar } from "@shopify/polaris"
import api from "./api"
import testingTransform from "../testing/transform.js"
import { history } from "@/util/history";
import IssuesCheckbox from "./IssuesPage/IssuesCheckbox.jsx"
import TooltipText from "../../components/shared/TooltipText.jsx"

const transform = {
    sortIssues: (issueItem, sortKey, sortOrder) => {
        return issueItem.sort((a, b) => {
            let aValue, bValue
            if (sortKey === 'numberOfEndpoints') {
                aValue = a.numberOfEndpoints
                bValue = b.numberOfEndpoints
            }
    
            if (aValue < bValue) return -1 * sortOrder
            if (aValue > bValue) return 1 * sortOrder
            return 0;
        })
    },
    async getNextUrl(issueId, isCompliancePage){
        const res = await api.fetchTestingRunResult(issueId)
        const hexId = isCompliancePage ? `/dashboard/reports/compliance?result=${res.testingRunResult.hexId}` 
        : `/dashboard/reports/issues?result=${res.testingRunResult.hexId}`
        history.navigate(hexId)
    },
    getIssuesPageCollapsibleRow(urls, isCompliancePage) {
        return(
        <tr style={{background: "#FAFBFB", padding: '0px !important', borderTop: '1px solid #dde0e4'}}>
            <td colSpan={'100%'} style={{padding: '0px !important'}}>
                {urls.map((ele,index)=>{
                const borderStyle = index < (urls.length - 1) ? {borderBlockEndWidth : 1} : {}
                const jiraKey = ele?.jiraIssueUrl && ele.jiraIssueUrl.length > 0 ?  /[^/]*$/.exec(ele.jiraIssueUrl)[0] : ""
                return(
                    <Box padding={"2"} paddingInlineEnd={"4"} paddingInlineStart={"3"} key={index}
                    borderColor="border-subdued" {...borderStyle}>
                        <HorizontalStack gap={24} wrap={false}>
                            <Box paddingInlineStart={10}>
                                <IssuesCheckbox id={ele.id}/>
                            </Box>
                            <HorizontalStack gap={"4"}>
                                <Link monochrome onClick={() => this.getNextUrl(JSON.parse(ele.id), isCompliancePage)} removeUnderline >
                                    {testingTransform.getUrlComp(ele.url)}
                                </Link>
                                <Box maxWidth="250px" paddingInlineStart="3">
                                  <TooltipText
                                      text={ele.issueDescription}
                                      tooltip={ele.issueDescription}
                                      textProps={{ color: "subdued"}}
                                      />
                                  </Box>
                                {jiraKey &&
                                    <Tag>
                                        <HorizontalStack gap={1}>
                                            <Avatar size="extraSmall" shape='round' source="/public/logo_jira.svg" />
                                            <Link url={ele?.jiraIssueUrl} target="_blank">
                                                <Text>
                                                    {jiraKey}
                                              </Text>
                                            </Link>
                                        </HorizontalStack>
                                    </Tag>
                                }
                            </HorizontalStack>
                        </HorizontalStack>
                    </Box>
                )
                })}
            </td>
        </tr>
        )
    },
    convertToIssueTableData: async (rawData, subCategoryMap, isCompliancePage = false) => {
        const processedData = await Promise.all(
            await Promise.all(rawData.map(async (issue, idx) => {
                const key = `${issue.id.testSubCategory}|${issue.severity}|${issue.testRunIssueStatus}|${idx}`
                let totalCompliance = (issue.compliance || []).length
                let maxShowCompliance = 2
                let badge = totalCompliance > maxShowCompliance ? <Badge size="extraSmall">+{totalCompliance - maxShowCompliance}</Badge> : null
                return {
                    key: key,
                    id: issue.urls.map((x) => x.id),
                    severity: <div className={`badge-wrapper-${issue.severityType}`}>
                                <Badge size="small" key={idx}>{issue.severity}</Badge>
                            </div>,
                    issueName: subCategoryMap[issue.issueName]?.testName,
                    category: subCategoryMap[issue.issueName]?.superCategory?.shortName,
                    numberOfEndpoints: issue.numberOfEndpoints,
                    compliance: <HorizontalStack wrap={false} gap={1}>{issue.compliance.slice(0, maxShowCompliance).map(x => <Avatar source={func.getComplianceIcon(x)} shape="square"  size="extraSmall"/>)}<Box>{badge}</Box></HorizontalStack>,
                    creationTime: func.prettifyEpoch(issue.creationTime),
                    issueStatus: (
                        <div className={`custom-tag-${issue.issueStatus}`}>
                            <Tag>
                                <Text>{issue.issueStatus === 'false' ? "read" : "unread"}</Text>
                            </Tag>
                        </div>
                    ),
                    domains: (
                        <ShowListInBadge
                            itemsArr={issue.domains}
                            maxItems={1}
                            maxWidth={"250px"}
                            status={"new"}
                            itemWidth={"200px"}
                        />
                    ),
                    collapsibleRow: transform.getIssuesPageCollapsibleRow(issue.urls.map(urlObj => ({
                        url: `${urlObj.method} ${urlObj.url}`,
                        id: urlObj.id,
                        issueDescription: urlObj.issueDescription,
                        jiraIssueUrl: urlObj.jiraIssueUrl || ""
                    })), isCompliancePage)
                }
            }))
        )
        
        return processedData
    }
}

export default transform