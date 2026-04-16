import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Box, Text, HorizontalGrid, LegacyCard, VerticalStack } from '@shopify/polaris';
import SampleDataComponent from '../../../../components/shared/SampleDataComponent';
import MarkdownViewer from '../../../../components/shared/MarkdownViewer';

function parseDataTrace(dataTrace) {
    if (!dataTrace) return null;
    try {
        const parsed = JSON.parse(dataTrace);
        if (typeof parsed === 'object' && parsed !== null) {
            return { type: 'json', value: JSON.stringify(parsed, null, 2) };
        }
    } catch {}
    return { type: 'markdown', value: dataTrace };
}

function ChatInfoModal({ open, onClose, title, type, content, sampleData }) {
    if (type === 'text') {
        return (
            <Modal
                open={open}
                onClose={onClose}
                title={title}
            >
                <Modal.Section>
                    <Box padding="4">
                        <Text as="p" variant="bodyMd">
                            {content}
                        </Text>
                    </Box>
                </Modal.Section>
            </Modal>
        );
    }

    if (type === 'http') {
        return (
            <Modal
                open={open}
                onClose={onClose}
                title={title}
                large
            >
                <Modal.Section>
                    <HorizontalGrid columns="2" gap="4">
                        {['request', 'response'].map((reqResType) => (
                            <Box key={reqResType}>
                                <LegacyCard>
                                    <SampleDataComponent
                                        type={reqResType}
                                        sampleData={sampleData}
                                        minHeight="450px"
                                        isNewDiff={true}
                                        readOnly={true}
                                    />
                                </LegacyCard>
                            </Box>
                        ))}
                    </HorizontalGrid>
                </Modal.Section>
            </Modal>
        );
    }

    if (type === 'tools') {
        const toolEntries = Object.entries(content || {});
        return (
            <Modal
                open={open}
                onClose={onClose}
                title={title}
                large
            >
                <Modal.Section>
                    <VerticalStack gap="4">
                        {toolEntries.map(([toolName, toolMeta]) => {
                            const trace = parseDataTrace(toolMeta?.dataTrace);
                            const displayName = toolMeta?.name || toolName;
                            return (
                                <Box key={toolName}>
                                    <VerticalStack gap="2">
                                        <Text variant="bodyMd" fontWeight="semibold">{displayName}</Text>
                                        {trace ? (
                                            trace.type === 'json' ? (
                                                <SampleDataComponent
                                                    type="response"
                                                    sampleData={{ message: trace.value }}
                                                    minHeight="200px"
                                                    readOnly={true}
                                                    simpleJson={true}
                                                />
                                            ) : (
                                                <MarkdownViewer markdown={trace.value} />
                                            )
                                        ) : null}
                                    </VerticalStack>
                                </Box>
                            );
                        })}
                    </VerticalStack>
                </Modal.Section>
            </Modal>
        );
    }

    return null;
}

ChatInfoModal.propTypes = {
    open: PropTypes.bool.isRequired,
    onClose: PropTypes.func.isRequired,
    title: PropTypes.string.isRequired,
    type: PropTypes.oneOf(['text', 'http', 'tools']).isRequired,
    content: PropTypes.oneOfType([PropTypes.string, PropTypes.object]),
    sampleData: PropTypes.object,
};

ChatInfoModal.defaultProps = {
    content: null,
    sampleData: null,
};

export default ChatInfoModal;
