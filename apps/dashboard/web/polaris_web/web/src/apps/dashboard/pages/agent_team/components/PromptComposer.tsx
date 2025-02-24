import React, { useEffect, useState } from 'react';
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
  const [isFocused, setIsFocused] = useState(false);
  const {
    currentPrompt,
    setCurrentPrompt,
    availableModels,
    selectedModel,
    setSelectedModel,
    isPaused,
    setAttemptedOnPause
 } = useAgentsStore();
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
    editor?.setEditable(!isPaused);
  }, [isPaused]);

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
    <div className={`flex flex-col gap-4 border border-1 border-[var(--borderShadow-box-shadow)] py-2 px-4 rounded-sm relative z-[500] bg-white ${isFocused ? 'ring ring-violet-200' : ''}`}>
      <PausedState onResume={() => {}} onDiscard={() => {}} />
      <div className="flex flex-col gap-2 justify-start">
        <div className="w-full" onClick={() => isPaused && setAttemptedOnPause(true)}>
          <Button 
            disabled={isPaused} 
            icon={PlusMinor} 
            size="micro" 
            monochrome
          >Add context</Button>
        </div>
        <EditorContent editor={editor} onClick={() => isPaused && setAttemptedOnPause(true)} />
      </div>
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-1">
         {availableModels.length > 0 && (
          <ModelPicker availableModels={availableModels} selectedModel={selectedModel} setSelectedModel={setSelectedModel} />
         )}
        </div>
        <Tooltip content="Send">
          <div className="w-full" onClick={() => isPaused && setAttemptedOnPause(true)}>
            <Button
              disabled={!selectedModel || !currentPrompt || isPaused}
              size="slim"
              plain
              icon={SendMajor}
              accessibilityLabel="Send message to agent"
              onClick={handleSend}
            />
          </div>
        </Tooltip>
      </div>
    </div>
  );
};