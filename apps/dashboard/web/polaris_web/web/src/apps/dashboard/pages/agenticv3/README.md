# AgenticV3 - Pure Polaris Implementation

A clean, fresh implementation of the Agentic AI interface using **only Polaris components** with **mock API services**.

## 🎯 Key Features

- ✅ **Pure Polaris Components** - No HTML tags, only Shopify Polaris
- ✅ **Mock AI Service** - Simulated streaming responses, no real API calls
- ✅ **Clean Architecture** - Modular, reusable components
- ✅ **Streaming Simulation** - Realistic typing effect for AI responses
- ✅ **Full Feature Parity** - All features from the original implementation

## 📁 Folder Structure

```
agenticv3/
├── components/           # Reusable UI components (Polaris only)
│   ├── UserMessage.jsx
│   ├── ResponseContent.jsx
│   ├── SearchInput.jsx
│   ├── SuggestionsList.jsx
│   └── CopyButton.jsx
├── hooks/               # Custom React hooks
│   └── useAgenticChatV3.js
├── services/            # Mock API services
│   └── mockAIService.js
├── MainPage.jsx         # Landing page with examples
├── ConversationPage.jsx # Chat interface
├── index.js            # Main exports
└── README.md           # This file
```

## 🚀 Usage

### Basic Usage

```jsx
import { MainPage } from '@/apps/dashboard/pages/agenticv3';

function App() {
    return <MainPage />;
}
```

### Using ConversationPage Directly

```jsx
import { ConversationPage } from '@/apps/dashboard/pages/agenticv3';

function App() {
    return (
        <ConversationPage
            initialQuery="Show me all vulnerabilities"
            onBack={() => console.log('Back clicked')}
        />
    );
}
```

### Using the Hook Directly

```jsx
import { useAgenticChatV3 } from '@/apps/dashboard/pages/agenticv3';

function CustomChat() {
    const {
        messages,
        input,
        handleInputChange,
        handleSubmit,
        isLoading,
        append
    } = useAgenticChatV3({
        conversationId: 'my-conversation',
        onFinish: (message) => console.log('Done:', message),
        onError: (error) => console.error('Error:', error)
    });

    // Your custom UI here
}
```

## 🎨 Components

### UserMessage
Displays user messages with avatar and background.

```jsx
<UserMessage content="What are my vulnerabilities?" />
```

### ResponseContent
Displays AI responses with sections, items, and optional time taken.

```jsx
<ResponseContent
    content={{
        title: 'Results',
        sections: [
            {
                header: 'Vulnerabilities',
                items: ['SQL Injection', 'XSS']
            }
        ]
    }}
    timeTaken={3}
/>
```

### SearchInput
Input field with submit button, supports fixed positioning.

```jsx
<SearchInput
    input={input}
    handleInputChange={handleInputChange}
    handleSubmit={handleSubmit}
    isLoading={false}
    placeholder="Ask anything..."
    isFixed={false}
/>
```

### SuggestionsList
Displays clickable suggestion chips.

```jsx
<SuggestionsList
    suggestions={['Tell me more', 'How to fix?']}
    onSuggestionClick={(suggestion) => console.log(suggestion)}
/>
```

### CopyButton
Copies content to clipboard.

```jsx
<CopyButton content="Text or object to copy" />
```

## 🔧 Mock Service

The mock AI service simulates streaming responses without any real API calls.

### Functions

- `streamMockResponse(message, onChunk, onComplete, onError)` - Simulates streaming
- `getMockSuggestions()` - Returns mock suggestions
- `generateConversationId()` - Generates unique conversation IDs
- `saveConversation(id, messages)` - Mock save (console log only)
- `loadConversation(id)` - Mock load (returns empty)

### Customizing Mock Responses

Edit `services/mockAIService.js` to add or modify mock responses:

```javascript
const MOCK_RESPONSES = {
    'your-keyword': {
        title: 'Your Title',
        sections: [
            {
                header: 'Section Header',
                items: ['Item 1', 'Item 2']
            }
        ]
    }
};
```

## 🎭 Design Principles

1. **No HTML Tags** - Everything uses Polaris components
2. **Mock First** - All API calls are mocked for rapid development
3. **Component Isolation** - Each component is self-contained
4. **Clean State Management** - Using React hooks, no external state library
5. **Type Safety Ready** - Structure supports easy TypeScript migration

## 📝 Notes

- All styling is done through Polaris component props
- No inline styles except for absolutely positioned elements
- Streaming simulation uses 50ms delay between chunks
- Response parsing handles both JSON and plain text
- Conversation IDs are generated client-side

## 🔄 Migration Path to Real APIs

When ready to connect to real APIs:

1. Replace `mockAIService.js` with real API calls
2. Update `useAgenticChatV3.js` to use real streaming endpoint
3. Implement proper error handling for network failures
4. Add authentication headers
5. Update response parsing if needed

## 🐛 Troubleshooting

**Issue**: Components not rendering
- Check that Polaris components are properly imported
- Verify Polaris is installed: `@shopify/polaris`

**Issue**: Streaming not working
- Check console for errors in mock service
- Verify chunk delay in `mockAIService.js`

**Issue**: Styles not applying
- Polaris uses its own theming system
- Check Polaris version compatibility

## 📚 Further Reading

- [Shopify Polaris Documentation](https://polaris.shopify.com/)
- [React Hooks Documentation](https://react.dev/reference/react)
