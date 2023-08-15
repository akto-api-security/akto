import React, { useEffect, useRef, useState } from 'react'
import { Avatar, Box, Button, Icon, Scrollable, Spinner,TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { ConversationMinor, SendMajor } from "@shopify/polaris-icons"
import PromptContainer from './PromptContainer'
import "./style.css"
import Store from '../../store'
import func from '@/util/func'
import IntroComponent from './IntroComponent'
import api from './api'
import ResponseComponent from './ResponseComponent'
import { useNavigate } from 'react-router-dom'
import {ClipboardMinor} from '@shopify/polaris-icons';

function AktoGptLayout({prompts,closeModal, runCustomTests}) {

    const [activePrompt, setActivePrompt] = useState("")
    const [currentObj, setCurrentObj] = useState(null)
    const [inputPrompt, setInputPrompt] = useState("")
    const username = Store((state) => state.username)
    const [loading, setLoading] = useState(false)
    const [response, setResponse] = useState(null)
    const [queryType,setQueryType] = useState(null)
    const [allResponse, setAllResponse] = useState(null)

    const [buttonState, setButtonState] = useState(0)

    const navigate = useNavigate()

    const chatLogRef = useRef(null);

    const setSelectedObj = (item) => {
        setCurrentObj(item)
        setActivePrompt(item.label)
    }

    const handleCompletion = () => {
        setTimeout(()=> {
            setButtonState(2)
        },100)
    }

    useEffect(() => {
        if (chatLogRef.current) {
          chatLogRef.current.scrollIntoView({
            behavior: "smooth",
            block: "end",
          });
        }
    },[])

    useEffect(()=> {
        setInputPrompt("")
        setButtonState(0)
    },[activePrompt])

    const placeHolderText = activePrompt.length > 0 ? "" : "Send a message"

    const sendToGpt = async() => {
        if(currentObj){
            const queryPayload = currentObj.prepareQuery(inputPrompt)
            setLoading(true)
            await api.ask_ai(queryPayload).then((resp)=> {
                setLoading(false)
                setAllResponse(resp)
                setResponse(resp.response)
                setQueryType(resp.type)
            }).catch(()=>{
                setLoading(false)
            })
        }
    }

    const handleClick = () => {
        setButtonState(1)
        sendToGpt()
    }

    const checkQuery = () => {
        if(buttonState === 1 || activePrompt.length === 0 || (activePrompt.includes("${input}") && inputPrompt.length === 0)){
            return true
        }
        return false
    }

    const addRegex = () => {
        const regexObj = {
            name: allResponse.meta.input_query.toUpperCase(),
            valueConditions: {
                operator: "OR",
                predicates: [
                    {
                        type: "REGEX",
                        value: response.responses[0].regex
                    }
                ]
            },
        }
        navigate("/dashboard/observe/data-types", {state: {regexObj}})
        closeModal()
    }

    const runTests = () => {
        runCustomTests(response.responses[0].tests)
    }

    return (
        <div style={{display: 'flex'}} className='gpt-container'>
            <div className='left-nav-gpt'>
                <span className='header-left-nav'>
                    <Box>
                        <Icon source={ConversationMinor} color="highlight"/>
                    </Box>
                    Select Any Prompt
                </span>

                <br/>

                <VerticalStack>
                    {prompts.map((item)=>(
                        <PromptContainer itemObj={item} activePrompt={activePrompt} setActivePrompt={(item) => setSelectedObj(item)} key={item.label}/>
                    ))}
                </VerticalStack>
            </div>

            <div className='message-container'>
                <div className='chat-section'>
                    {buttonState > 0 ? <div className='single-prompt' ref={chatLogRef}>
                        <div className='prompt-section'>
                            <Avatar name={username} initials={func.initials(username)} size="medium"/>
                            {activePrompt.split("${input}")[0] + inputPrompt}
                        </div>
                        <Scrollable shadow style={{height: '35vh'}} focusable>
                            <div style={{background: "#444654"}}>
                                <div className='prompt-section fixed-logo'>
                                    <span className='logo-akto'>
                                        <Avatar name="Akto" source='/public/akto_logo.svg' size="medium"/>
                                    </span>
                                    {loading ? <Spinner size="small" /> 
                                        : <ResponseComponent response={func.getResponse(response,queryType)} chatLogRef={chatLogRef} onCompletion={() => handleCompletion()}/>
                                    }
                                </div>
                            </div>
                            {buttonState === 2 ? 
                                queryType === "generate_regex" && response?.responses[0]?.regex ?
                                    <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                        <Button primary onClick={addRegex}>Add Regex to Akto</Button>
                                    </div>
                                : queryType === "suggest_tests" && response?.responses[0]?.tests ?
                                <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                    <Button primary onClick={runTests}>Run tests via Akto</Button>
                                </div>
                                :queryType === "generate_curl_for_test" && response?.responses[0]?.curl ?
                                <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                    <Tooltip content="Copy curl command">
                                        <Button icon={ClipboardMinor} onClick={()=> navigator.clipboard.writeText( response.responses[0].curl)} />
                                    </Tooltip>
                                </div>
                                :null
                                :null
                            }
                        </Scrollable>
                    </div>
                     : <IntroComponent/>
                    }
                </div>
                <div className='input-gpt'>
                    <TextField 
                        prefix={<span style={{color: "#fff"}}>{activePrompt.split("${input}")[0]}</span>} 
                        suffix={
                            <div {...checkQuery() ? null : {style: {background: "#19C37D", padding: "4px", borderRadius: "4px"}}}>
                                <Button plain disabled={checkQuery()} onClick={handleClick} icon={SendMajor}/>
                            </div>
                        } 
                        placeholder={placeHolderText}
                        {...activePrompt.includes("${input}") ? {onChange: setInputPrompt} : null}
                        value={inputPrompt}
                        borderless
                    />
                </div>
            </div>
        </div>
    )
}

export default AktoGptLayout