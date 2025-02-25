import { Box, Button, Text, Icon } from '@shopify/polaris';
import { KeyMajor } from '@shopify/polaris-icons';
import React from 'react';

type AuthOptionProps = {
    options: string[];
    onSelect: (option: string) => void;
}

export const AuthOptions = ({ options, onSelect }: AuthOptionProps) => {
    return (
        <div className="flex flex-col gap-2">
            <Text as="p">I've identified 4 tokens. Which one I should proceed with?</Text>
            <div className="flex gap-2">
                {options.map((option) => (
                    <Button plain monochrome icon={KeyMajor} onClick={() => onSelect(option)} removeUnderline>
                        {option}
                    </Button>
                ))}
            </div>
        </div>
    )
}