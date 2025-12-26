import React, { useState, useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { isReasoningUIPart } from 'ai'
import AskAktoChatInput from '../../components/shared/AskAktoChatInput'
import ReasoningDisplay from '../../components/shared/ReasoningDisplay'
import SuggestionsComponent from '../../components/shared/SuggestionsComponent'
import HistorySidebar from '../../components/shared/HistorySidebar'
import SessionStore from '../../../main/SessionStore'
import Store from '../../../dashboard/store'
import './AskAktoConversation.css'

function AskAktoConversation() {
    const location = useLocation()
    const navigate = useNavigate()
    const [userMessage, setUserMessage] = useState('')
    const [followUpInput, setFollowUpInput] = useState('')
    const [response, setResponse] = useState('')
    const [reasoning, setReasoning] = useState('')
    const [isStreamingReasoning, setIsStreamingReasoning] = useState(false)
    const [showThoughtLabel, setShowThoughtLabel] = useState(false)
    const [showAnalysis, setShowAnalysis] = useState(false)
    const [showButtons, setShowButtons] = useState(false)
    const [showSuggestions, setShowSuggestions] = useState(false)
    const [isTypingResponse, setIsTypingResponse] = useState(false)
    const [conversationSummary, setConversationSummary] = useState('')
    const [showHistory, setShowHistory] = useState(false)
    
    const username = Store((state) => state.username)

    // Get the user's question from the navigation state
    useEffect(() => {
        if (location.state?.message) {
            setUserMessage(location.state.message)
            startThinkingProcess(location.state.message)
            // Generate a summary of the conversation
            generateConversationSummary(location.state.message)
        }
    }, [location.state])

    const generateConversationSummary = (message) => {
        // Simple summary generation (in real implementation, this would use AI SDK)
        const words = message.split(' ')
        if (words.length <= 6) {
            setConversationSummary(message)
        } else {
            setConversationSummary(words.slice(0, 6).join(' ') + '...')
        }
    }

    // Reasoning steps that would come from AI SDK
    const reasoningSteps = [
        "Searching for agentic security best practices...",
        "Analyzing current security frameworks...",
        "Cross-referencing with threat models...",
        "Identifying key guardrails...",
        "Compiling security recommendations..."
    ]

    const startThinkingProcess = (message) => {
        // Step 1: Start chain of thought
        setIsStreamingReasoning(true)
        
        // Simulate streaming reasoning steps
        let currentStep = 0
        const reasoningInterval = setInterval(() => {
            if (currentStep < reasoningSteps.length) {
                setReasoning(reasoningSteps.slice(0, currentStep + 1).join('\n'))
                currentStep++
            } else {
                clearInterval(reasoningInterval)
                setIsStreamingReasoning(false)
                
                // Step 2: Show "Thought for 12 secs" after reasoning
                setTimeout(() => {
                    setShowThoughtLabel(true)
                    
                    // Step 3: Show analysis after thought label
                    setTimeout(() => {
                        setShowAnalysis(true)
                        setIsTypingResponse(true)
                        typeResponse(getDummyResponse(message))
                    }, 500)
                }, 1000)
            }
        }, 1000)
    }

    const typeResponse = (text) => {
        let i = 0
        const timer = setInterval(() => {
            if (i <= text.length) {
                setResponse(text.slice(0, i))
                i++
            } else {
                clearInterval(timer)
                setIsTypingResponse(false)
                
                // Step 4: Show buttons after analysis is complete
                setTimeout(() => {
                    setShowButtons(true)
                    
                    // Step 5: Show suggestions after buttons
                    setTimeout(() => {
                        setShowSuggestions(true)
                    }, 500)
                }, 500)
            }
        }, 30)
    }

    const getDummyResponse = (message) => {
        return `**Weekly Agentic Risk Summary**

**Scope analyzed**
 14 active agent workflows
 7 day observation window

**Key risks identified**  
 2 agents (14%) executing tools or APIs without execution guardrails
 1 agent (7%) accessing sensitive customer data without output redaction
 1 newly discovered agent with elevated execution privileges
 1 shadow agent without an assigned owner

**Threat activity observed**
  3 prompt injection attempts detected
  100% of attempts blocked before execution

**Posture change**
 Overall agentic risk increased week over week
 Primary drivers: new agents and expanded execution scope

**Top priorities**
 Apply execution guardrails to the 2 highest risk agents
 Enforce output redaction on agents handling sensitive data
 Assign ownership and review permissions for shadow agents`
    }

    const handleFollowUpSubmit = (message, attachments) => {
        // Handle follow-up message
        console.log('Follow-up:', message, attachments)
    }

    const followUpSuggestions = [
        "Which agents should I secure first and why?",
        "What actions can these high risk agents perform if compromised?", 
        "Apply recommended guardrails to the highest risk agents"
    ]

    const handleFollowUpSuggestionClick = (suggestion) => {
        // Populate the chat input with the suggestion text
        setFollowUpInput(suggestion)
    }

    const handleEditClick = () => {
        navigate('/dashboard/ask-akto')
    }

    const toggleHistory = () => {
        setShowHistory(!showHistory)
    }

    return (
        <div className="ask-akto-conversation">
            <div className="conversation-header">
                <h1>{conversationSummary || 'Executive report on agentic risks'}</h1>
                <div className="header-actions">
                    <button className="action-btn" onClick={toggleHistory} title="History">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20" fill="none">
                            <path d="M10.625 6.25002V9.64612L13.4469 11.3391C13.589 11.4245 13.6914 11.5628 13.7316 11.7237C13.7717 11.8845 13.7463 12.0548 13.6609 12.1969C13.5756 12.339 13.4372 12.4414 13.2764 12.4816C13.1155 12.5217 12.9453 12.4963 12.8031 12.411L9.67812 10.536C9.58563 10.4804 9.50911 10.4018 9.45599 10.3079C9.40287 10.214 9.37497 10.1079 9.375 10V6.25002C9.375 6.08426 9.44085 5.92529 9.55806 5.80808C9.67527 5.69087 9.83424 5.62502 10 5.62502C10.1658 5.62502 10.3247 5.69087 10.4419 5.80808C10.5592 5.92529 10.625 6.08426 10.625 6.25002ZM10 2.50002C9.01406 2.49757 8.03742 2.69067 7.12661 3.06817C6.21579 3.44566 5.38889 4.00005 4.69375 4.69924C4.12578 5.27424 3.62109 5.82737 3.125 6.40627V5.00002C3.125 4.83426 3.05915 4.67529 2.94194 4.55808C2.82473 4.44087 2.66576 4.37502 2.5 4.37502C2.33424 4.37502 2.17527 4.44087 2.05806 4.55808C1.94085 4.67529 1.875 4.83426 1.875 5.00002V8.12502C1.875 8.29078 1.94085 8.44975 2.05806 8.56697C2.17527 8.68418 2.33424 8.75002 2.5 8.75002H5.625C5.79076 8.75002 5.94973 8.68418 6.06694 8.56697C6.18415 8.44975 6.25 8.29078 6.25 8.12502C6.25 7.95926 6.18415 7.80029 6.06694 7.68308C5.94973 7.56587 5.79076 7.50002 5.625 7.50002H3.82812C4.38672 6.84221 4.94297 6.22268 5.57734 5.58049C6.44598 4.71186 7.55133 4.11847 8.75529 3.87446C9.95924 3.63045 11.2084 3.74665 12.3467 4.20853C13.485 4.67041 14.4619 5.45749 15.1555 6.47144C15.849 7.48538 16.2283 8.68121 16.2461 9.90952C16.2639 11.1378 15.9193 12.3441 15.2554 13.3777C14.5915 14.4113 13.6377 15.2263 12.5132 15.7209C11.3888 16.2155 10.1435 16.3678 8.93299 16.1587C7.72249 15.9496 6.60043 15.3885 5.70703 14.5453C5.64732 14.4889 5.57708 14.4448 5.50032 14.4155C5.42356 14.3862 5.34179 14.3724 5.25967 14.3747C5.17754 14.377 5.09668 14.3955 5.0217 14.429C4.94672 14.4626 4.87908 14.5106 4.82266 14.5703C4.76623 14.63 4.72212 14.7003 4.69283 14.777C4.66355 14.8538 4.64967 14.9356 4.652 15.0177C4.65432 15.0998 4.67279 15.1807 4.70636 15.2557C4.73993 15.3306 4.78795 15.3983 4.84766 15.4547C5.73785 16.2948 6.82012 16.9042 8 17.2298C9.17989 17.5554 10.4215 17.5873 11.6166 17.3226C12.8116 17.058 13.9237 16.505 14.8559 15.7117C15.788 14.9184 16.5118 13.9091 16.9642 12.7718C17.4165 11.6344 17.5836 10.4037 17.4509 9.18689C17.3182 7.97011 16.8897 6.80431 16.2029 5.79122C15.516 4.77813 14.5916 3.94854 13.5104 3.37485C12.4292 2.80117 11.224 2.50082 10 2.50002Z" fill="#374151"/>
                        </svg>
                    </button>
                    <button className="action-btn" onClick={handleEditClick} title="Edit">
                        <svg xmlns="http://www.w3.org/2000/svg" width="15.625" height="15.625" viewBox="0 0 16 16" fill="none">
                            <path d="M15.4422 2.6833L12.9422 0.183304C12.8841 0.125194 12.8152 0.0790944 12.7393 0.0476417C12.6635 0.016189 12.5821 0 12.5 0C12.4179 0 12.3365 0.016189 12.2607 0.0476417C12.1848 0.0790944 12.1159 0.125194 12.0578 0.183304L4.55781 7.6833C4.49979 7.74139 4.45378 7.81034 4.42241 7.88621C4.39105 7.96208 4.37494 8.04339 4.375 8.12549V10.6255C4.375 10.7913 4.44085 10.9502 4.55806 11.0674C4.67527 11.1846 4.83424 11.2505 5 11.2505H7.5C7.5821 11.2506 7.66341 11.2344 7.73928 11.2031C7.81515 11.1717 7.8841 11.1257 7.94219 11.0677L15.4422 3.56768C15.5003 3.50963 15.5464 3.4407 15.5779 3.36483C15.6093 3.28896 15.6255 3.20763 15.6255 3.12549C15.6255 3.04336 15.6093 2.96203 15.5779 2.88615C15.5464 2.81028 15.5003 2.74135 15.4422 2.6833ZM7.24141 10.0005H5.625V8.38409L10.625 3.38409L12.2414 5.00049L7.24141 10.0005ZM13.125 4.1169L11.5086 2.50049L12.5 1.50909L14.1164 3.12549L13.125 4.1169ZM15 8.12549V14.3755C15 14.707 14.8683 15.025 14.6339 15.2594C14.3995 15.4938 14.0815 15.6255 13.75 15.6255H1.25C0.918479 15.6255 0.600537 15.4938 0.366116 15.2594C0.131696 15.025 0 14.707 0 14.3755V1.87549C0 1.54397 0.131696 1.22603 0.366116 0.991608C0.600537 0.757188 0.918479 0.625492 1.25 0.625492H7.5C7.66576 0.625492 7.82473 0.69134 7.94194 0.80855C8.05915 0.92576 8.125 1.08473 8.125 1.25049C8.125 1.41625 8.05915 1.57522 7.94194 1.69243C7.82473 1.80964 7.66576 1.87549 7.5 1.87549H1.25V14.3755H13.75V8.12549C13.75 7.95973 13.8158 7.80076 13.9331 7.68355C14.0503 7.56634 14.2092 7.50049 14.375 7.50049C14.5408 7.50049 14.6997 7.56634 14.8169 7.68355C14.9342 7.80076 15 7.95973 15 8.12549Z" fill="#374151"/>
                        </svg>
                    </button>
                </div>
            </div>

            <div className="conversation-content">
                {/* Display user question */}
                {userMessage && (
                    <div className="user-question">
                        <div className="user-message-bubble">
                            {userMessage}
                        </div>
                    </div>
                )}


                {/* Step 1: Chain of thought (reasoning) - hide when analysis is shown */}
                {reasoning && !showAnalysis && (
                    <ReasoningDisplay 
                        reasoning={reasoning}
                        isStreaming={isStreamingReasoning}
                        isCollapsed={false}
                    />
                )}

                {/* Step 2: "Thought for 12 secs" label */}
                {showThoughtLabel && (
                    <div className="thought-label">Thought for 12 secs</div>
                )}

                {/* Step 3: Analysis/Response */}
                {showAnalysis && (
                    <div className="response-section">
                        <div className="response-content">
                            <div dangerouslySetInnerHTML={{ __html: response.replace(/\n/g, '<br>').replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') }} />
                        </div>
                        
                        {isTypingResponse && (
                            <div className="typing-indicator">
                                <span className="typing-dots">
                                    <span></span>
                                    <span></span>
                                    <span></span>
                                </span>
                            </div>
                        )}
                        
                        {/* Step 4: Action buttons (5 buttons) */}
                        {showButtons && (
                            <div className="response-actions">
                                <button className="action-icon" title="Copy">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                        <path d="M11.8125 1.75H4.8125C4.69647 1.75 4.58519 1.79609 4.50314 1.87814C4.42109 1.96019 4.375 2.07147 4.375 2.1875V4.375H2.1875C2.07147 4.375 1.96019 4.42109 1.87814 4.50314C1.79609 4.58519 1.75 4.69647 1.75 4.8125V11.8125C1.75 11.9285 1.79609 12.0398 1.87814 12.1219C1.96019 12.2039 2.07147 12.25 2.1875 12.25H9.1875C9.30353 12.25 9.41481 12.2039 9.49686 12.1219C9.57891 12.0398 9.625 11.9285 9.625 11.8125V9.625H11.8125C11.9285 9.625 12.0398 9.57891 12.1219 9.49686C12.2039 9.41481 12.25 9.30353 12.25 9.1875V2.1875C12.25 2.07147 12.2039 1.96019 12.1219 1.87814C12.0398 1.79609 11.9285 1.75 11.8125 1.75ZM8.75 11.375H2.625V5.25H8.75V11.375ZM11.375 8.75H9.625V4.8125C9.625 4.69647 9.57891 4.58519 9.49686 4.50314C9.41481 4.42109 9.30353 4.375 9.1875 4.375H5.25V2.625H11.375V8.75Z" fill="#374151"/>
                                    </svg>
                                </button>
                                <button className="action-icon" title="Like">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                        <path d="M12.7969 4.38156C12.6737 4.24196 12.5222 4.13016 12.3525 4.0536C12.1828 3.97704 11.9987 3.93746 11.8125 3.9375H8.75V3.0625C8.75 2.48234 8.51953 1.92594 8.1093 1.5157C7.69906 1.10547 7.14266 0.875 6.5625 0.875C6.48122 0.874942 6.40153 0.897526 6.33237 0.940221C6.26321 0.982916 6.20731 1.04403 6.17094 1.11672L4.10484 5.25H1.75C1.51794 5.25 1.29538 5.34219 1.13128 5.50628C0.967187 5.67038 0.875 5.89294 0.875 6.125V10.9375C0.875 11.1696 0.967187 11.3921 1.13128 11.5562C1.29538 11.7203 1.51794 11.8125 1.75 11.8125H11.1563C11.476 11.8126 11.7848 11.696 12.0247 11.4845C12.2645 11.2731 12.4189 10.9813 12.4589 10.6641L13.1152 5.41406C13.1384 5.22923 13.1221 5.04157 13.0672 4.86354C13.0123 4.68552 12.9202 4.52122 12.7969 4.38156ZM1.75 6.125H3.9375V10.9375H1.75V6.125ZM12.2467 5.30469L11.5905 10.5547C11.5771 10.6604 11.5257 10.7577 11.4457 10.8282C11.3658 10.8987 11.2628 10.9375 11.1563 10.9375H4.8125V5.79086L6.82008 1.77516C7.11763 1.83471 7.38535 1.99552 7.57768 2.23023C7.77002 2.46495 7.87509 2.75905 7.875 3.0625V4.375C7.875 4.49103 7.92109 4.60231 8.00314 4.68436C8.08519 4.76641 8.19647 4.8125 8.3125 4.8125H11.8125C11.8746 4.81248 11.936 4.82567 11.9926 4.8512C12.0491 4.87673 12.0997 4.91401 12.1407 4.96056C12.1818 5.00712 12.2125 5.06188 12.2308 5.12122C12.249 5.18055 12.2545 5.24309 12.2467 5.30469Z" fill="#374151"/>
                                    </svg>
                                </button>
                                <button className="action-icon" title="Dislike">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                        <path d="M13.1152 8.58594L12.4589 3.33594C12.4189 3.01869 12.2645 2.72694 12.0247 2.51548C11.7848 2.30402 11.476 2.18739 11.1563 2.1875H1.75C1.51794 2.1875 1.29538 2.27969 1.13128 2.44378C0.967187 2.60788 0.875 2.83044 0.875 3.0625V7.875C0.875 8.10706 0.967187 8.32962 1.13128 8.49372C1.29538 8.65781 1.51794 8.75 1.75 8.75H4.10484L6.17094 12.8833C6.20731 12.956 6.26321 13.0171 6.33237 13.0598C6.40153 13.1025 6.48122 13.1251 6.5625 13.125C7.14266 13.125 7.69906 12.8945 8.1093 12.4843C8.51953 12.0741 8.75 11.5177 8.75 10.9375V10.0625H11.8125C11.9988 10.0626 12.1829 10.023 12.3527 9.9464C12.5224 9.86981 12.674 9.75797 12.7972 9.61831C12.9204 9.47864 13.0125 9.31435 13.0673 9.13635C13.1221 8.95835 13.1384 8.77073 13.1152 8.58594ZM3.9375 7.875H1.75V3.0625H3.9375V7.875ZM12.1406 9.0393C12.0999 9.0862 12.0494 9.12373 11.9928 9.14931C11.9361 9.17489 11.8746 9.18792 11.8125 9.1875H8.3125C8.19647 9.1875 8.08519 9.23359 8.00314 9.31564C7.92109 9.39769 7.875 9.50897 7.875 9.625V10.9375C7.87509 11.241 7.77002 11.5351 7.57768 11.7698C7.38535 12.0045 7.11763 12.1653 6.82008 12.2248L4.8125 8.20914V3.0625H11.1563C11.2628 3.06246 11.3658 3.10134 11.4457 3.17183C11.5257 3.24231 11.5771 3.33956 11.5905 3.44531L12.2467 8.69531C12.2549 8.75691 12.2497 8.81955 12.2314 8.87893C12.213 8.93831 12.1821 8.99302 12.1406 9.0393Z" fill="#374151"/>
                                    </svg>
                                </button>
                                <button className="action-icon" title="Share">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                        <path d="M1.31284 7.00024C1.314 5.95631 1.72922 4.95546 2.46739 4.21729C3.20556 3.47912 4.20641 3.0639 5.25034 3.06274H11.1943L10.6283 2.49727C10.5462 2.41518 10.5001 2.30384 10.5001 2.18774C10.5001 2.07165 10.5462 1.9603 10.6283 1.87821C10.7104 1.79612 10.8217 1.75 10.9378 1.75C11.0539 1.75 11.1653 1.79612 11.2474 1.87821L12.5599 3.19071C12.6006 3.23134 12.6328 3.2796 12.6548 3.33271C12.6769 3.38582 12.6882 3.44275 12.6882 3.50024C12.6882 3.55774 12.6769 3.61467 12.6548 3.66778C12.6328 3.72089 12.6006 3.76914 12.5599 3.80977L11.2474 5.12227C11.1653 5.20437 11.0539 5.25049 10.9378 5.25049C10.8217 5.25049 10.7104 5.20437 10.6283 5.12227C10.5462 5.04018 10.5001 4.92884 10.5001 4.81274C10.5001 4.69665 10.5462 4.5853 10.6283 4.50321L11.1943 3.93774H5.25034C4.43839 3.93861 3.65993 4.26155 3.08579 4.83569C2.51165 5.40983 2.18871 6.18828 2.18784 7.00024C2.18784 7.11628 2.14175 7.22756 2.0597 7.3096C1.97766 7.39165 1.86638 7.43774 1.75034 7.43774C1.63431 7.43774 1.52303 7.39165 1.44098 7.3096C1.35894 7.22756 1.31284 7.11628 1.31284 7.00024ZM12.2503 6.56274C12.1343 6.56274 12.023 6.60884 11.941 6.69088C11.8589 6.77293 11.8128 6.88421 11.8128 7.00024C11.812 7.8122 11.489 8.59066 10.9149 9.1648C10.3408 9.73894 9.5623 10.0619 8.75034 10.0627H2.80636L3.37238 9.49728C3.41302 9.45663 3.44527 9.40837 3.46727 9.35526C3.48926 9.30215 3.50059 9.24523 3.50059 9.18774C3.50059 9.13026 3.48926 9.07334 3.46727 9.02023C3.44527 8.96712 3.41302 8.91886 3.37238 8.87821C3.33173 8.83756 3.28347 8.80532 3.23036 8.78332C3.17725 8.76132 3.12033 8.75 3.06284 8.75C3.00536 8.75 2.94844 8.76132 2.89533 8.78332C2.84222 8.80532 2.79396 8.83756 2.75331 8.87821L1.44081 10.1907C1.40014 10.2313 1.36787 10.2796 1.34585 10.3327C1.32383 10.3858 1.3125 10.4427 1.3125 10.5002C1.3125 10.5577 1.32383 10.6147 1.34585 10.6678C1.36787 10.7209 1.40014 10.7691 1.44081 10.8098L2.75331 12.1223C2.79396 12.1629 2.84222 12.1952 2.89533 12.2172C2.94844 12.2392 3.00536 12.2505 3.06284 12.2505C3.12033 12.2505 3.17725 12.2392 3.23036 12.2172C3.28347 12.1952 3.33173 12.1629 3.37238 12.1223C3.41302 12.0816 3.44527 12.0334 3.46727 11.9803C3.48926 11.9272 3.50059 11.8702 3.50059 11.8127C3.50059 11.7553 3.48926 11.6983 3.46727 11.6452C3.44527 11.5921 3.41302 11.5439 3.37238 11.5032L2.80636 10.9377H8.75034C9.79428 10.9366 10.7951 10.5214 11.5333 9.7832C12.2715 9.04502 12.6867 8.04418 12.6878 7.00024C12.6878 6.88421 12.6418 6.77293 12.5597 6.69088C12.4777 6.60884 12.3664 6.56274 12.2503 6.56274Z" fill="#374151"/>
                                    </svg>
                                </button>
                                <button className="action-icon" title="More">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14" fill="none">
                                        <path d="M7.65625 7C7.65625 7.12979 7.61776 7.25667 7.54565 7.36459C7.47354 7.47251 7.37105 7.55663 7.25114 7.6063C7.13122 7.65597 6.99927 7.66896 6.87197 7.64364C6.74467 7.61832 6.62774 7.55582 6.53596 7.46404C6.44418 7.37226 6.38168 7.25533 6.35636 7.12803C6.33104 7.00073 6.34403 6.86878 6.3937 6.74886C6.44337 6.62895 6.52749 6.52646 6.63541 6.45435C6.74333 6.38224 6.87021 6.34375 7 6.34375C7.17405 6.34375 7.34097 6.41289 7.46404 6.53596C7.58711 6.65903 7.65625 6.82595 7.65625 7ZM10.7188 6.34375C10.589 6.34375 10.4621 6.38224 10.3542 6.45435C10.2462 6.52646 10.1621 6.62895 10.1125 6.74886C10.0628 6.86878 10.0498 7.00073 10.0751 7.12803C10.1004 7.25533 10.1629 7.37226 10.2547 7.46404C10.3465 7.55582 10.4634 7.61832 10.5907 7.64364C10.718 7.66896 10.85 7.65597 10.9699 7.6063C11.0898 7.55663 11.1923 7.47251 11.2644 7.36459C11.3365 7.25667 11.375 7.12979 11.375 7C11.375 6.82595 11.3059 6.65903 11.1828 6.53596C11.0597 6.41289 10.8928 6.34375 10.7188 6.34375ZM3.28125 6.34375C3.15146 6.34375 3.02458 6.38224 2.91666 6.45435C2.80874 6.52646 2.72462 6.62895 2.67495 6.74886C2.62528 6.86878 2.61229 7.00073 2.63761 7.12803C2.66293 7.25533 2.72543 7.37226 2.81721 7.46404C2.90899 7.55582 3.02592 7.61832 3.15322 7.64364C3.28052 7.66896 3.41247 7.65597 3.53239 7.6063C3.6523 7.55663 3.75479 7.47251 3.8269 7.36459C3.89901 7.25667 3.9375 7.12979 3.9375 7C3.9375 6.82595 3.86836 6.65903 3.74529 6.53596C3.62222 6.41289 3.4553 6.34375 3.28125 6.34375Z" fill="#374151"/>
                                    </svg>
                                </button>
                            </div>
                        )}

                        {/* Step 5: Follow-up suggestions using reusable component */}
                        {showSuggestions && (
                            <div className="conversation-suggestions-wrapper">
                                <SuggestionsComponent 
                                    suggestions={followUpSuggestions}
                                    onSuggestionClick={handleFollowUpSuggestionClick}
                                />
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Reuse the chat input component */}
            <div className="conversation-footer">
                <AskAktoChatInput 
                    value={followUpInput}
                    onChange={(e) => setFollowUpInput(e.target.value)}
                    onSubmit={handleFollowUpSubmit}
                    placeholder="Ask a follow up..."
                    disabled={isTypingResponse}
                />
            </div>

            {/* History Sidebar */}
            <HistorySidebar 
                isOpen={showHistory}
                onClose={() => setShowHistory(false)}
                onHistoryItemClick={(item) => {
                    console.log('History item clicked:', item)
                    // You can navigate to a different conversation or reload with that data
                }}
            />
        </div>
    )
}

export default AskAktoConversation