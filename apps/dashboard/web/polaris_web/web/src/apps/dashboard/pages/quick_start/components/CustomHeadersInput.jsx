import { Button, TextField, Text, VerticalStack, HorizontalStack } from '@shopify/polaris';
import { DeleteMajor, CirclePlusMajor } from '@shopify/polaris-icons';
import React from 'react';

function CustomHeadersInput({ customHeaders, setCustomHeaders, description }) {

    function handleUpdate(targetIndex, field, value) {
        setCustomHeaders(prev => {
            return prev.map((header, index) => {
                if (index === targetIndex) {
                    return { ...header, [field]: value };
                }
                return header;
            });
        });
    }

    function handleAdd() {
        setCustomHeaders(prev => [...prev, { key: "", value: "" }]);
    }

    function handleRemove(removeIndex) {
        setCustomHeaders(prev => prev.filter((_, index) => index !== removeIndex));
    }

    return (
        <VerticalStack gap={3}>
            <Text variant='headingMd'>Custom Headers</Text>
            <Text variant='bodyMd' color='subdued'>
                {description ?? "Add custom HTTP headers to be sent with all crawler requests"}
            </Text>

            {customHeaders.map((header, index) => (
                <div key={index}>
                    <HorizontalStack gap={2} wrap={false} align="center">
                        <div style={{ flex: 1 }}>
                            <TextField
                                label={index === 0 ? "Header Name" : ""}
                                placeholder="X-Custom-Header"
                                value={header.key}
                                onChange={(value) => handleUpdate(index, "key", value)}
                            />
                        </div>
                        <div style={{ flex: 1 }}>
                            <TextField
                                label={index === 0 ? "Header Value" : ""}
                                placeholder="custom-value"
                                value={header.value}
                                onChange={(value) => handleUpdate(index, "value", value)}
                            />
                        </div>
                        <div style={{ paddingTop: index === 0 ? "24px" : "0" }}>
                            <Button
                                icon={DeleteMajor}
                                onClick={() => handleRemove(index)}
                                plain
                            />
                        </div>
                    </HorizontalStack>
                </div>
            ))}

            <div>
                <Button icon={CirclePlusMajor} onClick={handleAdd} plain>
                    Add header
                </Button>
            </div>
        </VerticalStack>
    );
}

export default CustomHeadersInput;
