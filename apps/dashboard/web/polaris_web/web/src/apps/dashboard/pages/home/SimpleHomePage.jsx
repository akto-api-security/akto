import React, { useState, useCallback } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import { Plus, Sliders, ArrowUp, ShieldCheck, Detective, Bug, TestTube } from 'phosphor-react';
import { Popover, ActionList } from '@shopify/polaris';

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

  const editor = useEditor({
    extensions: [
      StarterKit,
      Placeholder.configure({
        placeholder: 'Ask Akto anything about your API security...',
      }),
    ],
    content: '',
    editorProps: {
      attributes: {
        class: 'tiptap-editor',
      },
    },
  });

  const [popoverActive, setPopoverActive] = useState(false);
  const togglePopoverActive = useCallback(() => setPopoverActive((active) => !active), []);

  const agentOptions = [
    { content: 'Auto', helpText: 'Agent will choose for you' },
    { content: 'Modern web app', helpText: 'Made with React and Node.js' },
    { content: 'Interactive data app', helpText: 'Made with Streamlit and Python' },
    { content: '3D game', helpText: 'Three.js games and simulations' },
    { content: 'Web app (Python)', helpText: 'Websites with Python backend' },
  ];

  const activator = (
    <button
      style={toolButtonStyle}
      onClick={togglePopoverActive}
      onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#f3f4f6'}
      onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
    >
      <Sliders size={20} />
      <span>Agents</span>
    </button>
  );

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
    minHeight: '140px',
    padding: '16px 20px',
    justifyContent: 'space-between'
  };

  const editorWrapperStyle = {
    flex: 1,
    width: '100%',
    minHeight: '60px',
    maxHeight: '400px',
    overflowY: 'auto',
    textAlign: 'left'
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
  
  const submitButtonStyle = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: '32px',
    height: '32px',
    backgroundColor: '#000000',
    border: 'none',
    borderRadius: '50%',
    cursor: 'pointer',
    color: '#ffffff',
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
        <h1 style={headingStyle}>What can I help with?</h1>
        
        <div style={inputContainerStyle}>
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
              <Popover
                active={popoverActive}
                activator={activator}
                autofocusTarget="first-node"
                onClose={togglePopoverActive}
              >
                <ActionList
                  actionRole="menuitem"
                  items={agentOptions}
                />
              </Popover>
            </div>
            <div style={rightFooterStyle}>
              <button style={submitButtonStyle}>
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
            min-width: 280px;
          }
          .Polaris-ActionList__Item {
            padding: 12px 16px;
          }
          .tiptap-editor .ProseMirror {
            outline: none;
            min-height: 60px;
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