import { useEffect, useState } from "react"
import func from "../../../../../util/func"
import testingApi from "../../../pages/testing/api"
import testingTransform from "../../../pages/testing/transform"
import { Box, Button, Text, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "../../../components/shared/DropdownSearch"

function TestFalsePositiveInitializer(props) {
    const { agentType } = props
    /*
    Select a testing run. We will run the agent on all the 
    vulnerable test results for the testing run.

    TODO: handle for multiple summaries. For now, taking latest summary in account.
    */

    const [allTestingRuns, setAllTestingRuns] = useState([])
    const [testingRuns, setTestingRuns] = useState([])

    const optionsList = allTestingRuns.map((x) => {
        return {
            label: x.name,
            value: x.testingRunResultSummaryHexId,
        }
    })

    async function fetchTypeTests(type) {

        const endTimestamp = func.timeNow();
        const startTimestamp = 0;

        let skip = 0;
        let limit = 50;
        let total = 50;
        let ret = [];

        while (skip < total) {
            await testingApi.fetchTestingDetails(
                startTimestamp, endTimestamp, "scheduleTimestamp", -1, skip, limit, {}, type, ""
            ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
                ret = [...ret, ...testingTransform.processData(testingRuns, latestTestingRunResultSummaries, true)];
                total = testingRunsCount;
            });
            skip += limit
        }
        return ret;
    }

    async function fetchAllTests() {
        let tempTestingRuns = []
        tempTestingRuns = [...tempTestingRuns, ...await fetchTypeTests("ONE_TIME")]
        tempTestingRuns = [...tempTestingRuns, ...await fetchTypeTests("CI_CD")]
        tempTestingRuns = [...tempTestingRuns, ...await fetchTypeTests("RECURRING")]
        tempTestingRuns = [...tempTestingRuns, ...await fetchTypeTests("CONTINUOUS_TESTING")]
        setAllTestingRuns(tempTestingRuns)
    }

    useEffect(() => {
        fetchAllTests();
    }, [])


    async function startAgent(testingRuns) {
        if (testingRuns.length === 0) {
            func.setToast(true, true, "Please select tests to run the agent")
            return
        }

        await agentApi.createAgentRun({
            agent: agentType,
            data: {
                testingRunSummaries: testingRuns
            }
        })
        func.setToast(true, false, "Agent run scheduled")
    }

    function StartButton() {
        return <Button onClick={() => startAgent(testingRuns)} >
            Let's start!!
        </Button>
    }


    function TestSelector() {
        return (
            <Box as='div' paddingBlockStart={"5"}>
                <VerticalStack gap={"4"}>
                    <Text as='span' variant='bodyMd'>
                        Hey! Let's select Test runs to run the test false positive finder on.
                    </Text>
                    <Box width='350px' paddingInlineStart={"2"}>
                        <DropdownSearch
                            placeholder={"Select testing runs"}
                            allowMultiple
                            optionsList={optionsList}
                            setSelected={setTestingRuns}
                            preSelected={testingRuns}
                            value={`${testingRuns.length} tests selected`}
                        />
                    </Box>
                    <StartButton />
                </VerticalStack>
            </Box>
        )
    }

    return <TestSelector />
}

export default TestFalsePositiveInitializer