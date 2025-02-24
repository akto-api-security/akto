import React, { useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import ListItem from '@tiptap/extension-list-item';
import OrderedList from '@tiptap/extension-ordered-list';
import BulletList from '@tiptap/extension-bullet-list';
import './composer.styles.css';

import { Button, Icon, Tooltip, Text } from '@shopify/polaris';

import { PauseMajor, PlusMinor, SendMajor, TimelineAttachmentMajor } from '@shopify/polaris-icons';


import { ModelPicker } from './ModelPicker';
import { useAgentsStore } from '../agents.store';
import { PromptPayload } from '../types';
import { getPromptContent } from '../utils';
import { PausedState } from './PausedState';


interface PromptComposerProps {
  onSend: (prompt: PromptPayload) => void;
}

export const PromptComposer = ({ onSend }: PromptComposerProps) => {
  const {
    currentPrompt,
    setCurrentPrompt,
    availableModels,
    selectedModel,
    setSelectedModel,
    isPaused,
 } = useAgentsStore();
  const editor = useEditor({
    extensions: [
      StarterKit,
      Placeholder.configure({
        placeholder: 'Message member...',
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
  });

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
    <div className="flex flex-col gap-4 border border-1 border-[#AEB4B9] py-2 px-4 rounded-sm relative">
      <PausedState onResume={() => {}} onDiscard={() => {}} />
      <div className="flex flex-col gap-2 justify-start">
        <div className="w-fit">
          <Button disabled={isPaused} icon={PlusMinor} size="micro">Add context</Button>
        </div>
        <EditorContent disabled={isPaused} editor={editor} />
      </div>
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-1">
         {availableModels.length > 0 && (
          <ModelPicker availableModels={availableModels} selectedModel={selectedModel} setSelectedModel={setSelectedModel} />
         )}
        </div>
        <Tooltip content="Send">
          <Button
            disabled={!selectedModel || !currentPrompt || isPaused}
            size="slim"
            plain
            icon={SendMajor}
            accessibilityLabel="Send message to agent"
            onClick={handleSend}
          />
        </Tooltip>
      </div>
    </div>
  );
};