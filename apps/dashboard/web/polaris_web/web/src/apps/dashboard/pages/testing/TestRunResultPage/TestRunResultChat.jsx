import React from 'react';
import PropTypes from 'prop-types';
import { Box, VerticalStack } from '@shopify/polaris';
import ConversationHistory from './components/ConversationHistory';
import AiExecutionJourney from './components/AiExecutionJourney';

function TestRunResultChat({ conversations, testResults, runAutomatedTests = false }) {
    return (
        <Box padding="4" minHeight="500px">
            <VerticalStack gap="4">
                <AiExecutionJourney runAutomatedTests={runAutomatedTests} />
                <ConversationHistory conversations={conversations} testResults={testResults} />
            </VerticalStack>
        </Box>
    );
}

TestRunResultChat.propTypes = {
    conversations: PropTypes.array,
    testResults: PropTypes.array,
    runAutomatedTests: PropTypes.bool,
};

TestRunResultChat.defaultProps = {
    conversations: [],
    testResults: [],
    runAutomatedTests: false,
};

export default TestRunResultChat;
