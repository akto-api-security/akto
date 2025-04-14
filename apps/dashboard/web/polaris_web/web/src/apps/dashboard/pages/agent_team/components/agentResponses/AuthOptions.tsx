import { Text, Icon } from '@shopify/polaris';
import { KeyMajor } from '@shopify/polaris-icons';
import React, { useState } from 'react';

type AgentOptionProps = {
    options: string[];
    text: string;
    onSelect: (option: string) => void;
    showIcon?: boolean;
}

export const AgentOptions = ({ options, onSelect, text, showIcon = true }: AgentOptionProps) => {
    const [selectedOption, setSelectedOption] = useState<string | null>(null);

    const handleSelect = (option: string) => {
        setSelectedOption(option);
        onSelect(option);
    }

    return (
        <div className="flex flex-col gap-2">
            <Text as="p">{text}</Text>
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
                        {showIcon && <Icon source={KeyMajor} />}
                        {option}
                    </button>
                ))}
            </div>
        </div>
    )
}