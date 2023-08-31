import { List, Text } from '@shopify/polaris';
import React, { useEffect, useState } from 'react'

function BotResponse({response, chatLogRef,onComplete,isTitle}) {
    const [botResponse, setBotResponse] = useState("");
    const [isTypingComplete, setIsTypingComplete] = useState(false);
    useEffect(() => {
        if(response && response.length > 0){
            let index = 1;
            let msg = setInterval(() => {
                setBotResponse(response.slice(0, index));
                if (index >= response.length) {
                    clearInterval(msg);
                    setIsTypingComplete(true);
                    if (onComplete) {
                        onComplete();
                    }
                }
                index++;

                if (chatLogRef !== undefined) {
                    chatLogRef.current.scrollIntoView({
                        behavior: "smooth",
                        block: "end",
                    });
                }
            }, 40);
            return () => clearInterval(msg);
        }
    }, [response]);
    return (
        <>
            {isTitle !== undefined ? 
                isTitle ? 
                    <Text variant='headingMd' as='h4'>
                        {botResponse}
                        {botResponse === response ? "" : "|"}
                    </Text>
                    :
                    <List.Item>
                        {botResponse}
                        {botResponse === response ? "" : "|"}
                    </List.Item>

                    :
                    <Text variant="bodyMd" fontWeight="medium">
                        {botResponse}
                        {botResponse === response ? "" : "|"}
                    </Text>
            }
        </>
    )
}

export default BotResponse