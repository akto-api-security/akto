import { Banner, Text, VerticalStack } from "@shopify/polaris"
import func from "@/util/func"
import observeFunc from "../../observe/transform"
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable"
import { TABLE_HEADERS, TABLE_RESOURCE_NAME, countFailedCollections, toTableRows } from "./trafficRetention"

function summaryText(preview) {
    const since = func.prettifyEpoch(preview.cutoffEpoch)
    if (preview.collections.length === 0) {
        return "There are no active host-based collections to evaluate."
    }
    if (preview.totalStaleApis === 0) {
        return `No APIs were found with no traffic in the last ${observeFunc.formatNumberWithCommas(preview.days)} days (cutoff ${since}).`
    }
    const prefix = preview.complete ? "" : "At least "
    return `${prefix}${observeFunc.formatNumberWithCommas(preview.totalStaleApis)} APIs have had no traffic since ${since}.`
}

function TrafficRetentionResults({ preview }) {
    const failed = countFailedCollections(preview)

    return (
        <VerticalStack gap="3">
            {!preview.complete && (
                <Banner status="warning" title="Some collections could not be evaluated">
                    <Text variant="bodyMd">
                        {`${observeFunc.formatNumberWithCommas(failed)} of ${observeFunc.formatNumberWithCommas(preview.collections.length)} collections could not be read, so the total below is a lower bound. Run the dry run again before deleting.`}
                    </Text>
                </Banner>
            )}
            <Text variant="bodyMd">{summaryText(preview)}</Text>
            {preview.collections.length > 0 && (
                <GithubSimpleTable
                    key={`retention_${preview.cutoffEpoch}`}
                    data={toTableRows(preview.collections)}
                    resourceName={TABLE_RESOURCE_NAME}
                    headers={TABLE_HEADERS}
                    loading={false}
                    hideQueryField={true}
                />
            )}
        </VerticalStack>
    )
}

export default TrafficRetentionResults
