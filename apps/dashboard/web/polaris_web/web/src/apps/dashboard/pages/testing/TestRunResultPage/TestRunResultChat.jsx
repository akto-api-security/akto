import React from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack } from '@shopify/polaris';
import ConversationHistory from './components/ConversationHistory';
import AiExecutionJourney from './components/AiExecutionJourney';

function TestRunResultChat({ conversations, testResults, runAutomatedTests = false, selectedTestRunResult = null, staticMode = false }) {
    const aiSummaryTraces = selectedTestRunResult?.aiSummaryTraces;
    return (
        <Box padding="4" minHeight="500px">
            <VerticalStack gap="4">
                <AiExecutionJourney runAutomatedTests={runAutomatedTests} aiSummaryTraces={aiSummaryTraces} />
                <ConversationHistory conversations={conversations} testResults={testResults} staticMode={staticMode} />
            </VerticalStack>
        </Box>
    );
}

TestRunResultChat.propTypes = {
    conversations: PropTypes.array,
    testResults: PropTypes.array,
    runAutomatedTests: PropTypes.bool,
    selectedTestRunResult: PropTypes.object,
};

TestRunResultChat.defaultProps = {
    conversations: [],
    testResults: [],
    runAutomatedTests: false,
    selectedTestRunResult: null,
};

export default TestRunResultChat;
