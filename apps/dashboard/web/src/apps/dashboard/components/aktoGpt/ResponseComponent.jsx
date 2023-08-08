import React, { useRef, useState } from 'react'
import BotResponse from './BotResponse'
import { VerticalStack } from '@shopify/polaris'

function ResponseComponent({response,chatLogRef}) {

    const [currentResponseIndex, setCurrentResponseIndex] = useState(-1);
    const [currentItemIndex, setCurrentItemIndex] = useState(0)
    const [singlePrint, setSinglePrint] = useState(false)
    const pre = useRef([])

    const handleResponseComplete = () => {
        if (currentItemIndex < response?.responses?.length) {
            if(currentResponseIndex === -1){
                setCurrentResponseIndex(0)
            }
            else if(currentResponseIndex < response?.responses[currentItemIndex]?.apis.length - 1){
                setCurrentResponseIndex(currentResponseIndex + 1)
            }else{
                setCurrentResponseIndex(-1)
                setCurrentItemIndex(currentItemIndex + 1)
            }
        }
        pre.current = [...pre.current, component(response?.responses, currentItemIndex, currentResponseIndex, chatLogRef, ()=>{})]
    };

    const handleString = () => {
        if(!singlePrint){
            setSinglePrint(true)
        }
        pre.current = [...pre.current,errorComp(response.message,singlePrint, chatLogRef, ()=>{})]
    }

    const handleArrResponse = () => {
        if (currentItemIndex < response?.responses?.length) {
            setCurrentItemIndex(currentItemIndex + 1)
        }
        pre.current = [...pre.current, arrComponent(response?.responses, currentItemIndex, chatLogRef, ()=>{})]
    }

    return (
        <VerticalStack gap="2">
            {response.responses ?
                response.responses[0].functionality ?
                    [...pre.current, component(response?.responses, currentItemIndex, currentResponseIndex, chatLogRef, handleResponseComplete)]
                :   [...pre.current, arrComponent(response?.responses, currentItemIndex, chatLogRef, handleArrResponse)]
                : 
                response.message ?
                [...pre.current, errorComp(response?.message, singlePrint, chatLogRef, handleString)]
                : null
            }
        </VerticalStack>
    );
}

function component(response, currentRow, currIndex, chatLogRef, handleResponseComplete){
    if(currentRow < response.length){
        let prompt = ""
        let isTitle = false
        if(currIndex === -1){
            prompt = response[currentRow].functionality.toUpperCase()
            isTitle = true
        }else{
            prompt = response[currentRow].apis[currIndex]
        }
        return (
            <BotResponse
                response={prompt}
                chatLogRef={chatLogRef}
                onComplete={handleResponseComplete}
                isTitle={isTitle}
            />
        )
    }
}

function errorComp(strPrompt, singlePrint ,chatLogRef, handleString,){
    if(!singlePrint){
        return(
            <BotResponse
                onComplete={handleString}
                response={strPrompt}
                chatLogRef={chatLogRef}
            />
        )
    }
}

function arrComponent(responses, currentItemIndex, chatLogRef, handleArrResponse){
    let prompt = ""
    if(currentItemIndex < responses.length){
        prompt = responses[currentItemIndex]
    }
    return(
        <BotResponse
            chatLogRef={chatLogRef}
            response={prompt}
            onComplete={handleArrResponse}
        />
    )
}

export default ResponseComponent