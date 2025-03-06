import React, { useEffect, useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import ListItem from '@tiptap/extension-list-item';
import OrderedList from '@tiptap/extension-ordered-list';
import BulletList from '@tiptap/extension-bullet-list';
import './composer.styles.css';

import { Button, Icon, Tooltip, Text, VerticalStack, Box, HorizontalStack } from '@shopify/polaris';

import { SendMajor } from '@shopify/polaris-icons';


import { ModelPicker } from './ModelPicker';
import { isBlockingState, useAgentsStore } from '../agents.store';
import { PromptPayload } from '../types';
import { getPromptContent } from '../utils';
import { BlockedState } from './BlockedState';


interface PromptComposerProps {
  onSend: (prompt: PromptPayload) => void;
}

export const PromptComposer = ({ onSend }: PromptComposerProps) => {
  const [isFocused, setIsFocused] = useState(false);
  const {
    currentPrompt,
    setCurrentPrompt,
    availableModels,
    selectedModel,
    setSelectedModel,
    agentState,
    setAttemptedInBlockedState,
 } = useAgentsStore();

  const isInBlockedState = isBlockingState(agentState);
  const editor = useEditor({
    extensions: [
      StarterKit,
      Placeholder.configure({
        placeholder: 'Message member...',
        showOnlyWhenEditable: false,
      }),
      ListItem,
      OrderedList,
      BulletList,
    ],
    content: '',
    editorProps: {
      attributes: {
        class: 'prose prose-slate focus:outline-none',
      },
    },
    onUpdate: ({ editor }) => {
      setCurrentPrompt(getPromptContent(editor));
    },
    onFocus: () => {
      setIsFocused(true);
    },
    onBlur: () => {
      setIsFocused(false);
    }
  });

  useEffect(() => {
    editor?.setEditable(!isInBlockedState);
  }, [isInBlockedState]);

  if (!editor) return null;

  const handleSend = () => {
    if (!selectedModel || !currentPrompt) return;

    const prompt: PromptPayload = {
      model: selectedModel,
      prompt: currentPrompt,
    }

    onSend(prompt);
  }

  return (
    <Box
      paddingBlockStart="2"
      paddingBlockEnd="2"
      paddingInlineStart="3"
      paddingInlineEnd="3"
      borderRadius="1"
      borderWidth="1"
      borderColor="border"
      position="relative"
    >
      <BlockedState onResume={() => {}} onDiscard={() => {}} />
      <VerticalStack gap="4">
        <EditorContent editor={editor} />
        <HorizontalStack align="space-between">
          <ModelPicker availableModels={availableModels} selectedModel={selectedModel} setSelectedModel={setSelectedModel} />
          <Button icon={SendMajor} onClick={handleSend} plain />
        </HorizontalStack>
      </VerticalStack>
    </Box>
  );
};