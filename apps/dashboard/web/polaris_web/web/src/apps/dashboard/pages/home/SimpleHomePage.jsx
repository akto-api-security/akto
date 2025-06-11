import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { useEditor, EditorContent, BubbleMenu } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import { Ai } from '@tiptap-pro/extension-ai';
import { Plus, Sliders, ArrowUp, ShieldCheck, Detective, Bug, TestTube, X, BookOpen, Terminal, MagnifyingGlass, TextAa } from 'phosphor-react';
import { Popover, ActionList, LegacyCard, TextField, Text, Icon } from '@shopify/polaris';
import settingFunctions from '../settings/module';

function SimpleHomePage() {
  const iconButtonStyle = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'transparent',
    border: 'none',
    cursor: 'pointer',
    color: '#6b7280',
    padding: '4px',
    borderRadius: '8px',
    transition: 'background-color 0.2s',
  };

  const toolButtonStyle = {
    ...iconButtonStyle,
    gap: '6px',
    fontSize: '14px',
    fontWeight: '500',
  };

  const [isEditorEmpty, setIsEditorEmpty] = useState(true);
  const [adminName, setAdminName] = useState('');

  useEffect(() => {
    async function fetchAdminName() {
      try {
        const { accountSettingsDetails } = await settingFunctions.fetchAdminInfo();
        if (accountSettingsDetails?.name) {
          setAdminName(accountSettingsDetails.name);
        }
      } catch (error) {
        console.error("Failed to fetch admin name", error);
      }
    }
    fetchAdminName();
  }, []);

  const editor = useEditor({
    extensions: [
      StarterKit,
      Placeholder.configure({
        placeholder: 'How can I help you today?',
      }),
      Ai.configure({
        appId: 'j9yjx489',
        autocompletion: true,
        onAuthenticate: (data) => {
          return fetch('/api/tiptap-auth', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
          }).then(response => response.json());
        },
      }),
    ],
    content: '',
    editorProps: {
      attributes: {
        class: 'tiptap-editor',
      },
    },
    onUpdate: ({ editor }) => {
      setIsEditorEmpty(editor.isEmpty);
    },
  });

  // const [popoverActive, setPopoverActive] = useState(false);
  // const [searchQuery, setSearchQuery] = useState('');
  // const [selectedAgent, setSelectedAgent] = useState('Auto');
  // const togglePopoverActive = useCallback(() => setPopoverActive((active) => !active), []);

  // const agentOptions = useMemo(() => [
  //   { title: 'Auto', content: 'Agent will choose for you' },
  //   { title: 'Argus | Vulnerability scanner', content: 'Identifies and shields against attacks' },
  //   { title: 'Sentinel | Source code analyser', content: 'Analyzes source code for patterns' },
  //   { title: 'Hunter | Sensitive data type scanner', content: 'Spots exposed personal information' },
  //   { title: 'Moriarty | API grouping tool', content: 'Clusters APIs into service groups' },
  //   { title: 'Sage | Test false positive finder', content: 'Hunts down misleading test failures' },
  // ], []);

  // const filteredAgents = useMemo(() => {
  //   if (!searchQuery) return agentOptions;
  //   return agentOptions.filter(agent => 
  //     agent.title.toLowerCase().includes(searchQuery.toLowerCase()) || 
  //     agent.content.toLowerCase().includes(searchQuery.toLowerCase())
  //   );
  // }, [agentOptions, searchQuery]);

  // const activator = (
  //   <div
  //     onClick={togglePopoverActive}
  //     onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f3f4f6'}
  //     onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
  //     style={{...toolButtonStyle, display: 'inline-flex', padding: '8px 12px'}}
  //   >
  //     <Sliders size={20} />
  //     <span>Agent: {selectedAgent}</span>
  //   </div>
  // );

  const [toolsPopoverActive, setToolsPopoverActive] = useState(false);
  const toggleToolsPopover = useCallback(() => setToolsPopoverActive((active) => !active), []);
  const [selectedTool, setSelectedTool] = useState(null);

  const toolOptions = useMemo(() => [
    { title: 'Documentation', icon: <BookOpen size={16} /> },
    { title: 'Generate curl command', icon: <Terminal size={16} /> },
    { title: 'Search for CVE', icon: <MagnifyingGlass size={16} /> },
    { title: 'Regex validator', icon: <TextAa size={16} /> },
    { title: 'Create custom test', icon: <TestTube size={16} /> },
  ], []);

  const bubbleMenuStyle = {
    display: 'flex',
    backgroundColor: '#ffffff',
    padding: '4px',
    borderRadius: '8px',
    border: '1px solid #e5e7eb',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)',
  };

  const bubbleMenuButtonStyle = {
    border: 'none',
    backgroundColor: 'transparent',
    cursor: 'pointer',
    padding: '8px 12px',
    fontSize: '14px',
    borderRadius: '6px',
    transition: 'background-color 0.2s',
  };

  const selectedToolChipStyle = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '8px',
    padding: '4px 8px',
    backgroundColor: '#eef4ff',
    border: '1px solid #dbeafe',
    borderRadius: '16px',
    fontSize: '14px',
    color: '#3b82f6',
  };

  const chipCloseButtonStyle = {
      background: 'none',
      border: 'none',
      cursor: 'pointer',
      padding: '0',
      display: 'flex',
      alignItems: 'center',
      color: '#3b82f6'
  };

  const containerStyle = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f9fafb',
    padding: '20px',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    height: 'calc(100vh - 56px)',
  };

  const contentStyle = {
    width: '100%',
    maxWidth: '720px',
    textAlign: 'center'
  };

  const headingStyle = {
    fontSize: '32px',
    fontWeight: '400',
    color: '#000000',
    marginBottom: '40px'
  };

  const inputContainerStyle = {
    display: 'flex',
    flexDirection: 'column',
    border: '1px solid #e5e7eb',
    borderRadius: '24px',
    backgroundColor: '#ffffff',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -2px rgba(0, 0, 0, 0.1)',
    width: '100%',
    padding: '16px 20px',
    justifyContent: 'space-between'
  };

  const editorWrapperStyle = {
    flex: 1,
    width: '100%',
    maxHeight: '400px',
    overflowY: 'auto',
    textAlign: 'left',
    paddingTop: '4px',
    paddingBottom: '4px'
  };

  const footerStyle = {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    width: '100%',
    marginTop: '12px'
  };

  const leftFooterStyle = {
    display: 'flex',
    alignItems: 'center',
    gap: '12px'
  };

  const rightFooterStyle = {
    display: 'flex',
    alignItems: 'center',
    gap: '12px'
  };
  
  const aktoPurple = '#6200EA';

  const submitButtonStyle = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '32px',
    height: '32px',
    backgroundColor: aktoPurple,
    border: 'none',
    borderRadius: '50%',
    cursor: 'pointer',
    color: '#ffffff',
    transition: 'background-color 0.2s, opacity 0.2s',
  };

  const disabledSubmitButtonStyle = {
    ...submitButtonStyle,
    opacity: 0.5,
    cursor: 'not-allowed',
    backgroundColor: aktoPurple,
  };

  const suggestionsContainerStyle = {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '12px',
    justifyContent: 'center',
    marginTop: '32px',
    maxWidth: '720px'
  };

  const suggestionChipStyle = {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    padding: '8px 16px',
    backgroundColor: '#ffffff',
    border: '1px solid #e5e7eb',
    borderRadius: '20px',
    fontSize: '14px',
    color: '#374151',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    fontWeight: '500'
  };

  const suggestions = [
    { text: 'Show all APIs handling sensitive data', icon: <ShieldCheck size={16} /> },
    { text: 'Discover all new API endpoints', icon: <Detective size={16} /> },
    { text: 'Find my most vulnerable APIs', icon: <Bug size={16} /> },
    { text: 'Run security tests on production', icon: <TestTube size={16} /> }
  ];

  return (
    <div style={containerStyle}>
      <div style={contentStyle}>
        <h1 style={headingStyle}>{adminName ? `Welcome back, ${adminName}.` : 'What can I help with?'}</h1>
        
        <div style={inputContainerStyle}>
          {editor && <BubbleMenu editor={editor} tippyOptions={{ duration: 100 }}>
            <div style={bubbleMenuStyle}>
              <button
                style={bubbleMenuButtonStyle}
                onClick={() => editor.chain().focus().aiAsk('make it better').run()}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f3f4f6'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                Make it better
              </button>
              <button
                style={bubbleMenuButtonStyle}
                onClick={() => editor.chain().focus().aiAsk('summarize').run()}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f3f4f6'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                Summarize
              </button>
            </div>
          </BubbleMenu>}

          <div style={editorWrapperStyle}>
            <EditorContent editor={editor} />
          </div>
          
          <div style={footerStyle}>
            <div style={leftFooterStyle}>
              <button 
                style={iconButtonStyle}
                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f3f4f6'}
                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
              >
                <Plus size={20} />
              </button>
              {/* <Popover
                active={popoverActive}
                activator={activator}
                autofocusTarget="first-node"
                onClose={togglePopoverActive}
              >
                <LegacyCard>
                  <LegacyCard.Section>
                    <TextField
                      value={searchQuery}
                      onChange={setSearchQuery}
                      placeholder="Search agents..."
                      clearButton
                      onClearButtonClick={() => setSearchQuery('')}
                    />
                  </LegacyCard.Section>
                  <ActionList
                    actionRole="menuitem"
                    items={filteredAgents.map(agent => ({
                      content: (
                        <div>
                          <Text variant="bodyMd" as="span">{agent.title}</Text>
                          <Text variant="bodySm" as="p" color="subdued">{agent.content}</Text>
                        </div>
                      ),
                      active: selectedAgent === agent.title,
                      suffix: selectedAgent === agent.title ? <Check size={16} /> : undefined,
                      onAction: () => {
                        setSelectedAgent(agent.title);
                        togglePopoverActive();
                      }
                    }))}
                  />
                </LegacyCard>
              </Popover> */}
              <Popover
                active={toolsPopoverActive}
                activator={
                    <div
                        onClick={toggleToolsPopover}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f3f4f6'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                        style={{...toolButtonStyle, display: 'inline-flex', padding: '8px 12px'}}
                    >
                        <Sliders size={20} />
                        <span>Tools</span>
                    </div>
                }
                autofocusTarget="first-node"
                onClose={toggleToolsPopover}
              >
                <ActionList
                    actionRole="menuitem"
                    items={toolOptions.map(tool => ({
                        content: tool.title,
                        onAction: () => {
                            setSelectedTool(tool);
                            toggleToolsPopover();
                        }
                    }))}
                />
              </Popover>
              {selectedTool && (
                <div style={selectedToolChipStyle}>
                    {selectedTool.icon}
                    <span>{selectedTool.title}</span>
                    <button
                        style={chipCloseButtonStyle}
                        onClick={() => setSelectedTool(null)}
                    >
                        <X size={16} />
                    </button>
                </div>
              )}
            </div>
            <div style={rightFooterStyle}>
              <button 
                style={isEditorEmpty ? disabledSubmitButtonStyle : submitButtonStyle}
                disabled={isEditorEmpty}
                onMouseEnter={(e) => {
                  if (!isEditorEmpty) {
                    e.currentTarget.style.backgroundColor = '#4d00ba';
                  }
                }}
                onMouseLeave={(e) => {
                  if (!isEditorEmpty) {
                    e.currentTarget.style.backgroundColor = aktoPurple;
                  }
                }}
              >
                <ArrowUp size={18} weight="bold" />
              </button>
            </div>
          </div>
        </div>

        <div style={suggestionsContainerStyle}>
          {suggestions.map((suggestion, index) => (
            <button
              key={index}
              style={suggestionChipStyle}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = '#f8fafc';
                e.currentTarget.style.borderColor = '#d1d5db';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#ffffff';
                e.currentTarget.style.borderColor = '#e5e7eb';
              }}
              onClick={() => editor.commands.setContent(suggestion.text)}
            >
              {suggestion.icon}
              <span>{suggestion.text}</span>
            </button>
          ))}
        </div>

        <style jsx global>{`
          .Polaris-Popover__Content {
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            min-width: 340px;
          }
          .Polaris-ActionList__Item {
            padding: 12px 16px;
          }
          .tiptap-editor .ProseMirror {
            outline: none;
          }
          
          .tiptap-editor .ProseMirror p.is-editor-empty:first-child::before {
            color: #adb5bd;
            content: 'Ask Akto anything about your API security...';
            float: left;
            height: 0;
            pointer-events: none;
          }
        `}</style>
      </div>
    </div>
  );
}

export default SimpleHomePage; 