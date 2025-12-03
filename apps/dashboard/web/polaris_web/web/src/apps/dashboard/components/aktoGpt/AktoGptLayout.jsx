import React, { useEffect, useRef, useState } from 'react'
import { Avatar, Box, Button, HorizontalStack, Icon, Scrollable, Spinner,Text,TextField, Tooltip, VerticalStack } from "@shopify/polaris"
import { SendMajor } from "@shopify/polaris-icons"
import PromptContainer from './PromptContainer'
import "./style.css"
import Store from '../../store'
import func from '@/util/func'
import IntroComponent from './IntroComponent'
import api from './api'
import ResponseComponent from './ResponseComponent'
import { useNavigate } from 'react-router-dom'
import {ClipboardMinor} from '@shopify/polaris-icons';
import { mapLabel, getDashboardCategory } from '../../../main/labelHelper';

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
    const ref = useRef(null)

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

    const addAuthTypes = (name, headerKeys) => {
        const obj = {
            name: name,
            active: true,
            headerConditions: headerKeys,
            payloadConditions: [],
            edit: true
        }
        navigate("/dashboard/settings/auth-types/details", {state: obj})
        closeModal()
    }

    const runTests = () => {
        runCustomTests(response.responses[0].tests)
    }

    return (
        <div style={{display:"flex"}}>
            <div className='left-nav-gpt'>
                <Box padding="3">
                    <Text variant="bodyLg" fontWeight="semibold">Select any prompt</Text>
                </Box>
                <br/>

                <VerticalStack gap="2">
                    {prompts.map((item)=>(
                        <PromptContainer itemObj={item} activePrompt={activePrompt} setActivePrompt={(item) => setSelectedObj(item)} key={item.label}/>
                    ))}
                </VerticalStack>
            </div>

            <div className='message-container'>
                <div style={{flex: 6.5}}>
                    {buttonState > 0 ? <Box ref={chatLogRef}>
                        <Box padding="5" maxWidth="65vw">
                            <HorizontalStack gap="6">
                                <Avatar name={username} initials={func.initials(username)} size="medium"/>
                                <Text variant="bodyMd" fontWeight="semibold" color="subdued">{activePrompt.split("${input}")[0] + inputPrompt}</Text>
                            </HorizontalStack>
                        </Box>
                        <Scrollable shadow style={{height: '35vh'}} focusable>
                            <Box padding="5" maxWidth="65vw" background="bg-subdued">
                                <div className="response-message">
                                    <HorizontalStack gap="6" align="start">
                                        <Avatar name="Akto" source='/public/akto_colored.svg' size="medium"/>
                                        {loading ? <Spinner size="small" /> 
                                            : <ResponseComponent response={func.getResponse(response,queryType)} chatLogRef={chatLogRef} onCompletion={() => handleCompletion()}/>
                                        }
                                    </HorizontalStack>
                                </div>
                            </Box>
                            {buttonState === 2 ? 
                                queryType === "generate_regex" && response?.responses[0]?.regex ?
                                    <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                        <Button primary onClick={addRegex}>Add Regex to Akto</Button>
                                    </div>
                                : queryType === "suggest_tests" && response?.responses[0]?.tests ?
                                <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                    <Button primary onClick={runTests}>{mapLabel('Run tests', getDashboardCategory())} via Akto</Button>
                                </div>
                                : queryType === "find_auth_related_tokens" && response?.responses?.length>0 ?
                                <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                    <Button primary onClick={() => addAuthTypes("", response.responses)}>Add above Auth Types</Button>
                                </div>
                                : queryType === "generate_curl_for_test" && response?.responses[0]?.curl ?
                                <div style={{margin: "auto", marginTop: '10px', width: "30%"}}>
                                    <div ref={ref}/>
                                    <Tooltip content="Copy curl command">
                                        <Button icon={ClipboardMinor} onClick={()=> func.copyToClipboard(response.responses[0].curl, ref)} />
                                    </Tooltip>
                                </div>
                                :null
                                :null
                            }
                        </Scrollable>
                    </Box>
                     : <IntroComponent/>
                    }
                </div>
                <div className='input-gpt'>
                    <TextField 
                        prefix={<Text color="subdued">{activePrompt.split("${input}")[0]}</Text>} 
                        suffix={
                            <div {...checkQuery() ? null : {style: {background: "#19C37D", padding: "4px", borderRadius: "4px"}}}>
                                <Button plain disabled={checkQuery()} onClick={handleClick} icon={SendMajor}/>
                            </div>
                        } 
                        placeholder={placeHolderText}
                        {...activePrompt.includes("${input}") ? {onChange: setInputPrompt} : null}
                        value={inputPrompt}
                    />
                </div>
            </div>
        </div>
    )
}

export default AktoGptLayout