import { useState, useEffect, useRef, useCallback } from 'react';
import { Icon, Spinner, Text } from '@shopify/polaris';
import { SearchMajor, CancelSmallMinor } from '@shopify/polaris-icons';
import './AktoAISearch.css';

export default function AktoAISearchMock({
    placeholder = "Ask Akto AI for help with APIs, security, testing...",
    className = ""
}) {
    const [query, setQuery] = useState('');
    const [isOpen, setIsOpen] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const [thinking, setThinking] = useState('');
    const [showThinking, setShowThinking] = useState(false);
    const [answer, setAnswer] = useState('');
    const [metrics, setMetrics] = useState([]);
    const [actions, setActions] = useState([]);
    const [sources, setSources] = useState([]);
    const [followUpQuestions, setFollowUpQuestions] = useState([]);

    const inputRef = useRef(null);
    const dropdownRef = useRef(null);

    useEffect(() => {
        function handleClickOutside(event) {
            if (dropdownRef.current && !dropdownRef.current.contains(event.target) &&
                inputRef.current && !inputRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        }
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            return () => document.removeEventListener('mousedown', handleClickOutside);
        }
    }, [isOpen]);

    useEffect(() => {
        function handleEscape(event) {
            if (event.key === 'Escape') {
                setIsOpen(false);
                setIsLoading(false);
            }
        }
        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, []);

    const generateMockResponse = useCallback(async (queryText) => {
        const queryLower = queryText.toLowerCase();
        let answerText = '';
        let thinkingText = '';
        let metricsArr = [];
        let actionsArr = [];
        let sourcesArr = [];
        let followUps = [];

        if (queryLower.includes('api security') || queryLower.includes('security')) {
            thinkingText = 'Analyzing security landscape... Checking vulnerability database... Identifying key threats...';
            answerText = '**API security** protects your APIs from threats like:\n\n- **Authentication bypasses** - Unauthorized access\n- **Injection attacks** - SQL, NoSQL, command injection\n- **Data exposure** - Leaking sensitive information\n- **Broken authorization** - Accessing other users\' data\n\nAkto continuously monitors your APIs and runs automated tests to catch these vulnerabilities before attackers do.';
            metricsArr = [
                { label: '1,247', sublabel: 'APIs discovered', status: 'info' },
                { label: '23', sublabel: 'Critical vulnerabilities', status: 'critical' },
                { label: '156', sublabel: 'APIs with sensitive data', status: 'warning' }
            ];
            actionsArr = [
                { text: 'Run security tests on high-risk collections', url: '/dashboard/testing', icon: 'test' },
                { text: 'Review critical vulnerabilities in Testing > Issues', url: '/dashboard/testing/issues', icon: 'alert' },
                { text: 'Enable authentication testing in Test Library', url: '/dashboard/test-library', icon: 'settings' }
            ];
            sourcesArr = [
                { url: 'https://docs.akto.io/api-security/overview', title: 'API Security Overview' },
                { url: 'https://docs.akto.io/testing/owasp-top-10', title: 'OWASP API Top 10' }
            ];
            followUps = ['What are the OWASP API Top 10?', 'How does Akto detect vulnerabilities?', 'What is broken authentication?'];
        } else if (queryLower.includes('risk') || queryLower.includes('high risk')) {
            thinkingText = 'Calculating risk scores... Analyzing collection vulnerabilities... Reviewing sensitive data exposure...';
            answerText = 'Collections are marked **high risk** based on:\n\n- **Vulnerability severity** - Presence of critical/high issues\n- **Sensitive data exposure** - APIs handling PII, credentials, tokens\n- **Authentication weaknesses** - Missing or broken auth\n- **Public exposure** - Externally accessible endpoints\n\nRisk scores range from 0-100, with 70+ considered high risk requiring immediate attention.';
            metricsArr = [
                { label: '12', sublabel: 'High risk collections', status: 'critical' },
                { label: '89', sublabel: 'Avg. risk score', status: 'critical' },
                { label: '45', sublabel: 'APIs need fixes', status: 'warning' }
            ];
            actionsArr = [
                { text: 'Filter collections by risk score in Inventory', url: '/dashboard/inventory', icon: 'filter' },
                { text: 'Run tests on collections with score > 70', url: '/dashboard/testing', icon: 'test' },
                { text: 'Review sensitive parameter detections', url: '/dashboard/inventory/sensitive-data', icon: 'eye' },
                { text: 'Check APIs missing authentication', url: '/dashboard/inventory', icon: 'lock' }
            ];
            sourcesArr = [
                { url: 'https://docs.akto.io/risk-scoring', title: 'Risk Scoring' },
                { url: 'https://docs.akto.io/testing/run-test', title: 'Running Tests' }
            ];
            followUps = ['How do I reduce my risk score?', 'What vulnerabilities are most critical?', 'How often should I run tests?'];
        } else if (queryLower.includes('discovery')) {
            thinkingText = 'Scanning API inventory... Identifying shadow APIs... Analyzing traffic patterns...';
            answerText = '**API Discovery** automatically finds all APIs by analyzing:\n\n- **Traffic patterns** - HTTP/HTTPS requests and responses\n- **Endpoints** - URLs, methods, parameters\n- **Authentication** - Auth headers, tokens, cookies\n- **Data types** - Request/response schemas and sensitive data\n\nNo manual configuration needed - just connect your traffic source.';
            metricsArr = [
                { label: '1,247', sublabel: 'APIs discovered', status: 'info' },
                { label: '89', sublabel: 'Shadow APIs found', status: 'warning' },
                { label: '34', sublabel: 'New APIs (7 days)', status: 'attention' }
            ];
            actionsArr = [
                { text: 'View all discovered APIs in Inventory', url: '/dashboard/inventory', icon: 'view' },
                { text: 'Check shadow APIs (undocumented)', url: '/dashboard/inventory', icon: 'alert' },
                { text: 'Set up traffic mirroring in Settings > Sources', url: '/dashboard/settings', icon: 'settings' },
                { text: 'Review sensitive data detections', url: '/dashboard/inventory/sensitive-data', icon: 'eye' }
            ];
            sourcesArr = [
                { url: 'https://docs.akto.io/api-inventory/api-discovery', title: 'API Discovery' },
                { url: 'https://docs.akto.io/traffic-sources', title: 'Traffic Sources' }
            ];
            followUps = ['How do I set up traffic collection?', 'What are shadow APIs?', 'Can I manually add APIs?'];
        } else if (queryLower.includes('test') || queryLower.includes('testing')) {
            thinkingText = 'Reviewing test library... Checking test results... Analyzing vulnerability patterns...';
            answerText = '**Security testing** checks for vulnerabilities:\n\n- **Authentication** - Bypass, broken auth, weak tokens\n- **Authorization** - BOLA, privilege escalation\n- **Data exposure** - Sensitive data leaks\n- **Injection** - SQL, NoSQL, command injection\n- **Business logic** - Rate limiting, workflow flaws\n\nTests run automatically or on-demand, with results prioritized by risk.';
            metricsArr = [
                { label: '2,341', sublabel: 'Tests executed', status: 'info' },
                { label: '23', sublabel: 'Vulnerabilities found', status: 'critical' },
                { label: '156', sublabel: 'Tests in queue', status: 'attention' }
            ];
            actionsArr = [
                { text: 'Run full test suite in Testing > Run Tests', url: '/dashboard/testing', icon: 'test' },
                { text: 'Review failed tests in Testing > Issues', url: '/dashboard/testing/issues', icon: 'alert' },
                { text: 'Create custom tests in Test Editor', url: '/dashboard/test-editor', icon: 'edit' },
                { text: 'Schedule recurring tests in Settings', url: '/dashboard/settings', icon: 'calendar' }
            ];
            sourcesArr = [
                { url: 'https://docs.akto.io/testing/run-test', title: 'Running Tests' },
                { url: 'https://docs.akto.io/test-library', title: 'Test Library' }
            ];
            followUps = ['How long do tests take?', 'Can I create custom tests?', 'How do I read test results?'];
        } else if (queryLower.includes('collection')) {
            thinkingText = 'Loading collections data... Analyzing grouping strategies... Reviewing risk metrics...';
            answerText = '**Collections** group related APIs:\n\n- **Auto-grouped** - By domain, service, or auth\n- **Custom collections** - Manual organization\n- **Risk metrics** - Aggregate security scores\n- **Testing** - Run tests on entire collection\n\nCollections help you manage APIs at scale and track security by service.';
            metricsArr = [
                { label: '47', sublabel: 'Total collections', status: 'info' },
                { label: '12', sublabel: 'High risk', status: 'critical' },
                { label: '8', sublabel: 'Untested', status: 'warning' }
            ];
            actionsArr = [
                { text: 'View all collections in Inventory > Collections', url: '/dashboard/inventory/collections', icon: 'view' },
                { text: 'Create custom collection with filters', url: '/dashboard/inventory/collections', icon: 'add' },
                { text: 'Run tests on untested collections', url: '/dashboard/testing', icon: 'test' },
                { text: 'Review high-risk collection details', url: '/dashboard/inventory/collections', icon: 'alert' }
            ];
            sourcesArr = [
                { url: 'https://docs.akto.io/api-inventory/collections', title: 'Collections' }
            ];
            followUps = ['How do I create a collection?', 'Can I move APIs between collections?', 'What is a deactivated collection?'];
        } else {
            thinkingText = 'Understanding your question... Searching knowledge base... Preparing response...';
            answerText = 'I can help with:\n\n- **API security** - Vulnerabilities, threats, best practices\n- **Risk assessment** - Understanding and reducing risk scores\n- **Testing** - Running tests, interpreting results\n- **Discovery** - Finding and cataloging APIs\n- **Collections** - Organizing and managing APIs\n\nAsk specific questions for detailed guidance.';
            actionsArr = [
                { text: 'Explore API Inventory to see all discovered APIs', url: '/dashboard/inventory', icon: 'view' },
                { text: 'Run your first security test in Testing', url: '/dashboard/testing', icon: 'test' },
                { text: 'Check risk scores in Dashboard', url: '/dashboard', icon: 'chart' }
            ];
            sourcesArr = [
                { url: 'https://docs.akto.io', title: 'Documentation' },
                { url: 'https://docs.akto.io/getting-started', title: 'Getting Started' }
            ];
            followUps = ['What is API security?', 'What is API discovery?', 'How do I run tests?'];
        }

        // Show thinking process
        setShowThinking(true);
        const thinkingWords = thinkingText.split(' ');
        for (let i = 0; i < thinkingWords.length; i++) {
            setThinking(prev => prev + thinkingWords[i] + (i < thinkingWords.length - 1 ? ' ' : ''));
            await new Promise(resolve => setTimeout(resolve, 50));
        }

        // Wait a bit before showing answer
        await new Promise(resolve => setTimeout(resolve, 300));
        setShowThinking(false);
        setThinking('');

        // Simulate streaming answer
        const words = answerText.split(' ');
        for (let i = 0; i < words.length; i++) {
            setAnswer(prev => prev + words[i] + (i < words.length - 1 ? ' ' : ''));
            await new Promise(resolve => setTimeout(resolve, 30));
        }

        setMetrics(metricsArr);
        setActions(actionsArr);
        setSources(sourcesArr);
        setFollowUpQuestions(followUps);
    }, []);

    const handleSubmit = useCallback(async (e) => {
        e?.preventDefault();
        if (!query.trim() || isLoading) return;

        setIsLoading(true);
        setAnswer('');
        setThinking('');
        setShowThinking(false);
        setMetrics([]);
        setActions([]);
        setSources([]);
        setFollowUpQuestions([]);
        setIsOpen(true);

        try {
            await generateMockResponse(query);
        } catch (err) {
            console.error('Error:', err);
        } finally {
            setIsLoading(false);
        }
    }, [query, isLoading, generateMockResponse]);

    const handleFollowUpClick = useCallback((question) => {
        setQuery(question);
        setTimeout(() => {
            setIsLoading(true);
            setAnswer('');
            setMetrics([]);
            setActions([]);
            setSources([]);
            setFollowUpQuestions([]);
            generateMockResponse(question).finally(() => setIsLoading(false));
        }, 0);
    }, [generateMockResponse]);

    // Format answer with markdown-like styling
    const formatAnswer = (text) => {
        return text.split('\n').map((line, i) => {
            // Bullet points (must check before bold to handle bold within bullets)
            if (line.startsWith('- ')) {
                const content = line.substring(2);
                if (content.includes('**')) {
                    const parts = content.split('**');
                    return (
                        <li key={i} className="akto-ai-bullet">
                            {parts.map((part, j) =>
                                j % 2 === 0 ? part : <strong key={j}>{part}</strong>
                            )}
                        </li>
                    );
                }
                return <li key={i} className="akto-ai-bullet">{content}</li>;
            }
            // Bold text
            if (line.includes('**')) {
                const parts = line.split('**');
                return (
                    <p key={i} className="akto-ai-answer-line">
                        {parts.map((part, j) =>
                            j % 2 === 0 ? part : <strong key={j}>{part}</strong>
                        )}
                    </p>
                );
            }
            // Empty lines
            if (!line.trim()) {
                return <div key={i} className="akto-ai-spacer" />;
            }
            return <p key={i} className="akto-ai-answer-line">{line}</p>;
        });
    };

    return (
        <div className={`akto-ai-search ${className}`}>
            <div className="akto-ai-search__input-wrapper" ref={inputRef}>
                <div className="akto-ai-search__input-container">
                    <div className="akto-ai-search__icon">
                        <Icon source={SearchMajor} color="base" />
                    </div>
                    <input
                        type="text"
                        className="akto-ai-search__input"
                        placeholder={placeholder}
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleSubmit(e)}
                    />
                    <div className="akto-ai-search__actions">
                        {isLoading ? (
                            <div className="akto-ai-search__loading">
                                <Spinner size="small" />
                                <Text variant="bodySm" color="subdued">Thinking...</Text>
                            </div>
                        ) : query ? (
                            <button className="akto-ai-search__clear" onClick={() => { setQuery(''); setIsOpen(false); }}>
                                <Icon source={CancelSmallMinor} />
                            </button>
                        ) : null}
                    </div>
                </div>
            </div>

            {isOpen && (
                <div className="akto-ai-dropdown" ref={dropdownRef}>
                    {isLoading && !answer && !showThinking && (
                        <div className="akto-ai-loading-state">
                            <Spinner size="small" />
                            <Text variant="bodyMd" color="subdued">Searching...</Text>
                        </div>
                    )}

                    {showThinking && (
                        <div className="akto-ai-thinking">
                            <div className="akto-ai-thinking-icon">
                                <Spinner size="small" />
                            </div>
                            <div className="akto-ai-thinking-text">
                                <Text variant="bodySm" color="subdued">{thinking}</Text>
                            </div>
                        </div>
                    )}

                    {answer && (
                        <div className="akto-ai-content">
                            <div className="akto-ai-header">
                                <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
                                    <path d="M10 2L10.5 8.5L13 6L11 10L16 10.5L11 11L13 15L10.5 11.5L10 18L9.5 11.5L7 15L9 11L4 10.5L9 10L7 6L9.5 8.5L10 2Z" fill="#6747ED"/>
                                </svg>
                                <Text variant="headingMd" as="h3">Akto AI</Text>
                            </div>

                            {/* Key Metrics */}
                            {metrics.length > 0 && (
                                <div className="akto-ai-metrics">
                                    {metrics.map((m, i) => (
                                        <div key={i} className={`akto-ai-metric akto-ai-metric--${m.status}`}>
                                            <div className="akto-ai-metric-value">{m.label}</div>
                                            <div className="akto-ai-metric-label">{m.sublabel}</div>
                                        </div>
                                    ))}
                                </div>
                            )}

                            {/* Answer */}
                            <div className="akto-ai-answer">
                                {formatAnswer(answer)}
                            </div>

                            {/* Actions */}
                            {actions.length > 0 && (
                                <div className="akto-ai-actions">
                                    <div className="akto-ai-actions-header">
                                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                                            <path d="M8 3v10M3 8h10" stroke="#6747ED" strokeWidth="1.5" strokeLinecap="round"/>
                                        </svg>
                                        <Text variant="bodyMd" fontWeight="semibold">Next Steps</Text>
                                    </div>
                                    <div className="akto-ai-actions-list">
                                        {actions.map((a, i) => (
                                            <a key={i} href={a.url} className="akto-ai-action-item">
                                                {a.text}
                                            </a>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Follow-ups */}
                            {followUpQuestions.length > 0 && (
                                <div className="akto-ai-followups">
                                    <Text variant="headingSm" as="h4">Related</Text>
                                    <div className="akto-ai-followups-list">
                                        {followUpQuestions.map((q, i) => (
                                            <button key={i} className="akto-ai-followup" onClick={() => handleFollowUpClick(q)}>
                                                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" className="akto-ai-followup-icon">
                                                    <path d="M4 4V10C4 10.5304 4.21071 11.0391 4.58579 11.4142C4.96086 11.7893 5.46957 12 6 12H12M12 12L9 9M12 12L9 15" stroke="#637381" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                                </svg>
                                                {q}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Sources */}
                            {sources.length > 0 && (
                                <div className="akto-ai-sources">
                                    <Text variant="headingSm" as="h4">Sources</Text>
                                    <div className="akto-ai-sources-list">
                                        {sources.map((s, i) => (
                                            <a key={i} href={s.url} target="_blank" rel="noopener noreferrer" className="akto-ai-source">
                                                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                                                    <path d="M3 9L9 3M9 3H5M9 3V7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
                                                </svg>
                                                {s.title}
                                            </a>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
