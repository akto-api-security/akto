import React, { useEffect, useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import ListItem from '@tiptap/extension-list-item';
import OrderedList from '@tiptap/extension-ordered-list';
import BulletList from '@tiptap/extension-bullet-list';
import './composer.styles.css';

import { Button, Tooltip } from '@shopify/polaris';

import { SendMajor } from '@shopify/polaris-icons';

import { ModelPicker } from './ModelPicker';
import { isBlockingState, useAgentsStore } from '../agents.store';
import { PromptPayload, State } from '../types';
import { getPromptContent } from '../utils';
import { BlockedState } from './BlockedState';
import api from '../api';
import func from '../../../../../util/func';
import { intermediateStore } from '../intermediate.store';
import { structuredOutputFormat } from '../constants';


interface PromptComposerProps {
  onSend: (prompt: PromptPayload) => void;
  agentId: string | undefined;
}

export const PromptComposer = ({ onSend, agentId }: PromptComposerProps) => {
  const [isFocused, setIsFocused] = useState(false);
  const { currentProcessId, currentSubprocess, currentAttempt } = useAgentsStore();
  const { filteredUserInput } = intermediateStore();
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
        placeholder: 'Message agent...',
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
  
  async function onResume() {

    // when selected choices are provided by the user, accepted case should be returned to the agent
    // agent if gets user-input, with the accepted state, it should take that into account
    // Does not make sense ^, since irrespective of weather the user selects or takes all,
    // The res will always be similar, i.e. the result could be the complete array or a sub-array, 
    // but they would behave the same.
    // Using a single state till better use case.

    await api.updateAgentSubprocess({
      processId: currentProcessId,
      subProcessId: currentSubprocess,
      attemptId: currentAttempt,
      state: State.ACCEPTED.toString(),
      data: { selectedOptions: structuredOutputFormat(filteredUserInput, agentId , currentSubprocess || "") }
    });
    func.setToast(true, false, "Member solution accepted")
  }

  async function onDiscard() {
    await api.updateAgentSubprocess({
      processId: currentProcessId,
      subProcessId: currentSubprocess,
      attemptId: currentAttempt,
      state: State.DISCARDED.toString(),
      data: { selectedOptions: structuredOutputFormat(filteredUserInput, agentId , currentSubprocess || "") }
    });
    func.setToast(true, false, "Member solution discarded")
  }

  return (
    <div className={`flex flex-col gap-4 border border-1 border-[var(--borderShadow-box-shadow)] py-2 px-4 rounded-sm relative z-[500] bg-white ${isFocused ? 'ring ring-violet-200' : ''}`}>
      <BlockedState onResume={onResume} onDiscard={onDiscard} />
      <div className="flex flex-col gap-2 justify-start">
        <div className="w-full" onClick={() => isInBlockedState && setAttemptedInBlockedState(true)}>
          {/* <Button 
            disabled={isInBlockedState} 
            icon={PlusMinor} 
            size="micro" 
            monochrome
          >Add context</Button> */}
        </div>
        <EditorContent editor={editor} onClick={() => isInBlockedState && setAttemptedInBlockedState(true)} />
      </div>
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-1">
         {availableModels.length > 0 && (
          <ModelPicker availableModels={availableModels} selectedModel={selectedModel} setSelectedModel={setSelectedModel} />
         )}
        </div>
        <Tooltip content="Send">
          <div className="w-full" onClick={() => isInBlockedState && setAttemptedInBlockedState(true)}>
            <Button
              disabled={!selectedModel || !currentPrompt || isInBlockedState}
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