import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Box, Text, HorizontalGrid, LegacyCard, List } from '@shopify/polaris';
import SampleDataComponent from '../../../../components/shared/SampleDataComponent';

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
        // show in format of bulleted list inside modal
        return ( 
            <Modal
                open={open}
                onClose={onClose}
                title={title}
            >
                <Modal.Section>
                    <Box padding="4">
                        <List type="bullet">
                            {content.map(tool => (
                                <List.Item key={tool}>{tool}</List.Item>
                            ))}
                        </List>
                    </Box>
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
    type: PropTypes.oneOf(['text', 'http']).isRequired,
    content: PropTypes.string,
    sampleData: PropTypes.object,
};

ChatInfoModal.defaultProps = {
    content: null,
    sampleData: null,
};

export default ChatInfoModal;
