import { Box, Button, Text, Icon } from '@shopify/polaris';
import { KeyMajor } from '@shopify/polaris-icons';
import React, { useState } from 'react';

type AuthOptionProps = {
    options: string[];
    onSelect: (option: string) => void;
}

export const AuthOptions = ({ options, onSelect }: AuthOptionProps) => {
    const [selectedOption, setSelectedOption] = useState<string | null>(null);

    const handleSelect = (option: string) => {
        setSelectedOption(option);
        onSelect(option);
    }

    return (
        <div className="flex flex-col gap-2">
            <Text as="p">I've identified 4 tokens. Which one I should proceed with?</Text>
            <div className="flex gap-2">
                {options.map((option) => (
                    <button
                        className={
                            `cursor-pointer flex gap-1 items-center text-sm text-[var(--text-default)]
                            py-0.5 px-2 rounded-sm hover:bg-[var(--p-color-bg-primary-subdued-hover)]
                            border
                            ${selectedOption === option ? 'bg-[var(--p-color-bg-primary-subdued-hover)] border-[#B6B0FE]' : 'border-transparent'}
                        `}
                        onClick={() => handleSelect(option)}
                    >
                        <Icon source={KeyMajor} />
                        {option}
                    </button>
                ))}
            </div>
        </div>
    )
}