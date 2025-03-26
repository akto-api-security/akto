import * as Select from '@radix-ui/react-select';
import React, { MouseEventHandler, useEffect, useState } from 'react';
import { CaretDownMinor, CaretUpMinor, TickMinor } from '@shopify/polaris-icons';
import { motion, AnimatePresence } from 'framer-motion';

import { Icon, Text } from '@shopify/polaris';
import { Model } from '../types';
import { isBlockingState, useAgentsStore } from '../agents.store';

export const MODELS: Model[] = [
  { id: 'claude-3-sonnet', name: 'Claude-3.5-sonnet' },
  { id: 'gpt-4o', name: 'GPT-4o' },
  { id: 'gpt-4o-mini', name: 'GPT-4o-mini' },
  { id: 'gpt-3.5-turbo', name: 'GPT-3.5-turbo' },
  { id: 'gemini-1.5-flash', name: 'Gemini-1.5-flash' },
  // Add other models here
];

interface ModelPickerProps {
  selectedModel: Model | null;
  setSelectedModel: (modelId: Model) => void;
  availableModels: Model[];
}

export const ModelPicker = ({ availableModels, selectedModel, setSelectedModel }: ModelPickerProps) => {
  const [open, setOpen] = useState(false);

  const onModelChange = (value: string) => {
    const model = availableModels.find((model) => model.id === value);
    if (model) {
      setSelectedModel(model);
    }
  };

  const { agentState, setAttemptedInBlockedState } = useAgentsStore();

  const isInBlockedState = isBlockingState(agentState);

  useEffect(() => {
    if (availableModels.length > 0) {
      setSelectedModel(availableModels[0]);
    }
  }, [availableModels]);

  const handleTriggerClick: MouseEventHandler<HTMLButtonElement> = (event) => {
    if (isInBlockedState) {
      setAttemptedInBlockedState(true);
      event.preventDefault();
    } else {
      setOpen(true);
    }
  }

  const trigger = (
    <button 
      className="flex cursor-pointer text-xs items-center outline-none"
      onClick={handleTriggerClick}
    >
      {open ? <Icon source={CaretUpMinor} color="subdued" /> : <Icon source={CaretDownMinor} color="subdued" />}
      <Text as="span" color="subdued" variant="bodySm">
        {selectedModel?.name}
      </Text>
    </button>
  )


  return (
    <Select.Root 
      onOpenChange={setOpen} 
      value={selectedModel?.id} 
      onValueChange={onModelChange} 
      defaultValue={availableModels[0].id}
    >
      {isInBlockedState ? trigger : <Select.Trigger aria-label="Select model" asChild disabled={isInBlockedState}>{trigger}</Select.Trigger>}
      <AnimatePresence>
        <Select.Content
          position="popper"
          className="z-[1000] bg-white rounded-lg shadow-lg border border-gray-200 w-[200px] overflow-hidden"
          sideOffset={5} 
          side="top"
          asChild
        >   
          <motion.div
            initial={{ opacity: 0, y: -10, scaleY: 0, transformOrigin: 'bottom' }}
            animate={{ opacity: 1, y: 0, scaleY: 1 }}
            exit={{ opacity: 0, y: -10, scaleY: 0 }}
            transition={{ 
              type: "spring",
              stiffness: 500,
              damping: 30,
              mass: 0.8
            }}
          >
            <Select.Viewport className="p-1">
              {availableModels.map((model, index) => (
                  <Select.Item
                    key={model.id}
                    value={model.id}
                    className="flex outline-none items-center gap-2 px-6 py-2 text-xs hover:bg-gray-100 cursor-pointer h-[24px]"
                  >
                    <Select.ItemText>{model.name}</Select.ItemText>
                    <Select.ItemIndicator>
                      <Icon source={TickMinor} color="subdued" />
                    </Select.ItemIndicator>
                  </Select.Item>
              ))}
            </Select.Viewport>
          </motion.div>
        </Select.Content>
      </AnimatePresence>
    </Select.Root>
  );
};