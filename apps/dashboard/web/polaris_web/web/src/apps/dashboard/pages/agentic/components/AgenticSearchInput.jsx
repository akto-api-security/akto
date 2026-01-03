
import React, { useState, useCallback } from 'react';
import { TextField, Box, Icon, HorizontalStack } from '@shopify/polaris';
import { SearchMajor, SendMajor } from '@shopify/polaris-icons';

function AgenticSearchInput() {
    const [value, setValue] = useState('');

    const handleChange = useCallback((newValue) => setValue(newValue), []);

    return (
        <Box maxWidth="800px" marginInline="auto" paddingBlockEnd="10">
            <div className="agentic-search-input">
                <TextField
                    value={value}
                    onChange={handleChange}
                    placeholder="How can I help you today?"
                    autoComplete="off"
                    prefix={<Icon source={SearchMajor} color="subdued" />}
                    role="search"
                    connectedRight={
                        <div style={{ cursor: 'pointer', paddingRight: '8px' }}>
                            <Icon source={SendMajor} color="base" />
                        </div>
                    }
                />
            </div>
        </Box>
    );
}

export default AgenticSearchInput;
