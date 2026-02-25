import React from 'react';
import PropTypes from 'prop-types';
import { Modal, Box, Text, HorizontalGrid, LegacyCard } from '@shopify/polaris';
import SampleDataComponent from '../../../../components/shared/SampleDataComponent';

function ChatInfoModal({ open, onClose, title, type, content, sampleData }) {
    if (type === 'text') {
        return (
            <Modal
                open={open}
                onClose={onClose}
                title={title}
                primaryAction={{
                    content: 'Close',
                    onAction: onClose,
                }}
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
