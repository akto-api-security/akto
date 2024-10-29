import React, { useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from '../../../components/shared/TitleWithInfo'
import { Box, Button, Divider, LegacyCard } from '@shopify/polaris'
import TestSuiteResourceList from './TestSuiteResourceList'
import func from '@/util/func'

const TestSuite = () => {
    const [selectedTests, setSelectedTests] = useState([])
    const [testSuite, setTestSuite] = useState('')

    const activeTestPreviousFooterActions = {
        content: 'Save',
        onAction: (testSuite.length === 0 || selectedTests.length === 0) ? () => {} : () => {func.setToast(true, false, "Test suite has been saved!")}
    }

    const components = (
        <LegacyCard primaryFooterAction={activeTestPreviousFooterActions} >
            <TestSuiteResourceList setSelectedTests={setSelectedTests} testSuite={testSuite} setTestSuite={setTestSuite} isCreatingTestSuite={true} />
            <Box paddingBlockEnd={"5"}>
                <Divider />
            </Box>
        </LegacyCard>
    )
    return (
        <PageWithMultipleCards
            isFirstPage={true}
            title={
            <TitleWithInfo
                titleText={"Test Suite"}
            />
            }
            secondaryActions={<Button secondaryActions>Documentation</Button>}
            components={[components]}
        />
    )
}

export default TestSuite