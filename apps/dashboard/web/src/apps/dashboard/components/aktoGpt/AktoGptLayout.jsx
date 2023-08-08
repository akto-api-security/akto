import React, { useEffect, useRef, useState } from 'react'
import { Avatar, Box, Button, Icon, Scrollable, Spinner, Text, TextField, VerticalStack } from "@shopify/polaris"
import { ConversationMinor, SendMajor } from "@shopify/polaris-icons"
import PromptContainer from './PromptContainer'
import "./style.css"
import Store from '../../store'
import func from '@/util/func'
import IntroComponent from './IntroComponent'
import api from './api'
import ResponseComponent from './ResponseComponent'

function AktoGptLayout({prompts, apiCollectionId}) {

    const [activePrompt, setActivePrompt] = useState("")
    const [currentObj, setCurrentObj] = useState(null)
    const [inputPrompt, setInputPrompt] = useState("")
    const username = Store((state) => state.username)
    const [loading, setLoading] = useState(false)
    const [response, setResponse] = useState(null)
    const [queryType,setQueryType] = useState(null)

    const [buttonState, setButtonState] = useState(0)

    const chatLogRef = useRef(null);

    const setSelectedObj = (item) => {
        setCurrentObj(item)
        setActivePrompt(item.label)
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
                setResponse(resp.response)
                setQueryType(resp.type)
                setButtonState(2)
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
        if(buttonState !== 0 || activePrompt.length === 0 || (activePrompt.includes("${input}") && inputPrompt.length === 0)){
            return true
        }
        return false
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
                                        : <ResponseComponent response={func.getResponse(response,queryType)} chatLogRef={chatLogRef}/>
                                    }
                                </div>
                            </div>
                        </Scrollable>
                    </div>
                     : <IntroComponent/>
                    }
                </div>
                <div className='input-gpt'>
                    <TextField 
                        prefix={<span style={{color: "#fff"}}>{activePrompt.split("${input}")[0]}</span>} 
                        suffix={<Button plain icon={SendMajor} disabled={checkQuery()} onClick={handleClick}/>} 
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